package dev.pranav.bryte.server.ai

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.feature.handler.agent.AgentStartingContext
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
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
import ai.koog.rag.vector.storage.InMemoryDocumentEmbeddingStorage
import dev.pranav.bryte.model.quiz.Content
import dev.pranav.bryte.model.quiz.Question
import dev.pranav.bryte.model.session.DocumentChunk
import dev.pranav.bryte.model.session.Session
import dev.pranav.bryte.server.GEMINI_API_KEY
import dev.pranav.bryte.server.MISTRAL_API_KEY
import dev.pranav.bryte.server.ai.embedding.TextDocumentEmbedder
import dev.pranav.bryte.server.migration.Neo4jManager
import dev.pranav.bryte.server.postgrest.DocumentChunkRepository
import dev.pranav.bryte.server.postgrest.QuestionRepository
import dev.pranav.bryte.server.postgrest.TopicAnalyticsRepository
import dev.pranav.bryte.server.util.serialization.markdownStreamingParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking


//suspend fun main() = runBlocking {
////    val sessions by supabase.sessions()
////    val documentChunks by supabase.documentChunks()
////    val generator = QuestionGenerator(sessions.getById("ef265311-2207-41b2-a59b-452c9f119c98")!!, documentChunks)
////    generator.generateQuestions().let {
////        println("Generated questions")
////
////        it.collect {
////            println(it)
////        }
////
////        println()
////        println("First batch complete")
////
////    }
//
//    val service = AIAgentService(
//        promptExecutor = simpleGoogleAIExecutor(GEMINI_API_KEY),
//        agentConfig = AIAgentConfig(
//            prompt = Prompt.build("Test Prompt") {
//                system("You are a helpful assistant.")
//            }, model = GoogleModels.Gemini2_5Flash, maxAgentIterations = 5
//        ),
//        singleRunStrategy()
//    )
//
//    val agent = service.createAgent()
//    println(agent.run("Hello Jon"))
//    println(agent.run("What was my last message?"))
//
//}

/**
 * AI-powered question generator that creates questions from document chunks.
 * This class handles the logic for generating different types of questions
 * using Koog AI framework.
 */
