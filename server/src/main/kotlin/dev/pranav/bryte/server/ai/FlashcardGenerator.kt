package dev.pranav.bryte.server.ai

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.isRunning
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.feature.handler.agent.AgentStartingContext
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
import ai.koog.prompt.executor.clients.mistralai.MistralAIModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.filterTextOnly
import ai.koog.prompt.structure.markdown.MarkdownStructureDefinition
import ai.koog.rag.vector.EmbeddingBasedDocumentStorage
import ai.koog.rag.vector.InMemoryVectorStorage
import dev.pranav.bryte.model.card.Flashcard
import dev.pranav.bryte.model.common.ImportanceLevel
import dev.pranav.bryte.model.session.DocumentChunk
import dev.pranav.bryte.model.session.Session
import dev.pranav.bryte.server.GEMINI_API_KEY
import dev.pranav.bryte.server.MISTRAL_API_KEY
import dev.pranav.bryte.server.ai.embedding.TextDocumentEmbedder
import dev.pranav.bryte.server.migration.Neo4jManager
import dev.pranav.bryte.server.postgrest.DocumentChunkRepository
import dev.pranav.bryte.server.postgrest.FlashcardRepository
import dev.pranav.bryte.server.util.ext.documentChunks
import dev.pranav.bryte.server.util.ext.flashcards
import dev.pranav.bryte.server.util.ext.sessions
import dev.pranav.bryte.server.util.ext.supabase
import dev.pranav.bryte.server.util.serialization.markdownStreamingParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking


fun main() = runBlocking {
    val sessions by supabase.sessions()
    val documentChunks by supabase.documentChunks()
    val flashcards by supabase.flashcards()
    val generator =
        FlashcardGenerator(sessions.getById("98040688-6252-446b-b495-96c28ddc80cd")!!, documentChunks, flashcards)

    generator.use {
        println("Starting flashcard generation cycle...")
        it.generateFlashcards().let { flow ->
            var count = 0
            flow.collect { card ->
                println(card)
                count++
            }
            println("Generated $count flashcards in this cycle.")
        }
    }
}

/**
 * AI-powered question generator that creates questions from document chunks.
 * This class handles the logic for generating different types of questions
 * using Koog AI framework.
 */
