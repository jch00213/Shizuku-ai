package com.jeremy.shizukuai

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

class ShizukuManager(
    private val onServiceConnected: (IRemoteAiService) -> Unit,
    private val onServiceDisconnected: () -> Unit
) {

    var remoteService: IRemoteAiService? = null
        private set

    private val REQUEST_CODE = 1001

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, RemoteAiService::javaClass.name)
    )
        .processNameSuffix("ai_service")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                remoteService = IRemoteAiService.Stub.asInterface(binder)
                remoteService?.let { onServiceConnected(it) }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remoteService = null
            onServiceDisconnected()
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
            bindService()
        }
    }

    fun registerListeners() {
        Shizuku.addRequestPermissionResultListener(permissionListener)
    }

    fun unregisterListeners() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun checkAndBind() {
        if (!isShizukuAvailable()) return

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            bindService()
        } else {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    private fun bindService() {
        if (Shizuku.getVersion() >= 10) {
            Shizuku.bindUserService(serviceArgs, connection)
        }
    }

    fun unbindService() {
        if (isShizukuAvailable() && Shizuku.getVersion() >= 10) {
            Shizuku.unbindUserService(serviceArgs, connection, true)
        }
    }
}
