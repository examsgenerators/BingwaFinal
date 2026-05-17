package com.bingwa.mobile
import android.content.Context; import android.content.SharedPreferences; import org.json.JSONArray; import org.json.JSONException

class UssdResponsePatternManager(ctx: Context) {
    private val prefs: SharedPreferences = ctx.getSharedPreferences("ussd_patterns", Context.MODE_PRIVATE)
    private val defSuccess = listOf("You have successfully purchased", "Keep selling", "Submitted successfully", "You have transferred")
    private val defFailed = listOf("USSD failure", "insufficient airtime", "Recommendation failed")
    private val defMaint = listOf("under maintenance", "please try again")
    private val defAlready = listOf("already been recommended today", "already been recommended")
    init { listOf("s" to defSuccess, "f" to defFailed, "m" to defMaint, "a" to defAlready).forEach { (k,v) -> if (!prefs.contains(k)) { val a = JSONArray(); v.forEach { a.put(it) }; prefs.edit().putString(k, a.toString()).apply() } } }
    private fun get(k: String, d: List<String>): List<String> = try { val a = JSONArray(prefs.getString(k, "[]")); (0 until a.length()).map { a.getString(it) } } catch (e: JSONException) { d }
    fun determine(r: CharSequence?): String = when { r == null -> "Failed"; matches(r, "m", defMaint) -> "UnderMaintenance"; matches(r, "s", defSuccess) -> "Success"; matches(r, "a", defAlready) -> "Failed"; else -> "Failed" }
    private fun matches(r: CharSequence, k: String, d: List<String>) = get(k, d).any { r.contains(it, true) }
}
