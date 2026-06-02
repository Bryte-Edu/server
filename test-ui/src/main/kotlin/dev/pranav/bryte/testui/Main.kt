package dev.pranav.bryte.testui

import dev.pranav.bryte.client.BryteClient
import dev.pranav.bryte.model.DocumentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import java.awt.BorderLayout
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

        val typeCombo = JComboBox(DocumentType.values())
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
        var currentSessionId: String? = null

        val scope = CoroutineScope(Dispatchers.Swing)

        btnCreateSession.addActionListener {
            val url = urlField.text.trim()
            val type = typeCombo.selectedItem as DocumentType
            if (url.isNotBlank()) {
                log("Creating session for: $url (Type: $type)...")
                scope.launch {
                    try {
                        val client = getClient()
                        val response = client.createSession(type, url)
                        currentSessionId = response.sessionId
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

        frame.add(topContainer, BorderLayout.NORTH)
        frame.add(scrollPane, BorderLayout.CENTER)

        frame.isVisible = true
    }
}
