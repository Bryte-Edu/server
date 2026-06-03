package com.mistral.api.apis

import com.mistral.api.MistralClient
import com.mistral.api.exceptions.MistralApiException
import com.mistral.api.header
import com.mistral.api.models.embeddings.EmbeddingsRequest
import com.mistral.api.models.embeddings.EmbeddingsResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay

class EmbeddingsApi(private val client: MistralClient) {
    suspend fun create(req: EmbeddingsRequest): EmbeddingsResponse {
        val resp = client.http.post(client.basePath("/v1/embeddings")) {
            header(client.authHeader())
            contentType(ContentType.Application.Json)
            setBody(req)
        }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        if (status == 429) {
            delay(1000)
            val retryResp = client.http.post(client.basePath("/v1/embeddings")) {
                header(client.authHeader())
                contentType(ContentType.Application.Json)
                setBody(req)
            }
            if (retryResp.status.value in 200..299) return retryResp.body()
            throw MistralApiException(retryResp.status.value, retryResp.status.description, retryResp.bodyAsText())
        }
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }
}