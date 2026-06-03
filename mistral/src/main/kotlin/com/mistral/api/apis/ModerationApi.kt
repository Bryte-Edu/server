package com.mistral.api.apis

import com.mistral.api.MistralClient
import com.mistral.api.exceptions.MistralApiException
import com.mistral.api.header
import com.mistral.api.models.moderation.ModerationRequest
import com.mistral.api.models.moderation.ModerationResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class ModerationApi(private val client: MistralClient) {
    suspend fun moderateText(req: ModerationRequest): ModerationResponse {
        val resp = client.http.post(client.basePath("/v1/moderations")) {
            header(client.authHeader()); contentType(ContentType.Application.Json); setBody(req)
        }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }
}