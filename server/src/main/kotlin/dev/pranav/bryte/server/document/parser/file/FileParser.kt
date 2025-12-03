package dev.pranav.bryte.server.document.parser.file

import com.mistral.api.MistralClient
import com.mistral.api.models.ocr.OcrResponse
import dev.pranav.bryte.server.document.DocumentType
import dev.pranav.bryte.server.document.ParsedDocument
import dev.pranav.bryte.server.document.DocParser
import dev.pranav.bryte.server.document.Topic
import dev.pranav.bryte.server.models.Image
import kotlinx.serialization.Serializable
import kotlin.collections.flatMap


fun main() {
    val fileUrl = "https://dspace.mit.edu/bitstream/handle/1721.1/144261/12525_2022_Article_570.pdf"
    val parser = FileParser()

    kotlinx.coroutines.runBlocking {
        val parsedDocument = parser.parseDocument(fileUrl)
        println("Parsed Document: $parsedDocument")
    }
}

class FileParser : DocParser<String> {
    override suspend fun parseDocument(input: String): ParsedDocument {
        val ocrResponse = MistralService.ocrByFileUrl(input)

        val document = parseOcrResponse(ocrResponse)

        return ParsedDocument(
            id = input,
            title = document.title,
            type = DocumentType.PDF,
            topics = document.topics.map {
                Topic(
                    header = it.title,
                    content = it.markdown,
                    images = it.images.map { img ->
                        Image(
                            id = img.id,
                            base64 = img.image_base64
                        )
                    },
                    pages = it.pageNumbers
                )
            }
        )
    }
}

fun parseOcrResponse(response: OcrResponse): OcrDocument {
        val pages = response.pages.map { page ->
            OcrPage(
                page.index + 1, page.markdown, page.images.map { image ->
                    OcrImage(
                        image.id, image.imageBase64
                    )
                })
        }
        val document = OcrDocument(
            title = extractTitle(pages)
        )
        document.pages = pages
        document.pagesProcessed = response.usageInfo.pagesProcessed
        document.topics = parseTopics(pages)

        return document
    }

    /**
     * Extracts the title of the document from the first heading found in the pages.
     * If no heading is found, returns "Untitled Document".
     *
     * @param pages The list of pages to search for a heading.
     * @return The extracted title or "Untitled Document" if no heading is found.
     */
    private fun extractTitle(pages: List<OcrPage>): String {
        val headingRegex = Regex("^#{1,6}\\s+(.+?)\\s*$")
        for (page in pages) {
            val lines = page.text.lines()
            for (line in lines) {
                val match = headingRegex.find(line)
                if (match != null) {
                    return match.groupValues[1].trim()
                }
            }
        }
        return "Untitled Document"
    }

    /**
     * Parses the pages to extract topics based on markdown headings.
     * Each topic includes its title, associated page numbers, markdown content, and images.
     *
     * @param pages The list of pages to parse for topics.
     * @return A list of extracted topics.
     */
    private fun parseTopics(pages: List<OcrPage>): List<OcrTopic> {
        if (pages.isEmpty()) return emptyList()

        val headingRegex = Regex("^#{1,6}\\s+(.+?)\\s*$")
        val imageRegex = Regex("!\\[[^]]*]\\(([^)]+)\\)")
        val imageById: Map<String, OcrImage> = pages.flatMap { it.images }.associateBy { it.id }

        val topics = mutableListOf<OcrTopic>()
        var currentTitle: String? = null
        val content = StringBuilder()
        val imageIds = linkedSetOf<String>()
        val pageSet = linkedSetOf<Int>()
        var skippingReferences = false

        fun finalizeCurrentTopic() {
            val title = currentTitle ?: return
            val md = content.toString().trim()
            if (md.isNotBlank()) {
                val imgs = imageIds.mapNotNull { imageById[it] }
                topics += OcrTopic(title.trim(), pageSet.toList(), md, imgs)
            }
            currentTitle = null
            content.setLength(0)
            imageIds.clear()
            pageSet.clear()
        }

        for (page in pages) {
            for (line in page.text.lines()) {
                val headingMatch = headingRegex.find(line)
                if (headingMatch != null) {
                    val title = headingMatch.groupValues[1].trim()
                    if (title.equals("references", ignoreCase = true)) {
                        finalizeCurrentTopic()
                        skippingReferences = true
                        continue
                    } else {
                        finalizeCurrentTopic()
                        currentTitle = title
                        skippingReferences = false
                        continue
                    }
                }

                if (skippingReferences) continue

                if (currentTitle != null) {
                    if (line.isNotBlank()) pageSet += page.pageNumber
                    content.appendLine(line)
                    imageRegex.findAll(line).forEach { match ->
                        val raw = match.groupValues[1].trim()
                        val id = raw.substringAfterLast('/')
                        imageIds += id
                        pageSet += page.pageNumber
                    }
                }
            }
        }

        finalizeCurrentTopic()
        return topics
    }

/**
 * Data class representing a Document with its pages and topics.
 *
 * @property pages List of pages in the document.
 * @property pagesProcessed Number of pages processed in the OCR response.
 * @property topics List of topics extracted from the document.
 */
@Serializable
data class OcrDocument(
    var title: String, var pages: List<OcrPage> = listOf(), var pagesProcessed: Int = 0, var topics: List<OcrTopic> = listOf()
)

/**
 * Data class representing a Topic within a document.
 *
 * @property title The title of the topic.
 * @property pageNumbers List of page numbers where the topic appears.
 * @property markdown The markdown content associated with the topic.
 * @property images List of images related to the topic.
 */
@Serializable
data class OcrTopic(
    val title: String, val pageNumbers: List<Int>, val markdown: String, val images: List<OcrImage> = listOf()
)

/**
 * Data class representing a Page in a document.
 *
 * @property pageNumber The page number.
 * @property text The text content of the page.
 * @property images List of images present on the page.
 */
@Serializable
data class OcrPage(
    val pageNumber: Int, val text: String, val images: List<OcrImage>
)

/**
 * Data class representing an Image within a document or topic.
 *
 * @property id The unique identifier of the image.
 * @property image_base64 The base64 encoded string of the image (optional).
 * @property annotations List of annotations associated with the image (default is an empty list).
 */
@Serializable
data class OcrImage(
    val id: String, val image_base64: String?, val annotations: List<Annotation> = listOf()
)