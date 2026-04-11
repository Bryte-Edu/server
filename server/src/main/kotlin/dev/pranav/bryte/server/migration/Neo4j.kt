package dev.pranav.bryte.server.migration

import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
import ai.koog.prompt.executor.clients.mistralai.MistralAIModels
import dev.pranav.bryte.model.session.DocumentChunk
import dev.pranav.bryte.model.session.DocumentItem
import dev.pranav.bryte.server.MISTRAL_API_KEY
import dev.pranav.bryte.server.NEO4J_PASSWORD
import dev.pranav.bryte.server.NEO4J_URI
import dev.pranav.bryte.server.NEO4J_USERNAME
import dev.pranav.bryte.server.ai.embedding.TextDocumentEmbedder
import dev.pranav.bryte.server.util.ext.documentChunks
import dev.pranav.bryte.server.util.ext.documents
import dev.pranav.bryte.server.util.ext.supabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.css.times
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase
import org.neo4j.driver.Values

fun main() {
    val neo4j = Neo4jManager()
    val docsRepository by supabase.documents()
    val chunksRepository by supabase.documentChunks()

    neo4j.setupIndex()

    runBlocking {
        val embedder = TextDocumentEmbedder(
            LLMEmbedder(
                MistralAILLMClient(MISTRAL_API_KEY),
                MistralAIModels.Embeddings.MistralEmbed
            ), setOf(), chunksRepository
        )


        println("🚀 Step 1: Ingesting ALL Documents...")
        val allDocs = docsRepository.getAll()
        println(allDocs.size.toString() + " documents found.")
//        allDocs.first { it.id == "8479d115-dfd5-4421-b638-b9a1cf978f6c" }.let {
//            println("Generating embeddings for document: ${it.title} (${it.id})")
//
//            val chunks = chunksRepository.getByDocumentId(it.id)
//
//            val embedder = TextDocumentEmbedder(LLMEmbedder(MistralAILLMClient(MISTRAL_API_KEY), MistralAIModels.Embeddings.MistralEmbed), chunks.toSet(), chunksRepository)
//
//
//            for (chunk in chunks) {
//                if (chunk.embedding == null) {
//                    println("🔍 Embedding chunk: ${chunk.id} (${chunk.content.take(100)})")
//                    embedder.embed(chunk.id!!)
//                }
//            }
//        }


        allDocs.forEach { doc ->
            val docChunks = chunksRepository.getByDocumentId(doc.id)
            println("📄 Ingesting document: ${doc.title} (${doc.id}) with ${docChunks.size} chunks...")
            if (docChunks.isNotEmpty()) neo4j.ingestDocument(doc, docChunks)
        }

        // MANDATORY: Wait for the Neo4j background indexer
        println("⏳ Waiting for vector index to process embeddings...")
        var isReady = false
        while (!isReady) {
            delay(2000)
            isReady = neo4j.checkIndexReady()
        }

        println("🔗 Step 2: Inter-linking all chunks...")
        allDocs.forEach { doc ->
            neo4j.interLinkWithDocumentBias(doc.userId)
        }


        val userQueryEmbedding = embedder.embed("how does the machine understand and act by itself?")

        // 2. Query the graph
        val results = neo4j.searchKnowledgeGraph(userQueryEmbedding.values)

        results.forEach { res ->
            // These keys MUST match the RETURN aliases in the Cypher query above
            val source = res["docSource"]
            val header = res["sectionHeader"]
            val score = res["matchScore"]
            val related = res["related"]

            println("Found in [$source]: $header (Score: $score)")
            println("Related Concepts: $related")
            println("-" * 20)
        }
    }
    neo4j.close()
}

class Neo4jManager {
    private val driver = GraphDatabase.driver(
        NEO4J_URI, AuthTokens.basic(NEO4J_USERNAME, NEO4J_PASSWORD)
    )

