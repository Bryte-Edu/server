package com.mistral.api.apis


import com.mistral.api.MistralClient
import com.mistral.api.exceptions.MistralApiException
import com.mistral.api.header
import com.mistral.api.models.conversations.ConversationCreateRequest
import com.mistral.api.models.conversations.ConversationResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*


class ConversationsApi(private val client: MistralClient) {
    suspend fun createConversation(req: ConversationCreateRequest): ConversationResponse {
        val resp = client.http.post(client.basePath("/v1/conversations")) {
            header(client.authHeader()); contentType(ContentType.Application.Json); setBody(req)
        }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }


    suspend fun getConversation(id: String): ConversationResponse {
        val resp =
            client.http.get(client.basePath("/v1/conversations/$id")) { header(client.authHeader()); accept(ContentType.Application.Json) }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }
}