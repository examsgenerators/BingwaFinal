package com.bingwa.mobile

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log

class BalanceChecker : Service() {
    companion object {
        private const val TAG = "BalanceChecker"
        var currentBalance = "KSh --"
        var balanceCallback: ((String) -> Unit)? = null
        fun requestBalanceCheck(context: Context) {
            try {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:*144%23"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: SecurityException) { Log.e(TAG, "Permission denied") }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?) = null
}
