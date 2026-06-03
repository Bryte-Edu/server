package com.mistral.api.apis

import com.mistral.api.MistralClient
import com.mistral.api.exceptions.MistralApiException
import com.mistral.api.header
import com.mistral.api.models.agents.AgentCreateRequest
import com.mistral.api.models.agents.AgentDetail
import com.mistral.api.models.agents.AgentsListResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class AgentsApi(private val client: MistralClient) {
    suspend fun listAgents(): AgentsListResponse {
        val resp =
            client.http.get(client.basePath("/v1/agents")) { header(client.authHeader()); accept(ContentType.Application.Json) }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }

    suspend fun getAgent(id: String): AgentDetail {
        val resp =
            client.http.get(client.basePath("/v1/agents/$id")) { header(client.authHeader()); accept(ContentType.Application.Json) }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }

    suspend fun createAgent(req: AgentCreateRequest): AgentDetail {
        val resp = client.http.post(client.basePath("/v1/agents")) {
            header(client.authHeader()); contentType(ContentType.Application.Json); setBody(req)
        }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }
}