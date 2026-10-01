package com.jeremy.shizukuai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

enum class MessageType {
    USER, AI, SYSTEM, COMMAND_OUTPUT
}

data class ChatMessage(
    val text: String,
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToHuggingFace: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDumpUi: () -> Unit,
    onTestTap: () -> Unit,
    messages: List<ChatMessage>,
    isLoading: Boolean,
    isShizukuConnected: Boolean,
    isModelReady: Boolean
) {
    var inputText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shizuku AI Terminal") },
                actions = {
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(if (isShizukuConnected) "Shizuku Ready" else "Disconnected")
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isShizukuConnected) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                        ),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    IconButton(onClick = onNavigateToHuggingFace) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Hugging Face Models")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDumpUi,
                    enabled = isShizukuConnected && !isLoading,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dump UI", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onTestTap,
                    enabled = isShizukuConnected && !isLoading,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tap (500,1000)", style = MaterialTheme.typography.labelMedium)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                reverseLayout = true
            ) {
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
                items(messages.reversed()) { msg ->
                    ChatBubbleItem(message = msg)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                if (isModelReady) "Prompt AI or type shell command..."
                                else "Direct Shizuku shell command..."
                            )
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank() && !isLoading && isShizukuConnected
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubbleItem(message: ChatMessage) {
    val isUser = message.type == MessageType.USER
    val isCommandOutput = message.type == MessageType.COMMAND_OUTPUT
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    val bgColor = when (message.type) {
        MessageType.USER -> MaterialTheme.colorScheme.primaryContainer
        MessageType.AI -> MaterialTheme.colorScheme.secondaryContainer
        MessageType.SYSTEM -> MaterialTheme.colorScheme.surfaceVariant
        MessageType.COMMAND_OUTPUT -> Color(0xFF1E1E1E)
    }

    val textColor = when (message.type) {
        MessageType.USER -> MaterialTheme.colorScheme.onPrimaryContainer
        MessageType.AI -> MaterialTheme.colorScheme.onSecondaryContainer
        MessageType.SYSTEM -> MaterialTheme.colorScheme.onSurfaceVariant
        MessageType.COMMAND_OUTPUT -> Color(0xFF00FF66)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(bgColor, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                text = message.text,
                color = textColor,
                fontFamily = if (isCommandOutput) FontFamily.Monospace else FontFamily.Default,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
