package com.bingwa.mobile
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.concurrent.TimeUnit

class UnderMaintenanceRetryReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "MaintRetryReceiver"
        private const val REQUEST_CODE = 4015
        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, UnderMaintenanceRetryReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
            val triggerTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2)
            if (Build.VERSION.SDK_INT >= 23) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            else alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, UnderMaintenanceRetryReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
            alarmManager.cancel(pendingIntent)
        }
    }
    override fun onReceive(context: Context, intent: Intent?) {
        if (!context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).getBoolean("auto_retry", false)) { cancel(context); return }
        RetryWorker(context).run()
        schedule(context)
    }
}
