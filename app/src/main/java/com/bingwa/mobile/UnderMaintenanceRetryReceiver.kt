package com.bingwa.mobile
import android.app.*; import android.content.*; import android.os.Build; import java.util.concurrent.TimeUnit
class UnderMaintenanceRetryReceiver : BroadcastReceiver() {
    companion object {
        fun schedule(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(ctx, 4015, Intent(ctx, UnderMaintenanceRetryReceiver::class.java), if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2), pi)
            else am.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2), pi)
        }
        fun cancel(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(ctx, 4015, Intent(ctx, UnderMaintenanceRetryReceiver::class.java), if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            am.cancel(pi)
        }
    }
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (!ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE).getBoolean("auto_retry", false)) { cancel(ctx); return }
        RetryWorker(ctx).run(); schedule(ctx)
    }
}
