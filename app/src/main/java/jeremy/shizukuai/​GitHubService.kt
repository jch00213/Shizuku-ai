package com.jeremy.shizukuai

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GitHubCommentRequest(val body: String)

class GitHubService(private val token: String) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    // 1. Fetch raw PR diff text directly from GitHub REST API
    suspend fun getPullRequestDiff(owner: String, repo: String, prNumber: Int): String {
        val response = client.get("https://api.github.com/repos/$owner/$repo/pulls/$prNumber") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/vnd.github.v3.diff")
            header(HttpHeaders.UserAgent, "ShizukuAI-Agent")
        }
        return if (response.status.isSuccess()) response.bodyAsText() else ""
    }

    // 2. Post review comment back to the PR
    suspend fun postPrComment(owner: String, repo: String, prNumber: Int, comment: String): Boolean {
        val response = client.post("https://api.github.com/repos/$owner/$repo/issues/$prNumber/comments") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.UserAgent, "ShizukuAI-Agent")
            contentType(ContentType.Application.Json)
            setBody(GitHubCommentRequest(body = comment))
        }
        return response.status.isSuccess()
    }
}
