package dev.pranav.bryte.server.document.parser.web

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import dev.pranav.bryte.model.DocumentType
import dev.pranav.bryte.model.session.Image
import dev.pranav.bryte.server.GEMINI_API_KEY
import dev.pranav.bryte.server.document.DocParser
import dev.pranav.bryte.server.document.ParsedDocument
import dev.pranav.bryte.server.document.Topic
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Parses generic webpage URLs by fetching content natively and summarizing.
 */
class WebpageParser : DocParser<String> {

    override suspend fun parseDocument(input: String): ParsedDocument? {
        val executor = simpleGoogleAIExecutor(GEMINI_API_KEY)

        // Fetch raw HTML from website
        val client = HttpClient(CIO)
        val htmlContent = try {
            client.get(input).bodyAsText()
        } catch (e: HttpRequestTimeoutException) {
            return null
        } finally {
            client.close()
        }

        val cleanText = htmlContent
            .replace(Regex("(?is)<script.*?>.*?</script>"), "")
            .replace(Regex("(?is)<style.*?>.*?</style>"), "")
            .replace(Regex("(?is)<nav.*?>.*?</nav>"), "")
            .replace(Regex("(?is)<footer.*?>.*?</footer>"), "")
            .replace(Regex("(?is)<header.*?>.*?</header>"), "")
            .replace(Regex("(?is)<svg.*?>.*?</svg>"), "")
            .replace(Regex("<[^>]+>"), " ") // Remove all remaining HTML tags
            .replace(Regex("\\s+"), " ") // Compress multiple whitespaces
            .trim()

        val response = executor.executeStructured<WebDocument>(
            prompt = prompt("Structure Webpage Notes") {
                system(
                    "You are an expert student note-taker that makes high quality notes. Extract and convert the main informational content from the following extracted webpage text into clear, well-organized, comprehensive study notes written exclusively in the voice of a student, not a teacher." +
                            " The notes must be self-contained and fully understandable by a reader who has not read the source page." +
                            " Output must be in markdown, using headings, bullet lists, and tables as needed to best present the information." +
                            " Do not use any instructional or teacher-like phrasing." +
                            " Simplify, reorder, or expand content using common knowledge when it improves clarity, while preserving all important ideas, definitions, and steps." +
                            " Start with content directly relevant to the main topic, avoiding tangential remarks or asides." +
                            " Ensure technical terms are clearly defined upon first use." +
                            " Break down complex concepts into simpler sub-points for better understanding."
                )
                user(
                    "Create notes from the following webpage URL: $input\n\nText Content:\n\n${cleanText.take(65000)}" // Safety truncate for LLM context limits
                )
            },
            model = GoogleModels.Gemini2_5Flash,
            fixingParser = StructureFixingParser(
                model = GoogleModels.Gemini2_0FlashLite001,
                retries = 4
            )
        )

        val doc = response.getOrElse {
            it.printStackTrace()
            return null
        }.data

        return ParsedDocument(
            input,
            doc.title,
            DocumentType.WEBPAGE,
            mapOf("title" to doc.title, "url" to input),
            doc.topics.mapIndexed { index, topic ->
                Topic(
                    header = topic.title,
                    content = topic.markdown,
                    images = topic.images,
                    pages = listOf(index + 1)
                )
            }
        )
    }
}


@Serializable
@SerialName("Document")
@LLMDescription("Document containing webpage notes and topics.")
data class WebDocument(
    @property:LLMDescription("Most appropriate title for the document.")
    val title: String,
    @property:LLMDescription("List of topics within the document.")
    var topics: List<WebTopic> = listOf(),
)


@Serializable
@SerialName("Topic")
@LLMDescription("A topic or section within the document.")
data class WebTopic(
    @property:LLMDescription("Title of the topic.")
    val title: String,
    @property:LLMDescription("Markdown content associated with the topic.")
    val markdown: String,
    @property:LLMDescription("Optional list of images associated with the topic.")
    val images: List<Image> = listOf()
)