no@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    // Back press handling
    BackHandler(enabled = backStack.size > 1) {
        backStack.removeLast()
        selected = backStack.last()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
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

// ─── HOME SCREEN (New Dark UI) ───
@Composable
fun HomeScreen(tokens: Int, airtime: String, onRefresh: () -> Unit, onToggle: () -> Unit, isRunning: Boolean, openDrawer: () -> Unit) {
    val ctx = LocalContext.current
    val tx = remember { loadRecentTransactions(ctx) }
    val completed = tx.count { it.status == "Success" || it.status == "Completed" }
    val pending = tx.count { it.status == "Pending" || it.status == "Processing" }
    val failed = tx.count { it.status == "Failed" || it.status == "Cancelled" }
    val rate = if (tx.isNotEmpty()) "%.1f%%".format(completed.toDouble() / tx.size * 100) else "0.0%"

    Column(Modifier.fillMaxSize().background(DeepNavy).padding(16.dp)) {
        // Header
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("👑 BINGWA", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("— MOBILE —", color = AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        }
        Spacer(Modifier.height(16.dp))

        // Top Summary Card
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF1E2F47), RoundedCornerShape(16.dp))) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(progress = { 0.75f }, modifier = Modifier.size(40.dp), color = TextCyan, strokeWidth = 4.dp, trackColor = Color(0xFF152A42))
                    Spacer(Modifier.width(12.dp))
                    Column { Text("Airtime Balance", color = TextGray, fontSize = 12.sp); Text(airtime, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
                Box(Modifier.size(45.dp).background(Color(0xFF003366), CircleShape).border(2.dp, AccentGreen, CircleShape), contentAlignment = Alignment.Center) { Text("📱", fontSize = 20.sp) }
                Column(horizontalAlignment = Alignment.End) { Text("TOKEN UNITS", color = TextGray, fontSize = 12.sp); Text("$tokens", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("UNITS", color = TextGray, fontSize = 10.sp) }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Stats Grid
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
                        CircularProgressIndicator(progress = { 0.75f }, modifier = Modifier.size(65.dp), color = TextCyan, strokeWidth = 6.dp, trackColor = Color(0xFF152A42))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("$tokens", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text("UNITS", color = TextGray, fontSize = 9.sp) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Excellent", color = TextCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Recent Transactions
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

        // Stop Automation Button
        Button(onClick = onToggle, colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Color(0xFF8B0000) else AccentGreen), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(Color.White, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(8.dp))
                Text(if (isRunning) "STOP AUTOMATION" else "START AUTOMATION", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun TransactionRow(tx: Transaction) {
    val isSuccess = tx.status == "Success" || tx.status == "Completed"
    val rowBg = if (isSuccess) Color(0xFF071E1A) else Color(0xFF220C14)
    val tint = if (isSuccess) StatusGreen else StatusRed
    Row(Modifier.fillMaxWidth().background(rowBg, RoundedCornerShape(10.dp)).border(0.5.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(10.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(Color(0xFF152A42), CircleShape).border(1.dp, tint, CircleShape), contentAlignment = Alignment.Center) { Text(tx.description.take(2).uppercase(), color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text(tx.description, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text(tx.date, color = TextGray, fontSize = 11.sp) }
        Column(horizontalAlignment = Alignment.End) {
            Text(tx.amount, color = if (tx.amount.startsWith("+")) StatusGreen else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.background(if (isSuccess) Color(0xFF003821) else Color(0xFF4A0E17), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) { Text(tx.status, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// ─── TOKENS SCREEN (Buy with Airtime) ───
@Composable
fun TokensScreen() {
    val ctx = LocalContext.current
    val tm = remember { TokenManager(ctx) }
    var balance by remember { mutableStateOf(tm.getBalance()) }
    var showBuyDialog by remember { mutableStateOf(false) }
    var buyAmount by remember { mutableStateOf("10") }

    Column(Modifier.fillMaxSize().background(DeepNavy).padding(16.dp)) {
        Text("TOKEN UNITS", color = TextGray, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text("$balance", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text("UNITS", color = TextGray, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        // Token packages
        val packages = listOf(
            Triple("5", "50 units", "KSh 5"),
            Triple("10", "105 units", "KSh 10"),
            Triple("20", "200 units", "KSh 20"),
            Triple("50", "625 units", "KSh 50"),
            Triple("100", "1100 units", "KSh 100"),
            Triple("200", "2500 units", "KSh 200")
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(packages) { (amt, units, price) ->
                Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF1E2F47), RoundedCornerShape(16.dp)).clickable { buyAmount = amt; showBuyDialog = true }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text(units, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Price: $price airtime", color = TextGray, fontSize = 12.sp) }
                        Icon(Icons.Default.KeyboardArrowRight, null, tint = RoseGold)
                    }
                }
            }
        }
    }

    if (showBuyDialog) {
        AlertDialog(
            onDismissRequest = { showBuyDialog = false },
            title = { Text("Buy $buyAmount KSh Tokens", color = Color.White) },
            text = { Text("This will dial *140*$buyAmount*PHONE# to transfer airtime and credit tokens.", color = TextGray) },
            confirmButton = {
                Button(onClick = {
                    showBuyDialog = false
                    val receiver = MpesaReceiver()
                    receiver.buyTokensWithAirtime(ctx, buyAmount.toIntOrNull() ?: 10) { success ->
                        if (success) { balance = tm.getBalance(); Toast.makeText(ctx, "Tokens added!", Toast.LENGTH_SHORT).show() }
                        else Toast.makeText(ctx, "Purchase failed. Check airtime.", Toast.LENGTH_LONG).show()
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showBuyDialog = false }) { Text("Cancel", color = TextGray) } }
        )
    }
}

// ─── CONTACTS SCREEN ───
@Composable
fun ContactsScreen() {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("saved_contacts", Context.MODE_PRIVATE)
    var contacts by remember { mutableStateOf(loadContacts(prefs)) }
    Column(Modifier.fillMaxSize().background(DeepNavy).padding(16.dp)) {
        Text("Saved Contacts", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(contacts, key = { it.phone }) { c ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text(c.phone, color = Color.White, fontWeight = FontWeight.Medium); if (c.name.isNotBlank()) Text(c.name, color = TextGray) }
                        IconButton(onClick = { contacts = contacts.toMutableList().also { it.remove(c) }; saveContacts(prefs, contacts) }) { Icon(Icons.Default.Delete, "Delete", tint = StatusRed) }
                    }
                }
            }
        }
    }
}

// ─── OFFERS SCREEN (New Rose Gold UI) ───
@Composable
fun OffersScreen() {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("DataOffers", Context.MODE_PRIVATE)
    val gson = remember { Gson() }
    var offers by remember { mutableStateOf(try { gson.fromJson<List<DataOffer>>(prefs.getString("offers", "[]")!!, object : TypeToken<List<DataOffer>>() {}.type) } catch (_: Exception) { emptyList() }) }
    var showDialog by remember { mutableStateOf(false) }
    var editOffer by remember { mutableStateOf<DataOffer?>(null) }

    Column(Modifier.fillMaxSize().background(DeepNavy).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Data Plans Mastery", color = RoseGold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { editOffer = null; showDialog = true }) { Icon(Icons.Default.Add, null, tint = RoseGold) }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(offers) { idx, offer ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF162534)), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().border(0.5.dp, RoseGold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(offer.name, color = RoseGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("USSD: ${offer.ussdCode}", color = TextGray, fontSize = 12.sp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Selling: KSh ${offer.price}", color = RoseGold, fontSize = 14.sp)
                                    Text(offer.executionMode, color = TextGray, fontSize = 12.sp)
                                }
                                Text(offer.mode, color = TextGray, fontSize = 11.sp)
                            }
                            Row {
                                IconButton(onClick = { editOffer = offer; showDialog = true }) { Icon(Icons.Default.Edit, "Edit", tint = RoseGold) }
                                IconButton(onClick = { offers = offers.toMutableList().also { it.removeAt(idx) }; prefs.edit().putString("offers", gson.toJson(offers)).apply() }) { Icon(Icons.Default.Delete, "Delete", tint = StatusRed) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDialog) { AddOfferDialog(editOffer, onSave = { offer -> offers = offers.toMutableList().also { list -> val i = list.indexOfFirst { it.name == offer.name }; if (i >= 0) list[i] = offer else list.add(offer) }; prefs.edit().putString("offers", gson.toJson(offers)).apply(); showDialog = false }, onDismiss = { showDialog = false }) }
}

@Composable
fun AddOfferDialog(existing: DataOffer?, onSave: (DataOffer) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var price by remember { mutableStateOf(existing?.price?.toString() ?: "") }
    var code by remember { mutableStateOf(existing?.ussdCode ?: "") }
    var execMode by remember { mutableStateOf(existing?.executionMode ?: "SIMPLE") }
    var mode by remember { mutableStateOf(existing?.mode ?: "daily") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing != null) "Edit Offer" else "Add Offer", color = Color.White) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }); OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("USSD code") }); Row(verticalAlignment = Alignment.CenterVertically) { Text("Execution: ", color = Color.White); FilterChip(selected = execMode == "SIMPLE", onClick = { execMode = "SIMPLE" }, label = { Text("SIMPLE") }); Spacer(Modifier.width(4.dp)); FilterChip(selected = execMode == "ADVANCED", onClick = { execMode = "ADVANCED" }, label = { Text("ADVANCED") }) }; Row(verticalAlignment = Alignment.CenterVertically) { Text("Mode: ", color = Color.White); listOf("daily", "weekly", "monthly").forEach { FilterChip(selected = mode == it, onClick = { mode = it }, label = { Text(it) }); Spacer(Modifier.width(4.dp)) } } } }, confirmButton = { Button(onClick = { val p = price.toIntOrNull() ?: 0; if (p > 0 && code.isNotBlank()) onSave(DataOffer(name.ifBlank { "New" }, p, code, execMode, mode)) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

// ─── SETTINGS SCREEN ───
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var auto by remember { mutableStateOf(prefs.getBoolean("automation_enabled", true)) }
    var saveContacts by remember { mutableStateOf(prefs.getBoolean("auto_save_contacts", false)) }
    var autoRetry by remember { mutableStateOf(prefs.getBoolean("auto_retry", false)) }
    var simId by remember { mutableStateOf(prefs.getInt("selected_sim_id", -1)) }
    var showSimDlg by remember { mutableStateOf(false) }
    val simList = remember { getAvailableSims(ctx) }
    val simLabel = if (simId == -1) "Default" else simList.find { it.subscriptionId == simId }?.displayName?.toString() ?: "Unknown"
    val accEnabled = remember { AccessibilityStatusChecker.isAccessibilityEnabled(ctx) }

    Column(Modifier.fillMaxSize().background(DeepNavy).padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Settings", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
            Column {
                SwitchRow("Enable Automation", "SMS & USSD processing", Icons.Default.PowerSettingsNew, auto) { auto = it; prefs.edit().putBoolean("automation_enabled", it).apply(); if (it) ctx.startService(Intent(ctx, BalanceChecker::class.java)) else ctx.stopService(Intent(ctx, BalanceChecker::class.java)) }
                Divider(color = TextGray, modifier = Modifier.padding(horizontal = 16.dp))
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.SimCard, null, tint = RoseGold, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("SIM for USSD", color = Color.White, fontWeight = FontWeight.Medium); Text(simLabel, color = TextGray) }; TextButton(onClick = { showSimDlg = true }) { Text("Change", color = RoseGold) } }
                Divider(color = TextGray, modifier = Modifier.padding(horizontal = 16.dp))
                SwitchRow("Auto-save contacts", "From M-PESA messages", Icons.Default.Contacts, saveContacts) { saveContacts = it; prefs.edit().putBoolean("auto_save_contacts", it).apply() }
                Divider(color = TextGray, modifier = Modifier.padding(horizontal = 16.dp))
                SwitchRow("Auto-retry failed", "Every 2 minutes", Icons.Default.RestartAlt, autoRetry) { autoRetry = it; prefs.edit().putBoolean("auto_retry", it).apply(); if (it) UnderMaintenanceRetryReceiver.schedule(ctx) else UnderMaintenanceRetryReceiver.cancel(ctx) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
            Row(Modifier.fillMaxWidth().padding(16.dp).clickable { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, verticalAlignment = Alignment.CenterVertically) { Icon(if (accEnabled) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (accEnabled) StatusGreen else StatusOrange, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Accessibility Service", color = Color.White, fontWeight = FontWeight.Medium); Text(if (accEnabled) "✅ Active" else "❌ Required – tap to enable", color = if (accEnabled) StatusGreen else StatusOrange, fontSize = 13.sp) } }
        }
        Spacer(Modifier.height(12.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) { Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Bingwa Mobile v3.0", color = Color.White, fontWeight = FontWeight.Bold); Text("Powered by Victor Ngetich", color = RoseGold, fontSize = 12.sp) } }
    }
    if (showSimDlg) SimDialog(simList, simId, { simId = it; prefs.edit().putInt("selected_sim_id", it).apply() }, { showSimDlg = false })
}

@SuppressLint("MissingPermission")
fun getAvailableSims(ctx: Context): List<SubscriptionInfo> {
    if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return emptyList()
    return if (Build.VERSION.SDK_INT >= 22) (ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager).activeSubscriptionInfoList ?: emptyList() else emptyList()
}

@Composable
fun SimDialog(list: List<SubscriptionInfo>, cur: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Select SIM", color = Color.White) }, text = { if (list.isEmpty()) Text("No SIMs found or permission missing.", color = TextGray) else Column { list.forEach { sim -> Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = sim.subscriptionId == cur, onClick = { onSelect(sim.subscriptionId) }); Spacer(Modifier.width(8.dp)); Text("${sim.displayName} (Slot ${sim.simSlotIndex+1})", color = Color.White) } } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
fun SwitchRow(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(40.dp).clip(CircleShape).background(CardBg), contentAlignment = Alignment.Center) { Icon(icon, title, tint = RoseGold, modifier = Modifier.size(20.dp)) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.Medium); Text(desc, color = TextGray, fontSize = 13.sp) }; Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = RoseGold)) }
}

// ─── HELPERS ───
data class SavedContact(val name: String, val phone: String)
private fun loadContacts(prefs: SharedPreferences): List<SavedContact> = try { val arr = JSONArray(prefs.getString("list", "[]")!!); (0 until arr.length()).map { val o = arr.getJSONObject(it); SavedContact(o.optString("name"), o.getString("phone")) } } catch (_: Exception) { emptyList() }
private fun saveContacts(prefs: SharedPreferences, list: List<SavedContact>) { val arr = JSONArray(); list.forEach { arr.put(JSONObject().apply { put("name", it.name); put("phone", it.phone) }) }; prefs.edit().putString("list", arr.toString()).apply() }
private fun loadRecentTransactions(context: Context): List<Transaction> = try { val arr = JSONArray(context.getSharedPreferences("transactions", Context.MODE_PRIVATE).getString("list", "[]")); (0 until arr.length()).map { val o = arr.getJSONObject(it); Transaction(o.getInt("id"), o.getString("description"), o.getString("amount"), o.optDouble("amountValue", 0.0), o.getString("date"), o.getString("status"), TransactionStatus.fromString(o.getString("status"))) } } catch (_: Exception) { emptyList() }
