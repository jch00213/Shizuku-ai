package com.jeremy.shizukuai

import android.content.Context
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.gson.gson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TokenSavePayload(val token: String)
data class PrReviewPayload(val owner: String, val repo: String, val prNumber: Int)
data class IssueCreatePayload(val owner: String, val repo: String, val title: String, val body: String, val labels: List<String> = emptyList())
data class WorkflowDispatchPayload(val owner: String, val repo: String, val workflowId: String, val ref: String = "main")

class AgentServer(
    private val context: Context,
    private val engineProvider: () -> LiteRtEngine?
) {
    private var server: ApplicationEngine? = null
    private val tokenManager = TokenManager(context)
    private val gitHubService = GitHubService(tokenManager)

    fun start(port: Int = 8080) {
        if (server != null) return

        server = embeddedServer(CIO, host = "0.0.0.0", port = port) {
            install(ContentNegotiation) {
                gson()
            }

            routing {
                get("/status") {
                    val isReady = engineProvider() != null
                    call.respond(mapOf(
                        "status" to if (isReady) "ready" else "model_not_loaded",
                        "github_token_configured" to tokenManager.hasToken()
                    ))
                }

                // Endpoint to set/update PAT securely
                post("/github/token") {
                    val payload = call.receive<TokenSavePayload>()
                    if (payload.token.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Token cannot be blank"))
                        return@post
                    }
                    tokenManager.saveGitHubToken(payload.token)
                    call.respond(mapOf("status" to "Token saved successfully"))
                }

                // Automated PR Review using LiteRtEngine
                post("/github/pr/review") {
                    if (!tokenManager.hasToken()) {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "GitHub PAT not configured"))
                        return@post
                    }

                    val req = call.receive<PrReviewPayload>()
                    val diff = gitHubService.getPullRequestDiff(req.owner, req.repo, req.prNumber)

                    if (diff.isBlank()) {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Could not fetch PR diff"))
                        return@post
                    }

                    val engine = engineProvider()
                    if (engine == null) {
                        call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "LiteRtEngine not ready"))
                        return@post
                    }

                    val prompt = "Analyze this PR diff for bugs, performance issues, or architectural errors:\n\n${diff.take(3500)}"
                    val review = withContext(Dispatchers.IO) {
                        engine.generateCommand(this@AgentServer.context, prompt)
                    }

                    val posted = gitHubService.postComment(req.owner, req.repo, req.prNumber, "[Shizuku AI Review]\n\n$review")
                    call.respond(mapOf("comment_posted" to posted, "review" to review))
                }

                // Create Issue
                post("/github/issues/create") {
                    if (!tokenManager.hasToken()) {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "GitHub PAT not configured"))
                        return@post
                    }

                    val req = call.receive<IssueCreatePayload>()
                    val result = gitHubService.createIssue(req.owner, req.repo, req.title, req.body, req.labels)
                    if (result != null) {
                        call.respond(result)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to create issue"))
                    }
                }

                // Trigger GitHub Actions Workflow
                post("/github/workflow/dispatch") {
                    if (!tokenManager.hasToken()) {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "GitHub PAT not configured"))
                        return@post
                    }

                    val req = call.receive<WorkflowDispatchPayload>()
                    val triggered = gitHubService.triggerWorkflow(req.owner, req.repo, req.workflowId, req.ref)
                    call.respond(mapOf("triggered" to triggered))
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }
}
