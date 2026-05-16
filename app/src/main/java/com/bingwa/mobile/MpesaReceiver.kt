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
        private val TOKEN_NAMES = listOf("victor ngetich", "victor kiplangat ngetich")
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (!appPrefs.getBoolean("automation_enabled", true)) return

        val pdus = intent.extras?.get("pdus") as? Array<*> ?: return
        for (pdu in pdus) {
            val sms = SmsMessage.createFromPdu(pdu as ByteArray)
            val body = sms.messageBody ?: continue
            val sender = sms.originatingAddress ?: ""
            if (!sender.equals("MPESA", true)) continue

            val lower = body.lowercase()
            val isTokenPurchase = TOKEN_NAMES.any { lower.contains(it) }

            if (appPrefs.getBoolean("auto_save_contacts", false)) {
                val partyNumber = extractPartyNumber(body)
                if (partyNumber.isNotEmpty()) saveContact(context, partyNumber)
            }

            if (isTokenPurchase) handleTokenPurchase(context, body)
            else if (lower.contains("received")) handleDataSelling(context, body)
        }
    }

    private fun handleTokenPurchase(context: Context, body: String) {
        val tokens = context.getSharedPreferences("TokenStore", Context.MODE_PRIVATE)
        val cur = tokens.getInt("balance", 0)
        val amount = extractAmount(body)
        val tokensToAdd = when { amount >= 100 -> 1000; amount >= 50 -> 500; amount >= 10 -> 90; else -> 0 }
        if (tokensToAdd > 0) {
            tokens.edit().putInt("balance", cur + tokensToAdd).apply()
            addTransaction(context, Transaction("Token Purchase", "+KSh $amount", getCurrentDate(), "Completed"))
            notify(context, "Tokens Added", "+$tokensToAdd tokens (KSh $amount)")
            BalanceChecker.requestBalanceCheck(context)
        }
    }

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
            context.startService(Intent(context, AutomationService::class.java).apply {
                putExtra("mode", "ADVANCED")
                putExtra("code", ussdCode.replace("pppp", phone))
                putExtra("phoneNumber", phone)
            })
            addTransaction(context, Transaction(label, "-KSh $amount", getCurrentDate(), "Completed"))
            notify(context, "USSD Sent", "$label for $phone")
            BalanceChecker.requestBalanceCheck(context)
        } else if (ussdCode != null) {
            addTransaction(context, Transaction(label, "-KSh $amount", getCurrentDate(), "Failed"))
            addRetry(context, ussdCode.replace("pppp", phone), phone)
            notify(context, "Insufficient Tokens", "Will retry later")
        }
    }

    private fun addTransaction(context: Context, transaction: Transaction) {
        val prefs = context.getSharedPreferences("transactions", Context.MODE_PRIVATE)
        val list = try {
            val arr = JSONArray(prefs.getString("list", "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Transaction(o.getString("description"), o.getString("amount"), o.getString("date"), o.getString("status"))
            }.toMutableList()
        } catch (e: Exception) { mutableListOf() }
        list.add(0, transaction)
        if (list.size > 100) list.removeAt(list.lastIndex)
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().apply {
            put("description", it.description); put("amount", it.amount); put("date", it.date); put("status", it.status)
        }) }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    private fun addRetry(context: Context, code: String, phone: String) {
        val prefs = context.getSharedPreferences("retry_queue", Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString("list", "[]")) } catch (e: Exception) { JSONArray() }
        arr.put(JSONObject().apply { put("code", code); put("phone", phone) })
        prefs.edit().putString("list", arr.toString()).apply()
    }

    private fun extractAmount(body: String): Int {
        val regex = Regex("Ksh\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        val match = regex.find(body) ?: return 0
        return match.groupValues[1].replace(",", "").toDoubleOrNull()?.toInt() ?: 0
    }
    private fun extractPhoneNumber(body: String) = Regex("07\\d{8}").find(body)?.value ?: ""
    private fun extractPartyNumber(body: String): String {
        val regexFrom = Regex("from\\s*(07\\d{8})", RegexOption.IGNORE_CASE)
        val regexTo = Regex("to\\s*(07\\d{8})", RegexOption.IGNORE_CASE)
        return regexFrom.find(body)?.groupValues?.get(1) ?: regexTo.find(body)?.groupValues?.get(1) ?: ""
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
