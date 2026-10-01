package com.jeremy.shizukuai

import android.content.Context
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InferenceRequest(val prompt: String)
data class InferenceResponse(val command: String, val status: String = "success")

class AgentServer(private val context: Context, private val engineProvider: () -> LiteRtEngine?) {

    private var server: CIOApplicationEngine? = null

    fun start(port: Int = 8080) {
        if (server != null) return

        server = embeddedServer(CIO, host = "0.0.0.0", port = port) {
            install(ContentNegotiation) {
                gson()
            }

            routing {
                // Health Check Endpoint
                get("/status") {
                    val isReady = engineProvider() != null
                    call.respond(mapOf("status" to if (isReady) "ready" else "model_not_loaded"))
                }

                // AI Command Generation Endpoint
                post("/generate") {
                    val request = call.receive<InferenceRequest>()
                    val engine = engineProvider()

                    if (engine == null) {
                        call.respond(InferenceResponse("Error: Model not loaded", status = "error"))
                        return@post
                    }

                    val resultCommand = withContext(Dispatchers.IO) {
                        engine.generateCommand(context, request.prompt)
                    }

                    call.respond(InferenceResponse(command = resultCommand))
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }
}
