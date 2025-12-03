package dev.pranav.bryte.server.document.parser.youtube

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * An implementation of the NewPipe Downloader interface using the Ktor HttpClient.
 */
class NewPipeDownloader private constructor(private val client: HttpClient) : Downloader() {

    private val cookiesMap = ConcurrentHashMap<String, String>()

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
        const val YOUTUBE_RESTRICTED_MODE_COOKIE_KEY = "youtube_restricted_mode_key"
        const val YOUTUBE_RESTRICTED_MODE_COOKIE = "PREF=f2=8000000"
        const val YOUTUBE_DOMAIN = "youtube.com"

        @Volatile
        private var instance: NewPipeDownloader? = null

        /**
         * Initializes and returns the singleton instance of KtorDownloader.
         */
        fun init(customClient: HttpClient? = null): NewPipeDownloader =
            instance ?: synchronized(this) {
                instance ?: NewPipeDownloader(customClient ?: createDefaultClient()).also { instance = it }
            }

        /**
         * Creates a default Ktor HttpClient with necessary configurations, mirroring the original OkHttp setup.
         */
        private fun createDefaultClient(): HttpClient = HttpClient(CIO) {
            engine {
                requestTimeout = 30_000
            }

            defaultRequest {
                header(HttpHeaders.UserAgent, USER_AGENT)
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 30000
            }

            followRedirects = true
        }
    }

    fun getCookie(key: String): String? = cookiesMap[key]
    fun setCookie(key: String, cookie: String) {
        cookiesMap[key] = cookie
    }

    fun removeCookie(key: String) {
        cookiesMap.remove(key)
    }

    /**
     * Gathers and formats the specific cookies required for the given URL.
     * This logic directly mirrors the original Java implementation.
     */
    fun getCookies(url: String): String {
        val youtubeCookie = if (url.contains(YOUTUBE_DOMAIN)) {
            getCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY)
        } else {
            null
        }

        return listOfNotNull(youtubeCookie, getCookie("recaptcha_cookies_key"))
            .flatMap { it.split(";\\s*".toRegex()).map(String::trim) }
            .distinct()
            .joinToString("; ")
    }

    /**
     * Executes the network request. Since the base Downloader interface is synchronous,
     * this implementation uses runBlocking to call the suspend network operation.
     */
    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        return runBlocking {
            executeSuspend(request)
        }
    }

    /**
     * Core suspend function for executing the network request using Ktor.
     */
    @Throws(IOException::class, ReCaptchaException::class)
    private suspend fun executeSuspend(request: Request): Response {
        try {
            val kRequestMethod = HttpMethod.parse(request.httpMethod())

            val httpResponse = client.request(request.url()) {
                method = kRequestMethod

                val cookies = getCookies(request.url())
                if (cookies.isNotEmpty()) {
                    header(HttpHeaders.Cookie, cookies)
                }

                request.headers().forEach { (name, values) ->
                    values.forEach { value -> header(name, value) }
                }

                if (request.dataToSend() != null) {
                    setBody(request.dataToSend())
                }
            }

            // Check for ReCaptcha exception (HTTP 429)
            if (httpResponse.status.value == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            }

            val responseBodyToReturn = httpResponse.bodyAsText()

            val responseHeaders = httpResponse.headers.entries()
                .associate { it.key to it.value }

            val finalUrl = httpResponse.request.url.toString()

            return Response(
                httpResponse.status.value,
                httpResponse.status.description,
                responseHeaders,
                responseBodyToReturn,
                finalUrl
            )
        } catch (e: Exception) {
            when (e) {
                is ReCaptchaException -> throw e
                is IOException -> throw e
                else -> throw IOException("Network error during execution: ${e.message}", e)
            }
        }
    }

    /**
     * Get the size of the content that the url is pointing by firing a HEAD request.
     * This method also uses runBlocking to remain synchronous as per the original interface.
     */
    @Throws(IOException::class, ReCaptchaException::class)
    fun getContentLength(url: String): Long {
        return runBlocking {
            try {
                val response = head(url)
                val lengthHeader = response.getHeader("Content-Length")
                lengthHeader?.toLongOrNull() ?: throw IOException("Content-Length header missing or invalid.")
            } catch (e: NumberFormatException) {
                throw IOException("Invalid content length", e)
            } catch (e: ReCaptchaException) {
                throw IOException(e)
            }
        }
    }
}