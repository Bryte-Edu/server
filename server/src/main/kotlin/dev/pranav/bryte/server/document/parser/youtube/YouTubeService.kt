package dev.pranav.bryte.server.document.parser.youtube

import kotlinx.serialization.Serializable
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Request

object YouTubeService {
    init {
        NewPipe.init(NewPipeDownloader.init())
    }

    fun getVideo(videoId: String): Video {
        ServiceList.YouTube.getStreamExtractor(videoId).let {
            it.fetchPage()

            val subtitle = runCatching {
                it.fetchPage()
                it.subtitlesDefault.firstOrNull { it.languageTag == "en" } ?: it.subtitlesDefault.firstOrNull()
            }

            val transcript = subtitle.getOrNull()?.content?.let { subtitleUrl ->
                val rawTranscription: String? = try {
                    val request = Request(
                        "GET",
                        subtitleUrl,
                        emptyMap<String, List<String>>(),
                        null,
                        null,
                        true
                    )

                    val response = NewPipeDownloader.init().execute(request)

                    response.responseBody()

                } catch (e: Exception) {
                    println("Error fetching subtitle content: ${e.message}")
                    e.printStackTrace()
                    null
                }
                rawTranscription?.ttmlToText() ?: ""
            } ?: ""

            return Video(
                videoId,
                it.name,
                it.uploaderName,
                it.length.toInt(),
                transcript
            )
        }
    }

    suspend fun getTranscript(videoId: String): String {
        ServiceList.YouTube.getStreamExtractor(videoId).let {
            it.fetchPage()
            val subtitle = runCatching {
                it.fetchPage()
                it.subtitlesDefault
                    .filter { it.languageTag == "en" }
                    .firstOrNull() ?: it.subtitlesDefault.firstOrNull()
            }

            subtitle.getOrNull()?.content?.let { subtitleUrl ->
                val rawTranscription: String? = try {
                    val request = Request(
                        "GET",
                        subtitleUrl,
                        emptyMap<String, List<String>>(),
                        null,
                        null,
                        true
                    )

                    val response = NewPipeDownloader.init().execute(request)

                    response.responseBody()

                } catch (e: Exception) {
                    println("Error fetching subtitle content: ${e.message}")
                    e.printStackTrace()
                    null
                }
                return rawTranscription?.ttmlToText() ?: ""
            }
        }
        return ""
    }

    suspend fun getVideoDetails(videoId: String): VideoDetails {
        ServiceList.YouTube.getStreamExtractor(videoId).let {
            it.fetchPage()

            return VideoDetails(
                it.name,
                it.uploaderName,
                it.description.content.take(100),
                it.length.toInt()
            )
        }
    }
}

@Serializable
data class Video(
    val id: String,
    val title: String,
    val author: String,
    val length: Int,
    val transcript: String
)

@Serializable
data class VideoDetails(
    val title: String,
    val author: String,
    val description: String,
    val lengthSeconds: Int
)

/**
 * Decodes common named HTML entities and all numeric entities (decimal and hexadecimal).
 */
private fun String.decodeHtmlEntities(): String {
    var decodedString = this
        // Decode common named entities
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")

    // Decode numeric entities (&#DDDD; or &#xHHHH;)
    // Regex: &# followed by optional 'x' (hex), one or more digits/hex chars, ending with ;
    val numericEntityRegex = "&#(x?)(\\d+|[a-fA-F0-9]+);".toRegex()

    decodedString = numericEntityRegex.replace(decodedString) { match ->
        val isHex = match.groupValues[1].isNotEmpty()
        val value = match.groupValues[2]

        // Parse the value as a Unicode code point
        val charCode = try {
            if (isHex) value.toInt(16) else value.toInt()
        } catch (e: NumberFormatException) {
            return@replace match.value
        }

        // Convert the code point to its character representation
        if (charCode > 0xFFFF) {
            String(Character.toChars(charCode))
        } else {
            charCode.toChar().toString()
        }
    }

    return decodedString
}

fun String.ttmlToText(): String {
    // Regex 1: Non-greedy match to capture text content between <p ...> and </p> tags.
    val paragraphRegex = "<p[^>]*>(.*?)</p>".toRegex(RegexOption.DOT_MATCHES_ALL)

    // Combined regex to remove all internal artifacts: HTML tags, bracketed markers (e.g., [Music]), and speaker labels.
    val fullCleanupRegex = "(<[^>]+>|\\[[^]]*]|\\s+\\w+:\\s)".toRegex()

    return paragraphRegex.findAll(this)
        .asSequence()
        .map { it.groupValues[1] }
        .map { rawLine ->
            rawLine
                // 1. Strip internal tags and markers
                .replace(fullCleanupRegex, "")
                // 2. Decode HTML entities (e.g., &#39; to ')
                .decodeHtmlEntities()
                .trim()
        }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}