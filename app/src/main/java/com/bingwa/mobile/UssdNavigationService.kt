package com.bingwa.mobile

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import android.util.Log

class UssdNavigationService : AccessibilityService() {
    companion object {
        const val TAG = "UssdNavigation"
        var airtimeBalance = "N/A"
        var balanceCallback: ((String) -> Unit)? = null
    }
    private var steps = arrayOf("6", "1")
    private var currentStep = 0

    override fun onServiceConnected() { super.onServiceConnected(); Log.d(TAG, "Connected") }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val node = event.source ?: return
        val text = extractAllText(node)
        if (text.contains("balance", true) || text.contains("airtime", true) || text.contains("Ksh", true)) {
            airtimeBalance = text
            balanceCallback?.invoke(text)
            BalanceChecker.currentBalance = text
            BalanceChecker.balanceCallback?.invoke(text)
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }
        val inputs = node.findAccessibilityNodeInfosByViewId("com.android.phone:id/input_field")
        if (inputs != null && inputs.isNotEmpty() && currentStep < steps.size) {
            val args = Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, steps[currentStep])
            inputs[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            node.findAccessibilityNodeInfosByText("Send").forEach { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
            currentStep++
        } else if (currentStep >= steps.size) { currentStep = 0; performGlobalAction(GLOBAL_ACTION_BACK) }
    }
    private fun extractAllText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder(); if (node.text != null) sb.append(node.text).append(" ")
        for (i in 0 until node.childCount) { val c = node.getChild(i); if (c != null) { sb.append(extractAllText(c)); c.recycle() } }
        return sb.toString().trim()
    }
    override fun onInterrupt() { Log.d(TAG, "Interrupted") }
}
