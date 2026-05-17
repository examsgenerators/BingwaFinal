@file:OptIn(ExperimentalMaterial3Api::class)

package com.bingwa.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// ─── Colors ───
private val DeepNavy = Color(0xFF030A16)
private val CardBg = Color(0xFF0A1626)
private val RoseGold = Color(0xFFC5A087)
private val AccentGreen = Color(0xFF39FF14)
private val StatusGreen = Color(0xFF00E676)
private val StatusRed = Color(0xFFFF1744)
private val StatusOrange = Color(0xFFFF9100)
private val TextCyan = Color(0xFF00E5FF)
private val TextGray = Color(0xFF8A99AD)

class MainActivity : ComponentActivity() {

    private val requestPhonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Phone permission is required to dial USSD", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request Phone permission on first launch
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }

        // Accessibility check
        if (!AccessibilityStatusChecker.isAccessibilityEnabled(this)) {
            Toast.makeText(this, "⚠️ Enable Accessibility Service for USSD automation", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val prefs = getSharedPreferences("DataOffers", Context.MODE_PRIVATE)
        if (prefs.getString("offers", null) == null) {
            val storage = UssdStorage(this)
            val list = storage.getLabels().map { (price, label) ->
                DataOffer(name = label, price = price.toInt(), ussdCode = storage.getUssdForAmount(price) ?: "", executionMode = "ADVANCED", mode = "daily")
            }
            prefs.edit().putString("offers", Gson().toJson(list)).apply()
        }
        startService(Intent(this, BalanceChecker::class.java))
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = RoseGold, secondary = AccentGreen, background = DeepNavy, surface = CardBg, onPrimary = Color.White, onSecondary = Color.White, onBackground = Color.White, onSurface = Color.White)) {
                BingwaApp()
            }
        }
    }
}

@Composable
fun BingwaApp() {
    val ctx = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf("Home") }
    val backStack = remember { mutableStateListOf("Home") }
    val tm = remember { TokenManager(ctx) }
    var tokenBal by remember { mutableStateOf(tm.getBalance()) }
    var airtimeBal by remember { mutableStateOf(BalanceChecker.currentBalance) }
    val appPrefs = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    var isRunning by remember { mutableStateOf(appPrefs.getBoolean("automation_enabled", true)) }

    DisposableEffect(Unit) {
        BalanceChecker.balanceCallback = { raw ->
            val nums = raw.replace(Regex("[^\\d.]"), " ").trim().split("\\s+".toRegex()).filter { it.toDoubleOrNull() != null }
            airtimeBal = if (nums.isNotEmpty()) "KES ${nums.first()}" else "KES --"
        }
        onDispose { BalanceChecker.balanceCallback = null }
    }
    LaunchedEffect(selected) { tokenBal = tm.getBalance() }

    BackHandler(enabled = backStack.size > 1) {
        backStack.removeLast()
        selected = backStack.last()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {   // containerColor removed – not available in this version
                Spacer(Modifier.height(24.dp))
                Text("Bingwa Mobile", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = RoseGold)
                Divider(color = TextGray)
                listOf(
                    "Home" to Icons.Default.Home,
                    "Tokens" to Icons.Default.Token,
                    "Contacts" to Icons.Default.Contacts,
                    "Offers" to Icons.Default.ShoppingCart,
                    "Settings" to Icons.Default.Settings
                ).forEach { (label, icon) ->
                    NavigationDrawerItem(
                        icon = { Icon(icon, label, tint = RoseGold) },
                        label = { Text(label, color = Color.White) },
                        selected = selected == label,
                        onClick = {
                            selected = label
                            backStack.add(label)
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(selectedIconColor = AccentGreen, selectedTextColor = AccentGreen)
                    )
                }
            }
        },
        content = {
            when (selected) {
                "Home" -> HomeScreen(tokenBal, airtimeBal, onRefresh = { BalanceChecker.requestBalanceCheck(ctx) }, onToggle = { isRunning = !isRunning; appPrefs.edit().putBoolean("automation_enabled", isRunning).apply(); if (!isRunning) ctx.stopService(Intent(ctx, BalanceChecker::class.java)) else ctx.startService(Intent(ctx, BalanceChecker::class.java)) }, isRunning = isRunning, openDrawer = { scope.launch { drawerState.open() } })
                "Tokens" -> TokensScreen()
                "Contacts" -> ContactsScreen()
                "Offers" -> OffersScreen()
                "Settings" -> SettingsScreen()
            }
        }
    )
}

