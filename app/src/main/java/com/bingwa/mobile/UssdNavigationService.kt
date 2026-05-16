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
    }

    private var currentStep = 0
    private var isProcessingUssd = false
    private var lastEventTimestamp = 0L
    private var lastProcessedDialogText = ""
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    override fun onServiceConnected() { super.onServiceConnected(); Log.d(TAG, "Connected") }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEventTimestamp < 300) return  // Debounce 300ms
        lastEventTimestamp = currentTime

        val node = event.source ?: return
        val text = extractAllText(node)

        // Don't process same dialog twice
        if (text == lastProcessedDialogText) return
        lastProcessedDialogText = text

        Log.d(TAG, "USSD Screen: $text")

        // Check for airtime balance response
        if (text.contains("balance", true) || text.contains("airtime", true) || text.contains("Ksh", true)) {
            airtimeBalance = text
            balanceCallback?.invoke(text)
            BalanceChecker.currentBalance = text
            BalanceChecker.balanceCallback?.invoke(text)
            performGlobalAction(GLOBAL_ACTION_BACK)
            cleanupAndExit()
            return
        }

        // Handle phone number input dialog
        if (isPhoneNumberDialog(text)) {
            val customerNumber = getSharedPreferences("UssdPrefs", MODE_PRIVATE).getString("customerNumber", "") ?: ""
            if (customerNumber.isNotEmpty()) {
                simulateTextInput(node, customerNumber)
                return
            }
        }

        // Parse and navigate menu options
        val options = parseMenuOptions(text)
        if (options.isNotEmpty() && currentStep < options.size) {
            val option = options[currentStep]
            val button = findButtonByText(node, option)
            if (button != null) {
                button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                currentStep++
                setDialogTimeout()
            }
        } else {
            // Try OK button
            val okButton = findButtonByText(node, "OK")
            if (okButton != null) {
                okButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                cleanupAndExit()
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
                cleanupAndExit()
            }
        }
    }

    private fun isPhoneNumberDialog(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("enter mobile number") || lower.contains("enter number") ||
               lower.contains("phone number") || lower.contains("recipient") ||
               lower.contains("mobile no") || lower.contains("simu namba") ||
               lower.contains("namba")
    }

    private fun simulateTextInput(root: AccessibilityNodeInfo, text: String) {
        val inputField = findEditableField(root)
        if (inputField != null) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Log.d(TAG, "Text input: $text")
            handler.postDelayed({
                val newRoot = rootInActiveWindow
                if (newRoot != null) {
                    val okBtn = findButtonByText(newRoot, "OK") ?: findButtonByText(newRoot, "Send")
                    okBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    currentStep++
                    newRoot.recycle()
                }
            }, 500)
        }
    }

    private fun findEditableField(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findEditableField(child)
                if (result != null) return result
                child.recycle()
            }
        }
        return null
    }

    private fun parseMenuOptions(text: String): List<String> {
        val options = mutableListOf<String>()
        val regex = Regex("(\\d+)\\.\\s*([^\\d]+)")
        regex.findAll(text).forEach { options.add(it.groupValues[2].trim()) }
        if (options.isEmpty()) {
            listOf("Daily", "Weekly", "Monthly", "OK", "Cancel").forEach { if (text.contains(it, true)) options.add(it) }
        }
        return options
    }

    private fun findButtonByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.equals(text, true) == true && node.isClickable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findButtonByText(child, text)
                if (result != null) return result
                child.recycle()
            }
        }
        return null
    }

    private fun setDialogTimeout() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = Runnable {
            Log.w(TAG, "Dialog timeout - exiting")
            cleanupAndExit()
        }
        handler.postDelayed(timeoutRunnable!!, 20000)
    }

    private fun cleanupAndExit() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        currentStep = 0
        isProcessingUssd = false
        lastProcessedDialogText = ""
    }

    private fun extractAllText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        if (node.text != null) sb.append(node.text).append(" ")
        for (i in 0 until node.childCount) {
            val c = node.getChild(i)
            if (c != null) { sb.append(extractAllText(c)); c.recycle() }
        }
        return sb.toString().trim()
    }

    override fun onInterrupt() { Log.d(TAG, "Interrupted"); cleanupAndExit() }
    override fun onDestroy() { super.onDestroy(); cleanupAndExit() }
}