class QuestionGenerator(
    val session: Session,
    private val documentChunks: DocumentChunkRepository,
    private val questions: QuestionRepository,
    private val topicAnalyticsRepo: TopicAnalyticsRepository? = null
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
        InMemoryDocumentEmbeddingStorage(embedder)
    }

    private val documentTopics: Set<DocumentChunk>


    init {
        require(session.id.isNotBlank()) { "Session ID must not be blank" }

        documentTopics = runBlocking {
            var topics = documentChunks.getByDocumentId(session.documentId).toList()
            if (topicAnalyticsRepo != null) {
                // Fetch stats for all topics and sort by readiness (lowest readiness first)
                val stats = topics.map { it.id to topicAnalyticsRepo.getByTopicId(it.id!!) }
                val scoreMap = stats.associate { it.first to (it.second?.readinessScore ?: Double.MAX_VALUE) }
                topics = topics.sortedBy { scoreMap[it.id] ?: Double.MAX_VALUE }
            } else {
                topics = topics.sortedBy { it.index }
            }
            topics.toSet()
        }

        runBlocking {
            documentStorage.add(documentTopics.filter { it.content.length > 200 }.mapNotNull { it.id })
        }
    }

    @Suppress("unused")
    @LLMDescription("Tools for retrieving document topics and searching content.")
    inner class RAGToolset : ToolSet {
        var index = 0

        fun currentTopicId(): String {
            return documentTopics.elementAt(index).id!!
        }

        @Tool
        @LLMDescription("Returns the next topic content from document.")
        fun getNextTopic(): String {
            if (index >= documentTopics.size) {
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
                h2("Graph RAG Context: $query")

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

                    h3("Result ${idx + 1}: $sectionHeader")
                    text("source=$docSource | focusDoc=$isFocusDoc | baseScore=$matchScore | weightedScore=$weightedScore")
                    text(sectionContent)

                    @Suppress("UNCHECKED_CAST")
                    val related = res["related"] as? List<Map<String, Any>> ?: emptyList()
                    if (related.isNotEmpty()) {
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

                text("Prefer focusDoc=true results by default. Only use cross-doc bridges when the current chunk references prerequisites or when a contrast improves question quality.")
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

        @Tool
        @LLMDescription("Call this tool ONLY when you determine that the *entire document* has been processed and you can no longer generate meaningful, high-quality, deep-thinking questions from the remaining content. This signals the **absolute end** of the question generation task.")
        fun contentExhausted(): Boolean {
            return true
        }
    }

    private lateinit var generationAgent: AIAgent<String, Flow<StreamFrame>>

    /**
     * Generates questions from a list of document chunks.
     *
     * @return List of generated Question objects
     */
    suspend fun generateQuestions(): Flow<Question> {
        val toolset = RAGToolset()

        if (!::generationAgent.isInitialized) {
            createAgent(toolset)
        }
//
//        while (generationAgent.agentConfig.) {
//            delay(500)
//            println("Waiting for previous generation to complete...")
//        }

        val markdownStream = generationAgent.run("Generate unique questions based on the content")
        return parseMDStreamToQuestions(markdownStream, toolset)
    }

    /**
     * Generates questions for a single document chunk.
     *
     * @param sessionId The session ID for the questions
     * @param chunk The document chunk to generate questions from
     *
     * @return List of generated Question objects for this chunk
     */
    suspend fun generateQuestionsForChunk(
        sessionId: String, chunk: DocumentChunk
    ): List<Question> {

        val questions = mutableListOf<Question>()
        val toolset = RAGToolset()
        toolset.index = documentTopics.indexOf(chunk)

        val systemPrompt = """
        You are an **Expert Question Generator** AI specializing in **critical thinking, inference, and foundational recall**. Your task is to generate high-quality, relevant, and diverse questions (MULTIPLE_CHOICE, SPOT_THE_ERROR, MATCH_THE_FOLLOWING) based **STRICTLY ON THE DOCUMENT CONTENT PROVIDED IN THE INITIAL PROMPT**.

        **GENERATION MANDATE (Varying Difficulty):**
        * **Coverage:** Questions MUST cover the full range of difficulty: **easy** (key definitions, simple recall), **medium** (application, comparison), and **hard** (synthesis, complex inference).
        * **Focus:** Ensure all **key definitions, important terms, and fundamental facts** in the chunk are covered.

        **GENERATION CONSTRAINTS:**
        1. **Content Source:** The content you must base your questions on is the input text you just received.
        2. **Allowed Tool Use for Synthesis:** You are **permitted** to use the 'search(query)' tool to look up previously introduced concepts that are **referenced or built upon** in the current chunk. This is essential for creating cross-topic synthesis questions.
        3. **STRICT THEMATIC ANCHOR (Cross-Topic Synthesis):** The question's primary subject must be rooted in the current input chunk. However, for hard questions, you **CAN** use the 'search' tool to retrieve relevant details from *previous topics* to force the user to synthesize ideas or apply foundational knowledge that is mentioned or implied in the current content. DO NOT introduce a completely new topic not related to the current chunk.
        4. **Forbidden Tools:** DO NOT use 'getNextTopic()' or 'getAllTopics()'.

        **STRICT OUTPUT RULE:**
        * RESPOND **STRICTLY AND ONLY** in the required Markdown structured format, including the required 'difficulty' field.
        * **Quality Saturation Gate:** If, after analyzing the current content **AND** after utilizing the 'search(query)' tool to explore supplementary information, you determine that continuing to generate questions would result in only **low-quality, redundant, or simple recall-based questions**, you **MUST** call the 'contentExhausted()' tool. **This signals the immediate and complete exhaustion of this single chunk's high-quality question potential.**
    """.trimIndent()

        val agentConfig = AIAgentConfig(
            prompt = Prompt.build("Question Generation Prompt") {
                system(systemPrompt)
            }, model = GoogleModels.Gemini2_5Flash, maxAgentIterations = 20
        )

        val strategy = strategy<String, Flow<StreamFrame>>("question-generator-single-chunk") {
            val sendInput by nodeLLMRequest()
            val runTools by nodeExecuteTool()
            val sendToolResults by nodeLLMSendToolResult()

            val returnStream by nodeLLMRequestStreaming("questions", questionsStructure)

            edge(nodeStart forwardTo sendInput)
            edge(sendInput forwardTo runTools onToolCall { true })
            edge(runTools forwardTo sendToolResults)
            edge(sendToolResults forwardTo runTools onToolCall { true })
            edge(sendToolResults forwardTo returnStream onAssistantMessage { true })
            edge(returnStream forwardTo nodeFinish)
        }

        val agent = AIAgent<String, Flow<StreamFrame>>(
            promptExecutor = simpleGoogleAIExecutor(GEMINI_API_KEY),
            agentConfig = agentConfig,
            strategy = strategy,
            toolRegistry = ToolRegistry {
                tools(toolset)
            })
        val markdownStream =
            agent.run("Use the RAG tools to retrieve the content for the topic provided and generate maximum number of questions possible based on the content: ${chunk.content}")
        parseMDStreamToQuestions(markdownStream, toolset).collect { questions.add(it) }
        return questions
    }

    private fun createAgent(toolset: ToolSet) {
        val systemPrompt = """
            You are an **Expert Question Generator** AI specializing in **critical thinking, inference, and foundational recall**. Your task is to process the available document content and generate a batch of high-quality, relevant, and diverse questions (MULTIPLE_CHOICE, SPOT_THE_ERROR, MATCH_THE_FOLLOWING).

            **TASK FLOW, LIMITS, AND MANDATE:**
            1. **Content Retrieval:** You **MUST** call the 'getNextTopic()' tool to retrieve the first available topic. Process this content and continue calling 'getNextTopic()' **only as needed to generate your target batch.**
            2. **Output Limit:** Generate a total of **10 to 15 questions** across the topics you retrieve, then **IMMEDIATELY STOP**. Do not exceed 15 questions.
            3. **Graph RAG Contextual Tool Use (IMPORTANT):**
               - You are permitted to use `graphContext(query)` when you need definitions, prerequisites, mechanisms, or exact phrasing.
               - The tool returns results with `focusDoc=true/false`. Prefer `focusDoc=true` evidence to keep questions anchored to the current document.
               - Use cross-document bridges only to clarify prerequisites or to create a synthesis/contrast question.
            4. **STRICT THEMATIC ANCHOR:** The question's primary subject must be rooted in the current input chunk. Graph RAG is supporting evidence; it must not cause topic drift.

            **GENERATION MANDATE (Pedagogical Engineering):**
            * **Diagnostic Distractor Rule (MCQs):** You MUST NOT generate obviously wrong answers. Every "wrong" option must represent a specific Cognitive Trap.
            * **Synthetic Conflict (Hard Questions):** Use the `graphContext` or `search` tool to find nuance from earlier content. Create questions that ask the user to reconcile two different parts of the document.
            * **Error Classification (Spot the Error):** Errors should be logical inconsistencies, not spelling mistakes.

            **STRICT OUTPUT RULE:**
            * After reaching your output limit, RESPOND strictly in the required Markdown structured format.
        """.trimIndent()


        val agentConfig = AIAgentConfig(
            prompt = Prompt.build("Question Generation Prompt") {
                system(systemPrompt)
            }, model = GoogleModels.Gemini2_5FlashLite, maxAgentIterations = 50, enforceSingleRun = false
        )


        val agentStrategy = strategy<String, Flow<StreamFrame>>("question-generator") {
            val sendInput by nodeLLMRequest()
            val runTools by nodeExecuteTool()
            val sendToolResults by nodeLLMSendToolResult()

            val returnStream by nodeLLMRequestStreaming("questions", questionsStructure)

            edge(nodeStart forwardTo sendInput)
            edge(sendInput forwardTo runTools onToolCall { true })
            edge(runTools forwardTo sendToolResults)
            edge(sendToolResults forwardTo runTools onToolCall { true })
            edge(sendToolResults forwardTo returnStream onAssistantMessage { true })
            edge(returnStream forwardTo nodeFinish)
        }

        generationAgent = AIAgent<String, Flow<StreamFrame>>(
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
                    onToolCallStarting {
                        println("Tool call started: ${it.toolName}")
                    }

                    onToolCallCompleted {
                        if (it.toolName == "contentExhausted") {
                            exhausted = true
                            println("Exhaustion tool called. Ending question generation.")
                        }
                    }
                }
            })
    }

    fun parseMDStreamToQuestions(markdownStream: Flow<StreamFrame>, toolset: RAGToolset): Flow<Question> {
        return flow {
            markdownStreamingParser {
                var type = ""
                var question = ""
                var difficulty = ""
                var explanation = ""
                var correctAnswerIndex = -1
                val bulletPoints = mutableListOf<String>()
                val rows = mutableListOf<Pair<String, String>>()
                val correctMatches = mutableListOf<Pair<Int, Int>>()

                onHeader(4) { headerText ->
                    if (headerText.isEmpty()) return@onHeader

                    println("Question type: $headerText")

                    if (type.isEmpty()) {
                        type = headerText
                        rows.clear()
                        correctMatches.clear()
                        bulletPoints.clear()
                        return@onHeader
                    }

                    val content = when (type) {
                        "mcq" -> {
                            Content.MultipleChoice(
                                question, options = bulletPoints.toList(), correctOptionIndex = correctAnswerIndex
                            )
                        }

                        "spot_the_error" -> {
                            val steps = bulletPoints.mapIndexed { index, desc ->
                                Content.SpotTheError.Step(
                                    isCorrect = index != correctAnswerIndex, stepNumber = index + 1, description = desc
                                )
                            }
                            Content.SpotTheError(
                                steps = steps, scenario = question, errorStepIndex = correctAnswerIndex
                            )
                        }

                        "match_the_following" -> {
                            val leftItems = rows.map { it.first }
                            val rightItems = rows.map { it.second }

                            Content.MatchTheFollowing(
                                leftItems = leftItems, rightItems = rightItems, correctMatches = correctMatches
                            )
                        }

                        else -> {
                            throw IllegalArgumentException("Unknown question type: $type")
                        }
                    }

                    val question = Question(
                        sessionId = session.id,
                        chunkId = toolset.currentTopicId(),
                        page = toolset.index + 1,
                        type = content.type,
                        difficulty = difficulty,
                        content = content,
                        explanation = explanation,
                    )

                    questions.insert(question)

                    emit(question)

                    correctMatches.clear()
                    rows.clear()

                    type = headerText
                }

                onHeader(1) { headerText ->
                    if (headerText.isEmpty()) return@onHeader
                    bulletPoints.clear()
                    question = headerText
                }

                onHeader(2) { headerText ->
                    if (headerText.isEmpty()) return@onHeader
                    explanation = headerText
                }

                onHeader(5) { headerText ->
                    if (headerText.isEmpty()) return@onHeader
                    difficulty = headerText.lowercase()
                }

                onHeader(3) { headerText ->
                    if (headerText.isEmpty()) return@onHeader
                    correctAnswerIndex = headerText.toIntOrNull() ?: -1

                    if (type == "match_the_following") {
                        val parts = headerText.removePrefix("[(").removeSuffix(")]").split("), (")
                        parts.forEach { pairStr ->
                            val indices = pairStr.split(",").map { it.trim().toInt() }
                            if (indices.size == 2) {
                                correctMatches.add(Pair(indices[0], indices[1]))
                            }
                        }
                    }
                }

                onTable { _, row ->
                    if (row.size >= 2) {
                        val leftItem = row[0]
                        val rightItem = row[1]
                        rows.add(Pair(leftItem, rightItem))
                    }
                }

                // Handle the event of receiving the Markdown bullets list in the response stream
                onBullet { bulletText ->
                    bulletPoints.add(bulletText)
                }

                onFinishStream {
                    val shouldEmitFinal = when (type) {
                        "match_the_following" -> rows.isNotEmpty()
                        "mcq", "spot_the_error" -> question.isNotEmpty() && bulletPoints.isNotEmpty()
                        else -> false
                    }

                    if (shouldEmitFinal) {
                        val content = when (type) {
                            "mcq" -> {
                                Content.MultipleChoice(
                                    question, options = bulletPoints.toList(), correctOptionIndex = correctAnswerIndex
                                )
                            }

                            "spot_the_error" -> {
                                val steps = bulletPoints.mapIndexed { index, desc ->
                                    Content.SpotTheError.Step(
                                        isCorrect = index != correctAnswerIndex,
                                        stepNumber = index + 1,
                                        description = desc
                                    )
                                }
                                Content.SpotTheError(
                                    steps = steps, scenario = question, errorStepIndex = correctAnswerIndex
                                )
                            }

                            "match_the_following" -> {
                                val leftItems = rows.map { it.first }
                                val rightItems = rows.map { it.second }
                                Content.MatchTheFollowing(
                                    leftItems = leftItems, rightItems = rightItems, correctMatches = correctMatches
                                )
                            }

                            else -> {
                                throw IllegalArgumentException("Unknown question type: $type")
                            }
                        }

                        val question =
                            Question(
                                sessionId = session.id,
                                chunkId = toolset.currentTopicId(),
                                page = toolset.index,
                                type = content.type,
                                difficulty = difficulty,
                                content = content,
                                explanation = explanation,
                            )

                        questions.insert(question)

                        emit(question)

                    }
                }

            }.parseStream(markdownStream.filterTextOnly())
        }
    }

    companion object {
        val questionsStructure = MarkdownStructureDefinition("questionsList", schema = {
            markdown {
                bulleted {
                    item {
                        h4("mcq")
                        h1("question")
                        bulleted {
                            item("option A")
                            item("option B")
                            item("option C")
                            item("option D")
                        }
                        h2("explanation")
                        h3("correct_answer_index")
                        h5("difficulty")
                    }

                    item {
                        h4("spot_the_error")
                        h1("scenario")
                        bulleted {
                            item("step 1 description")
                            item("step 2 description")
                            item("step 3 description")
                            item("step 4 description")
                        }
                        h2("explanation")
                        h3("correct_answer_index")
                        h5("difficulty")
                    }

                    item {
                        h4("match_the_following")
                        table(
                            headers = listOf("Left Items", "Right Items"), rows = listOf(
                                listOf("Left Item 1", "Right Item A"),
                                listOf("Left Item 2", "Right Item B"),
                                listOf("Left Item 3", "Right Item C"),
                                listOf("Left Item 4", "Right Item D"),
                            )
                        )
                        h2("explanation")
                        h3("correct_matches")
                        h5("difficulty")
                    }
                }
            }
        }, examples = {
            markdown {
                bulleted {
                    item {
                        h4("mcq")
                        h1("What is the capital of France?")
                        bulleted {
                            item("Berlin")
                            item("Madrid")
                            item("Paris")
                            item("Rome")
                        }
                        h2("Paris is the capital and most populous city of France.")
                        h3("2")
                        h5("medium")
                    }

                    item {
                        h4("spot_the_error")
                        h1("A student is analyzing how different inductive learning algorithms can incorporate transfer learning. They are focusing on how source-task knowledge influences the learning process in the target task.")
                        bulleted {
                            item("In inductive transfer methods, source-task knowledge is used to modify or select the inductive bias for the target task.")
                            item("This adjustment of inductive bias can involve narrowing the hypothesis space, thereby limiting the possible models the learning algorithm can consider.")
                            item("Conversely, inductive transfer can also broaden the hypothesis space, allowing the algorithm to discover more complex or varied models than it might initially consider.")
                            item("Thrun and Mitchell's work on lifelong learning with neural networks enhances the standard gradient-descent algorithm by modifying the initial random weights of the network based on previous tasks, which speeds up convergence.")
                            item("Mihalkova and Mooney's approach to transfer between Markov Logic Networks involves starting with a source-task MLN and adjusting its formulas to be more general or specific for the target domain.")
                        }
                        h2("Thrun and Mitchell's approach enhances the gradient-descent algorithm with *slope information* acquired from previous tasks, not by modifying the initial random weights. This biases the search for network parameters.")
                        h3("3")
                        h5("hard")
                    }

                    item {
                        h4("match_the_following")
                        table(
                            headers = listOf("Left Items", "Right Items"), rows = listOf(
                                listOf("Water", "C6H12O6"),
                                listOf("Carbon Dioxide", "NaCl"),
                                listOf("Sodium Chloride", "H2O"),
                                listOf("Glucose", "CO2"),
                            )
                        )
                        h2("Water is H2O, Carbon Dioxide is CO2, Sodium Chloride is NaCl, and Glucose is C6H12O6.")
                        h3("[(1, 3), (2, 4), (3, 2), (4, 1)]")
                        h5("easy")
                    }
                }
            }
        })
    }
}