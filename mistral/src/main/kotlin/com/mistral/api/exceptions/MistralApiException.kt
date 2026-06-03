package com.mistral.api.exceptions

/** Exception thrown on non-2xx responses. */
class MistralApiException(
    val status: Int,
    message: String,
    val responseBody: String? = null
) : RuntimeException("HTTP $status: $message")