package com.bingwa.mobile

import android.content.*
import android.telephony.SmsMessage
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MpesaReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "MpesaReceiver"
        // TOKEN PURCHASE PHONE NUMBER – change this to your real number
        private const val TOKEN_PHONE = "0700000000"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (!appPrefs.getBoolean("automation_enabled", true)) return

        val pdus = intent.extras?.get("pdus") as? Array<*> ?: return
        for (pdu in pdus) {
            val sms = SmsMessage.createFromPdu(pdu as ByteArray)
            val body = sms.messageBody ?: continue
            val sender = sms.originatingAddress ?: ""
            if (!isMpesaMessage(body, sender)) continue

            Log.d(TAG, "M-PESA SMS: ${body.take(100)}...")

            // Auto-save contact
            if (appPrefs.getBoolean("auto_save_contacts", false)) {
                val partyNumber = extractPhoneNumber(body)
                if (partyNumber.isNotEmpty()) saveContact(context, partyNumber)
            }

            // Only handle data selling (received payments)
            if (body.lowercase().contains("received")) {
                handleDataSelling(context, body)
            }
        }
    }

    private fun isMpesaMessage(body: String, sender: String): Boolean {
        return sender.equals("MPESA", true) || body.contains("M-Pesa", true) || body.contains("Mpesa", true)
    }

    // ─── TOKEN PURCHASE VIA AIRTIME (called from UI) ───
    fun buyTokensWithAirtime(context: Context, amount: Int, callback: (Boolean) -> Unit) {
        val ussdCode = "*140*$amount*$TOKEN_PHONE#"
        Log.d(TAG, "Buying tokens with airtime: $ussdCode")

        // Set callback for accessibility service to detect success/failure
        UssdNavigationService.tokenPurchaseCallback = { success ->
            if (success) {
                val tokenManager = TokenManager(context)
                val tokensToAdd = tokenManager.convertAmountToTokens(amount)
                tokenManager.addTokens(tokensToAdd)
                addTransaction(context, Transaction(
                    description = "Token Purchase (Airtime)", amount = "-KSh $amount",
                    amountValue = amount.toDouble(), date = getCurrentDate(),
                    status = TransactionStatus.SUCCESS.value
                ))
                notify(context, "Tokens Added!", "+$tokensToAdd tokens (KSh $amount airtime)")
                BalanceChecker.requestBalanceCheck(context)
            }
            callback(success)
        }

        // Dial USSD
        context.startService(Intent(context, AutomationService::class.java).apply {
            putExtra("mode", "SIMPLE")
            putExtra("code", ussdCode)
        })
    }

    // ─── DATA SELLING (unchanged) ───
    private fun handleDataSelling(context: Context, body: String) {
        val tokens = context.getSharedPreferences("TokenStore", Context.MODE_PRIVATE)
        val cur = tokens.getInt("balance", 0)
        val amount = extractAmount(body)
        val phone = extractPhoneNumber(body)

        val ussdStorage = UssdStorage(context)
        val ussdCode = ussdStorage.getUssdForAmount(amount.toDouble())
        val label = ussdStorage.getLabels()[amount.toDouble()] ?: "Data Bundle"
        val tokenCost = 1

        if (ussdCode != null && cur >= tokenCost) {
            tokens.edit().putInt("balance", cur - tokenCost).apply()
            val finalCode = ussdCode.replace("pn", phone)
            context.startService(Intent(context, AutomationService::class.java).apply {
                putExtra("mode", "ADVANCED"); putExtra("code", finalCode); putExtra("phoneNumber", phone)
            })
            addTransaction(context, Transaction(description = label, amount = "-KSh $amount",
                amountValue = amount.toDouble(), date = getCurrentDate(), status = TransactionStatus.SUCCESS.value,
                ussdCode = finalCode, phoneNumber = phone))
            notify(context, "USSD Sent", "$label for $phone")
            BalanceChecker.requestBalanceCheck(context)
        } else if (ussdCode != null) {
            addTransaction(context, Transaction(description = label, amount = "-KSh $amount",
                amountValue = amount.toDouble(), date = getCurrentDate(), status = TransactionStatus.RETRYING.value,
                ussdCode = ussdCode, phoneNumber = phone))
            addRetry(context, ussdCode.replace("pn", phone), phone)
        }
    }

    // ─── HELPERS ───
    private fun extractAmount(body: String): Int {
        val regex = Regex("Ksh\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        return regex.find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()?.toInt() ?: 0
    }
    private fun extractPhoneNumber(body: String): String {
        val regex = Regex("07\\d{8}")
        return regex.find(body)?.value ?: "Unknown"
    }
    private fun addTransaction(context: Context, tx: Transaction) {
        val prefs = context.getSharedPreferences("transactions", Context.MODE_PRIVATE)
        val list = try {
            val arr = JSONArray(prefs.getString("list", "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Transaction(o.getInt("id"), o.getString("description"), o.getString("amount"),
                    o.optDouble("amountValue", 0.0), o.getString("date"), o.getString("status"),
                    TransactionStatus.fromString(o.getString("status")))
            }.toMutableList()
        } catch (e: Exception) { mutableListOf() }
        list.add(0, tx.copy(id = list.size, timestamp = System.currentTimeMillis()))
        if (list.size > 100) list.removeAt(list.lastIndex)
        val arr = JSONArray(); list.forEach { arr.put(JSONObject().apply {
            put("id", it.id); put("description", it.description); put("amount", it.amount)
            put("amountValue", it.amountValue); put("date", it.date); put("status", it.status)
        }) }; prefs.edit().putString("list", arr.toString()).apply()
    }
    private fun addRetry(context: Context, code: String, phone: String) {
        val prefs = context.getSharedPreferences("retry_queue", Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString("list", "[]")) } catch (e: Exception) { JSONArray() }
        arr.put(JSONObject().apply { put("code", code); put("phone", phone) })
        prefs.edit().putString("list", arr.toString()).apply()
    }
    private fun saveContact(context: Context, number: String) {
        val prefs = context.getSharedPreferences("saved_contacts", Context.MODE_PRIVATE)
        val list = try { JSONArray(prefs.getString("list", "[]")) } catch (e: Exception) { JSONArray() }
        for (i in 0 until list.length()) { if (list.getJSONObject(i).getString("phone") == number) return }
        list.put(JSONObject().apply { put("name", ""); put("phone", number) })
        prefs.edit().putString("list", list.toString()).apply()
    }
    private fun getCurrentDate() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    private fun notify(context: Context, title: String, msg: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                nm.createNotificationChannel(android.app.NotificationChannel("bingwa", "Bingwa", android.app.NotificationManager.IMPORTANCE_HIGH))
            }
            nm.notify(System.currentTimeMillis().toInt(), androidx.core.app.NotificationCompat.Builder(context, "bingwa")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(msg).setAutoCancel(true).build())
        } catch (_: Exception) {}
    }
}
