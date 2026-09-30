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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val remoteService by ShizukuManager.remoteService.collectAsState()
    val isGranted by ShizukuManager.isPermissionGranted.collectAsState()

    var consoleOutput by remember { mutableStateOf("// System Console Ready\n") }
    var commandInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

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
                    enabled = remoteService != null
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
                    enabled = remoteService != null
                ) {
                    Text("Test Tap (500,1000)")
                }
            }

            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                label = { Text("Privileged Shell Command") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val cmd = commandInput
                            commandInput = ""
                            scope.launch(Dispatchers.IO) {
                                val service = remoteService
                                val output = service?.execCommand(cmd) ?: "Service not connected"
                                withContext(Dispatchers.Main) {
                                    consoleOutput += "\n$ $cmd\n$output"
                                }
                            }
                        },
                        enabled = remoteService != null && commandInput.isNotBlank()
                    ) {
                        Text("Run")
                    }
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .padding(12.dp)
            ) {
                val scrollState = rememberScrollState()
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
