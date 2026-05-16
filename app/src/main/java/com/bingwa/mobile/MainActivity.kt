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
import androidx.compose.foundation.background
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// ─── Dark Theme Colors ───
private val BackgroundDark = Color(0xFF0F0E13)
private val CardBackground = Color(0xFF16151A)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF8A898E)
private val AirtimeBlue = Color(0xFF1E88E5)
private val TokenGreen = Color(0xFF4CAF50)
private val StatusGreenBg = Color(0xFF142217)
private val StatusGreenText = Color(0xFF4CAF50)
private val StatusOrangeBg = Color(0xFF241A10)
private val StatusOrangeText = Color(0xFFFFA726)
private val StatusRedBg = Color(0xFF241215)
private val StatusRedText = Color(0xFFEF5350)
private val StopButtonRed = Color(0xFFE53935)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("DataOffers", Context.MODE_PRIVATE)
        if (prefs.getString("offers", null) == null) {
            val storage = UssdStorage(this)
            val defaultOffers = storage.getLabels().map { (price, label) ->
                DataOffer(name = label, price = price.toInt(), ussdCode = storage.getUssdForAmount(price) ?: "", executionMode = "ADVANCED", mode = "daily")
            }
            prefs.edit().putString("offers", Gson().toJson(defaultOffers)).apply()
        }
        startService(Intent(this, BalanceChecker::class.java))
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = AirtimeBlue, secondary = TokenGreen, background = BackgroundDark, surface = CardBackground, onPrimary = Color.White, onSecondary = Color.White, onBackground = TextPrimary, onSurface = TextPrimary)) {
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
    var selected by remember { mutableStateOf("Dashboard") }
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(24.dp))
                Text("Bingwa Mobile", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AirtimeBlue)
                Divider(color = TextSecondary)
                listOf("Dashboard" to Icons.Default.Home, "Contacts" to Icons.Default.Contacts, "Offers" to Icons.Default.ShoppingCart, "Settings" to Icons.Default.Settings).forEach { (label, icon) ->
                    NavigationDrawerItem(
                        icon = { Icon(icon, label, tint = TextSecondary) }, label = { Text(label, color = TextPrimary) },
                        selected = selected == label,
                        onClick = { selected = label; scope.launch { drawerState.close() } },
                        colors = NavigationDrawerItemDefaults.colors(selectedIconColor = AirtimeBlue, selectedTextColor = AirtimeBlue, unselectedIconColor = TextSecondary, selectedContainerColor = CardBackground)
                    )
                }
            }
        },
        content = {
            when (selected) {
                "Dashboard" -> DashboardScreen(airtimeBal, tokenBal, onRefresh = { BalanceChecker.requestBalanceCheck(ctx) }, onToggle = { isRunning = !isRunning; appPrefs.edit().putBoolean("automation_enabled", isRunning).apply(); if (!isRunning) ctx.stopService(Intent(ctx, BalanceChecker::class.java)) else ctx.startService(Intent(ctx, BalanceChecker::class.java)) }, isRunning = isRunning, openDrawer = { scope.launch { drawerState.open() } })
                "Contacts" -> ContactsScreen()
                "Offers" -> OffersScreen()
                "Settings" -> SettingsScreen()
            }
        }
    )
}

