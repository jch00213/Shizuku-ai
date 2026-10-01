package com.jeremy.shizukuai

import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// Keep ui imports ONLY for files actually located inside the 'ui' package directory
import com.jeremy.shizukuai.ui.ChatMessage
import com.jeremy.shizukuai.ui.ChatScreen
import com.jeremy.shizukuai.ui.MessageType
import com.jeremy.shizukuai.ui.Screen
import com.jeremy.shizukuai.ui.SettingsScreen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppHost()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuManager.unbindUserService()
    }
}

@Composable
fun AppHost() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val remoteService by ShizukuManager.remoteService.collectAsState()
    val isGranted by ShizukuManager.isPermissionGranted.collectAsState()

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var isProcessing by remember { mutableStateOf(false) }
    var isModelReady by remember { mutableStateOf(false) }
    var liteRtEngine by remember { mutableStateOf<LiteRtEngine?>(null) }
    var currentModelFileName by remember { mutableStateOf("qwen2.5-1.5b-instruct-gpu-int4.bin") }

    // Matrix Agent State
    var matrixHomeserver by remember { mutableStateOf("https://matrix.org") }
    var matrixToken by remember { mutableStateOf("") }
    var matrixRoomId by remember { mutableStateOf("") }
    var isMatrixConnected by remember { mutableStateOf(false) }
    var matrixBridge by remember { mutableStateOf<MatrixAgentBridge?>(null) }
    var matrixJob by remember { mutableStateOf<Job?>(null) }

    fun resolveModelFile(fileName: String): File {
        val cleanName = fileName.trim()

        val candidateDirs = listOf(
            File("/sdcard/models"),
            File("/storage/emulated/0/models"),
            File("/storage/self/primary/models"),
            File(Environment.getExternalStorageDirectory(), "models")
        )

        for (dir in candidateDirs) {
            if (!dir.exists()) {
                try { dir.mkdirs() } catch (_: Exception) {}
            }

            val directFile = File(dir, cleanName)
            if (directFile.exists() && directFile.isFile) {
                return directFile
            }

            val caseMatch = dir.listFiles()?.firstOrNull { 
                it.isFile && it.name.equals(cleanName, ignoreCase = true) 
            }
            if (caseMatch != null) {
                return caseMatch
            }
        }

        val appSpecificFile = File(context.getExternalFilesDir(null), cleanName)
        if (appSpecificFile.exists() && appSpecificFile.isFile) {
            return appSpecificFile
        }

        return File("/sdcard/models", cleanName)
    }

    fun loadModel(fileName: String) {
        scope.launch(Dispatchers.IO) {
            isModelReady = false
            liteRtEngine?.close()
            liteRtEngine = null

            val modelFile = resolveModelFile(fileName)
            if (modelFile.exists()) {
                try {
                    withContext(Dispatchers.Main) {
                        messages.add(ChatMessage("[LiteRT]: Loading model ${modelFile.name} from ${modelFile.parent}...", MessageType.SYSTEM))
                    }

                    val engine = LiteRtEngine.create(context, modelFile.absolutePath)
                    liteRtEngine = engine
                    isModelReady = true

                    withContext(Dispatchers.Main) {
                        messages.add(ChatMessage("[LiteRT]: Model Ready! Active: ${modelFile.name}", MessageType.SYSTEM))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        messages.add(ChatMessage("[LiteRT Error]: ${e.localizedMessage}", MessageType.SYSTEM))
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    messages.add(
                        ChatMessage(
                            "[LiteRT]: Model missing at ${modelFile.absolutePath}. Place your file in /sdcard/models/ or download via Hugging Face.",
                            MessageType.SYSTEM
                        )
                    )
                }
            }
        }
    }

    fun toggleMatrixAgent() {
        if (isMatrixConnected) {
            matrixJob?.cancel()
            matrixBridge?.stop()
            matrixBridge = null
            isMatrixConnected = false
            messages.add(ChatMessage("[Matrix Agent]: Stopped.", MessageType.SYSTEM))
        } else {
            if (matrixHomeserver.isBlank() || matrixToken.isBlank() || matrixRoomId.isBlank()) {
                messages.add(ChatMessage("[Matrix Error]: Please configure Homeserver, Access Token, and Room ID in Settings.", MessageType.SYSTEM))
                return
            }

            val bridge = MatrixAgentBridge(matrixHomeserver, matrixToken, matrixRoomId)
            matrixBridge = bridge
            isMatrixConnected = true

            messages.add(ChatMessage("[Matrix Agent]: Starting sync loop on $matrixHomeserver...", MessageType.SYSTEM))

            matrixJob = bridge.startListening { sender, prompt ->
                withContext(Dispatchers.Main) {
                    messages.add(ChatMessage("[Matrix @ $sender]: $prompt", MessageType.USER))
                }

                val engine = liteRtEngine
                val service = remoteService

                if (engine != null && isModelReady) {
                    val extractedCmd = engine.generateCommand(prompt)

                    withContext(Dispatchers.Main) {
                        messages.add(ChatMessage("Extracted Command:\n$extractedCmd", MessageType.AI))
                    }

                    val output = service?.execCommand(extractedCmd) ?: "Shizuku service not connected"

                    withContext(Dispatchers.Main) {
                        messages.add(ChatMessage(output, MessageType.COMMAND_OUTPUT))
                    }

                    "🤖 [OpenClaw Agent Execution]\nCommand:\n$extractedCmd\n\nOutput:\n$output"
                } else {
                    val output = service?.execCommand(prompt) ?: "Shizuku service not connected"
                    
                    withContext(Dispatchers.Main) {
                        messages.add(ChatMessage(output, MessageType.COMMAND_OUTPUT))
                    }

                    "⚠️ [Raw Shell Fallback (Model Not Loaded)]\nOutput:\n$output"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        messages.add(ChatMessage("// Shizuku AI Dashboard Initialized", MessageType.SYSTEM))
        loadModel(currentModelFileName)
    }

    NavHost(navController = navController, startDestination = Screen.Chat.route) {
        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToHuggingFace = { navController.navigate(Screen.HuggingFace.route) },
                messages = messages,
                isLoading = isProcessing,
                isShizukuConnected = remoteService != null || isGranted,
                isModelReady = isModelReady,
                onSendMessage = { prompt ->
                    messages.add(ChatMessage(prompt, MessageType.USER))
                    isProcessing = true

                    scope.launch(Dispatchers.IO) {
                        val engine = liteRtEngine
                        val service = remoteService

                        if (isModelReady && engine != null) {
                            withContext(Dispatchers.Main) {
                                messages.add(ChatMessage("[LiteRT Thinking...]", MessageType.SYSTEM))
                            }

                            val extractedCmd = engine.generateCommand(prompt)

                            withContext(Dispatchers.Main) {
                                messages.add(ChatMessage("Extracted Command:\n$extractedCmd", MessageType.AI))
                            }

                            val output = service?.execCommand(extractedCmd) ?: "Service not connected"
                            withContext(Dispatchers.Main) {
                                messages.add(ChatMessage(output, MessageType.COMMAND_OUTPUT))
                            }
                        } else {
                            val output = service?.execCommand(prompt) ?: "Service not connected"
                            withContext(Dispatchers.Main) {
                                messages.add(ChatMessage(output, MessageType.COMMAND_OUTPUT))
                            }
                        }
                        isProcessing = false
                    }
                },
                onDumpUi = {
                    scope.launch(Dispatchers.IO) {
                        val service = remoteService
                        val result = service?.dumpUiHierarchy() ?: "Error: Service not bound"
                        withContext(Dispatchers.Main) {
                            messages.add(ChatMessage("[UI Dump]:\n${result.take(500)}...", MessageType.COMMAND_OUTPUT))
                        }
                    }
                },
                onTestTap = {
                    scope.launch(Dispatchers.IO) {
                        remoteService?.injectTap(500, 1000)
                        withContext(Dispatchers.Main) {
                            messages.add(ChatMessage("[Action]: Injected tap at (500, 1000)", MessageType.SYSTEM))
                        }
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                selectedModelPath = currentModelFileName,
                onModelPathChanged = { newPath -> currentModelFileName = newPath },
                onReloadModel = { loadModel(currentModelFileName) },
                isShizukuConnected = remoteService != null || isGranted,
                onRequestShizukuPermission = { ShizukuManager.checkPermission() },
                // Matrix Bridge parameters explicitly typed
                matrixHomeserver = matrixHomeserver,
                onMatrixHomeserverChanged = { newServer -> matrixHomeserver = newServer },
                matrixToken = matrixToken,
                onMatrixTokenChanged = { newToken -> matrixToken = newToken },
                matrixRoomId = matrixRoomId,
                onMatrixRoomIdChanged = { newRoom -> matrixRoomId = newRoom },
                isMatrixConnected = isMatrixConnected,
                onToggleMatrixAgent = { toggleMatrixAgent() }
            )
        }

        composable(Screen.HuggingFace.route) {
            HuggingFaceScreen(
                onBack = { navController.popBackStack() },
                onModelDownloaded = { fileName ->
                    currentModelFileName = fileName
                    loadModel(fileName)
                }
            )
        }
    }
}
