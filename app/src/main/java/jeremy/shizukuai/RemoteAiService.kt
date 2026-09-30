package com.jeremy.shizukuai

import android.os.Process
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

class RemoteAiService : IRemoteAiService.Stub() {

    override fun execCommand(command: String?): String {
        if (command.isNull_orEmpty()) return "Error: Empty command"
        
        return try {
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            while (errorReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            process.waitFor()
            output.toString().ifEmpty { "Command executed with exit code: ${process.exitValue()}" }
        } catch (e: Exception) {
            "Error executing shell command: ${e.message}"
        }
    }

    override fun dumpUiHierarchy(): String {
        return try {
            val process = Runtime.getRuntime().exec("uiautomator dump /sdcard/window_dump.xml")
            process.waitFor()
            val catProcess = Runtime.getRuntime().exec("cat /sdcard/window_dump.xml")
            val reader = BufferedReader(InputStreamReader(catProcess.inputStream))
            val xml = reader.readText()
            catProcess.waitFor()
            
            // Clean up temporary dump file
            Runtime.getRuntime().exec("rm /sdcard/window_dump.xml")
            xml
        } catch (e: Exception) {
            "Error dumping UI hierarchy: ${e.message}"
        }
    }

    override fun injectTap(x: Int, y: Int) {
        try {
            Runtime.getRuntime().exec("input tap $x $y")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Required by Shizuku for service teardown (Transaction code 16777114 / 0x00FFFFFF)
    override fun destroy() {
        exitProcess(0)
    }
}
