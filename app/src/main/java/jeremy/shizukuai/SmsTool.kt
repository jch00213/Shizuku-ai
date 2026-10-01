package com.jeremy.shizukuai

import android.content.Context
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager

object SmsTool {

    fun sendSms(context: Context, recipient: String, message: String): String {
        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // Split long messages automatically if needed
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(recipient, null, message, null, null)
            }

            "Success: SMS sent to $recipient"
        } catch (e: Exception) {
            "Error sending SMS: ${e.localizedMessage}"
        }
    }

    fun readRecentSms(context: Context, limit: Int = 5): String {
        return try {
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("address", "body", "date")
            val cursor = context.contentResolver.query(
                uri, 
                projection, 
                null, 
                null, 
                "date DESC LIMIT $limit"
            )

            val sb = StringBuilder()
            cursor?.use {
                val addressIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")

                var count = 1
                while (it.moveToNext()) {
                    val address = it.getString(addressIdx)
                    val body = it.getString(bodyIdx)
                    sb.append("[$count] From: $address\nMessage: $body\n\n")
                    count++
                }
            }

            if (sb.isEmpty()) "No recent SMS messages found." else sb.toString().trim()
        } catch (e: Exception) {
            "Error reading SMS: ${e.localizedMessage}"
        }
    }
}
