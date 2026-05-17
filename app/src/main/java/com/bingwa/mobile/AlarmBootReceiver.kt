package com.bingwa.mobile
import android.content.*; import android.util.Log
class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("automation_enabled", true)) ctx.startService(Intent(ctx, BalanceChecker::class.java))
        if (prefs.getBoolean("auto_retry", false)) UnderMaintenanceRetryReceiver.schedule(ctx)
    }
}
