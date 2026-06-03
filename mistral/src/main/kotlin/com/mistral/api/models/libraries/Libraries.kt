package com.mistral.api.models.libraries

import kotlinx.serialization.Serializable

@Serializable
data class LibrariesListResponse(val data: List<LibraryDetail>)

@Serializable
data class LibraryDetail(val id: String, val name: String? = null, val description: String? = null)