    fun searchKnowledgeGraph(queryEmbedding: List<Double>, topK: Int = 3): List<Map<String, Any>> {

        driver.session().use { session ->
            return session.executeRead { tx ->
                val cypher = $$"""
    CALL db.index.vector.queryNodes('chunk_embeddings', $topK, $queryEmbedding)
    YIELD node AS startNode, score

    MATCH (doc:Document)-[:HAS_PART]->(startNode)
    OPTIONAL MATCH (startNode)-[r:RELATED_TO]-(neighbor:Chunk)
    
    WITH doc, startNode, score, neighbor, r
    ORDER BY r.weight DESC

RETURN 
    coalesce(doc.name, "Unknown") AS docSource,
    startNode.header AS sectionHeader,
    startNode.content AS sectionContent,
    score AS matchScore,
    collect(DISTINCT {
        topic: neighbor.header,
        type: CASE WHEN r.isInternal THEN 'Same Doc' ELSE 'Cross-Doc Bridge' END
    })[0..3] AS related
""".trimIndent()

                val params = mapOf(
                    "queryEmbedding" to queryEmbedding,
                    "topK" to topK
                )

                tx.run(cypher, params).list().map { it.asMap() }
            }
        }
    }

    fun searchKnowledgeGraphWeighted(
        queryEmbedding: List<Double>,
        userId: String,
        focusDocumentId: String?,
        topK: Int = 6,
        neighborLimit: Int = 5,
        docBias: Double = 0.15,
        crossDocPenalty: Double = 0.05
    ): List<Map<String, Any>> {
        if (queryEmbedding.isEmpty()) return emptyList()

        driver.session().use { session ->
            return session.executeRead { tx ->
                val cypher = $$"""
CALL db.index.vector.queryNodes('chunk_embeddings', $topK, $queryEmbedding)
YIELD node AS startNode, score AS baseScore

MATCH (doc:Document)-[:HAS_PART]->(startNode)
MATCH (:User {id: $userId})-[:OWNS]->(doc)

WITH doc, startNode, baseScore,
     (CASE
        WHEN $focusDocumentId IS NOT NULL AND doc.id = $focusDocumentId THEN baseScore + $$docBias
        WHEN $focusDocumentId IS NOT NULL AND doc.id <> $focusDocumentId THEN baseScore - $$crossDocPenalty
        ELSE baseScore
     END) AS weightedScore

OPTIONAL MATCH (startNode)-[r:RELATED_TO]-(neighbor:Chunk)
WITH doc, startNode, baseScore, weightedScore, neighbor, r
ORDER BY weightedScore DESC, coalesce(r.weight, 0.0) DESC

RETURN
    doc.id AS docId,
    coalesce(doc.name, doc.title, "Unknown") AS docSource,
    startNode.id AS chunkId,
    startNode.header AS sectionHeader,
    startNode.content AS sectionContent,
    baseScore AS matchScore,
    weightedScore AS weightedScore,
    ($focusDocumentId IS NOT NULL AND doc.id = $focusDocumentId) AS isFocusDoc,
    collect(DISTINCT {
        topic: neighbor.header,
        type: CASE WHEN r.isInternal THEN 'Same Doc' ELSE 'Cross-Doc Bridge' END,
        weight: coalesce(r.weight, 0.0)
    })[0..$neighborLimit] AS related
""".trimIndent()

                val params = mapOf(
                    "queryEmbedding" to queryEmbedding,
                    "topK" to topK,
                    "userId" to userId,
                    "focusDocumentId" to focusDocumentId,
                    "docBias" to docBias,
                    "crossDocPenalty" to crossDocPenalty,
                    "neighborLimit" to neighborLimit
                )

                tx.run(cypher, params).list().map { it.asMap() }
            }
        }
    }

    fun ingestDocument(doc: DocumentItem, chunks: List<DocumentChunk>) {
        driver.session().use { session ->
            session.executeWrite { tx ->
                // Ensure User and Document exist
                tx.run(
                    $$"""
                    MERGE (u:User {id: $userId})
                    MERGE (d:Document {id: $docId})
                    ON CREATE SET d.title = $title, d.name = $title
                    MERGE (u)-[:OWNS]->(d)
                """.trimIndent(), Values.parameters(
                        "userId", doc.userId, "docId", doc.id, "title", doc.title
                    )
                )

                // Create Chunks
                chunks.forEach { chunk ->
                    println("   - Ingesting chunk: ${chunk.id} (Pages: ${chunk.pageNumber})")
                    tx.run(
                        $$"""
                        MATCH (d:Document {id: $docId})
                        CREATE (c:Chunk {
                            id: $chunkId,
                            name: $header,
                            header: $header,
                            content: $content,
                            pages: $pages,
                            embedding: $embedding
                        })
                        MERGE (d)-[:HAS_PART]->(c)
                    """.trimIndent(), Values.parameters(
                            "docId",
                            doc.id,
                            "chunkId",
                            chunk.id,
                            "header",
                            chunk.header,
                            "content",
                            chunk.content,
                            "pages",
                            chunk.pageNumber,
                            "embedding",
                            chunk.embedding
                        )
                    ).consume()
                }
            }
        }
    }

