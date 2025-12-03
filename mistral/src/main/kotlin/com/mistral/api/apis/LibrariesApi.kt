package com.mistral.api.apis


import com.mistral.api.MistralClient
import com.mistral.api.exceptions.MistralApiException
import com.mistral.api.header
import com.mistral.api.models.libraries.LibrariesListResponse
import com.mistral.api.models.libraries.LibraryDetail
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*


class LibrariesApi(private val client: MistralClient) {
    suspend fun listLibraries(): LibrariesListResponse {
        val resp =
            client.http.get(client.basePath("/v1/libraries")) { header(client.authHeader()); accept(ContentType.Application.Json) }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }


    suspend fun getLibrary(id: String): LibraryDetail {
        val resp =
            client.http.get(client.basePath("/v1/libraries/$id")) { header(client.authHeader()); accept(ContentType.Application.Json) }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }
}