package com.mistral.api.apis


import com.mistral.api.MistralClient
import com.mistral.api.exceptions.MistralApiException
import com.mistral.api.header
import com.mistral.api.models.batch.BatchCreateRequest
import com.mistral.api.models.batch.BatchResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*


class BatchApi(private val client: MistralClient) {
    suspend fun createBatch(req: BatchCreateRequest): BatchResponse {
        val resp = client.http.post(client.basePath("/v1/batches")) {
            header(client.authHeader())
            contentType(ContentType.Application.Json)
            setBody(req)
        }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }


    suspend fun getBatch(id: String): BatchResponse {
        val resp =
            client.http.get(client.basePath("/v1/batches/$id")) { header(client.authHeader()); accept(ContentType.Application.Json) }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }
}