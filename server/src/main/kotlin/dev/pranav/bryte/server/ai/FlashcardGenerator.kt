package dev.pranav.bryte.server.ai

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
import ai.koog.prompt.executor.clients.mistralai.MistralAIModels
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.filterTextOnly
import ai.koog.prompt.structure.markdown.MarkdownStructureDefinition
import ai.koog.rag.vector.storage.InMemoryDocumentEmbeddingStorage
import com.cohere.api.Cohere
import com.cohere.api.core.Environment
import com.cohere.api.requests.RerankRequest
import com.cohere.api.types.RerankRequestDocumentsItem
import dev.pranav.bryte.model.card.Flashcard
import dev.pranav.bryte.model.common.ImportanceLevel
import dev.pranav.bryte.model.session.DocumentChunk
import dev.pranav.bryte.model.session.Session
import dev.pranav.bryte.server.AZURE_API_KEY
import dev.pranav.bryte.server.AZURE_API_URL
import dev.pranav.bryte.server.MISTRAL_API_KEY
import dev.pranav.bryte.server.ai.embedding.TextDocumentEmbedder
import dev.pranav.bryte.server.migration.Neo4jManager
import dev.pranav.bryte.server.postgrest.DocumentChunkRepository
import dev.pranav.bryte.server.postgrest.FlashcardRepository
import dev.pranav.bryte.server.util.serialization.markdownStreamingParser
import io.ktor.util.logging.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.seconds

