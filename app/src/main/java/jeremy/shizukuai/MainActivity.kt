package com.jeremy.shizukuai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
                    AiAgentDashboard()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuManager.unbindUserService()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentDashboard() {
    val context = LocalContext.current
    val remoteService by ShizukuManager.remoteService.collectAsState()
    val isGranted by ShizukuManager.isPermissionGranted.collectAsState()

    var consoleOutput by remember { mutableStateOf("// System Console Ready\n") }
    var promptInput by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var isModelReady by remember { mutableStateOf(false) }

    // Use LiteRtEngine (Java) instead of importing com.google.ai.edge.litertlm.Engine directly
    var liteRtEngine by remember { mutableStateOf<LiteRtEngine?>(null) }

    val scope = rememberCoroutineScope()

    // Load LiteRT-LM Model on launch
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val modelFile = File(context.getExternalFilesDir(null), "gemma-3n-E2B-it-int4.bin")
            if (modelFile.exists()) {
                try {
                    withContext(Dispatchers.Main) {
                        consoleOutput += "[LiteRT]: Loading model ${modelFile.name}...\n"
                    }

                    // Instantiate via Java Bridge
                    val engine = LiteRtEngine.create(context, modelFile.absolutePath)

                    liteRtEngine = engine
                    isModelReady = true

                    withContext(Dispatchers.Main) {
                        consoleOutput += "[LiteRT]: Local LLM Ready!\n"
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        consoleOutput += "[LiteRT Error]: ${e.localizedMessage}\n"
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    consoleOutput += "[LiteRT]: Model file missing at:\n${modelFile.absolutePath}\n(Falling back to direct shell input)\n"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jeremy AI Agent") },
                actions = {
                    AssistChip(
                        onClick = { ShizukuManager.checkPermission() },
                        label = {
                            Text(
                                if (remoteService != null) "Connected (UID 2000)"
                                else if (isGranted) "Permission Granted"
                                else "Request Shizuku"
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (remoteService != null) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                        )
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val service = remoteService
                            val result = service?.dumpUiHierarchy() ?: "Error: Service not bound"
                            withContext(Dispatchers.Main) {
                                consoleOutput += "\n[UI Dump]:\n${result.take(500)}...\n"
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = remoteService != null && !isProcessing
                ) {
                    Text("Dump UI Tree")
                }

                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            remoteService?.injectTap(500, 1000)
                            withContext(Dispatchers.Main) {
                                consoleOutput += "\n[Action]: Injected tap at (500, 1000)\n"
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = remoteService != null && !isProcessing
                ) {
                    Text("Test Tap (500,1000)")
                }
            }

            // Input Field supporting both Natural Language AI Prompts and Direct Commands
            OutlinedTextField(
                value = promptInput,
                onValueChange = { promptInput = it },
                label = { Text(if (isModelReady) "Ask AI or Enter Shell Command" else "Privileged Shell Command") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isProcessing,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val query = promptInput
                            promptInput = ""
                            isProcessing = true

                            scope.launch(Dispatchers.IO) {
                                val engine = liteRtEngine
                                val service = remoteService

                                if (isModelReady && engine != null) {
                                    withContext(Dispatchers.Main) {
                                        consoleOutput += "\n> $query\n[LiteRT Thinking...]\n"
                                    }

                                    // Run inference safely inside the Java engine wrapper
                                    val extractedCmd = engine.generateCommand(query)

                                    withContext(Dispatchers.Main) {
                                        consoleOutput += "[AI Command]: $extractedCmd\n"
                                    }

                                    // Execute command via Shizuku user service
                                    val output = service?.execCommand(extractedCmd) ?: "Service not connected"
                                    withContext(Dispatchers.Main) {
                                        consoleOutput += "[Output]:\n$output\n"
                                    }
                                } else {
                                    // Direct Shell Execution fallback
                                    val output = service?.execCommand(query) ?: "Service not connected"
                                    withContext(Dispatchers.Main) {
                                        consoleOutput += "\n$ $query\n$output\n"
                                    }
                                }
                                isProcessing = false
                            }
                        },
                        enabled = remoteService != null && promptInput.isNotBlank() && !isProcessing
                    ) {
                        Text(if (isProcessing) "..." else "Run")
                    }
                }
            )

            if (isProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .padding(12.dp)
            ) {
                val scrollState = rememberScrollState()

                // Auto-scroll console to bottom as text appends
                LaunchedEffect(consoleOutput) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

                Text(
                    text = consoleOutput,
                    color = Color(0xFF00FF66),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                )
            }
        }
    }
}
