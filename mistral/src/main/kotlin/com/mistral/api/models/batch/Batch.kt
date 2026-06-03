package com.mistral.api.models.batch

import kotlinx.serialization.Serializable

@Serializable
data class BatchCreateRequest(val model: String, val inputs: List<String>)

@Serializable
data class BatchResponse(val id: String, val status: String, val results: List<String>? = null)