package com.mistral.api.apis


import com.mistral.api.MistralClient
import com.mistral.api.exceptions.MistralApiException
import com.mistral.api.header
import com.mistral.api.models.chat.ChatCompletionRequest
import com.mistral.api.models.chat.ChatCompletionResponse
import com.mistral.api.models.chat.ChatStreamEvent
import com.mistral.api.sse.SseParser
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


class ChatApi(private val client: MistralClient) {


    suspend fun create(req: ChatCompletionRequest): ChatCompletionResponse {
        val resp = client.http.post(client.basePath("/v1/chat/completions")) {
            header(client.authHeader())
            contentType(ContentType.Application.Json)
            setBody(req)
        }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }


    fun stream(req: ChatCompletionRequest): Flow<ChatStreamEvent> = flow {
        val reqStream = req.copy(stream = true)
        val resp: HttpResponse = client.http.post(client.basePath("/v1/chat/completions")) {
            header(client.authHeader())
            contentType(ContentType.Application.Json)
            setBody(reqStream)
        }
        val status = resp.status.value
        if (status !in 200..299) throw MistralApiException(status, resp.status.description, resp.bodyAsText())


        val channel: ByteReadChannel = resp.body()
        val parser = SseParser()
        while (!channel.isClosedForRead) {
            val raw = parser.readNext(channel) ?: break
// convert raw to event object (best-effort: try to deserialize to ChatStreamEvent)
            emit(ChatStreamEvent(raw))
        }
    }
}