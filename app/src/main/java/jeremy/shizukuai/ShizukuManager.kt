package com.jeremy.shizukuai

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

object ShizukuManager {

    private const val REQUEST_CODE = 2000

    private val _remoteService = MutableStateFlow<IRemoteAiService?>(null)
    val remoteService: StateFlow<IRemoteAiService?> = _remoteService

    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted

    private val _isShizukuAvailable = MutableStateFlow(false)
    val isShizukuAvailable: StateFlow<Boolean> = _isShizukuAvailable

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, RemoteAiService::class.java.name)
    )
        .tag("privileged_ai_service")
        .processNameSuffix("privileged_ai")
        .debuggable(BuildConfig.DEBUG)
        .version(1)

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        _isShizukuAvailable.value = true
        checkPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _isShizukuAvailable.value = false
        _remoteService.value = null
        _isPermissionGranted.value = false
    }

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            _isPermissionGranted.value = granted
            if (granted) {
                bindUserService()
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service != null && service.pingBinder()) {
                _remoteService.value = IRemoteAiService.Stub.asInterface(service)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _remoteService.value = null
        }
    }

    fun init() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)

        if (Shizuku.pingBinder()) {
            _isShizukuAvailable.value = true
            checkPermission()
        }
    }

    fun checkPermission() {
        if (!Shizuku.pingBinder()) {
            _isShizukuAvailable.value = false
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            _isPermissionGranted.value = true
            bindUserService()
        } else {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    fun bindUserService() {
        try {
            Shizuku.bindUserService(serviceArgs, serviceConnection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unbindUserService() {
        try {
            Shizuku.unbindUserService(serviceArgs, serviceConnection, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun destroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }
}
