package com.mistral.api.apis

import com.mistral.api.MistralClient
import com.mistral.api.exceptions.MistralApiException
import com.mistral.api.header
import com.mistral.api.models.finetuning.FineTuneCreateRequest
import com.mistral.api.models.finetuning.FineTuneJobResponse
import com.mistral.api.models.finetuning.FineTuneListResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class FineTuningApi(private val client: MistralClient) {
    suspend fun createJob(req: FineTuneCreateRequest): FineTuneJobResponse {
        val resp = client.http.post(client.basePath("/v1/fine_tunes")) {
            header(client.authHeader()); contentType(ContentType.Application.Json); setBody(req)
        }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }

    suspend fun getJob(id: String): FineTuneJobResponse {
        val resp =
            client.http.get(client.basePath("/v1/fine_tunes/$id")) { header(client.authHeader()); accept(ContentType.Application.Json) }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }

    suspend fun listJobs(): FineTuneListResponse {
        val resp =
            client.http.get(client.basePath("/v1/fine_tunes")) { header(client.authHeader()); accept(ContentType.Application.Json) }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }
}