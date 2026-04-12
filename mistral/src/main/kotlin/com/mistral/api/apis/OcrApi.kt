package com.mistral.api.apis

import com.mistral.api.MistralClient
import com.mistral.api.header
import com.mistral.api.models.ocr.OcrRequest
import com.mistral.api.models.ocr.OcrResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class OcrApi(private val client: MistralClient) {

    /**
     * Perform OCR on an uploaded file or URL.
     *
     * @param request OcrRequest specifying model and input (file or fileUrl).
     * @return OcrResponse containing per-page OCR info.
     */
//    suspend fun recognize(request: OcrRequest): OcrResponse =
//        client.http.post("${client.baseUrl}/v1/ocr") {
//            contentType(ContentType.Application.Json)
//            setBody(request)
//        }.body()
    suspend fun recognize(request: OcrRequest): OcrResponse {
        val httpResponse = client.http.post("${client.baseUrl}/v1/ocr") {
            header(client.authHeader())
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        return httpResponse.body()
    }
}
