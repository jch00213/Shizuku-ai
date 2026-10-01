package com.jeremy.shizukuai

import android.os.Bundle
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
import com.jeremy.shizukuai.ui.*
import kotlinx.coroutines.Dispatchers
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
    var currentModelFileName by remember { mutableStateOf("gemma-3n-E2B-it-int4.bin") }

    // Function to initialize or reload model
    fun loadModel(fileName: String) {
        scope.launch(Dispatchers.IO) {
            isModelReady = false
            liteRtEngine?.close()
            liteRtEngine = null

            val modelFile = File(context.getExternalFilesDir(null), fileName)
            if (modelFile.exists()) {
                try {
                    withContext(Dispatchers.Main) {
                        messages.add(ChatMessage("[LiteRT]: Loading model ${modelFile.name}...", MessageType.SYSTEM))
                    }

                    val engine = LiteRtEngine.create(context, modelFile.absolutePath)
                    liteRtEngine = engine
                    isModelReady = true

                    withContext(Dispatchers.Main) {
                        messages.add(ChatMessage("[LiteRT]: Model Ready!", MessageType.SYSTEM))
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
                            "[LiteRT]: Model missing at ${modelFile.name}. Open Hugging Face screen to download.",
                            MessageType.SYSTEM
                        )
                    )
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
                selectedFileName = currentModelFileName,
                onFileNameChanged = { currentModelFileName = it },
                onReloadModel = { loadModel(currentModelFileName) },
                isShizukuConnected = remoteService != null || isGranted,
                onRequestShizukuPermission = { ShizukuManager.checkPermission() }
            )
        }

        composable(Screen.HuggingFace.route) {
            HuggingFaceScreen(
                onBack = { navController.popBackStack() },
                onModelDownloaded = { fileName ->
                    currentModelFileName = fileName
                }
            )
        }
    }
}
