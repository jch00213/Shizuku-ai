package com.jeremy.shizukuai

import android.content.Context
import io.ktor.serialization.gson.gson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
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
                get("/status") {
                    val isReady = engineProvider() != null
                    call.respond(mapOf("status" to if (isReady) "ready" else "model_not_loaded"))
                }

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
