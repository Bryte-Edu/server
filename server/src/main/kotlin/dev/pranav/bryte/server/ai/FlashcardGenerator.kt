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
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
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
import dev.pranav.bryte.server.MISTRAL_API_KEY
import dev.pranav.bryte.server.OPENROUTER_API_KEY
import dev.pranav.bryte.server.ai.embedding.TextDocumentEmbedder
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
import kotlinx.css.h1
import kotlinx.css.h2
import kotlinx.css.h3
import kotlinx.css.h4


fun main() = runBlocking {
    val sessions by supabase.sessions()
    val documentChunks by supabase.documentChunks()
    val flashcards by supabase.flashcards()
    val generator = FlashcardGenerator(sessions.getById("d07cedf7-9ab5-4955-845a-59f7d4386771")!!, documentChunks, flashcards)

    while (!generator.exhausted) {
        println("Starting flashcard generation cycle...")
        generator.generateFlashcards().let {
            var count = 0
            it.collect {
                println(it)
                count++
            }
            println("Generated $count flashcards in this cycle.")
        }
        delay(5000)
    }
}

/**
 * AI-powered question generator that creates questions from document chunks.
 * This class handles the logic for generating different types of questions
 * using Koog AI framework.
 */
class FlashcardGenerator(val session: Session, private val documentChunks: DocumentChunkRepository, private val flashcards: FlashcardRepository) {

    var exhausted = false

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
    inner class RAGToolset : ToolSet {
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
        @LLMDescription("Search for related information about the given query.")
        suspend fun search(
            @LLMDescription("The query to search for related information.") query: String
        ): String {
            val results = mutableListOf<Pair<String, String>>()
            documentStorage.rankDocuments(query).collect {
                if (it.similarity == 0.0) return@collect
                val document = documentTopics.find { topic -> topic.id == it.document }
                    ?: return@collect
                println("RAG search result score: ${it.similarity}, content: ${document.content.take(100)}...")
                results.add(document.header to document.content)
            }

            println("Total RAG search results: ${results.size} for query: $query")

            return markdown {
                h2("Search Results for: $query")
                results.forEach { (title, content) ->
                    h3(title)
                    text(content)
                }
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

    suspend fun generateFlashcards(): Flow<Flashcard> {
        val toolset = RAGToolset()

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
                    emit(it)
                }
            }
        }
    }

    suspend fun generateFlashcardsForTopic(
        chunk: DocumentChunk
    ): List<Flashcard> {

        val flashcards = mutableListOf<Flashcard>()
        val toolset = RAGToolset()
        toolset.index = documentTopics.indexOf(chunk)
        val systemPrompt = """
You are an **Expert Flashcard Creator** skilled at creating important, high-quality flashcards (front/back pairs) that promote active recall, application, and deep understanding. Produce flashcards based **STRICTLY ON THE DOCUMENT CONTENT PROVIDED**.

**GENERATION MANDATE (Varying Difficulty / Importance):**
* **Focus:** Ensure key definitions, important terms, and fundamental facts from the chunk are captured as flashcard fronts with concise, accurate backs.

**CONSTRAINTS:**
1. **Content Source:** Base flashcards only on the input text you were given.
2. **Allowed Tool Use:** You may use the 'search(query)' tool to retrieve prior topic details referenced by the current chunk for cross-topic synthesis.
3. **THEMATIC ANCHOR:** Each flashcard must be rooted in the current chunk. For hard flashcards you may incorporate information retrieved via 'search' from previous topics, but do not introduce unrelated new topics.
4. **Forbidden Tools:** DO NOT use 'getNextTopic()' or 'getAllTopics()'.

**STRICT OUTPUT RULE:**
* RESPOND **STRICTLY AND ONLY** in the required Markdown structured format: header \(front\) and a bulleted list with `back`, `topic`, and `importance_level` (values: `easy`, `medium`, `hard`).
* **Quality Saturation Gate:** After analyzing the content and using 'search(query)' as needed, if further flashcards would only be low-quality, redundant, or simple recall, you **MUST** call the 'contentExhausted()' tool. This signals the end of flashcard generation for this chunk.
""".trimIndent()

        val agentConfig = AIAgentConfig(
            prompt = Prompt.build("Flashcard Generation Prompt") {
                system(systemPrompt)
            }, model = GoogleModels.Gemini2_5Flash, maxAgentIterations = 20
        )

        val strategy = strategy<String, Flow<StreamFrame>>("flashcard-generator-topic") {
            val sendInput by nodeLLMRequest()
            val runTools by nodeExecuteTool()
            val sendToolResults by nodeLLMSendToolResult()

            val returnStream by nodeLLMRequestStreaming("flashcards", structure)

            edge(nodeStart forwardTo sendInput)
            edge(sendInput forwardTo runTools onToolCall { true })
            edge(runTools forwardTo sendToolResults)
            edge(sendToolResults forwardTo runTools onToolCall { true })
            edge(sendToolResults forwardTo returnStream onAssistantMessage { true })
            edge(returnStream forwardTo nodeFinish)
        }

        val agent = AIAgent<String, Flow<StreamFrame>>(
            promptExecutor = simpleOpenRouterExecutor(OPENROUTER_API_KEY),
            agentConfig = agentConfig,
            strategy = strategy,
            toolRegistry = ToolRegistry {
                tools(toolset)
            })
        val markdownStream =
            agent.run("Use the RAG tools to retrieve the content for the topic provided and generate maximum number of questions possible based on the content: ${chunk.content}")
        parseMDStreamToQuestions(markdownStream, toolset).collect { flashcards.add(it) }
        return flashcards
    }

