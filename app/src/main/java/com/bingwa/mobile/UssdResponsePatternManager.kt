package com.bingwa.mobile

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONException

class UssdResponsePatternManager(private val context: Context) {
    companion object {
        private const val TAG = "UssdResponsePatternManager"
        private val DEFAULT_SUCCESS = listOf("You have successfully purchased", "Keep selling", "Submitted successfully", "Kindly wait")
        private val DEFAULT_FAILED = listOf("USSD failure", "insufficient airtime", "Recommendation failed")
        private val DEFAULT_MAINTENANCE = listOf("Service is currently under maintenance", "under maintenance", "please try again")
        private val DEFAULT_ALREADY_RECOMMENDED = listOf("has already been recommended today", "already been recommended")
    }
    private val prefs: SharedPreferences = context.getSharedPreferences("ussd_response_patterns", Context.MODE_PRIVATE)
    init { initializeDefaults() }
    private fun initializeDefaults() {
        if (!prefs.contains("ussd_success")) savePatterns("ussd_success", DEFAULT_SUCCESS)
        if (!prefs.contains("ussd_failed")) savePatterns("ussd_failed", DEFAULT_FAILED)
        if (!prefs.contains("ussd_maintenance")) savePatterns("ussd_maintenance", DEFAULT_MAINTENANCE)
        if (!prefs.contains("ussd_already")) savePatterns("ussd_already", DEFAULT_ALREADY_RECOMMENDED)
    }
    private fun savePatterns(key: String, patterns: List<String>) {
        val arr = JSONArray(); patterns.filter { it.isNotBlank() }.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }
    private fun getPatterns(key: String, default: List<String>): List<String> {
        val json = prefs.getString(key, null) ?: return default
        return try {
            val arr = JSONArray(json); (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
        } catch (e: JSONException) { default }
    }
    fun determineResponseStatus(response: CharSequence?): String {
        if (response == null) return "Failed"
        if (matchesMaintenance(response)) return "UnderMaintenance"
        if (matchesSuccess(response)) return "Success"
        if (matchesAlreadyRecommended(response)) return "Failed"
        return "Failed"
    }
    fun matchesSuccess(r: CharSequence?) = r != null && getPatterns("ussd_success", DEFAULT_SUCCESS).any { r.contains(it, true) }
    fun matchesFailed(r: CharSequence?) = r != null && getPatterns("ussd_failed", DEFAULT_FAILED).any { r.contains(it, true) }
    fun matchesMaintenance(r: CharSequence?) = r != null && getPatterns("ussd_maintenance", DEFAULT_MAINTENANCE).any { r.contains(it, true) }
    fun matchesAlreadyRecommended(r: CharSequence?) = r != null && getPatterns("ussd_already", DEFAULT_ALREADY_RECOMMENDED).any { r.contains(it, true) }
}
