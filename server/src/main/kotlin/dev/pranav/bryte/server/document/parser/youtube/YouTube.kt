package dev.pranav.bryte.server.document.parser.youtube

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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Parser for YouTube video transcripts to generate structured study notes.
 */
class YouTube: DocParser<String> {

    override suspend fun parseDocument(input: String): ParsedDocument? {
        val executor = simpleGoogleAIExecutor(GEMINI_API_KEY)

        val video = YouTubeService.getVideo(input)

        val response = executor.executeStructured<VideoDocument>(
            prompt = prompt("Structure Notes") {
                system(
                    "You are an expert student note-taker that makes high quality notes. Convert the following transcript into clear, well-organized, comprehensive study notes written exclusively in the voice of a student, not a teacher." +
                            " The notes must be self-contained and fully understandable by a reader who has not watched the source video." +
                            " Output must be in markdown, using headings, bullet lists, and tables as needed to best present the information." +
                            " Do not use any instructional or teacher-like phrasing (e.g., 'now we will,' 'in this lesson,' 'you will learn,' or addressing the reader as 'you')." +
                            " Critically, remove all time-based or contextual references to the video itself (e.g., avoid 'this week,' 'last class,' or 'the professor')." +
                            " Simplify, reorder, or expand content using common knowledge when it improves clarity, while preserving all important ideas, definitions, and steps." +
                            " Start with content directly relevant to the main topic, avoiding tangential remarks or asides." +
                            " Ensure technical terms are clearly defined upon first use." +
                            " Break down complex concepts into simpler sub-points for better understanding."
                )
                user(
                    "Create notes for the following: ${video.title} by ${video.author}, transcript:\n${video.transcript}"
                )
            },
            model = GoogleModels.Gemini2_5Flash,
            fixingParser = StructureFixingParser(
                model = GoogleModels.Gemini2_0FlashLite001,
                retries = 4
            )
        )

        val doc = response.getOrElse {
            println("Failed to parse transcript structure: ${it.message}")
            it.printStackTrace()
            return null
        }.data

        return ParsedDocument(
            input,
            doc.title,
            DocumentType.YOUTUBE,
            mapOf("title" to doc.title, "author" to video.author, "duration" to video.length.toString()),
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
@LLMDescription("Document containing video notes and topics.")
data class VideoDocument(
    @property:LLMDescription("Most appropriate title for the document.")
    val title: String,
    @property:LLMDescription("List of topics within the document.")
    var topics: List<VideoTopic> = listOf(),
)


@Serializable
@SerialName("Topic")
@LLMDescription("A topic or section within the document.")
data class VideoTopic(
    @property:LLMDescription("Title of the topic.")
    val title: String,
    @property:LLMDescription("Markdown content associated with the topic.")
    val markdown: String,
    @property:LLMDescription("Optional list of images associated with the topic.")
    val images: List<Image> = listOf()
)