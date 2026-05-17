package com.bingwa.mobile

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class UssdNavigationService : AccessibilityService() {
    companion object {
        const val TAG = "UssdNavigation"
        var airtimeBalance = "N/A"
        var balanceCallback: ((String) -> Unit)? = null
        var tokenPurchaseCallback: ((Boolean) -> Unit)? = null
    }
    private var currentStep = 0
    private var lastEventTimestamp = 0L
    private var lastProcessedDialogText = ""
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() { super.onServiceConnected(); Log.d(TAG, "Connected") }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEventTimestamp < 300) return
        lastEventTimestamp = currentTime

        val node = event.source ?: return
        val text = extractAllText(node)
        if (text == lastProcessedDialogText) return
        lastProcessedDialogText = text
        Log.d(TAG, "USSD Screen: $text")

        // Check for token purchase success (airtime transfer confirmation)
        if (text.contains("You have transferred", true) || text.contains("successfully", true) ||
            text.contains("transfer", true) && text.contains("Ksh", true)) {
            tokenPurchaseCallback?.invoke(true)
            performGlobalAction(GLOBAL_ACTION_BACK)
            cleanup()
            return
        }

        // Check for token purchase failure
        if (text.contains("insufficient", true) || text.contains("failed", true) || text.contains("error", true)) {
            tokenPurchaseCallback?.invoke(false)
            performGlobalAction(GLOBAL_ACTION_BACK)
            cleanup()
            return
        }

        // Check for airtime balance response
        if (text.contains("balance", true) || text.contains("airtime", true) || text.contains("Ksh", true)) {
            airtimeBalance = text
            balanceCallback?.invoke(text)
            BalanceChecker.currentBalance = text
            BalanceChecker.balanceCallback?.invoke(text)
            performGlobalAction(GLOBAL_ACTION_BACK)
            cleanup()
            return
        }

        // Parse and navigate menu options
        val options = parseMenuOptions(text)
        if (options.isNotEmpty() && currentStep < options.size) {
            val button = findButtonByText(node, options[currentStep])
            if (button != null) { button.performAction(AccessibilityNodeInfo.ACTION_CLICK); currentStep++ }
        } else {
            val okButton = findButtonByText(node, "OK")
            if (okButton != null) { okButton.performAction(AccessibilityNodeInfo.ACTION_CLICK); cleanup() }
            else { performGlobalAction(GLOBAL_ACTION_BACK); cleanup() }
        }
    }

    private fun cleanup() { currentStep = 0; lastProcessedDialogText = "" }

    private fun parseMenuOptions(text: String): List<String> {
        val options = mutableListOf<String>()
        val regex = Regex("(\\d+)\\.\\s*([^\\d]+)")
        regex.findAll(text).forEach { options.add(it.groupValues[2].trim()) }
        if (options.isEmpty()) listOf("Daily", "Weekly", "Monthly", "OK", "Cancel").forEach { if (text.contains(it, true)) options.add(it) }
        return options
    }

    private fun findButtonByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.equals(text, true) == true && node.isClickable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) { val r = findButtonByText(child, text); if (r != null) return r; child.recycle() }
        }
        return null
    }

    private fun extractAllText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder(); if (node.text != null) sb.append(node.text).append(" ")
        for (i in 0 until node.childCount) { val c = node.getChild(i); if (c != null) { sb.append(extractAllText(c)); c.recycle() } }
        return sb.toString().trim()
    }

    override fun onInterrupt() { cleanup() }
    override fun onDestroy() { super.onDestroy(); cleanup() }
}
