package com.bingwa.mobile
import android.content.Context; import android.provider.Settings; import android.util.Log
object AccessibilityStatusChecker {
    fun isAccessibilityEnabled(ctx: Context): Boolean = try {
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains(ctx.packageName) == true
    } catch (e: Exception) { false }
}
