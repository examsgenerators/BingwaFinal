package com.bingwa.mobile

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import java.util.ArrayList

class UssdResponsePatternManager(private val context: Context) {
    companion object {
        private const val TAG = "UssdResponsePatternManager"
        private val DEFAULT_SUCCESS_PATTERNS = listOf(
            "You have successfully purchased", "Kindly wait as we process your request. Thank you",
            "Kindly wait while we process your request", "Keep selling", "Submitted successfully",
            "Keep selling!!", "Keep selling!! Be a Bingwa Sokoni Champion", "Kindly wait"
        )
        private val DEFAULT_FAILED_PATTERNS = listOf(
            "USSD failure", "insufficient airtime", "Okoa Jahazi and cannot receive bundles",
            "Dear Partner", "Recommendation failed", "has already been recommended the same product today",
            "has already been recommended"
        )
        private val DEFAULT_MAINTENANCE_PATTERNS = listOf(
            "Service is currently under maintenance", "under maintenance", "please try again"
        )
        private val DEFAULT_ALREADY_RECOMMENDED_PATTERNS = listOf(
            "has already been recommended the same product today", "has already been recommended",
            "Failed. 254 has already been recommended today", "has already been recommended today", "already been recommended"
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("ussd_response_patterns", Context.MODE_PRIVATE)

    init { initializeDefaults() }

    private fun initializeDefaults() {
        if (!prefs.contains("ussd_success_patterns")) savePatterns("ussd_success_patterns", DEFAULT_SUCCESS_PATTERNS)
        if (!prefs.contains("ussd_failed_patterns")) savePatterns("ussd_failed_patterns", DEFAULT_FAILED_PATTERNS)
        if (!prefs.contains("ussd_maintenance_patterns")) savePatterns("ussd_maintenance_patterns", DEFAULT_MAINTENANCE_PATTERNS)
        if (!prefs.contains("ussd_already_recommended_patterns")) savePatterns("ussd_already_recommended_patterns", DEFAULT_ALREADY_RECOMMENDED_PATTERNS)
    }

    private fun getPatterns(key: String, default: List<String>): List<String> {
        val json = prefs.getString(key, null) ?: return default
        return try {
            val arr = JSONArray(json)
            val list = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val s = arr.getString(i)
                if (s.isNotBlank()) list.add(s)
            }
            if (list.isEmpty()) default else list
        } catch (e: JSONException) { default }
    }

    private fun savePatterns(key: String, patterns: List<String>) {
        val arr = JSONArray()
        patterns.filter { it.isNotBlank() }.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    fun determineResponseStatus(response: CharSequence?): String {
        if (response == null) return "Failed"
        if (matchesMaintenancePattern(response)) return "UnderMaintenance"
        if (matchesSuccessPattern(response)) return "Success"
        if (matchesAlreadyRecommendedPattern(response)) return "Failed"
        return "Failed"
    }

    fun matchesSuccessPattern(response: CharSequence?): Boolean {
        if (response == null) return false
        return getPatterns("ussd_success_patterns", DEFAULT_SUCCESS_PATTERNS).any { response.contains(it, true) }
    }

    fun matchesFailedPattern(response: CharSequence?): Boolean {
        if (response == null) return true
        return getPatterns("ussd_failed_patterns", DEFAULT_FAILED_PATTERNS).any { response.contains(it, true) }
    }

    fun matchesMaintenancePattern(response: CharSequence?): Boolean {
        if (response == null) return false
        return getPatterns("ussd_maintenance_patterns", DEFAULT_MAINTENANCE_PATTERNS).any { response.contains(it, true) }
    }

    fun matchesAlreadyRecommendedPattern(response: CharSequence?): Boolean {
        if (response == null) return false
        return getPatterns("ussd_already_recommended_patterns", DEFAULT_ALREADY_RECOMMENDED_PATTERNS).any { response.contains(it, true) }
    }
}
