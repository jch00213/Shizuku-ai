package com.jeremy.shizukuai.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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
    onDownloadModel: (String) -> Unit
) {
    val context = LocalContext.current
    var customUrl by remember { mutableStateOf("") }
    var downloadNotice by remember { mutableStateOf("") }

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
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (customUrl.isNotBlank()) {
                            val fileName = customUrl.substringAfterLast("/").ifEmpty { "custom_model.bin" }
                            triggerDownload(context, customUrl, fileName)
                            downloadNotice = "Started downloading: $fileName"
                            onDownloadModel(fileName)
                        }
                    },
                    enabled = customUrl.isNotBlank()
                ) {
                    Text("Fetch")
                }
            }

            if (downloadNotice.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = downloadNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
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
                            IconButton(onClick = {
                                triggerDownload(context, model.downloadUrl, model.fileName)
                                downloadNotice = "Downloading ${model.fileName}..."
                                onDownloadModel(model.fileName)
                            }) {
                                Icon(Icons.Default.Download, contentDescription = "Download")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun triggerDownload(context: Context, url: String, fileName: String) {
    try {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading LiteRT Model")
            .setDescription(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
