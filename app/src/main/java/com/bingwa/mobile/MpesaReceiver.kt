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

            // Improved M-Pesa detection
            if (!isMpesaMessage(body, sender)) continue

            Log.d(TAG, "M-PESA SMS: ${body.take(100)}...")

            val lowerBody = body.lowercase()

            // Check for TOKEN PURCHASE (sent TO Victor Ngetich)
            val isTokenPurchase = TOKEN_NAMES.any { name ->
                lowerBody.contains("sent to $name") || lowerBody.contains("transfer to $name") ||
                lowerBody.contains("paid to $name") || lowerBody.contains("send to $name")
            }
            val containsName = TOKEN_NAMES.any { lowerBody.contains(it) }

            // Save customer info to SharedPrefs for accessibility service
            saveCustomerInfo(context, body)

            // Auto-save contact if enabled
            if (appPrefs.getBoolean("auto_save_contacts", false)) {
                val partyNumber = extractPartyNumber(body)
                if (partyNumber.isNotEmpty()) saveContact(context, partyNumber)
            }

            if (isTokenPurchase || (containsName && !lowerBody.contains("received"))) {
                handleTokenPurchase(context, body)
            } else if (lowerBody.contains("received")) {
                handleDataSelling(context, body)
            }
        }
    }

    private fun isMpesaMessage(body: String, sender: String): Boolean {
        return sender.equals("MPESA", true) ||
               body.contains("M-Pesa", true) ||
               body.contains("Mpesa", true) ||
               (body.contains("Ksh", true) && (body.contains("paid to", true) || body.contains("sent to", true)))
    }

    private fun saveCustomerInfo(context: Context, body: String) {
        val phone = extractPhoneNumber(body)
        val name = extractCustomerName(body)
        val amount = extractAmount(body)
        val prefs = context.getSharedPreferences("UssdPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("customerNumber", phone)
            .putString("customerName", name)
            .putString("amount", amount.toString())
            .apply()
    }

    private fun extractCustomerName(body: String): String {
        val regex = Regex("from\\s+([A-Za-z\\s]+)", RegexOption.IGNORE_CASE)
        return regex.find(body)?.groupValues?.get(1)?.trim() ?: "Customer"
    }

    private fun handleTokenPurchase(context: Context, body: String) {
        val tokens = context.getSharedPreferences("TokenStore", Context.MODE_PRIVATE)
        val cur = tokens.getInt("balance", 0)
        val amount = extractAmount(body)
        val tokenManager = TokenManager(context)
        val tokensToAdd = tokenManager.convertAmountToTokens(amount)

        if (tokensToAdd > 0) {
            tokens.edit().putInt("balance", cur + tokensToAdd).apply()
            // Set 30-day expiry
            val expiry = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
            tokens.edit().putLong("expiryTime", expiry).apply()
            addTransaction(context, Transaction(
                description = "Token Purchase", amount = "+KSh $amount", amountValue = amount.toDouble(),
                date = getCurrentDate(), status = TransactionStatus.SUCCESS.value
            ))
            notify(context, "Tokens Added!", "+$tokensToAdd tokens (KSh $amount)")
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
            val finalCode = ussdCode.replace("pppp", phone)
            context.startService(Intent(context, AutomationService::class.java).apply {
                putExtra("mode", "ADVANCED")
                putExtra("code", finalCode)
                putExtra("phoneNumber", phone)
            })
            addTransaction(context, Transaction(
                description = label, amount = "-KSh $amount", amountValue = amount.toDouble(),
                date = getCurrentDate(), status = TransactionStatus.SUCCESS.value,
                ussdCode = finalCode, phoneNumber = phone
            ))
            notify(context, "USSD Sent", "$label for $phone")
            BalanceChecker.requestBalanceCheck(context)
        } else if (ussdCode != null) {
            addTransaction(context, Transaction(
                description = label, amount = "-KSh $amount", amountValue = amount.toDouble(),
                date = getCurrentDate(), status = TransactionStatus.RETRYING.value,
                ussdCode = ussdCode, phoneNumber = phone
            ))
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
                Transaction(o.getInt("id"), o.getString("description"), o.getString("amount"),
                    o.optDouble("amountValue", 0.0), o.getString("date"), o.getString("status"),
                    TransactionStatus.fromString(o.getString("status")),
                    o.optString("ussdCode", ""), o.optString("phoneNumber", ""),
                    o.optString("response", ""), o.optLong("timestamp", 0))
            }.toMutableList()
        } catch (e: Exception) { mutableListOf() }
        list.add(0, transaction.copy(id = list.size, timestamp = System.currentTimeMillis()))
        if (list.size > 100) list.removeAt(list.lastIndex)
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().apply {
            put("id", it.id); put("description", it.description); put("amount", it.amount)
            put("amountValue", it.amountValue); put("date", it.date); put("status", it.status)
            put("ussdCode", it.ussdCode); put("phoneNumber", it.phoneNumber)
            put("response", it.response); put("timestamp", it.timestamp)
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
        return regex.find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()?.toInt() ?: 0
    }
    private fun extractPhoneNumber(body: String): String {
        val regex = Regex("07\\d{8}")
        val match = regex.find(body)
        if (match != null) return match.value
        val regex10 = Regex("\\d{10}")
        return regex10.find(body)?.value ?: "Unknown"
    }
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
