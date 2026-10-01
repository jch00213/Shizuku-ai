package com.jeremy.shizukuai.ui

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
import androidx.compose.ui.unit.dp
import com.jeremy.shizukuai.data.DownloadStatus
import com.jeremy.shizukuai.data.ModelDownloader
import com.jeremy.shizukuai.service.ModelDownloadService

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
    val downloadStatus by ModelDownloader.downloadStatus.collectAsState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    fun triggerDownload(url: String, fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ModelDownloadService.start(context, url, fileName)
    }

    val presetModels = remember {
        listOf(
            ModelItem(
                name = "Gemma 3n E2B INT4",
                repoId = "google/gemma-3n-E2B-it-int4",
                fileName = "gemma-3n-E2B-it-int4.bin",
                size = "1.2 GB",
                downloadUrl = "https://huggingface.co/google/gemma-3n-E2B-it-int4/resolve/main/gemma-3n-E2B-it-int4.bin"
            ),
            ModelItem(
                name = "Gemma 2B LiteRT",
                repoId = "google/gemma-2b-it-litert",
                fileName = "gemma-2b-it-litert.bin",
                size = "1.3 GB",
                downloadUrl = "https://huggingface.co/google/gemma-2b-it-litert/resolve/main/model.bin"
            ),
            ModelItem(
                name = "Qwen 2.5 0.5B Instruct",
                repoId = "Qwen/Qwen2.5-0.5B-Instruct",
                fileName = "qwen2.5-0.5b-instruct.bin",
                size = "350 MB",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct/resolve/main/model.bin"
            )
        )
    }

    LaunchedEffect(downloadStatus) {
        if (downloadStatus is DownloadStatus.Success) {
            val file = (downloadStatus as DownloadStatus.Success).file
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
                    enabled = downloadStatus !is DownloadStatus.Downloading
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (customUrl.isNotBlank()) {
                            val fileName = customUrl.substringAfterLast("/").ifEmpty { "custom_model.bin" }
                            triggerDownload(customUrl, fileName)
                        }
                    },
                    enabled = customUrl.isNotBlank() && downloadStatus !is DownloadStatus.Downloading
                ) {
                    Text("Fetch")
                }
            }

            when (val status = downloadStatus) {
                is DownloadStatus.Downloading -> {
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
                                progress = { status.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val currentMb = status.downloadedBytes / (1024 * 1024)
                                val totalMb = status.totalBytes / (1024 * 1024)
                                Text(
                                    text = if (status.totalBytes > 0) "$currentMb MB / $totalMb MB" else "$currentMb MB downloaded",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "${(status.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                is DownloadStatus.Success -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Successfully downloaded & activated ${status.file.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is DownloadStatus.Error -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Error: ${status.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                DownloadStatus.Canceled -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Download canceled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                DownloadStatus.Idle -> {}
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
                                enabled = downloadStatus !is DownloadStatus.Downloading
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
