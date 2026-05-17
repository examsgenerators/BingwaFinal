package com.bingwa.mobile

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log

class AutomationService : Service() {
    companion object { private const val TAG = "AutomationService" }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ussdCode = intent?.getStringExtra("code") ?: return START_NOT_STICKY
        val mode = intent.getStringExtra("mode") ?: "SIMPLE"
        Log.d(TAG, "Mode: $mode, Code: $ussdCode")
        if (mode.equals("SIMPLE", true)) dial(ussdCode + Uri.encode("#"))
        else dial(ussdCode.split("*").firstOrNull { it.isNotEmpty() }?.let { it + Uri.encode("#") } ?: (ussdCode + Uri.encode("#")))
        return START_NOT_STICKY
    }
    private fun dial(fullCode: String) {
        try {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$fullCode"))
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(callIntent)
        } catch (e: SecurityException) { Log.e(TAG, "Permission denied") }
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
