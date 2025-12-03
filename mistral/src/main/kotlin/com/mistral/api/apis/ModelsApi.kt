package com.mistral.api.apis


import com.mistral.api.MistralClient
import com.mistral.api.exceptions.MistralApiException
import com.mistral.api.header
import com.mistral.api.models.models.ModelDetail
import com.mistral.api.models.models.ModelsListResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*


class ModelsApi(private val client: MistralClient) {


    suspend fun listModels(): ModelsListResponse = client.http.get(client.basePath("/v1/models")) {
        header(client.authHeader())
        accept(ContentType.Application.Json)
    }.let { resp ->
        val status = resp.status.value
        if (status in 200..299) resp.body() else throw MistralApiException(
            status,
            resp.status.description,
            resp.bodyAsText()
        )
    }


    suspend fun getModel(id: String): ModelDetail = client.http.get(client.basePath("/v1/models/$id")) {
        header(client.authHeader())
        accept(ContentType.Application.Json)
    }.let { resp ->
        val status = resp.status.value
        if (status in 200..299) resp.body() else throw MistralApiException(
            status,
            resp.status.description,
            resp.bodyAsText()
        )
    }


// Patch, delete, archive/unarchive could be added here following spec
}