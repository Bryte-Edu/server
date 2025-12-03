package com.mistral.api.apis


import com.mistral.api.MistralClient
import com.mistral.api.exceptions.MistralApiException
import com.mistral.api.header
import com.mistral.api.models.files.FileItem
import com.mistral.api.models.files.FileListResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.io.File

class FilesApi(private val client: MistralClient) {

    /**
     * Upload a file to the Mistral API.
     *
     * @param file The file to upload.
     * @param purpose Optional purpose of the file (e.g., "fine-tune", "ocr").
     *
     * @return The uploaded FileItem.
     * @throws MistralApiException if the upload fails.
     */
    suspend fun upload(file: File, purpose: String? = null): FileItem {
        val resp = client.http.submitFormWithBinaryData(url = client.basePath("/v1/files"), formData = formData {
            purpose?.let { append("purpose", it) }
            append("file", file.readBytes(), Headers.build {
                append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"${file.name}\"")
                append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
            })
        }) { header(client.authHeader()) }


        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }

    /**
     * List files with optional filtering and pagination.
     *
     * @param page Optional page number for pagination.
     * @param pageSize Optional number of items per page.
     * @param sampleType Optional array of sample types to filter by.
     * @param source Optional source to filter by.
     * @param search Optional search term to filter filenames.
     * @param purpose Optional purpose to filter by.
     *
     * @return A FileListResponse containing the list of files.
     * @throws MistralApiException if the listing fails.
     */
    suspend fun list(
        page: Int? = null,
        pageSize: Int? = null,
        sampleType: Array<String>? = null,
        source: String? = null,
        search: String? = null,
        purpose: String? = null
    ): FileListResponse {
        val resp =
            client.http.get(client.basePath("/v1/files")) {
                header(client.authHeader())
                accept(ContentType.Application.Json)
                page?.let { parameter("page", it) }
                pageSize?.let { parameter("page_size", it) }
                sampleType?.let { parameter("sample_type", it) }
                source?.let { parameter("source", it) }
                search?.let { parameter("search", it) }
                purpose?.let { parameter("purpose", it) }
            }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }

    /**
     * Get a file by its ID.
     *
     * @param id The ID of the file to retrieve.
     *
     * @return The FileItem corresponding to the given ID.
     * @throws MistralApiException if the retrieval fails.
     */
    suspend fun get(id: String): FileItem {
        val resp =
            client.http.get(client.basePath("/v1/files/$id")) { header(client.authHeader()); accept(ContentType.Application.Json) }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }

    /**
     * Delete a file by its ID.
     *
     * @param id The ID of the file to delete.
     * @throws MistralApiException if the deletion fails.
     */
    suspend fun delete(id: String) {
        val resp = client.http.delete(client.basePath("/v1/files/$id")) { header(client.authHeader()) }
        val status = resp.status.value
        if (status !in 200..299) throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }

    /**
     * Download the file content as a ByteArray.
     *
     * @param id The ID of the file to download.
     * @return The file content as a ByteArray.
     */
    suspend fun download(id: String): ByteArray {
        val resp = client.http.get(client.basePath("/v1/files/$id/content")) { header(client.authHeader()) }
        val status = resp.status.value
        if (status in 200..299) return resp.body()
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }

    /**
     * Get a signed URL for downloading the file. The URL is valid for a limited time (default 24 hours).
     * You can specify a custom expiry time in hours
     *
     * @param id The ID of the file to get the signed URL for.
     * @param expiry Optional expiry time in hours (default 24 hours)
     *
     * @return The signed URL as a string.
     */
    suspend fun getSignedUrl(id: String, expiry: Int? = null): String {
        val resp = client.http.get(client.basePath("/v1/files/$id/url")) {
            header(client.authHeader())
            expiry?.let { parameter("expiry", it) }
            accept(ContentType.Application.Json)
        }
        val status = resp.status.value
        if (status in 200..299) {
            val jsonResponse: Map<String, String> = resp.body()
            return jsonResponse["url"] ?: throw MistralApiException(
                status,
                "URL not found in response",
                resp.bodyAsText()
            )
        }
        throw MistralApiException(status, resp.status.description, resp.bodyAsText())
    }
}