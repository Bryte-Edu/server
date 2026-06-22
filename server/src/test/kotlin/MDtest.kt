import dev.pranav.bryte.server.util.serialization.markdownStreamingParser
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownParserBuilderTest {

    @Test
    fun `test streaming parser handles broken chunks across tables and headers`() = runTest {
        // Track caught events
        val capturedHeaders = mutableListOf<Pair<Int, String>>()
        val capturedTableRows = mutableListOf<Pair<List<String>, List<String>>>()
        val capturedBullets = mutableListOf<String>()
        var finishCalled = false
        var lastBufferValue = ""

        // Configure the parser builder
        val streamingParser = markdownStreamingParser {
            onHeader(1) { text -> capturedHeaders.add(1 to text) }
            onHeader(4) { text -> capturedHeaders.add(4 to text) }

            onTable { headers, row ->
                capturedTableRows.add(headers to row)
            }

            onBullet { text -> capturedBullets.add(text) }

            onFinishStream { remainingBuffer ->
                finishCalled = true
                lastBufferValue = remainingBuffer
            }
        }

        // Simulate an LLM streaming chunks broken at brutal syntactic boundaries
        val brokenMarkdownStream = flowOf(
            "# Title of Qu",                          // Chunk 1: Cut off mid-header text
            "estion\n\n#### mcq\n| Left ",             // Chunk 2: Finish H1, start H4, start table row
            "Items | Right Items |\n|---|---|\n| Wa",  // Chunk 3: End table header row, start table data cell
            "ter | H2O |\n| Glucose | C6H12O",       // Chunk 4: Mid-row cell data cutoff
            "6 |\n\n- An option bullet point\n",       // Chunk 5: Complete table row, start bullet point
            "\n# Next Block\n- F",                    // Chunk 6: Start a new H1 section block
            "inal Bullet"                             // Chunk 7: Streaming finishes mid-bullet word
        )

        // Execute stream processing
        streamingParser.parseStream(brokenMarkdownStream)


        // 1. Verify Headers were parsed cleanly without fragment bleeding
        assertEquals(3, capturedHeaders.size, "Should have parsed exactly 3 headers")
        assertEquals(1 to "Title of Question", capturedHeaders[0])
        assertEquals(4 to "mcq", capturedHeaders[1])
        assertEquals(1 to "Next Block", capturedHeaders[2])

        // 2. Verify Table Parsing succeeded regardless of crossing chunk bounds
        assertEquals(2, capturedTableRows.size, "Should capture exactly 2 data rows")

        val expectedTableHeaders = listOf("Left Items", "Right Items")

        // Row 1 verification
        assertEquals(expectedTableHeaders, capturedTableRows[0].first)
        assertEquals(listOf("Water", "H2O"), capturedTableRows[0].second)

        // Row 2 verification (Checking correct extraction across chunk 4 & 5 boundary)
        assertEquals(expectedTableHeaders, capturedTableRows[1].first)
        assertEquals(listOf("Glucose", "C6H12O6"), capturedTableRows[1].second)

        // 3. Verify Bullet Points
        assertEquals(2, capturedBullets.size, "Should capture exactly 2 bullet points")
        assertEquals("An option bullet point", capturedBullets[0])
        assertEquals("Final Bullet", capturedBullets[1])

        // 4. Lifecycle Verification
        assertTrue(finishCalled, "onFinishStream callback should have triggered")
    }
}