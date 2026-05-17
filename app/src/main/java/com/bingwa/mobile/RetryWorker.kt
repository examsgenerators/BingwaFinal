package com.bingwa.mobile
import android.content.*; import org.json.JSONArray
class RetryWorker(private val ctx: Context) : Runnable {
    override fun run() {
        val arr = try { JSONArray(ctx.getSharedPreferences("retry_queue", Context.MODE_PRIVATE).getString("list", "[]")) } catch (e: Exception) { JSONArray() }
        if (arr.length() == 0) return
        val cur = ctx.getSharedPreferences("TokenStore", Context.MODE_PRIVATE).getInt("balance", 0)
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (cur >= 1) {
                ctx.getSharedPreferences("TokenStore", Context.MODE_PRIVATE).edit().putInt("balance", cur - 1).apply()
                ctx.startService(Intent(ctx, AutomationService::class.java).apply { putExtra("code", o.getString("code")); putExtra("mode", "ADVANCED"); putExtra("phoneNumber", o.getString("phone")) })
            } else { newArr.put(o) }
        }
        ctx.getSharedPreferences("retry_queue", Context.MODE_PRIVATE).edit().putString("list", newArr.toString()).apply()
    }
}
