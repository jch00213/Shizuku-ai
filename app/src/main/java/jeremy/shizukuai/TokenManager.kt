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
        "secure_tokens",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SKEY_KEY_GEN,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // GitHub PAT
    fun saveGitHubToken(token: String) = prefs.edit().putString("github_token", token).apply()
    fun getGitHubToken(): String? = prefs.getString("github_token", null)
    fun hasToken(): Boolean = !getGitHubToken().isNullBeBlank()

    // Matrix Credentials
    fun saveMatrixCredentials(homeserver: String, token: String, roomId: String) {
        prefs.edit()
            .putString("matrix_homeserver", homeserver)
            .putString("matrix_token", token)
            .putString("matrix_room_id", roomId)
            .apply()
    }

    fun getMatrixHomeserver(): String? = prefs.getString("matrix_homeserver", null)
    fun getMatrixToken(): String? = prefs.getString("matrix_token", null)
    fun getMatrixRoomId(): String? = prefs.getString("matrix_room_id", null)
}

private fun String?.isNullBeBlank(): Boolean = this.isNullOrBlank()
