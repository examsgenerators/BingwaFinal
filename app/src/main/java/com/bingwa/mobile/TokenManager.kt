package com.bingwa.mobile

import android.content.Context

class TokenManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("TokenStore", Context.MODE_PRIVATE)
    private val EXPIRY_KEY = "expiryTime"

    fun getBalance(): Int = prefs.getInt("balance", 0)

    fun addTokens(amount: Int) {
        prefs.edit().putInt("balance", getBalance() + amount).apply()
        // Set expiry 30 days from now
        val expiryTime = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
        prefs.edit().putLong(EXPIRY_KEY, expiryTime).apply()
    }

    fun deductTokens(amount: Int): Boolean {
        val cur = getBalance()
        return if (cur >= amount) {
            prefs.edit().putInt("balance", cur - amount).apply()
            true
        } else false
    }

    fun isSubscriptionValid(): Boolean {
        val expiryTime = prefs.getLong(EXPIRY_KEY, 0)
        val hasTokens = getBalance() > 0
        val hasValidTime = System.currentTimeMillis() < expiryTime
        return hasValidTime || hasTokens
    }

    fun convertAmountToTokens(amount: Int): Int {
        return when (amount) {
            10 -> 105
            20 -> 200
            25 -> 300
            50 -> 625
            100 -> 1100
            130 -> 1300
            200 -> 2500
            480 -> 4800
            else -> amount * 10
        }
    }
}