internal val FC_GEN_LOGGER = KtorSimpleLogger("dev.pranav.bryte.server.ai.FlashcardGenerator")

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

    override fun close() {
        neo4j.close()
    }

    private val embedder: TextDocumentEmbedder by lazy {
        TextDocumentEmbedder(
            LLMEmbedder(MistralAILLMClient(MISTRAL_API_KEY), MistralAIModels.Embeddings.MistralEmbed),
            documentTopics
        )
    }

    private val documentStorage by lazy {
        InMemoryDocumentEmbeddingStorage(embedder)
    }

    private var documentTopics: List<DocumentChunk> = emptyList()
    private var prepared = false


    init {
        require(session.id.isNotBlank()) { "Session ID must not be blank" }
    }

    private suspend fun ensureInitialized() {
        if (prepared) return

        require(session.id.isNotBlank()) { "Session ID must not be blank" }

        FC_GEN_LOGGER.info("Initializing FlashcardGenerator for session ${session.id}, document ${session.documentId}")
        documentTopics = documentChunks
            .getByDocumentId(session.documentId)
            .sortedBy { it.index }
        FC_GEN_LOGGER.info("Loaded ${documentTopics.size} document topics for flashcard generation")

        documentStorage.add(
            documentTopics
                .filter { it.content.length > 200 }
                .map { it.id!! }
        )

        prepared = true
    }


    @Suppress("unused")
    @LLMDescription("Tools for retrieving document topics and searching content.")
    inner class DocumentToolset : ToolSet {
        var index = 0
        var toolCallsInBatch = 0

        fun currentTopic(): DocumentChunk {
            return documentTopics.find { it.index == index } ?: documentTopics.last()
        }

        @Tool
        @LLMDescription("Returns the next topic content from document.")
        fun getNextTopic(): String {
            toolCallsInBatch++
            if (index >= documentTopics.size) {
                FC_GEN_LOGGER.info("All ${documentTopics.size} topics exhausted. No more topics available.")
                exhausted = true
                return "No more topics available."
            }

            if (toolCallsInBatch >= 4) {
                FC_GEN_LOGGER.info("Batch quota reached (4 topics). Pausing topic retrieval.")
                return "ERROR: Batch quota reached (4 topics). You MUST now generate flashcards for the topics already retrieved before calling this tool again."
            }
            val topic = documentTopics.find { it.index == index }
                ?: return "Topic at index $index not found."
            FC_GEN_LOGGER.info("Retrieving topic ${index + 1}/${documentTopics.size}: '${topic.header}'")
            index++
            return markdown {
                h1(topic.header)
                text(topic.content)
            }
        }

        @Tool
        @LLMDescription("Search for related information about the given query. Prioritizes the current document but can bridge across the user's other documents when useful.")
        fun search(
            @LLMDescription("The query to search for related information.") query: String
        ): String {
            FC_GEN_LOGGER.info("Executing Cohere rerank search: '$query'")
            val cohere = Cohere.builder()
                .token(AZURE_API_KEY)
                .environment(
                    Environment.custom(AZURE_API_URL)
                ).build()

            val rerankResponse = cohere.rerank(
                RerankRequest.builder()
                    .query(query)
                    .documents(documentTopics.map { it.content }.toList().map { RerankRequestDocumentsItem.of(it) })
                    .topN(5)
                    .build()
            )
            return rerankResponse.results.map { it.document.get().text }.joinToString("\n")
            //return graphContext(query = query, topK = graphRagConfig.topK, neighborLimit = graphRagConfig.neighborLimit)
        }

//        @Tool
//        @LLMDescription("Return a graph-augmented context pack for a query: top matches plus their strongest related concepts. Uses weighted doc-priority retrieval.")
//        suspend fun graphContext(
//            @LLMDescription("The query to retrieve context for.") query: String,
//            @LLMDescription("Number of starting chunks to retrieve from the vector index.") topK: Int = 6,
//            @LLMDescription("Max number of related concepts to include per chunk.") neighborLimit: Int = 5
//        ): String {
//            if (query.isBlank()) return "Query is blank."
//
//            val queryEmbedding = embedder.embed(query).values
//            if (queryEmbedding.isEmpty()) return "No embedding produced for query."
//
//            val results = neo4j.searchKnowledgeGraphWeighted(
//                queryEmbedding = queryEmbedding,
//                userId = session.userId,
//                focusDocumentId = session.documentId,
//                topK = topK,
//                neighborLimit = neighborLimit,
//                docBias = graphRagConfig.docBias,
//                crossDocPenalty = graphRagConfig.crossDocPenalty
//            )
//
//            return markdown {
//                h1("Graph RAG Context: $query")
//
//                if (results.isEmpty()) {
//                    text("No relevant context found in the knowledge graph.")
//                    return@markdown
//                }
//
//                results.forEachIndexed { idx, res ->
//                    val docSource = res["docSource"]?.toString() ?: "Unknown"
//                    val sectionHeader = res["sectionHeader"]?.toString() ?: ""
//                    val sectionContent = res["sectionContent"]?.toString() ?: ""
//                    val matchScore = res["matchScore"]?.toString() ?: ""
//                    val weightedScore = res["weightedScore"]?.toString() ?: ""
//                    val isFocusDoc = res["isFocusDoc"]?.toString() ?: "false"
//
//                    h2("Result ${idx + 1}: $sectionHeader")
//                    text("source=$docSource | focusDoc=$isFocusDoc | baseScore=$matchScore | weightedScore=$weightedScore")
//                    text(sectionContent)
//
//                    @Suppress("UNCHECKED_CAST")
//                    val related = res["related"] as? List<Map<String, Any>> ?: emptyList()
//                    if (related.isNotEmpty()) {
//                        h3("Related Concepts")
//                        bulleted {
//                            related.forEach { rel ->
//                                val topic = rel["topic"]?.toString() ?: "Unknown"
//                                val type = rel["type"]?.toString() ?: "Unknown"
//                                val weight = rel["weight"]?.toString() ?: ""
//                                item { text("$topic ($type, w=$weight)") }
//                            }
//                        }
//                    }
//                }
//
//                h2("Usage guidance")
//                text("Prefer facts from focusDoc=true results if they answer the question. Use cross-doc bridges only when they clarify prerequisites or definitions.")
//            }
//        }
//
//        @Tool
//        @LLMDescription("Get the list of titles of all topics in the document.")
//        fun getAllTopics(): String {
//            if (documentTopics.isEmpty()) {
//                return "No topics found in document"
//            }
//            return documentTopics.map { it.header }.distinct().joinToString()
//        }
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

    suspend fun generateFlashcards(): Flow<Flashcard> {
        ensureInitialized()
        val toolset = DocumentToolset()

        val existingCards = flashcards.getByDocumentId(session.documentId)
        if (existingCards.isNotEmpty()) {
            FC_GEN_LOGGER.info("Found ${existingCards.size} existing flashcards. Resuming from last topic index.")
            toolset.index = existingCards.maxOf { fc ->
                documentTopics.first { it.id == fc.chunkId }.index
            } + 1
        }

        if (toolset.index >= documentTopics.size) {
            FC_GEN_LOGGER.info("All topics already covered. Returning ${existingCards.size} existing cards.")
            exhausted = true
            return flow {
                existingCards.forEach { emit(it) }
            }
        }

        FC_GEN_LOGGER.info("Starting AI flashcard generation from topic index ${toolset.index}...")
        val frameChannel = Channel<StreamFrame>(Channel.UNLIMITED)

        if (!::generationAgent.isInitialized) {
            createAgent(toolset, frameChannel)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                generationAgent.run("Generate flashcards for the document.")
            } finally {
                FC_GEN_LOGGER.info("Flashcard generation agent finished. Exhausted=$exhausted")
                exhausted = true
                frameChannel.close()
            }
        }

        return parseMDStreamToQuestions(frameChannel.consumeAsFlow(), toolset)
    }

    @OptIn(InternalAgentsApi::class)
    private fun createAgent(toolset: DocumentToolset, receiver: Channel<StreamFrame>) {
        val systemPrompt = """
Role: Expert Flashcard Creator (Active Recall/Spaced Repetition). Objective: Convert document content into high-quality flashcards to promote understanding and learning using provided tools.

Operational Workflow
Source Content: Always call getNextTopic() to retrieve new content. Do not use overview tools or meta-info for card creation.

No Topic Drift: Keep cards anchored to the received content, if needed, request more content. You are allowed to skip topics that are not suitable for flashcard creation.

Volume & Continuity: Generate high quality, effective flashcards. If current content is exhausted, call getNextTopic() again until the quota is met. Never repeat cards.
Strict Boundaries: Focus only on actual knowledge. No questions about document structure, titles, authors, or meta-talk (e.g., "As mentioned in..."). No trivial nomenclature/units. All difficulty levels must be equally and consistently represented.
DO NOT respond with anything other than the markdown structure defined below.

Flashcard Standards
Easy (30-40%): Definition/Fact/Term/Important Formulas. (e.g., "What is...?" or "Define X").

Medium (40-50%): Concept/Explanation. (e.g., "Explain how..." or "Why does X happen?").

Hard (20-30%): Mechanism/Prediction/Application. (e.g., "What happens if...?" or "How does X lead to Y?").

**Substance**: Avoid one-word answers or trivial questions. Answers must be comprehensive to aid retention. Focus on core concepts, mechanisms, and applications, not peripheral details. Do not create cards on unimportant content like examples, anecdotes, or other unimportant nonsense.

Mandatory Field: Every card must include a hidden_rationale explaining the underlying mental model.
""".trimIndent()

        val agentConfig = AIAgentConfig(
            prompt = Prompt.build(
                id = "Flashcard Generation Prompt",
            ) {
                system(systemPrompt)
            },
            model = OpenAIModels.Chat.GPT5_2.copy(id = "gpt-5.2-chat"),
            maxAgentIterations = 150,
        )


        generationAgent = AIAgent<String, Flow<StreamFrame>>(
            promptExecutor = MultiLLMPromptExecutor(
                OpenAILLMClient(
                    apiKey = AZURE_API_KEY,
                    settings = OpenAIClientSettings(
                        baseUrl = AZURE_API_URL,
                    )
                )
            ),
            agentConfig = agentConfig,
            strategy = flashcardStrategy("flashcard-strategy", toolset, receiver),
            toolRegistry = ToolRegistry {
                tools(toolset)
            },
            installFeatures = {
                install(EventHandler)
            })
    }

    private suspend fun AIAgentContext.historyIsTooLong(): Boolean = llm.readSession {
        prompt.messages.sumOf { it.textContent().length } > 100_000
    }

    fun flashcardStrategy(
        name: String,
        toolset: DocumentToolset,
        receiver: Channel<StreamFrame>
    ): AIAgentGraphStrategy<String, Flow<StreamFrame>> {

        return strategy(name) {
            val nodeSendInput by nodeLLMRequest("sendInput")
            val nodeExecuteTool by nodeExecuteTools()
            val nodeSendToolResult by nodeLLMSendToolResults()

            val compressHistory by nodeLLMCompressHistory<Flow<StreamFrame>>(
                "compressHistory",
                strategy = HistoryCompressionStrategy.FromLastNMessages(10),
                preserveMemory = true
            )

            val returnStream by node<String, Flow<StreamFrame>>("requestFlashcards") { message ->
                llm.writeSession {
                    appendPrompt {
                        user(message)
                    }

                    val stream = try {
                        requestLLMStreaming(structure)
                    } catch (e: LLMClientException) {
                        if (e.cause!!.message!!.contains("Please retry after")) {
                            delay(20.seconds)
                            requestLLMStreaming(structure)
                        } else {
                            throw e
                        }
                    }

                    coroutineScope {
                        launch {
                            try {
                                stream.collect { frame ->
                                    receiver.send(frame)
                                    if (frame is StreamFrame.End) {
                                        this@launch.cancel()
                                    }
                                }
                            } catch (_: CancellationException) {
                                // Expected completion via cancellation
                            }
                        }.join()
                    }
                    stream
                }
            }

            edge(nodeStart forwardTo nodeSendInput)

            edge(
                (nodeSendInput forwardTo nodeExecuteTool)
                        onToolCalls { true }
            )

            edge(
                (nodeSendInput forwardTo returnStream)
                        onTextMessage { true }
            )

            edge(nodeExecuteTool forwardTo nodeSendToolResult)

            edge(
                (nodeSendToolResult forwardTo returnStream)
                        onTextMessage { true }
            )

            edge(
                nodeSendToolResult forwardTo nodeExecuteTool
                        onToolCalls { true }
            )

            edge(
                (returnStream forwardTo compressHistory)
                        onCondition { !exhausted && historyIsTooLong() }
            )

            edge(
                (compressHistory forwardTo nodeSendInput)
                        transformed { "Continue flashcard generation with the next topic." }
            )

            edge(
                (returnStream forwardTo nodeSendInput)
                        onCondition { !exhausted }
                        transformed {
                    toolset.toolCallsInBatch = 0
                    "Continue flashcard generation with the next topic."
                }
            )

            edge(
                (returnStream forwardTo nodeFinish)
                        onCondition { exhausted }
            )

            edge(
                (nodeSendToolResult forwardTo nodeFinish)
                        onCondition { exhausted }
                        transformed { emptyFlow() }
            )
        }
    }

    fun parseMDStreamToQuestions(markdownStream: Flow<StreamFrame>, toolset: DocumentToolset): Flow<Flashcard> {
        return flow {
            markdownStreamingParser {
                var front = ""
                var back = ""
                var topic = ""
                var importanceLevel = "medium"
                var rationale = ""

                onHeader(1) {
                    val chunk = toolset.currentTopic()

                    if (front.isNotEmpty() && back.isNotEmpty() && topic.isNotEmpty() && importanceLevel.isNotEmpty() && rationale.isNotEmpty()) {
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
                            },
                            rationale = rationale
                        )

                        FC_GEN_LOGGER.info("Emitting flashcard: '${flashcard.front.take(60)}...' [${flashcard.importance}] topic='${flashcard.topic}'")
                        emit(flashcard)
                        coroutineScope {
                            launch(Dispatchers.IO) {
                                flashcards.insert(flashcard)
                            }
                        }

                        front = ""
                        back = ""
                        topic = ""
                        importanceLevel = "medium"
                        rationale = ""
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

                onHeader(5) {
                    if (it.isEmpty()) return@onHeader
                    rationale = it
                }

                onFinishStream {
                    val chunk = toolset.currentTopic()

                    if (front.isNotEmpty() && back.isNotEmpty() && topic.isNotEmpty() && importanceLevel.isNotEmpty() && rationale.isNotEmpty()) {
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
                            },
                            rationale = rationale
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
                        h5("rationale")
                    }
                }
            }
        }, examples = {
            markdown {
                bulleted {
                    item {
                        h1("What is electric flux?")
                        h2("Electric flux is a measure of the electric field passing through a given area.")
                        h3("Electromagnetism")
                        h4("easy")
                        h5("Understanding electric flux helps in visualizing how electric fields interact with surfaces.")
                    }
                    item {
                        h1("What is Zeeman effect?")
                        h2("The Zeeman effect is the splitting of a spectral line into several components in the presence of a strong external magnetic field.")
                        h3("Quantum Mechanics")
                        h4("medium")
                        h5("To answer this question, one must understand the interaction between magnetic fields and atomic energy levels, which causes the splitting of spectral lines.")
                    }
                    item {
                        h1("What is Klystron?")
                        h2("A Klystron is a specialized linear-beam vacuum tube used to amplify high-frequency radio waves, and is widely used in radar systems, communications and particle accelerators.")
                        h3("Microwave Engineering")
                        h4("hard")
                        h5("Answering this question requires knowledge of microwave amplification techniques and the specific design and function of Klystron tubes.")
                    }
                }
            }
        })
    }
}