@Composable
fun DashboardScreen(airtime: String, tokens: Int, onRefresh: () -> Unit, onToggle: () -> Unit, isRunning: Boolean, openDrawer: () -> Unit) {
    val ctx = LocalContext.current
    val tx = remember { loadRecentTransactions(ctx) }
    val completed = tx.count { it.status == "Success" || it.status == "Completed" }
    val pending = tx.count { it.status == "Pending" || it.status == "Processing" }
    val failed = tx.count { it.status == "Failed" || it.status == "Cancelled" }
    val rate = if (tx.isNotEmpty()) "%.1f%%".format(completed.toDouble() / tx.size * 100) else "0.0%"

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Bingwa", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp); Text("USSD Automation", color = TextSecondary, fontSize = 12.sp) } }, navigationIcon = { IconButton(onClick = openDrawer) { Icon(Icons.Default.Menu, null, tint = TextPrimary) } }, actions = { IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, null, tint = StatusOrangeText) } }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BackgroundDark)) },
        floatingActionButton = { Button(onClick = onToggle, colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) StopButtonRed else TokenGreen), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(0.6f).height(56.dp), elevation = ButtonDefaults.buttonElevation(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(14.dp).background(Color.White, RoundedCornerShape(2.dp))); Spacer(Modifier.width(8.dp)); Text(if (isRunning) "Stop Automation" else "Start Automation", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) } } },
        floatingActionButtonPosition = FabPosition.Center,
        containerColor = BackgroundDark
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(16.dp))
            Text("Balances", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Current account balances", color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BalanceCard("Airtime", airtime, "Updated now", AirtimeBlue, Modifier.weight(1f))
                BalanceCard("Token", "$tokens units", "Updated now", TokenGreen, Modifier.weight(1f))
            }
            Spacer(Modifier.height(24.dp))
            Text("Transaction Overview", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Real-time statistics", color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { StatCard("$completed", "Completed", Icons.Default.CheckCircle, StatusGreenText, StatusGreenBg, Modifier.weight(1f)); StatCard("$pending", "Pending", Icons.Default.Refresh, StatusOrangeText, StatusOrangeBg, Modifier.weight(1f)) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { StatCard("$failed", "Failed", Icons.Default.Warning, StatusRedText, StatusRedBg, Modifier.weight(1f)); StatCard(rate, "Success Rate", Icons.Default.CheckCircle, StatusGreenText, StatusGreenBg, Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Recent Transactions", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold); TextButton(onClick = {}) { Text("View All", color = AirtimeBlue) } }
            Spacer(Modifier.height(12.dp))
            if (tx.isEmpty()) { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Description, null, tint = TextSecondary, modifier = Modifier.size(64.dp)); Spacer(Modifier.height(16.dp)); Text("No transactions yet", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Transactions appear here automatically.", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center) } }
            else { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(tx.take(10)) { t -> Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBackground)) { Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(t.description, color = TextPrimary, fontWeight = FontWeight.Medium); Text(t.date, color = TextSecondary, fontSize = 12.sp) }; Text(t.amount, color = if (t.amount.startsWith("+")) StatusGreenText else StatusOrangeText, fontWeight = FontWeight.Bold) } } } } }
            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
private fun BalanceCard(title: String, value: String, date: String, color: Color, modifier: Modifier) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = modifier) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) { Box(Modifier.width(4.dp).height(45.dp).background(color, RoundedCornerShape(2.dp))); Spacer(Modifier.width(12.dp)); Column { Text(title, color = TextSecondary, fontSize = 14.sp); Spacer(Modifier.height(4.dp)); Text(value, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(date, color = TextSecondary, fontSize = 11.sp) } }
    }
}

@Composable
private fun StatCard(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, bg: Color, modifier: Modifier) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = bg), modifier = modifier) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, label, tint = iconColor, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(16.dp)); Column { Text(value, color = iconColor, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(label, color = TextSecondary, fontSize = 14.sp) } }
    }
}

// ─── CONTACTS SCREEN ───
@Composable
fun ContactsScreen() {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("saved_contacts", Context.MODE_PRIVATE)
    var contacts by remember { mutableStateOf(loadContacts(prefs)) }
    Column(Modifier.fillMaxSize().background(BackgroundDark).padding(16.dp)) {
        Text("Saved Contacts", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(contacts, key = { it.phone }) { c ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBackground)) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text(c.phone, color = TextPrimary, fontWeight = FontWeight.Medium); if (c.name.isNotBlank()) Text(c.name, color = TextSecondary) }
                        IconButton(onClick = { contacts = contacts.toMutableList().also { it.remove(c) }; saveContacts(prefs, contacts) }) { Icon(Icons.Default.Delete, "Delete", tint = StatusRedText) }
                    }
                }
            }
        }
    }
}

