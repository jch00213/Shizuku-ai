// Screen.kt
package com.jeremy.shizukuai.ui

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object Settings : Screen("settings")
    object HuggingFace : Screen("huggingface")
}
