package com.jeremy.shizukuai

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.io.File

data class ModelItem(
    val name: String,
    val repoId: String,
    val fileName: String,
    val size: String,
    val downloadUrl: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuggingFaceScreen(
    onBack: () -> Unit,
    onModelDownloaded: (String) -> Unit
) {
    val context = LocalContext.current
    var customUrl by remember { mutableStateOf("") }
    var hfToken by remember { mutableStateOf("") }
    val downloadState by ModelDownloadService.downloadState.collectAsState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    fun triggerDownload(url: String, fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val formattedUrl = if (url.contains("huggingface.co") && !url.contains("?download=true")) {
            "$url?download=true"
        } else {
            url
        }

        customUrl = formattedUrl
        val targetPath = File(context.getExternalFilesDir(null), fileName).absolutePath
        ModelDownloadService.start(context, formattedUrl, targetPath, hfToken.ifBlank { null })
    }

    val presetModels = remember {
        listOf(
            ModelItem(
                name = "Gemma 2B IT (LiteRT Task)",
                repoId = "google/gemma-2b-it-litert",
                fileName = "gemma-2b-it-litert.bin",
                size = "1.3 GB",
                downloadUrl = "https://huggingface.co/google/gemma-2b-it-litert/resolve/main/model.bin?download=true"
            ),
            ModelItem(
                name = "Gemma 3n E2B INT4",
                repoId = "google/gemma-3n-E2B-it-int4",
                fileName = "gemma-3n-E2B-it-int4.bin",
                size = "1.2 GB",
                downloadUrl = "https://huggingface.co/google/gemma-3n-E2B-it-int4/resolve/main/gemma-3n-E2B-it-int4.bin?download=true"
            ),
            ModelItem(
                name = "Qwen 2.5 0.5B Instruct",
                repoId = "Qwen/Qwen2.5-0.5B-Instruct",
                fileName = "qwen2.5-0.5b-instruct.bin",
                size = "350 MB",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct/resolve/main/model.bin?download=true"
            )
        )
    }

    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Completed) {
            val file = (downloadState as DownloadState.Completed).file
            onModelDownloaded(file.name)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hugging Face Hub") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(text = "Authentication Token", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = hfToken,
                onValueChange = { hfToken = it },
                placeholder = { Text("hf_xxxxxxxx (User Access Token)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = downloadState !is DownloadState.Downloading
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Download Model Direct URL", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customUrl,
                    onValueChange = { customUrl = it },
                    placeholder = { Text("Direct .bin download URL") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = downloadState !is DownloadState.Downloading
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (customUrl.isNotBlank()) {
                            val cleanName = customUrl.substringBefore("?").substringAfterLast("/")
                            val fileName = if (cleanName.endsWith(".bin") || cleanName.endsWith(".task")) {
                                cleanName
                            } else {
                                "custom_model.bin"
                            }
                            triggerDownload(customUrl, fileName)
                        }
                    },
                    enabled = customUrl.isNotBlank() && downloadState !is DownloadState.Downloading
                ) {
                    Text("Fetch")
                }
            }

            when (val state = downloadState) {
                is DownloadState.Downloading -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Downloading Model...",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                IconButton(
                                    onClick = { ModelDownloadService.stop(context) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel Download")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { if (state.totalBytes > 0) state.progress / 100f else 0f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val currentMb = state.bytesDownloaded / (1024 * 1024)
                                val totalMb = state.totalBytes / (1024 * 1024)
                                Text(
                                    text = if (state.totalBytes > 0) "$currentMb MB / $totalMb MB" else "$currentMb MB downloaded",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "${state.progress}%",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                is DownloadState.Completed -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Successfully downloaded & activated ${state.file.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is DownloadState.Error -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Error: ${state.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                DownloadState.Canceled -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Download canceled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                DownloadState.Idle -> {}
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Recommended LiteRT Models", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn {
                items(presetModels) { model ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = model.name, style = MaterialTheme.typography.titleSmall)
                                Text(text = model.repoId, style = MaterialTheme.typography.bodySmall)
                                Text(text = "File: ${model.fileName} (${model.size})", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(
                                onClick = { triggerDownload(model.downloadUrl, model.fileName) },
                                enabled = downloadState !is DownloadState.Downloading
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Download")
                            }
                        }
                    }
                }
            }
        }
    }
}
