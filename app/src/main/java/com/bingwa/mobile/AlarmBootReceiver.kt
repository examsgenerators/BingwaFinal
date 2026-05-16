package com.bingwa.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class AlarmBootReceiver : BroadcastReceiver() {
    companion object { private const val TAG = "AlarmBootReceiver" }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d(TAG, "Device booted – rescheduling services")
        try {
            val appPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            if (appPrefs.getBoolean("automation_enabled", true)) {
                context.startService(Intent(context, BalanceChecker::class.java))
            }
            if (appPrefs.getBoolean("auto_retry", false)) {
                UnderMaintenanceRetryReceiver.schedule(context)
            }
        } catch (e: Exception) { Log.e(TAG, "Error on boot: ${e.message}") }
    }
}
