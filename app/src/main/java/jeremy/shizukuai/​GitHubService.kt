package com.jeremy.shizukuai

import android.util.Base64
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class GitHubService(private val tokenManager: TokenManager) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
    }

    private fun getToken(): String {
        return tokenManager.getGitHubToken()
            ?: throw IllegalStateException("GitHub PAT is not set in TokenManager.")
    }

    // 1. Get raw PR diff
    suspend fun getPullRequestDiff(owner: String, repo: String, prNumber: Int): String {
        val response = client.get("https://api.github.com/repos/$owner/$repo/pulls/$prNumber") {
            header(HttpHeaders.Authorization, "Bearer ${getToken()}")
            header(HttpHeaders.Accept, "application/vnd.github.v3.diff")
            header(HttpHeaders.UserAgent, "ShizukuAI-Agent")
        }
        return if (response.status.isSuccess()) response.bodyAsText() else ""
    }

    // 2. Post PR / Issue comment
    suspend fun postComment(owner: String, repo: String, issueOrPrNumber: Int, comment: String): Boolean {
        val response = client.post("https://api.github.com/repos/$owner/$repo/issues/$issueOrPrNumber/comments") {
            header(HttpHeaders.Authorization, "Bearer ${getToken()}")
            header(HttpHeaders.UserAgent, "ShizukuAI-Agent")
            contentType(ContentType.Application.Json)
            setBody(GitHubCommentRequest(body = comment))
        }
        return response.status.isSuccess()
    }

    // 3. Create a new issue
    suspend fun createIssue(
        owner: String,
        repo: String,
        title: String,
        body: String,
        labels: List<String> = emptyList()
    ): GitHubIssueResponse? {
        val response = client.post("https://api.github.com/repos/$owner/$repo/issues") {
            header(HttpHeaders.Authorization, "Bearer ${getToken()}")
            header(HttpHeaders.UserAgent, "ShizukuAI-Agent")
            contentType(ContentType.Application.Json)
            setBody(GitHubIssueCreateRequest(title = title, body = body, labels = labels))
        }
        return if (response.status.isSuccess()) response.body() else null
    }

    // 4. Trigger GitHub Actions Workflow Dispatch
    suspend fun triggerWorkflow(owner: String, repo: String, workflowId: String, ref: String = "main"): Boolean {
        val response = client.post("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowId/dispatches") {
            header(HttpHeaders.Authorization, "Bearer ${getToken()}")
            header(HttpHeaders.UserAgent, "ShizukuAI-Agent")
            contentType(ContentType.Application.Json)
            setBody(GitHubWorkflowDispatchRequest(ref = ref))
        }
        return response.status.isSuccess()
    }

    // 5. Commit/Create/Update file directly in repository
    suspend fun commitFile(
        owner: String,
        repo: String,
        path: String,
        fileContentRaw: String,
        commitMessage: String,
        sha: String? = null,
        branch: String = "main"
    ): Boolean {
        val base64Content = Base64.encodeToString(fileContentRaw.toByteArray(), Base64.NO_WRAP)
        val response = client.put("https://api.github.com/repos/$owner/$repo/contents/$path") {
            header(HttpHeaders.Authorization, "Bearer ${getToken()}")
            header(HttpHeaders.UserAgent, "ShizukuAI-Agent")
            contentType(ContentType.Application.Json)
            setBody(
                GitHubCreateOrUpdateFileRequest(
                    message = commitMessage,
                    content = base64Content,
                    sha = sha,
                    branch = branch
                )
            )
        }
        return response.status.isSuccess()
    }
}
