package com.bingwa.mobile

import android.content.Context

class TokenManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("TokenStore", Context.MODE_PRIVATE)
    fun getBalance() = prefs.getInt("balance", 0)
    fun addTokens(amount: Int) { prefs.edit().putInt("balance", getBalance() + amount).apply() }
    fun deductTokens(amount: Int): Boolean {
        val cur = getBalance()
        return if (cur >= amount) { prefs.edit().putInt("balance", cur - amount).apply(); true } else false
    }
}
