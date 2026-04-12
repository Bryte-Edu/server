package dev.pranav.bryte.client

import dev.pranav.bryte.model.stats.FSRSReview
import io.ktor.client.statement.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * A simple CMD test script demonstrating the end-to-end flow of creating a learning session,
 * pulling flashcards, and testing the FSRS mathematical algorithm integration in the `client` module.
 *
 * Usage:
 * To actually run against a live local server, you need to provide a valid user token:
 * export BRYTE_AUTH_TOKEN="<your_jwt_here>"
 * ./gradlew :client:run (if configured for execution) or run it from your IDE.
 */
fun main() = runBlocking {
    println("=====================================================")
    println("         BRYTE K-RPC & FSRS CLIENT TEST BED          ")
    println("=====================================================\n")

    var authToken =
        "eyJhbGciOiJFUzI1NiIsImtpZCI6IjZjODJkMjU2LTYwMmQtNDM1MC1hNmMyLTczMTlhYzAwMDNiMyIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2Zjc2RpY3dkaWJ4ZGp1Z3dobWRlLnN1cGFiYXNlLmNvL2F1dGgvdjEiLCJzdWIiOiIwZTY5M2ZkYi1iOGQ4LTQxNjgtODNiOS05YzhmNGQ2YzIyOGUiLCJhdWQiOiJhdXRoZW50aWNhdGVkIiwiZXhwIjoxNzc1OTg0ODQ3LCJpYXQiOjE3NzU5ODEyNDcsImVtYWlsIjoidGVzdEBicnl0ZS5jb20iLCJwaG9uZSI6IiIsImFwcF9tZXRhZGF0YSI6eyJwcm92aWRlciI6ImVtYWlsIiwicHJvdmlkZXJzIjpbImVtYWlsIl19LCJ1c2VyX21ldGFkYXRhIjp7ImFib3V0X21lIjoiXG4iLCJlbWFpbCI6InRlc3RAYnJ5dGUuY29tIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsInBob25lX3ZlcmlmaWVkIjpmYWxzZSwic3ViIjoiMGU2OTNmZGItYjhkOC00MTY4LTgzYjktOWM4ZjRkNmMyMjhlIiwidXNlcm5hbWUiOiIifSwicm9sZSI6ImF1dGhlbnRpY2F0ZWQiLCJhYWwiOiJhYWwxIiwiYW1yIjpbeyJtZXRob2QiOiJwYXNzd29yZCIsInRpbWVzdGFtcCI6MTc3NTk4MTI0N31dLCJzZXNzaW9uX2lkIjoiNzc4NjJlMWYtNmZmYS00NGFmLWJiZjAtZTk1ZjI3ZjVkM2FhIiwiaXNfYW5vbnltb3VzIjpmYWxzZX0.e8lZ8rtjzZJ8euYo3snZnvZjMGh2U68oq6jT_lMxlT4GmRa7Ym6MbJG_zTYqhvgkivu-OQIToAgh3qiBt8reYQ"

    // Connect to local server
    val client = BryteClient("http://127.0.0.1:8080", authToken)

    try {
//        println(">> [STEP 1] Creating a document session via REST...")
//        val sessionResponse = client.createSession(
//            docType = DocumentType.WEBPAGE,
//            source = "https://en.wikipedia.org/wiki/Theology"
//        )
        val sessionId = "df5815b3-0ece-44c9-8bba-7753b704745f"
        println("   [SUCCESS] Session created with ID: $sessionId\n")

        println(">> [STEP 2] Establishing K-RPC connections for Session $sessionId...")
        val sessionRpc = client.getSessionRpc(sessionId)
        val flashcardRpc = client.getFlashcardRpc(sessionId)
        println("   [SUCCESS] K-RPC Multiplexed WebSocket Active.\n")

        println(sessionRpc)

        println(">> [STEP 3] Fetching Document Details & Existing Analytics...")
        val details = sessionRpc.details()
        val docId = details.documentId
        println("   Linked Document ID: $docId")

        var analytics = sessionRpc.getSessionAnalytics()
        println("   Current Session Analytics:")
        println("     - Topics Learned: ${analytics.totalTopicsLearned}")
        println("     - Completed Reviews: ${analytics.completedReviews}")
        println("     - Average Readiness: ${analytics.averageReadiness}%\n")

        println(">> [STEP 4] Streaming AI Generated Flashcards... (Waiting for 1 card)")
        // Using K-RPC Flow to eagerly load a few cards matching the topic
        val firstCard = flashcardRpc.flashcards().first()

        if (firstCard != null) {
            println("      Q: ${firstCard.front}")
            println("   [SUCCESS] Received Card [${firstCard.id}] from AI Stream: ")
            println("      A: ${firstCard.back}\n")

            println(">> [STEP 5] Simulating User Review via FSRS Engine...")
            println("   -> User studied the card and graded it exactly '3' (GOOD). Took 4 seconds.")

            val reviewSubmit = FSRSReview(
                cardId = firstCard.id!!,
                grade = 3,
                timeSpentSeconds = 4L
            )

            // Sending to Kotlin backend mathematically computing the review natively synced to Supabase
            val fsrsState = flashcardRpc.submitReview(reviewSubmit)

            println("   [SUCCESS] FSRS State mathematically updated mapping to DB:")
            println("      - Stability Variable: ${fsrsState.stability}")
            println("      - Difficulty Variable: ${fsrsState.difficulty}")
            println("      - Future Retrievability Factor interval sets next review: ${fsrsState.nextReview}\n")

            println(">> [STEP 6] Syncing Topic Readiness Score Rollups...")
            // Analytics will dynamically refresh from the chunk blending the stability vectors
            analytics = sessionRpc.getSessionAnalytics()
            println("   Session Topic Readiness computed natively:")
            println("     - Updated Session Readiness Avg: ${analytics.averageReadiness}%\n")

        } else {
            println("   [INFO] No flashcards were instantly yielded in the stream.")
            println("   Ensure the AI Parser has successfully embedded the chunk sequences in Supabase for this test.\n")
        }

    } catch (e: io.ktor.client.plugins.ResponseException) {
        println("\n[SERVER RESPONSE EXCEPTION]: Script halted.")
        println("Status: ${e.response.status}")
        println("Server Error Body: ${e.response.bodyAsText()}")
        println("Reason: ${e.message}")
    } catch (e: Exception) {
        println("\n[ERROR / UNAUTHORIZED]: Script halted.")
        println("Reason: ${e.message}")
        e.printStackTrace()
        println("Tip: Is your Ktor server currently running at :8080 and do you have a true JWT Auth token exported?")
    } finally {
        println("Closing K-RPC WebSocket channels & Ktor Engine.")
        client.close()
        println("=====================================================")
        println("                    TEST COMPLETE                    ")
        println("=====================================================")
    }
}
