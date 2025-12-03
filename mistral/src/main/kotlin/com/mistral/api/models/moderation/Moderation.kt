package com.mistral.api.models.moderation


import kotlinx.serialization.Serializable


@Serializable
data class ModerationRequest(val model: String, val input: String)


@Serializable
data class ModerationResponse(val id: String, val results: List<ModerationResult>)


@Serializable
data class ModerationResult(val flagged: Boolean, val categories: Map<String, Boolean>)