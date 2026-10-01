package com.jeremy.shizukuai.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed interface DownloadStatus {
    object Idle : DownloadStatus
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadStatus
    data class Success(val file: File) : DownloadStatus
    data class Error(val message: String) : DownloadStatus
    object Canceled : DownloadStatus
}

object ModelDownloader {
    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus.asStateFlow()

    fun updateStatus(status: DownloadStatus) {
        _downloadStatus.value = status
    }

    fun resetStatus() {
        _downloadStatus.value = DownloadStatus.Idle
    }
}
