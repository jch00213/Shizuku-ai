package com.jeremy.shizukuai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jeremy.shizukuai.DownloadState
import com.jeremy.shizukuai.ModelDownloadService
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuggingFaceScreen(
    onBack: () -> Unit,
    onModelDownloaded: (String) -> Unit
) {
    val context = LocalContext.current
    val downloadState by ModelDownloadService.downloadState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hugging Face Model Hub") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val state = downloadState) {
                is DownloadState.Idle -> {
                    Text("Ready to download model files.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val defaultPath = File(context.filesDir, "qwen2.5-1.5b-instruct-gpu-int4.bin").absolutePath
                            ModelDownloadService.start(
                                context = context,
                                url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                                targetPath = defaultPath
                            )
                        }
                    ) {
                        Text("Download Default Model")
                    }
                }

                is DownloadState.Downloading -> {
                    Text("Downloading: ${state.progress}%")
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val mbDownloaded = state.bytesDownloaded / (1024 * 1024)
                    val mbTotal = state.totalBytes / (1024 * 1024)
                    if (state.totalBytes > 0) {
                        Text("$mbDownloaded MB / $mbTotal MB")
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        )
                    } else {
                        Text("$mbDownloaded MB downloaded")
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.8f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = { ModelDownloadService.stop(context) }) {
                        Text("Cancel")
                    }
                }

                is DownloadState.Completed -> {
                    Text("Download complete!\nFile: ${state.file.name}")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { onModelDownloaded(state.file.absolutePath) }) {
                        Text("Use Downloaded Model")
                    }
                }

                is DownloadState.Error -> {
                    Text(
                        text = "Download Failed: ${state.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        ModelDownloadService.start(
                            context = context,
                            url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                            targetPath = File(context.filesDir, "qwen2.5-1.5b-instruct-gpu-int4.bin").absolutePath
                        )
                    }) {
                        Text("Retry Download")
                    }
                }

                is DownloadState.Canceled -> {
                    Text("Download was canceled.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        ModelDownloadService.start(
                            context = context,
                            url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                            targetPath = File(context.filesDir, "qwen2.5-1.5b-instruct-gpu-int4.bin").absolutePath
                        )
                    }) {
                        Text("Start Download")
                    }
                }
            }
        }
    }
}