// ─── OFFERS SCREEN ───
@Composable
fun OffersScreen() {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("DataOffers", Context.MODE_PRIVATE)
    val gson = remember { Gson() }
    var offers by remember { mutableStateOf(try { gson.fromJson<List<DataOffer>>(prefs.getString("offers", "[]")!!, object : TypeToken<List<DataOffer>>() {}.type) } catch (_: Exception) { emptyList() }) }
    var showDialog by remember { mutableStateOf(false) }
    var editOffer by remember { mutableStateOf<DataOffer?>(null) }

    Column(Modifier.fillMaxSize().background(BackgroundDark).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Data Offers", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Button(onClick = { editOffer = null; showDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = AirtimeBlue)) { Icon(Icons.Default.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(4.dp)); Text("Add") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(offers) { idx, offer ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) { Text(offer.name, color = TextPrimary, fontWeight = FontWeight.Bold); Text("USSD: ${offer.ussdCode} | KSh ${offer.price} | ${offer.executionMode} | ${offer.mode}", color = TextSecondary, fontSize = 12.sp) }
                            Row {
                                IconButton(onClick = { editOffer = offer; showDialog = true }) { Icon(Icons.Default.Edit, "Edit", tint = AirtimeBlue) }
                                IconButton(onClick = { offers = offers.toMutableList().also { it.removeAt(idx) }; prefs.edit().putString("offers", gson.toJson(offers)).apply() }) { Icon(Icons.Default.Delete, "Delete", tint = StatusRedText) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDialog) { AddOfferDialog(editOffer, onSave = { offer -> offers = offers.toMutableList().also { list -> val idx = list.indexOfFirst { it.name == offer.name }; if (idx >= 0) list[idx] = offer else list.add(offer) }; prefs.edit().putString("offers", gson.toJson(offers)).apply(); showDialog = false }, onDismiss = { showDialog = false }) }
}

@Composable
fun AddOfferDialog(existing: DataOffer?, onSave: (DataOffer) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var price by remember { mutableStateOf(existing?.price?.toString() ?: "") }
    var code by remember { mutableStateOf(existing?.ussdCode ?: "") }
    var execMode by remember { mutableStateOf(existing?.executionMode ?: "SIMPLE") }
    var mode by remember { mutableStateOf(existing?.mode ?: "daily") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing != null) "Edit Offer" else "Add Offer", color = TextPrimary) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }); OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("USSD code") }); Row(verticalAlignment = Alignment.CenterVertically) { Text("Execution: ", color = TextPrimary); FilterChip(selected = execMode == "SIMPLE", onClick = { execMode = "SIMPLE" }, label = { Text("SIMPLE") }); Spacer(Modifier.width(4.dp)); FilterChip(selected = execMode == "ADVANCED", onClick = { execMode = "ADVANCED" }, label = { Text("ADVANCED") }) }; Row(verticalAlignment = Alignment.CenterVertically) { Text("Mode: ", color = TextPrimary); listOf("daily", "weekly", "monthly").forEach { FilterChip(selected = mode == it, onClick = { mode = it }, label = { Text(it) }); Spacer(Modifier.width(4.dp)) } } } }, confirmButton = { Button(onClick = { val p = price.toIntOrNull() ?: 0; if (p > 0 && code.isNotBlank()) onSave(DataOffer(name.ifBlank { "New" }, p, code, execMode, mode)) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
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

    Column(Modifier.fillMaxSize().background(BackgroundDark).padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Settings", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground)) {
            Column {
                SwitchRow("Enable Automation", "SMS & USSD processing", Icons.Default.PowerSettingsNew, auto) { auto = it; prefs.edit().putBoolean("automation_enabled", it).apply(); if (it) ctx.startService(Intent(ctx, BalanceChecker::class.java)) else ctx.stopService(Intent(ctx, BalanceChecker::class.java)) }
                Divider(color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.SimCard, null, tint = AirtimeBlue, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("SIM for USSD", color = TextPrimary, fontWeight = FontWeight.Medium); Text(simLabel, color = TextSecondary) }; TextButton(onClick = { showSimDlg = true }) { Text("Change", color = AirtimeBlue) } }
                Divider(color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
                SwitchRow("Auto-save contacts", "From M-PESA messages", Icons.Default.Contacts, saveContacts) { saveContacts = it; prefs.edit().putBoolean("auto_save_contacts", it).apply() }
                Divider(color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
                SwitchRow("Auto-retry failed", "Every 2 minutes", Icons.Default.RestartAlt, autoRetry) { autoRetry = it; prefs.edit().putBoolean("auto_retry", it).apply(); if (it) UnderMaintenanceRetryReceiver.schedule(ctx) else UnderMaintenanceRetryReceiver.cancel(ctx) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground)) {
            Row(Modifier.fillMaxWidth().padding(16.dp).clickable { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, verticalAlignment = Alignment.CenterVertically) { Icon(if (accEnabled) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (accEnabled) StatusGreenText else StatusOrangeText, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Accessibility Service", color = TextPrimary, fontWeight = FontWeight.Medium); Text(if (accEnabled) "✅ Active" else "❌ Required – tap to enable", color = if (accEnabled) StatusGreenText else StatusOrangeText, fontSize = 13.sp) } }
        }
        Spacer(Modifier.height(12.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground)) { Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Bingwa Mobile v1.0", color = TextPrimary, fontWeight = FontWeight.Bold); Text("Powered by Victor Ngetich", color = AirtimeBlue, fontSize = 12.sp) } }
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Select SIM", color = TextPrimary) }, text = { if (list.isEmpty()) Text("No SIMs found or permission missing.", color = TextSecondary) else Column { list.forEach { sim -> Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = sim.subscriptionId == cur, onClick = { onSelect(sim.subscriptionId) }); Spacer(Modifier.width(8.dp)); Text("${sim.displayName} (Slot ${sim.simSlotIndex+1})", color = TextPrimary) } } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
fun SwitchRow(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(40.dp).clip(CircleShape).background(BackgroundDark), contentAlignment = Alignment.Center) { Icon(icon, title, tint = AirtimeBlue, modifier = Modifier.size(20.dp)) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, color = TextPrimary, fontWeight = FontWeight.Medium); Text(desc, color = TextSecondary, fontSize = 13.sp) }; Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = AirtimeBlue)) }
}

// ─── DATA HELPERS ───
data class SavedContact(val name: String, val phone: String)
private fun loadContacts(prefs: SharedPreferences): List<SavedContact> {
    return try { val arr = JSONArray(prefs.getString("list", "[]")!!); (0 until arr.length()).map { val o = arr.getJSONObject(it); SavedContact(o.optString("name"), o.getString("phone")) } } catch (_: Exception) { emptyList() }
}
private fun saveContacts(prefs: SharedPreferences, list: List<SavedContact>) {
    val arr = JSONArray(); list.forEach { arr.put(JSONObject().apply { put("name", it.name); put("phone", it.phone) }) }; prefs.edit().putString("list", arr.toString()).apply()
}
private fun loadRecentTransactions(context: Context): List<Transaction> {
    return try { val arr = JSONArray(context.getSharedPreferences("transactions", Context.MODE_PRIVATE).getString("list", "[]")); (0 until arr.length()).map { val o = arr.getJSONObject(it); Transaction(o.getInt("id"), o.getString("description"), o.getString("amount"), o.optDouble("amountValue", 0.0), o.getString("date"), o.getString("status"), TransactionStatus.fromString(o.getString("status")), o.optString("ussdCode", ""), o.optString("phoneNumber", ""), o.optString("response", ""), o.optLong("timestamp", 0)) } } catch (_: Exception) { emptyList() }
}
