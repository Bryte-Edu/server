package com.mistral.api.models.finetuning

import kotlinx.serialization.Serializable

@Serializable
data class FineTuneCreateRequest(
    val training_file: String,
    val model: String,
    val hyperparameters: Map<String, String>? = null
)

@Serializable
data class FineTuneJobResponse(val id: String, val status: String, val model: String? = null)

@Serializable
data class FineTuneListResponse(val data: List<FineTuneJobResponse>)