    suspend fun interLinkWithDocumentBias(userId: String, threshold: Double = 0.65, bias: Double = 0.15) {
        var isReady = false
        while (!isReady) {
            delay(2000)
            isReady = checkIndexReady()
        }

        driver.session().use { session ->
            session.executeWrite { tx ->
                val cypher = $$"""
                MATCH (u:User {id: $userId})-[:OWNS]->(d1:Document)-[:HAS_PART]->(c1:Chunk)
                
                CALL {
                    WITH c1
                    CALL db.index.vector.queryNodes('chunk_embeddings', 20, c1.embedding)
                    YIELD node AS c2, score
                    RETURN c2, score
                }
                
                // Find document of the neighbor
                MATCH (d2:Document)-[:HAS_PART]->(c2)
                
                // Calculate Weighted Score: Add bias if in same document
                WITH c1, c2, score, d1, d2,
                     (CASE WHEN d1.id = d2.id THEN score + $$bias ELSE score END) AS weightedScore
                
                // Filter by the new weighted score
                WHERE weightedScore >= $$threshold AND c1.id <> c2.id
                
                MERGE (c1)-[r:RELATED_TO]->(c2)
                SET r.weight = weightedScore,
                    r.isInternal = (d1.id = d2.id),
                    r.originalSimilarity = score
            """.trimIndent()

                tx.run(cypher, Values.parameters("userId", userId, "threshold", threshold, "bias", bias)).consume()
            }
        }
    }

    /**
     * The "Graph Concept": Links isolated chunks into a web.
     * Fixed the 'neighborDoc' scoping and Null Pointer for embeddings.
     */
    @Suppress("unused")
    fun interLinkUserChunks(userId: String, threshold: Double = 0.65) {
        driver.session().use { session ->
            session.executeWrite { tx ->
                val cypher = """
                // 1. Find all chunks for the user
                MATCH (u:User {id: $userId})-[:OWNS]->(d:Document)-[:HAS_PART]->(target:Chunk)
                WHERE target.embedding IS NOT NULL
                
                // 2. Search for neighbors (increasing limit to 25 to find potential bridges)
                CALL {
                    WITH target
                    CALL db.index.vector.queryNodes('chunk_embeddings', 25, target.embedding)
                    YIELD node AS neighbor, score
                    RETURN neighbor, score
                }
                
                // 3. APPLY THRESHOLD & PREVENT SELF-LINKING
                WHERE score >= $threshold AND target.id <> neighbor.id
                
                // 4. Create the relationship
                WITH target, neighbor, score
                MATCH (docA:Document)-[:HAS_PART]->(target)
                MATCH (docB:Document)-[:HAS_PART]->(neighbor)
                
                MERGE (target)-[r:RELATED_TO]->(neighbor)
                SET r.weight = score,
                    r.isInternal = (docA.id = docB.id)
            """.trimIndent()

                tx.run(cypher, Values.parameters("userId", userId, "threshold", threshold)).consume()
            }
        }
    }

    fun setupIndex() {
        driver.session().use { session ->
            session.executeWrite { tx ->
                tx.run(
                    """
                CREATE VECTOR INDEX chunk_embeddings IF NOT EXISTS
                FOR (c:Chunk) ON (c.embedding)
                OPTIONS {indexConfig: {
                 `vector.dimensions`: 1024,
                 `vector.similarity_function`: 'cosine'
                }}
            """.trimIndent()
                ).consume()
            }
        }
        println("✅ Vector index 'chunk_embeddings' verified/created.")
    }

    fun checkIndexReady(): Boolean {
        return driver.session().use { session ->
            session.executeRead { tx ->
                val result = tx.run("SHOW INDEXES YIELD name, populationPercent, state WHERE name = 'chunk_embeddings'")
                if (result.hasNext()) {
                    val record = result.next()
                    val percent = record.get("populationPercent").asDouble()
                    val state = record.get("state").asString()
                    println("📊 Index Status: $state ($percent%)")
                    state == "ONLINE" && percent == 100.0
                } else false
            }
        }
    }

    fun close() = driver.close()
}