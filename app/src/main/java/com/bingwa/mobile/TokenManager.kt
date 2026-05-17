package com.bingwa.mobile

import android.content.Context

class TokenManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("TokenStore", Context.MODE_PRIVATE)
    private val EXPIRY_KEY = "expiryTime"

    fun getBalance(): Int = prefs.getInt("balance", 0)
    fun addTokens(amount: Int) {
        prefs.edit().putInt("balance", getBalance() + amount).apply()
        val expiryTime = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
        prefs.edit().putLong(EXPIRY_KEY, expiryTime).apply()
    }
    fun deductTokens(amount: Int): Boolean {
        val cur = getBalance()
        return if (cur >= amount) { prefs.edit().putInt("balance", cur - amount).apply(); true } else false
    }
    fun isSubscriptionValid(): Boolean {
        val expiryTime = prefs.getLong(EXPIRY_KEY, 0)
        return getBalance() > 0 || System.currentTimeMillis() < expiryTime
    }
    fun convertAmountToTokens(amount: Int): Int = when (amount) {
        5 -> 50; 10 -> 105; 20 -> 200; 25 -> 300; 50 -> 625; 100 -> 1100; 130 -> 1300; 200 -> 2500; 480 -> 4800
        else -> amount * 10
    }
    fun getTokenPrice(units: Int): Int = when {
        units <= 50 -> 5; units <= 105 -> 10; units <= 200 -> 20; units <= 300 -> 25
        units <= 625 -> 50; units <= 1100 -> 100; units <= 1300 -> 130; units <= 2500 -> 200; else -> 480
    }
}
