package com.jeremy.shizukuai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Completed(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
    object Canceled : DownloadState()
}

class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var downloadJob: Job? = null
    private var isCanceled = false

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_DOWNLOAD_URL)
                val targetPath = intent.getStringExtra(EXTRA_TARGET_PATH)
                val hfToken = intent.getStringExtra(EXTRA_HF_TOKEN)

                if (url.isNullOrBlank() || targetPath.isNullOrBlank() || isCanceled) {
                    _downloadState.value = DownloadState.Error("Invalid download URL or target path provided.")
                    stopSelf()
                    return START_NOT_STICKY
                }

                startForeground(NOTIFICATION_ID, buildNotification("Preparing download...", 0, true))
                startDownload(url, File(targetPath), hfToken)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                cancelDownload()
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(url: String, targetFile: File, hfToken: String?) {
        isCanceled = false
        _downloadState.value = DownloadState.Downloading(0, 0L, 0L)

        downloadJob = serviceScope.launch {
            try {
                val existingLength = if (targetFile.exists()) targetFile.length() else 0L

                val requestBuilder = Request.Builder().url(url)
                if (existingLength > 0) {
                    requestBuilder.addHeader("Range", "bytes=$existingLength-")
                }
                if (!hfToken.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $hfToken")
                }

                val response = okHttpClient.newCall(requestBuilder.build()).execute()

                if (response.code == 401) {
                    throw IllegalStateException("Authentication failed. Please check your Hugging Face API token.")
                }

                if (!response.isSuccessful && response.code != 206) {
                    throw IllegalStateException("HTTP error code: ${response.code}")
                }

                val body = response.body ?: throw IllegalStateException("Empty response body from server.")
                val totalBytes = (body.contentLength().takeIf { it != -1L } ?: 0L) + existingLength

                saveStreamToFile(body.byteStream(), targetFile, existingLength, totalBytes)

                if (!isCanceled) {
                    _downloadState.value = DownloadState.Completed(targetFile)
                    updateNotification("Download complete!", 100, false)
                }
            } catch (e: Exception) {
                if (isCanceled) {
                    _downloadState.value = DownloadState.Canceled
                } else {
                    _downloadState.value = DownloadState.Error(e.localizedMessage ?: "Download failed.")
                    updateNotification("Download failed: ${e.localizedMessage}", 0, false)
                }
            } finally {
                stopSelf()
            }
        }
    }

    private suspend fun saveStreamToFile(
        inputStream: InputStream,
        targetFile: File,
        alreadyDownloaded: Long,
        totalBytes: Long
    ) = withContext(Dispatchers.IO) {
        val append = alreadyDownloaded > 0
        FileOutputStream(targetFile, append).use { output ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var currentBytes = alreadyDownloaded
            var lastProgress = -1

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (isCanceled) {
                    inputStream.close()
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    break
                }

                output.write(buffer, 0, bytesRead)
                currentBytes += bytesRead

                if (totalBytes > 0) {
                    val progress = ((currentBytes * 100) / totalBytes).toInt()
                    if (progress != lastProgress) {
                        lastProgress = progress
                        _downloadState.value = DownloadState.Downloading(progress, currentBytes, totalBytes)
                        updateNotification("Downloading model ($progress%)", progress, false)
                    }
                }
            }
        }
    }

    private fun cancelDownload() {
        isCanceled = true
        downloadJob?.cancel()
        _downloadState.value = DownloadState.Canceled
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active background AI model downloads"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String, progress: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Model Download")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, indeterminate)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                getCancelPendingIntent()
            )
            .build()

    private fun updateNotification(content: String, progress: Int, indeterminate: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(content, progress, indeterminate))
    }

    private fun getCancelPendingIntent(): PendingIntent {
        val intent = Intent(this, ModelDownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "model_download_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_DOWNLOAD = "com.jeremy.shizukuai.ACTION_START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.jeremy.shizukuai.ACTION_CANCEL_DOWNLOAD"

        const val EXTRA_DOWNLOAD_URL = "extra_download_url"
        const val EXTRA_TARGET_PATH = "extra_target_path"
        const val EXTRA_HF_TOKEN = "extra_hf_token"

        private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
        val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

        fun start(context: Context, url: String, targetPath: String, hfToken: String? = null) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_URL, url)
                putExtra(EXTRA_TARGET_PATH, targetPath)
                putExtra(EXTRA_HF_TOKEN, hfToken)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
            }
            context.startService(intent)
        }
    }
}
