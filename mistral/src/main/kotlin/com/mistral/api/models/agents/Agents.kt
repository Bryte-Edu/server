package com.mistral.api.models.agents

import kotlinx.serialization.Serializable

@Serializable
data class AgentsListResponse(val data: List<AgentSummary>)

@Serializable
data class AgentSummary(val id: String, val name: String? = null, val description: String? = null)

@Serializable
data class AgentDetail(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    val config: Map<String, String>? = null
)

@Serializable
data class AgentCreateRequest(
    val name: String,
    val description: String? = null,
    val config: Map<String, String>? = null
)