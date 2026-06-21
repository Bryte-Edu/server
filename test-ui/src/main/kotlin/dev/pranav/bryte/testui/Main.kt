package dev.pranav.bryte.testui

import dev.pranav.bryte.client.BryteClient
import dev.pranav.bryte.model.DocumentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.serialization.json.*
import org.graphstream.graph.implementations.SingleGraph
import org.graphstream.ui.swing_viewer.SwingViewer
import org.graphstream.ui.view.Viewer
import java.awt.*
import javax.swing.*

fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("Bryte API & RPC Tester")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(1100, 750)

        val baseUrlField = JTextField("http://127.0.0.1:8080")
        val tokenField = JTextField("")
        val clientRef = arrayOf<BryteClient?>(null)

        val setupPanel = JPanel(GridLayout(2, 2, 5, 5))
        setupPanel.add(JLabel("Base URL:"))
        setupPanel.add(baseUrlField)
        setupPanel.add(JLabel("Auth Token (JWT):"))
        setupPanel.add(tokenField)

        val typeCombo = JComboBox(DocumentType.entries.toTypedArray())
        typeCombo.selectedItem = DocumentType.YOUTUBE
        val urlField = JTextField("https://www.youtube.com/watch?v=jjp3WC8Unj8")

        val inputPanel = JPanel(GridLayout(2, 2, 5, 5))
        inputPanel.add(JLabel("Doc Type:"))
        inputPanel.add(typeCombo)
        inputPanel.add(JLabel("Source/URL:"))
        inputPanel.add(urlField)

        val buttonsPanel = JPanel()
        val btnCreateSession = JButton("1. Create Session")
        val btnGraph = JButton("2. Get Graph")
        val btnFlashcards = JButton("3. Get Flashcards")
        buttonsPanel.add(btnCreateSession)
        buttonsPanel.add(btnGraph)
        buttonsPanel.add(btnFlashcards)

        // --- NEW RPC SERVICE CONTROL BOARDS ---
        val rpcControlsPanel = JPanel(GridLayout(1, 2, 10, 10))
        rpcControlsPanel.border =
            BorderFactory.createTitledBorder("RPC Real-time Services (Requires Active Session ID)")

        // SessionService Box
        val sessionServicePanel = JPanel(CompactLayout())
        sessionServicePanel.border = BorderFactory.createTitledBorder("SessionService RPC")
        val btnRpcDetails = JButton("Get Details")
        val btnRpcSavedQ = JButton("Saved Qs")
        val btnRpcStreamQ = JButton("Stream Questions (Flow)")
        val btnRpcAnalytics = JButton("Analytics Rollup")
        sessionServicePanel.add(btnRpcDetails)
        sessionServicePanel.add(btnRpcSavedQ)
        sessionServicePanel.add(btnRpcStreamQ)
        sessionServicePanel.add(btnRpcAnalytics)

        // FlashcardService Box
        val flashcardServicePanel = JPanel(CompactLayout())
        flashcardServicePanel.border = BorderFactory.createTitledBorder("FlashcardService RPC")
        val topicIdField = JTextField("target_topic_id")
        val btnRpcTopicCards = JButton("Cards by Topic")
        val btnRpcStreamCards = JButton("Stream Flashcards (Flow)")
        flashcardServicePanel.add(JLabel("Topic ID:"))
        flashcardServicePanel.add(topicIdField)
        flashcardServicePanel.add(btnRpcTopicCards)
        flashcardServicePanel.add(btnRpcStreamCards)

        rpcControlsPanel.add(sessionServicePanel)
        rpcControlsPanel.add(flashcardServicePanel)

        val logArea = JTextArea()
        val scrollPane = JScrollPane(logArea)
        logArea.isEditable = false

        val graphContainerPanel = JPanel(BorderLayout())
        graphContainerPanel.background = Color.WHITE
        val placeholderLabel = JLabel("No graph loaded. Create a session and click 'Get Graph'.", SwingConstants.CENTER)
        graphContainerPanel.add(placeholderLabel, BorderLayout.CENTER)

        fun log(text: String) {
            logArea.append(text + "\n")
            logArea.caretPosition = logArea.document.length
        }

        fun getClient(): BryteClient {
            if (clientRef[0] == null) {
                clientRef[0] = BryteClient(baseUrlField.text.trim(), tokenField.text.trim().takeIf { it.isNotEmpty() })
            } else {
                clientRef[0]!!.baseUrl = baseUrlField.text.trim()
                clientRef[0]!!.authToken = tokenField.text.trim().takeIf { it.isNotEmpty() }
            }
            return clientRef[0]!!
        }

        var currentSessionId: String? = null
        var currentDocId: String? = null
        val scope = CoroutineScope(Dispatchers.Swing)

        fun renderGraphStreamJson(graphData: JsonObject) {
            try {
                val rawNodes = graphData["nodes"]?.jsonArray ?: buildJsonArray { }
                val rawEdges = graphData["edges"]?.jsonArray ?: buildJsonArray { }
                val graph = SingleGraph("BryteCoreGraph")

                graph.setAttribute("ui.antialias", true)
                graph.setAttribute("ui.quality", true)

                rawNodes.forEachIndexed { index, nodeElement ->
                    val n = nodeElement.jsonObject
                    val id = n["id"]?.jsonPrimitive?.content ?: ""
                    val label = n["label"]?.jsonPrimitive?.content ?: "Untitled Node"

                    val node = graph.addNode(id)
                    node.setAttribute("ui.label", label)
                    if (index == 0) {
                        node.setAttribute(
                            "ui.style",
                            "fill-color: rgb(239,68,68); size: 36px; text-size: 15px; text-background-mode: rounded-box; text-background-color: rgb(255,255,255); text-alignment: under; text-padding: 3px; text-style: bold;"
                        )
                    } else {
                        node.setAttribute(
                            "ui.style",
                            "fill-color: rgb(59,130,246); size: 24px; text-size: 13px; text-background-mode: rounded-box; text-background-color: rgb(255,255,255); text-alignment: under; text-padding: 3px;"
                        )
                    }
                }

                rawEdges.forEachIndexed { idx, edgeElement ->
                    val e = edgeElement.jsonObject
                    val source = e["source"]?.jsonPrimitive?.content ?: ""
                    val target = e["target"]?.jsonPrimitive?.content ?: ""
                    val isInternal = e["isInternal"]?.jsonPrimitive?.booleanOrNull ?: true

                    if (graph.getNode(source) != null && graph.getNode(target) != null) {
                        val edgeId = "edge_${idx}_${source}_${target}"
                        val edge = graph.addEdge(edgeId, source, target)
                        if (isInternal) {
                            edge.setAttribute("ui.style", "fill-color: rgb(203,213,225); size: 1px;")
                        } else {
                            edge.setAttribute("ui.style", "fill-color: rgb(147,197,253); size: 2px;")
                        }
                    }
                }

                val viewer = SwingViewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD)
                viewer.enableAutoLayout()
                val graphViewComponent = viewer.addDefaultView(false) as Component

                graphContainerPanel.removeAll()
                graphContainerPanel.add(graphViewComponent, BorderLayout.CENTER)
                graphContainerPanel.revalidate()
                graphContainerPanel.repaint()
                log("Graph rendering engine successfully loaded component view safely.")
            } catch (ex: Exception) {
                log("Failed to process GraphStream initialization: ${ex.message}")
            }
        }

        // --- CORE CORE HTTP IMPLEMENTATIONS ---
        btnCreateSession.addActionListener {
            val url = urlField.text.trim()
            val type = typeCombo.selectedItem as DocumentType
            if (url.isNotBlank()) {
                log("Creating session for: $url (Type: $type)...")
                scope.launch {
                    try {
                        val client = getClient()
                        val response = client.createSession(type, url) { log(it) }
                        currentSessionId = response.sessionId
                        log("Session Created. Bound System RPC Session ID: $currentSessionId")

                        val rpc = client.getSessionRpc(response.sessionId)
                        val details = rpc.details()
                        currentDocId = details.documentId
                        log("Initial RPC Context Verification -> Linked Doc: ${details.documentId}")
                    } catch (e: Exception) {
                        log("Error: ${e.message}\n${e.stackTraceToString()}")
                    }
                }
            }
        }

        btnGraph.addActionListener {
            val docId = currentDocId ?: JOptionPane.showInputDialog(frame, "Enter Document ID:")
            if (!docId.isNullOrBlank()) {
                currentDocId = docId
                log("Fetching Graph visualization for: $docId...")
                scope.launch {
                    try {
                        val client = getClient()
                        val response = client.getGraphVisualization(docId)
                        renderGraphStreamJson(Json.parseToJsonElement(response).jsonObject)
                    } catch (e: Exception) {
                        log("Error fetching graph: ${e.message}")
                    }
                }
            }
        }

        btnFlashcards.addActionListener {
            val docId = currentDocId ?: JOptionPane.showInputDialog(frame, "Enter Document ID:")
            if (!docId.isNullOrBlank()) {
                currentDocId = docId
                log("Fetching standard fallback Flashcards for: $docId...")
                scope.launch {
                    try {
                        val client = getClient()
                        val response = client.getFlashcards(docId)
                        log("Flashcards count total: ${response.size}")
                        response.forEach { f -> log(" - Q: ${f.front} | A: ${f.back}") }
                    } catch (e: Exception) {
                        log("Error fetching flashcards: ${e.message}")
                    }
                }
            }
        }

        // --- NEW ACTION BINDINGS FOR INTERFACES ---
        fun ensureSession(action: suspend (String) -> Unit) {
            val session = currentSessionId ?: JOptionPane.showInputDialog(frame, "Enter Session ID manually:")
            if (!session.isNullOrBlank()) {
                currentSessionId = session
                scope.launch {
                    try {
                        action(session)
                    } catch (e: Exception) {
                        log("RPC Error: ${e.message}")
                    }
                }
            } else {
                log("Execution aborted: Active Session ID missing.")
            }
        }

        btnRpcDetails.addActionListener {
            ensureSession { id ->
                log("Invoking SessionService.details()...")
                val details = getClient().getSessionRpc(id).details()
                log("RPC Response -> User: ${details.userId} | Doc: ${details.documentId} | Level: ${details.createdAt}")
            }
        }

        btnRpcSavedQ.addActionListener {
            ensureSession { id ->
                log("Invoking SessionService.savedQuestions()...")
                val questions = getClient().getSessionRpc(id).savedQuestions()
                log("Fetched ${questions.size} Static System Questions:")
                questions.forEach { q -> log("  • [ID: ${q.id}] ${q.content} (Answer: ${q.explanation})") }
            }
        }

        btnRpcStreamQ.addActionListener {
            ensureSession { id ->
                log("Opening connection to SessionService.questions() Flow channel stream...")
                getClient().getSessionRpc(id).questions().collect { question ->
                    log("⚡ Live Flow Question Received -> [ID: ${question.id}] ${question.content}")
                }
                log("✓ Questions flow stream ended.")
            }
        }

        btnRpcAnalytics.addActionListener {
            ensureSession { id ->
                log("Invoking SessionService.getSessionAnalytics()...")
                val analytics = getClient().getSessionRpc(id).getSessionAnalytics()
                log("Analytics Matrix Loaded -> Summary Score metrics computed dynamically.")
                log(analytics.toString())
            }
        }

        btnRpcTopicCards.addActionListener {
            ensureSession { id ->
                val topic = topicIdField.text.trim()
                log("Invoking FlashcardService.flashcardsByTopic() for target: $topic...")
                val cards = getClient().getFlashcardRpc(id).flashcardsByTopic(topic)
                log("Found ${cards.size} Flashcards for Topic: $topic")
                cards.forEach { c -> log("  📇 Front: ${c.front} | Back: ${c.back}") }
            }
        }

        btnRpcStreamCards.addActionListener {
            ensureSession { id ->
                log("Opening pipeline connection to FlashcardService.flashcards() Flow emitter...")
                getClient().getFlashcardRpc(id).flashcards().collect { card ->
                    log("⚡ Live Flow Flashcard Streamed -> Q: ${card.front}")
                }
                log("✓ Flashcard flow stream ended.")
            }
        }

        frame.layout = BorderLayout()
        val controlHubContainer = JPanel(BorderLayout(5, 5))
        val httpForms = JPanel(BorderLayout())
        httpForms.add(setupPanel, BorderLayout.NORTH)
        httpForms.add(inputPanel, BorderLayout.CENTER)
        httpForms.add(buttonsPanel, BorderLayout.SOUTH)

        controlHubContainer.add(httpForms, BorderLayout.NORTH)
        controlHubContainer.add(rpcControlsPanel, BorderLayout.CENTER)

        val splitConsoleGraph = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, graphContainerPanel, scrollPane)
        splitConsoleGraph.dividerLocation = 600

        val masterVerticalSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, controlHubContainer, splitConsoleGraph)
        masterVerticalSplit.dividerLocation = 260

        frame.add(masterVerticalSplit, BorderLayout.CENTER)
        frame.isVisible = true
    }
}

private fun CompactLayout() = BoxLayout(JPanel(), BoxLayout.Y_AXIS).let { FlowLayout(FlowLayout.LEFT, 8, 4) }