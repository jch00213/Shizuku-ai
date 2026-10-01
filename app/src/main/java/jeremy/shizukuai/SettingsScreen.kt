package com.jeremy.shizukuai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    selectedModelPath: String,
    onModelPathChanged: (String) -> Unit,
    onReloadModel: () -> Unit,
    isShizukuConnected: Boolean,
    onRequestShizukuPermission: () -> Unit
) {
    var autoExecute by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "LiteRT Engine Configuration", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = selectedModelPath,
                onValueChange = onModelPathChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model File Name (in app files dir)") },
                singleLine = true
            )

            Button(
                onClick = onReloadModel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reload Model")
            }

            HorizontalDivider()

            Text(text = "Shizuku Runtime", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Shizuku Connection Status", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (isShizukuConnected) "Active (UID 2000)" else "Disconnected / Permission Needed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!isShizukuConnected) {
                    Button(onClick = onRequestShizukuPermission) {
                        Text("Connect")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Auto-Execute Extracted Shell Commands", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Execute shell commands via Shizuku automatically after LLM inference.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoExecute,
                    onCheckedChange = { autoExecute = it }
                )
            }
        }
    }
}
