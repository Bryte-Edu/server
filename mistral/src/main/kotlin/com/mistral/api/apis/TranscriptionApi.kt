package com.mistral.api.apis


import com.mistral.api.MistralClient
import com.mistral.api.exceptions.MistralApiException
import com.mistral.api.header
import com.mistral.api.models.transcriptions.TranscriptionRequest
import com.mistral.api.models.transcriptions.TranscriptionResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.io.File


class TranscriptionApi(private val client: MistralClient) {
    suspend fun create(req: TranscriptionRequest, file: File? = null): TranscriptionResponse {
        val resp = if (file != null) {
            client.http.submitFormWithBinaryData(
                url = client.basePath("/v1/audio/transcriptions"),
                formData = formData {
                    append("model", req.model)
                    append("file", file.readBytes(), Headers.build {
                        append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"${file.name}\"")
                        append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                    })
                    req.language?.let { append("language", it) }
                    req.temperature?.let { append("temperature", it.toString()) }
                }) { header(client.authHeader()) }
        } else {
            client.http.post(client.basePath("/v1/audio/transcriptions")) {
                header(client.authHeader())
                contentType(ContentType.Application.Json)
                setBody(req)
            }
        }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }
}