@Composable
fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    if (enabled) {
        androidx.activity.compose.BackHandler(onBack = onBack)
    }
}

@Composable
fun HomeScreen(tokens: Int, airtime: String, onRefresh: () -> Unit, onToggle: () -> Unit, isRunning: Boolean, openDrawer: () -> Unit) {
    val ctx = LocalContext.current
    val tx = remember { loadRecentTransactions(ctx) }
    val completed = tx.count { it.status == "Success" || it.status == "Completed" }
    val pending = tx.count { it.status == "Pending" || it.status == "Processing" }
    val failed = tx.count { it.status == "Failed" || it.status == "Cancelled" }
    val rate = if (tx.isNotEmpty()) "%.1f%%".format(completed.toDouble() / tx.size * 100) else "0.0%"

    Column(Modifier.fillMaxSize().background(DeepNavy).padding(16.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("👑 BINGWA", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("— MOBILE —", color = AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        }
        Spacer(Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF1E2F47), RoundedCornerShape(16.dp))) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(progress = 0.75f, modifier = Modifier.size(40.dp), color = TextCyan, strokeWidth = 4.dp, trackColor = Color(0xFF152A42))   // Fixed: progress as Float
                    Spacer(Modifier.width(12.dp))
                    Column { Text("Airtime Balance", color = TextGray, fontSize = 12.sp); Text(airtime, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
                Box(Modifier.size(45.dp).background(Color(0xFF003366), CircleShape).border(2.dp, AccentGreen, CircleShape), contentAlignment = Alignment.Center) { Text("📱", fontSize = 20.sp) }
                Column(horizontalAlignment = Alignment.End) { Text("TOKEN UNITS", color = TextGray, fontSize = 12.sp); Text("$tokens", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("UNITS", color = TextGray, fontSize = 10.sp) }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).border(1.dp, Color(0xFF1E2F47), RoundedCornerShape(16.dp))) {
                Column(Modifier.padding(12.dp)) {
                    Text("TRANSACTION OVERVIEW", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("$rate", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text("SUCCESS RATE", color = TextGray, fontSize = 9.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = StatusGreen, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("$completed", color = StatusGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(4.dp)); Text("COMPLETED", color = TextGray, fontSize = 10.sp) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Refresh, null, tint = StatusOrange, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("$pending", color = StatusOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(4.dp)); Text("PENDING", color = TextGray, fontSize = 10.sp) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, null, tint = StatusRed, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("$failed", color = StatusRed, fontSize = 11.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(4.dp)); Text("FAILED", color = TextGray, fontSize = 10.sp) }
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).border(1.dp, Color(0xFF1E2F47), RoundedCornerShape(16.dp))) {
                Column(Modifier.padding(12.dp)) {
                    Text("BALANCE & PERF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = 0.75f, modifier = Modifier.size(65.dp), color = TextCyan, strokeWidth = 6.dp, trackColor = Color(0xFF152A42))   // Fixed: progress as Float
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("$tokens", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text("UNITS", color = TextGray, fontSize = 9.sp) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Excellent", color = TextCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, Color(0xFF1E2F47), RoundedCornerShape(16.dp))) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("RECENT TRANSACTIONS", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("View All", color = TextGray, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                if (tx.isEmpty()) { Text("No transactions yet", color = TextGray, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                else { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(tx.take(5)) { t -> TransactionRow(t) } } }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(onClick = onToggle, colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Color(0xFF8B0000) else AccentGreen), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(Color.White, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(8.dp))
                Text(if (isRunning) "STOP AUTOMATION" else "START AUTOMATION", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

// (The rest of the composables – TransactionRow, TokensScreen, ContactsScreen, OffersScreen, AddOfferDialog, SettingsScreen, etc. – are unchanged from the working version you already have. Ensure they are present in the file.)
// ─────────────────────────────────────────────────
//  Keep the existing implementations of:
//    TransactionRow, TokensScreen, ContactsScreen,
//    OffersScreen, AddOfferDialog, SettingsScreen,
//    SimDialog, SwitchRow, helpers (loadRecentTransactions, loadContacts, etc.)
// ─────────────────────────────────────────────────