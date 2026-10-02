package com.jeremy.shizukuai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secret_tokens",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SKEY,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveMatrixCredentials(homeserver: String, token: String, roomId: String) {
        prefs.edit()
            .putString(KEY_MATRIX_HOMESERVER, homeserver)
            .putString(KEY_MATRIX_TOKEN, token)
            .putString(KEY_MATRIX_ROOM_ID, roomId)
            .apply()
    }

    fun getMatrixHomeserver(): String? = prefs.getString(KEY_MATRIX_HOMESERVER, null)
    fun getMatrixToken(): String? = prefs.getString(KEY_MATRIX_TOKEN, null)
    fun getMatrixRoomId(): String? = prefs.getString(KEY_MATRIX_ROOM_ID, null)

    companion object {
        private const val KEY_MATRIX_HOMESERVER = "matrix_homeserver"
        private const val KEY_MATRIX_TOKEN = "matrix_token"
        private const val KEY_MATRIX_ROOM_ID = "matrix_room_id"
    }
}
