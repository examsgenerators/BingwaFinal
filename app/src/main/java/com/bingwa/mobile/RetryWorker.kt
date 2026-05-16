package com.bingwa.mobile

import android.content.Context
import android.content.Intent
import org.json.JSONArray

class RetryWorker(private val context: Context) : Runnable {
    override fun run() {
        val prefs = context.getSharedPreferences("retry_queue", Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString("list", "[]")) } catch (e: Exception) { JSONArray() }
        if (arr.length() == 0) return
        val tokens = context.getSharedPreferences("TokenStore", Context.MODE_PRIVATE)
        val cur = tokens.getInt("balance", 0)
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (cur >= 1) {
                tokens.edit().putInt("balance", cur - 1).apply()
                context.startService(Intent(context, AutomationService::class.java).apply {
                    putExtra("code", obj.getString("code"))
                    putExtra("mode", "ADVANCED")
                    putExtra("phoneNumber", obj.getString("phone"))
                })
            } else { newArr.put(obj) }
        }
        prefs.edit().putString("list", newArr.toString()).apply()
    }
}
