// SettingsScreen.kt
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
    onModelPathChanged: (String) -> Unit
) {
    var autoExecuteShell by remember { mutableStateOf(false) }

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
                .padding(16.dp)
        ) {
            Text(text = "Active Model File", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = selectedModelPath,
                onValueChange = onModelPathChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Local .bin Path") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(text = "Execution Environment", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Auto-Execute Shizuku Commands", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Automatically execute generated shell commands via Shizuku without prompting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoExecuteShell,
                    onCheckedChange = { autoExecuteShell = it }
                )
            }
        }
    }
}
