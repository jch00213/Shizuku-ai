package com.jeremy.shizukuai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveGitHubToken(token: String) {
        prefs.edit().putString("github_token", token).apply()
    }

    fun getGitHubToken(): String? {
        return prefs.getString("github_token", null)
    }

    fun hasToken(): Boolean = !getGitHubToken().isNull_or_blank()

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
}
