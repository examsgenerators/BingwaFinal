package com.bingwa.mobile

import android.content.Context
import android.provider.Settings
import android.util.Log

object AccessibilityStatusChecker {
    private const val TAG = "AccessibilityStatusChecker"
    fun isAccessibilityEnabled(context: Context): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            enabledServices?.contains(context.packageName) == true
        } catch (e: Exception) { Log.e(TAG, "Error: ${e.message}"); false }
    }
    fun getStatusMessage(context: Context): String {
        return if (isAccessibilityEnabled(context)) "✅ Active" else "❌ Required"
    }
}
