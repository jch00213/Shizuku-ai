package com.jeremy.shizukuai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jeremy.shizukuai.MainActivity
import com.jeremy.shizukuai.data.DownloadStatus
import com.jeremy.shizukuai.data.ModelDownloader
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder().build()
    private lateinit var notificationManager: NotificationManager
    private var downloadJob: Job? = null
    private var currentTargetFile: File? = null

    companion object {
        private const val CHANNEL_ID = "model_download_channel"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_START_DOWNLOAD = "ACTION_START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "ACTION_CANCEL_DOWNLOAD"
        const val EXTRA_URL = "EXTRA_URL"
        const val EXTRA_FILE_NAME = "EXTRA_FILE_NAME"
        const val EXTRA_HF_TOKEN = "EXTRA_HF_TOKEN"

        fun start(context: Context, url: String, fileName: String, hfToken: String? = null) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FILE_NAME, fileName)
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

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "model.bin"
                val token = intent.getStringExtra(EXTRA_HF_TOKEN)

                startForeground(NOTIFICATION_ID, buildNotification("Preparing download...", 0, 0, true))

                downloadJob?.cancel()
                downloadJob = serviceScope.launch {
                    downloadFile(url, fileName, token)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                cancelCurrentDownload()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun downloadFile(url: String, fileName: String, token: String?) {
        try {
            ModelDownloader.updateStatus(DownloadStatus.Downloading(0f, 0, -1))

            val requestBuilder = Request.Builder().url(url)
            if (!token.isNull@DownloadStatus.CanceledOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${token.trim()}")
            }

            val response = client.newCall(requestBuilder.build()).execute()

            if (!response.isSuccessful) {
                val errorMsg = when (response.code) {
                    401 -> "HTTP 401: Unauthorized. HF Token required or invalid."
                    403 -> "HTTP 403: Forbidden. You must accept terms on HuggingFace first."
                    404 -> "HTTP 404: File or model repository not found."
                    else -> "HTTP ${response.code}: Download failed"
                }
                handleError(errorMsg)
                return
            }

            val body = response.body ?: run {
                handleError("Empty response body")
                return
            }

            val totalBytes = body.contentLength()
            val targetFile = File(getExternalFilesDir(null), fileName)
            currentTargetFile = targetFile

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var downloadedBytes = 0L
                    var lastNotifTime = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        currentCoroutineContext().ensureActive()

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else 0f

                        ModelDownloader.updateStatus(
                            DownloadStatus.Downloading(progress, downloadedBytes, totalBytes)
                        )

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastNotifTime > 500) {
                            lastNotifTime = currentTime
                            val progressPercent = (progress * 100).toInt()
                            val downloadedMb = downloadedBytes / (1024 * 1024)
                            val totalMb = totalBytes / (1024 * 1024)
                            val contentText = if (totalBytes > 0) {
                                "$downloadedMb MB / $totalMb MB ($progressPercent%)"
                            } else {
                                "$downloadedMb MB downloaded"
                            }

                            notificationManager.notify(
                                NOTIFICATION_ID,
                                buildNotification("Downloading $fileName", progressPercent, 100, false, contentText)
                            )
                        }
                    }
                    output.flush()
                }
            }

            ModelDownloader.updateStatus(DownloadStatus.Success(targetFile))
            showCompletionNotification("Download complete", "Saved ${targetFile.name}")
        } catch (e: CancellationException) {
            deletePartialFile()
            ModelDownloader.updateStatus(DownloadStatus.Canceled)
            showCompletionNotification("Download canceled", "Incomplete file removed")
        } catch (e: Exception) {
            deletePartialFile()
            handleError(e.localizedMessage ?: "Download failed")
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cancelCurrentDownload() {
        downloadJob?.cancel()
        deletePartialFile()
        ModelDownloader.updateStatus(DownloadStatus.Canceled)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun deletePartialFile() {
        currentTargetFile?.let {
            if (it.exists()) {
                it.delete()
            }
        }
        currentTargetFile = null
    }

    private fun handleError(message: String) {
        ModelDownloader.updateStatus(DownloadStatus.Error(message))
        showCompletionNotification("Download failed", message)
    }

    private fun buildNotification(
        title: String,
        progress: Int,
        maxProgress: Int,
        indeterminate: Boolean,
        contentText: String = ""
    ): Notification {
        val cancelIntent = Intent(this, ModelDownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(maxProgress, progress, indeterminate)
            .setContentIntent(getPendingIntent())
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun showCompletionNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(false)
            .setContentIntent(getPendingIntent())
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live download progress for AI models"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
