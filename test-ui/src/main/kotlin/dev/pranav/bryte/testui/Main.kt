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
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.GridLayout
import javax.swing.*

fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("Bryte API Tester")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(800, 600)

        val baseUrlField = JTextField("http://127.0.0.1:8080")
        val tokenField = JTextField("")

        val clientRef = arrayOf<BryteClient?>(null)

        val setupPanel = JPanel(GridLayout(2, 2))
        setupPanel.add(JLabel("Base URL:"))
        setupPanel.add(baseUrlField)
        setupPanel.add(JLabel("Auth Token (JWT):"))
        setupPanel.add(tokenField)

        val typeCombo = JComboBox(DocumentType.entries.toTypedArray())
        typeCombo.selectedItem = DocumentType.YOUTUBE

        val urlField = JTextField("https://www.youtube.com/watch?v=jjp3WC8Unj8")

        val inputPanel = JPanel(GridLayout(2, 2))
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

                // 3. Link Edges confirming source/target references exist
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
                ex.printStackTrace()
            }
        }
        btnCreateSession.addActionListener {
            val url = urlField.text.trim()
            val type = typeCombo.selectedItem as DocumentType
            if (url.isNotBlank()) {
                log("Creating session for: $url (Type: $type)...")
                scope.launch {
                    try {
                        val client = getClient()
                        val response = client.createSession(type, url)
                        log("Session Created. Session ID: ${response.sessionId}")

                        // We can fetch details via RPC
                        val rpc = client.getSessionRpc(response.sessionId)
                        val details = rpc.details()
                        currentDocId = details.documentId
                        log("RPC Details -> Document ID: ${details.documentId}")
                    } catch (e: Exception) {
                        log("Error: ${e.message}")
                        log("Stacktrace: ${e.stackTraceToString()}")
                        e.printStackTrace()
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
                        log("Graph Response:\n$response")

                        renderGraphStreamJson(Json.parseToJsonElement(response).jsonObject)
                    } catch (e: Exception) {
                        log("Error fetching graph: ${e.message}")
                        log("Stacktrace: ${e.stackTraceToString()}")
                    }
                }
            }
        }

        btnFlashcards.addActionListener {
            val docId = currentDocId ?: JOptionPane.showInputDialog(frame, "Enter Document ID:")
            if (!docId.isNullOrBlank()) {
                currentDocId = docId
                log("Fetching Flashcards for: $docId...")
                scope.launch {
                    try {
                        val client = getClient()
                        val response = client.getFlashcards(docId)
                        log("Flashcards count: ${response.size}")
                        response.forEach { f ->
                            log(" - Q: ${f.front} | A: ${f.back}")
                        }
                    } catch (e: Exception) {
                        log("Error fetching flashcards: ${e.message}")
                        log("Stacktrace: ${e.stackTraceToString()}")
                    }
                }
            }
        }

        frame.layout = BorderLayout()
        val topContainer = JPanel(BorderLayout())
        topContainer.add(setupPanel, BorderLayout.NORTH)
        topContainer.add(inputPanel, BorderLayout.CENTER)
        topContainer.add(buttonsPanel, BorderLayout.SOUTH)

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, graphContainerPanel, scrollPane)
        splitPane.dividerLocation = 650

        frame.add(topContainer, BorderLayout.NORTH)
        frame.add(splitPane, BorderLayout.CENTER)

        frame.isVisible = true
    }
}
