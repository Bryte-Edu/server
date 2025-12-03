package com.mistral.api.models.files


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class FileListResponse(val data: List<FileItem>)

@Serializable
data class FileItem(
    val id: String,
    val `object`: String,
    val bytes: Integer,
    @SerialName("created_at") val createdAt: Int,
    val filename: String,
    val purpose: String,
    @SerialName("sample_type") val sampleType: String,
    val source: String,
    @SerialName("num_lines") val numLines: Integer? = null,
    val mimetype: String? = null,
    val signature: String? = null
)