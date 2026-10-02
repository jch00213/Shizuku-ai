package com.jeremy.shizukuai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubCommentRequest(
    val body: String
)

@Serializable
data class GitHubIssueCreateRequest(
    val title: String,
    val body: String,
    val labels: List<String> = emptyList(),
    val assignees: List<String> = emptyList()
)

@Serializable
data class GitHubWorkflowDispatchRequest(
    val ref: String = "main",
    val inputs: Map<String, String> = emptyMap()
)

@Serializable
data class GitHubCreateOrUpdateFileRequest(
    val message: String,
    val content: String, // Base64 encoded string
    val sha: String? = null, // Required if updating an existing file
    val branch: String = "main"
)

@Serializable
data class GitHubIssueResponse(
    val number: Int,
    val title: String,
    @SerialName("html_url") val htmlUrl: String
)

@Serializable
data class GitHubContentResponse(
    val content: GitHubContentDetails
)

@Serializable
data class GitHubContentDetails(
    val name: String,
    val path: String,
    val sha: String,
    @SerialName("html_url") val htmlUrl: String
)