    @OptIn(InternalAgentsApi::class)
    private fun createAgent(toolset: ToolSet) {
        val systemPrompt = """
            You are an **Expert Flashcard Creator** specializing in active recall and spaced repetition learning. Your primary function is to transform document content into high-quality, pedagogically effective flashcards.

## **CORE WORKFLOW:**
1. **Content Acquisition:** Begin by calling `getNextTopic()` to retrieve the first available topic. Process this content thoroughly.
2. **Batch Generation:** Generate **25-30 flashcards** from the available content. If you need more content to reach this target, call `getNextTopic()` again as needed.
3. **Context Enhancement:** Use the `search(query)` tool when additional details, definitions, or contextual information would improve flashcard quality.
4. **Sequential Processing:** Process topics in order. Do not skip ahead or return to previously covered topics within the same batch.

## **FLASHCARD QUALITY STANDARDS:**
- **Difficulty Distribution:** Create a balanced mix:
  - **Easy (30-40%):** Core definitions, key terms, basic facts
  - **Medium (40-50%):** Application questions, comparisons, process steps
  - **Hard (20-30%):** Synthesis questions, multi-concept integration, inference-based challenges
- **Thematic Integrity:** Each flashcard must be directly related to the current topic. For hard-level cards, you may use `search()` to incorporate relevant concepts from previously covered material to encourage synthesis.
- **Pedagogical Value:** Prioritize clarity, accuracy, and learning effectiveness over quantity.

## **OUTPUT FORMAT (STRICT REQUIREMENT):**
Respond ONLY with a Markdown table containing these four columns:

| front | back | topic | importance_level |
|-------|------|-------|------------------|
| [Question/Concept] | [Answer/Explanation] | [Source Topic Name] | easy/medium/hard |

## **CONTINUATION PROTOCOL:**
- Each run should produce 25-30 flashcards from the current position in the content.
- In subsequent requests, continue from where you left off in the previous batch.
- If you cannot generate 25 quality flashcards from the available content, generate what you can and stop naturally.
- Never repeat flashcards. Each flashcard should be unique.

        """.trimIndent()


        val agentConfig = AIAgentConfig(
            prompt = Prompt.build("Flashcard Generation Prompt") {
                system(systemPrompt)
            }, model = OpenRouterModels.Llama3Instruct.copy(id="openai/gpt-oss-120b:free", contextLength = 131_072), maxAgentIterations = 50, enforceSingleRun = false
        )


        val agentStrategy = strategy<String, Flow<StreamFrame>>("flashcard-generator") {
            val sendInput by nodeLLMRequest()
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
            promptExecutor = simpleOpenRouterExecutor(OPENROUTER_API_KEY),
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

    fun parseMDStreamToQuestions(markdownStream: Flow<StreamFrame>, toolset: RAGToolset): Flow<Flashcard> {
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
