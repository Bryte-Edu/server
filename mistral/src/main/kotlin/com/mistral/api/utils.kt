package com.mistral.api

import io.ktor.http.*

fun HttpMessageBuilder.header(header: Pair<String, String>) {
    headers.append(header.first, header.second)
}