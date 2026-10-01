// HuggingFaceScreen.kt
package com.jeremy.shizukuai.ui

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
import androidx.compose.ui.unit.dp

data class ModelItem(
    val name: String,
    val repoId: String,
    val size: String,
    val downloadUrl: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuggingFaceScreen(
    onBack: () -> Unit,
    onDownloadModel: (ModelItem) -> Unit
) {
    var customRepoId by remember { mutableStateOf("") }

    val presetModels = remember {
        listOf(
            ModelItem("Gemma 2B LiteRT", "google/gemma-2b-it-litert", "1.3 GB", "https://huggingface.co/google/gemma-2b-it-litert/resolve/main/model.bin"),
            ModelItem("Qwen 2.5 0.5B Instruct", "Qwen/Qwen2.5-0.5B-Instruct", "350 MB", "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct/resolve/main/model.bin"),
            ModelItem("Llama 3.2 1B Instruct", "meta-llama/Llama-3.2-1B-Instruct", "750 MB", "https://huggingface.co/meta-llama/Llama-3.2-1B-Instruct/resolve/main/model.bin")
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
            Text(
                text = "Download Model from Repo",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customRepoId,
                    onValueChange = { customRepoId = it },
                    placeholder = { Text("repo/model-name or direct URL") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (customRepoId.isNotBlank()) {
                            onDownloadModel(
                                ModelItem("Custom Model", customRepoId, "Unknown", customRepoId)
                            )
                        }
                    },
                    enabled = customRepoId.isNotBlank()
                ) {
                    Text("Fetch")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Recommended LiteRT Models",
                style = MaterialTheme.typography.titleMedium
            )
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
                                Text(text = "Size: ${model.size}", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { onDownloadModel(model) }) {
                                Icon(Icons.Default.Download, contentDescription = "Download")
                            }
                        }
                    }
                }
            }
        }
    }
}