class FlashcardGenerator(
    val session: Session,
    private val documentChunks: DocumentChunkRepository,
    private val flashcards: FlashcardRepository
) : AutoCloseable {

    var exhausted = false
    private val neo4j = Neo4jManager()

    private data class GraphRagConfig(
        val topK: Int = 6,
        val neighborLimit: Int = 5,
        val docBias: Double = 0.15,
        val crossDocPenalty: Double = 0.05
    )

    private val graphRagConfig = GraphRagConfig()

    override fun close() {
        neo4j.close()
    }

    private val embedder: TextDocumentEmbedder by lazy {
        TextDocumentEmbedder(
            LLMEmbedder(MistralAILLMClient(MISTRAL_API_KEY), MistralAIModels.Embeddings.MistralEmbed), documentTopics, documentChunks
        )
    }

    private val documentStorage by lazy {
        EmbeddingBasedDocumentStorage(
            embedder, InMemoryVectorStorage()
        )
    }

    private val documentTopics: Set<DocumentChunk>


    init {
        require(session.id.isNotBlank()) { "Session ID must not be blank" }

        documentTopics = runBlocking {
            documentChunks.getByDocumentId(session.documentId).toSet()
        }

        documentTopics.forEach {
            runBlocking {
                if (it.content.length < 200) return@runBlocking
                documentStorage.store(it.id!!)
            }
        }
    }

    @Suppress("unused")
    @LLMDescription("Tools for retrieving document topics and searching content.")
    inner class DocumentToolset : ToolSet {
        var index = 0

        fun currentTopic(): DocumentChunk {
            return documentTopics.elementAtOrElse(index, { documentTopics.last() })
        }

        @Tool
        @LLMDescription("Returns the next topic content from document.")
        fun getNextTopic(): String {
            if (index >= documentTopics.size) {
                exhausted = true
                return "No more topics available."
            }
            val topic = documentTopics.elementAt(index)
            index++
            return markdown {
                h1(topic.header)
                text(topic.content)
            }
        }

        @Tool
        @LLMDescription("Search for related information about the given query. Prioritizes the current document but can bridge across the user's other documents when useful.")
        suspend fun search(
            @LLMDescription("The query to search for related information.") query: String
        ): String {
            return graphContext(query = query, topK = graphRagConfig.topK, neighborLimit = graphRagConfig.neighborLimit)
        }

        @Tool
        @LLMDescription("Return a graph-augmented context pack for a query: top matches plus their strongest related concepts. Uses weighted doc-priority retrieval.")
        suspend fun graphContext(
            @LLMDescription("The query to retrieve context for.") query: String,
            @LLMDescription("Number of starting chunks to retrieve from the vector index.") topK: Int = 6,
            @LLMDescription("Max number of related concepts to include per chunk.") neighborLimit: Int = 5
        ): String {
            if (query.isBlank()) return "Query is blank."

            val queryEmbedding = embedder.embed(query).values
            if (queryEmbedding.isEmpty()) return "No embedding produced for query."

            val results = neo4j.searchKnowledgeGraphWeighted(
                queryEmbedding = queryEmbedding,
                userId = session.userId,
                focusDocumentId = session.documentId,
                topK = topK,
                neighborLimit = neighborLimit,
                docBias = graphRagConfig.docBias,
                crossDocPenalty = graphRagConfig.crossDocPenalty
            )

            return markdown {
                h1("Graph RAG Context: $query")

                if (results.isEmpty()) {
                    text("No relevant context found in the knowledge graph.")
                    return@markdown
                }

                results.forEachIndexed { idx, res ->
                    val docSource = res["docSource"]?.toString() ?: "Unknown"
                    val sectionHeader = res["sectionHeader"]?.toString() ?: ""
                    val sectionContent = res["sectionContent"]?.toString() ?: ""
                    val matchScore = res["matchScore"]?.toString() ?: ""
                    val weightedScore = res["weightedScore"]?.toString() ?: ""
                    val isFocusDoc = res["isFocusDoc"]?.toString() ?: "false"

                    h2("Result ${idx + 1}: $sectionHeader")
                    text("source=$docSource | focusDoc=$isFocusDoc | baseScore=$matchScore | weightedScore=$weightedScore")
                    text(sectionContent)

                    @Suppress("UNCHECKED_CAST")
                    val related = res["related"] as? List<Map<String, Any>> ?: emptyList()
                    if (related.isNotEmpty()) {
                        h3("Related Concepts")
                        bulleted {
                            related.forEach { rel ->
                                val topic = rel["topic"]?.toString() ?: "Unknown"
                                val type = rel["type"]?.toString() ?: "Unknown"
                                val weight = rel["weight"]?.toString() ?: ""
                                item { text("$topic ($type, w=$weight)") }
                            }
                        }
                    }
                }

                h2("Usage guidance")
                text("Prefer facts from focusDoc=true results if they answer the question. Use cross-doc bridges only when they clarify prerequisites or definitions.")
            }
        }

        @Tool
        @LLMDescription("Get the list of all available topic titles.")
        fun getAllTopics(): String {
            if (documentTopics.isEmpty()) {
                return "No topics found in document"
            }
            return documentTopics.map { it.header }.distinct().joinToString()
        }
//
//        @Tool
//        @LLMDescription("Get content for a specific topic. Prefer getNextTopic for sequential access.")
//        fun getTopicByTitle(
//            @LLMDescription("The title of the topic to retrieve content for.") title: String
//        ): String {
//            val topic = documentTopics.find { it.header.equals(title, ignoreCase = true) }
//                ?: return "Topic with title '$title' not found."
//            return markdown {
//                h1(topic.header)
//                text(topic.content)
//            }
//        }
    }

    private lateinit var generationAgent: AIAgent<String, Flow<StreamFrame>>

    fun generateFlashcards(): Flow<Flashcard> {
        val toolset = DocumentToolset()

        if (!::generationAgent.isInitialized) {
            createAgent(toolset)
        }

        return flow {
            while (!exhausted) {
                while (generationAgent.isRunning()) {
                    delay(500)
                    println("Waiting for previous generation to complete...")
                }

                val flow = generationAgent.run("Generate flashcards based on the available content.")

                parseMDStreamToQuestions(flow, toolset).collect {
                    println("Generated flashcard: $it")
                    emit(it)
                }
            }
        }
    }

    @OptIn(InternalAgentsApi::class)
    private fun createAgent(toolset: ToolSet) {
        val systemPrompt = """
You are an **Expert Flashcard Creator** specializing in active recall and spaced repetition learning. Your primary function is to transform document content into high-quality, pedagogically effective flashcards.

## **CORE WORKFLOW:**
1. **Content Acquisition:** Begin by calling `getNextTopic()` to retrieve the first available topic. Process this content thoroughly.
2. **Batch Generation:** Generate about **25-30 flashcards** per run. If you need more content to reach this target, call `getNextTopic()` again as needed.
3. **Graph RAG Context Enhancement (IMPORTANT):**
   - Prefer `graphContext(query)` when you need definitions, mechanisms, prerequisites, or precise phrasing.
   - The tool returns results with `focusDoc=true/false`. Prefer `focusDoc=true` evidence to keep cards anchored to the current document.
   - Use cross-document bridges (`focusDoc=false` and `Cross-Doc Bridge` related concepts) only to clarify or contrast a concept.
4. **Sequential Processing:** Process topics in order. Do not skip ahead or return to previously covered topics within the same batch. You may call `getNextTopic()` multiple times to access new content.

## **FLASHCARD QUALITY STANDARDS:**
- **Difficulty Distribution:**
  - **Easy (30-40%):** Core definitions.
  - **Medium (40-50%):** **Concept Discrimination.** Instead of "What is X?", use "How does X differ from Y?" to ensure the user isn't just memorizing keywords.
  - **Hard (20-30%):** **The "Feynman" Challenge.** Formulate questions that require the user to explain a mechanism or predict an outcome based on the text.
- **Thematic Integrity:** Each flashcard must be related to the current topic. Graph RAG is supporting evidence; it must not cause topic drift.
- **Pedagogical Value:** For every card, produce a `hidden_rationale`: the mental model required to answer correctly.

## **CONTINUATION PROTOCOL:**
- Each run should produce 25-30 flashcards from the current position in the content.
- Never repeat flashcards.
- If you cannot generate more unique flashcards from the current material, call `getNextTopic()`.
""".trimIndent()


        // OpenRouterModels.Qwen3VL.copy(id="google/gemma-3-27b-it:free", contextLength = 131_072)
        val agentConfig = AIAgentConfig(
            prompt = Prompt.build(
                id = "Flashcard Generation Prompt",
//                params = GoogleParams(thinkingConfig = GoogleThinkingConfig(includeThoughts = true, thinkingLevel = GoogleThinkingLevel.LOW))
            ) {
                system(systemPrompt)
            },
            model = GoogleModels.Gemini2_5Flash, //.copy(id="gemini-3-flash-preview"),
            maxAgentIterations = 50,
            enforceSingleRun = false
        )


        val agentStrategy = strategy<String, Flow<StreamFrame>>("flashcard-generator") {
            val sendInput by nodeLLMRequest("sendInput")
            val runTools by nodeExecuteTool()
            val sendToolResults by nodeLLMSendToolResult()

            val returnStream by nodeLLMRequestStreaming("flashcards", structure)

            edge(nodeStart forwardTo sendInput)
            edge(sendInput forwardTo runTools onToolCall { true })
            edge(sendInput forwardTo returnStream onAssistantMessage { true })
            edge(runTools forwardTo sendToolResults)
            edge(sendToolResults forwardTo runTools onToolCall { true })
            edge(runTools forwardTo sendToolResults)
            edge(sendToolResults forwardTo returnStream onAssistantMessage { true })
            edge(returnStream forwardTo nodeFinish)
        }

        generationAgent = AIAgent<String, Flow<StreamFrame>>(
//            promptExecutor = simpleOpenRouterExecutor(OPENROUTER_API_KEY),
            promptExecutor = simpleGoogleAIExecutor(GEMINI_API_KEY),
            agentConfig = agentConfig,
            strategy = agentStrategy,
            toolRegistry = ToolRegistry {
                tools(toolset)
            },
            installFeatures = {
                install(EventHandler) {
                    onAgentStarting { eventContext: AgentStartingContext ->
                        println("Starting agent: ${eventContext.agent.id}")
                    }

                    onLLMStreamingStarting {
                        println("LLM streaming starting...")
                    }

                    onLLMStreamingFrameReceived {
                        if (it.streamFrame is StreamFrame.Append) {
                            print((it.streamFrame as StreamFrame.Append).text)
                        }
                    }

                    onLLMStreamingFailed {
                        println("LLM streaming failed: ${it.error.message}")
                    }

                    onToolCallCompleted {
                        println("Tool call completed: ${it.toolName} with result: ${it.toolResult}")
                        if (it.toolName == "contentExhausted") {
                            exhausted = true
                            println("Exhaustion tool called. Ending question generation.")
                        }
                    }
                }
            })
    }

    fun parseMDStreamToQuestions(markdownStream: Flow<StreamFrame>, toolset: DocumentToolset): Flow<Flashcard> {
        return flow {
            markdownStreamingParser {
                var front = ""
                var back = ""
                var topic = ""
                var importanceLevel = "medium"

                onHeader(1) {
                    val chunk = toolset.currentTopic()

                    if (front.isNotEmpty() && back.isNotEmpty() && topic.isNotEmpty() && importanceLevel.isNotEmpty()) {
                        val flashcard = Flashcard(
                            documentId = session.documentId,
                            chunkId = chunk.id!!,
                            page = chunk.pageNumber.first(),
                            front = front,
                            back = back,
                            topic = topic,
                            importance = when (importanceLevel) {
                                "easy" -> ImportanceLevel.LOW
                                "medium" -> ImportanceLevel.MEDIUM
                                "hard" -> ImportanceLevel.HIGH
                                else -> ImportanceLevel.MEDIUM
                            }
                        )

                        emit(flashcard)
                        flashcards.insert(flashcard)
                    }

                    if (it.isEmpty()) return@onHeader
                    front = it
                }

                onHeader(2) {
                    if (it.isEmpty()) return@onHeader
                    back = it
                }

                onHeader(3) {
                    if (it.isEmpty()) return@onHeader
                    topic = it
                }

                onHeader(4) {
                    if (it.isEmpty()) return@onHeader
                    importanceLevel = it.lowercase()
                }

                onFinishStream {
                    val chunk = toolset.currentTopic()

                    if (front.isNotEmpty() && back.isNotEmpty() && topic.isNotEmpty() && importanceLevel.isNotEmpty()) {
                        val flashcard = Flashcard(
                            documentId = session.documentId,
                            chunkId = chunk.id!!,
                            page = chunk.pageNumber.first(),
                            front = front,
                            back = back,
                            topic = topic,
                            importance = when (importanceLevel) {
                                "easy" -> ImportanceLevel.LOW
                                "medium" -> ImportanceLevel.MEDIUM
                                "hard" -> ImportanceLevel.HIGH
                                else -> ImportanceLevel.MEDIUM
                            }
                        )

                        emit(flashcard)
                        flashcards.insert(flashcard)
                    }
                }

            }.parseStream(markdownStream.filterTextOnly())
        }
    }

    companion object {
        val structure = MarkdownStructureDefinition("flashcards", schema = {
            markdown {
                bulleted {
                    item {
                        h1("front")
                        h2("back")
                        h3("topic")
                        h4("importance")
                    }
                }
            }
        }, examples = {
            markdown {
                bulleted {
                    item {
                        h1( "What is electric flux?")
                        h2("Electric flux is a measure of the electric field passing through a given area.")
                        h3("Electromagnetism")
                        h4("easy")
                    }
                    item {
                        h1("What is Zeeman effect?")
                        h2("The Zeeman effect is the splitting of a spectral line into several components in the presence of a strong external magnetic field.")
                        h3("Quantum Mechanics")
                        h4("medium")
                    }
                    item {
                        h1("What is Klystron?")
                        h2("A Klystron is a specialized linear-beam vacuum tube used to amplify high-frequency radio waves, and is widely used in radar systems, communications and particle accelerators.")
                        h3("Microwave Engineering")
                        h4("hard")
                    }
                }
            }
        })
    }
}
