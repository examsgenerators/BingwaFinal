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
import android.provider.ContactsContract
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, BalanceChecker::class.java))
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF1A73E8), secondary = Color(0xFF34A853),
                background = Color(0xFFF8F9FA), surface = Color.White
            )) { BingwaApp() }
        }
    }
}

@Composable
fun BingwaApp() {
    val ctx = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedItem by remember { mutableStateOf("Home") }
    val tm = remember { TokenManager(ctx) }
    var tokenBalance by remember { mutableStateOf(tm.getBalance()) }
    var airtimeBalance by remember { mutableStateOf(BalanceChecker.currentBalance) }

    DisposableEffect(Unit) {
        BalanceChecker.balanceCallback = { raw ->
            val cleaned = raw.replace(Regex("[^\\d.]"), " ").trim()
            val numbers = cleaned.split("\\s+".toRegex()).filter { it.toDoubleOrNull() != null }
            airtimeBalance = if (numbers.isNotEmpty()) "KSh ${numbers.first()}" else "KSh --"
        }
        onDispose { BalanceChecker.balanceCallback = null }
    }
    LaunchedEffect(selectedItem) { tokenBalance = tm.getBalance() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(24.dp))
                Text("Bingwa Mobile", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF1A73E8))
                Divider()
                listOf("Home" to Icons.Default.Home, "Contacts" to Icons.Default.Contacts, "Offers" to Icons.Default.ShoppingCart, "Settings" to Icons.Default.Settings).forEach { (label, icon) ->
                    NavigationDrawerItem(
                        icon = { Icon(icon, label) }, label = { Text(label) },
                        selected = selectedItem == label,
                        onClick = { selectedItem = label; scope.launch { drawerState.close() } },
                        colors = NavigationDrawerItemDefaults.colors(selectedIconColor = Color(0xFF1A73E8), selectedTextColor = Color(0xFF1A73E8))
                    )
                }
            }
        },
        content = {
            Column {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Menu", tint = Color(0xFF1A73E8)) }
                    Text(selectedItem, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    if (selectedItem == "Home") {
                        IconButton(onClick = { BalanceChecker.requestBalanceCheck(ctx) }) { Icon(Icons.Default.Refresh, "Refresh", tint = Color(0xFF1A73E8)) }
                    } else { Spacer(Modifier.size(48.dp)) }
                }
                Divider()
                when (selectedItem) {
                    "Home" -> HomeScreen(tokenBalance, airtimeBalance)
                    "Contacts" -> ContactsScreen()
                    "Offers" -> OffersScreen()
                    "Settings" -> SettingsScreen()
                }
            }
        }
    )
}

// ─── HOME SCREEN ───
@Composable
fun HomeScreen(tokens: Int, airtime: String) {
    val ctx = LocalContext.current
    val transactions = remember { loadRecentTransactions(ctx) }
    Column(Modifier.fillMaxSize().background(Color(0xFFF8F9FA)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A73E8))) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Token, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp)); Text("$tokens", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("Tokens", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
            Card(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF34A853))) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Phone, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp)); Text(airtime, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("Airtime", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Recent Transactions", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(transactions) { tx ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text(tx.description, fontWeight = FontWeight.Medium); Text(tx.date, fontSize = 12.sp, color = Color.Gray) }
                        Text(tx.amount, color = if (tx.amount.startsWith("+")) Color(0xFF4CAF50) else Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun loadRecentTransactions(context: Context): List<Transaction> {
    val prefs = context.getSharedPreferences("transactions", Context.MODE_PRIVATE)
    return try {
        val arr = JSONArray(prefs.getString("list", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Transaction(o.getString("description"), o.getString("amount"), o.getString("date"), o.getString("status"))
        }
    } catch (e: Exception) { emptyList() }
}

// ─── CONTACTS SCREEN ───
@Composable
fun ContactsScreen() {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("saved_contacts", Context.MODE_PRIVATE)
    var contacts by remember { mutableStateOf(loadContacts(prefs)) }
    Column(Modifier.fillMaxSize().background(Color(0xFFF8F9FA)).padding(16.dp)) {
        Text("Saved Contacts", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(contacts, key = { it.phone }) { contact ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text(contact.phone, fontWeight = FontWeight.Medium); if (contact.name.isNotBlank()) Text(contact.name, color = Color.Gray) }
                        IconButton(onClick = { contacts = contacts.toMutableList().also { it.remove(contact) }; saveContacts(prefs, contacts) }) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFF44336)) }
                    }
                }
            }
        }
    }
}

data class SavedContact(val name: String, val phone: String)
private fun loadContacts(prefs: SharedPreferences): List<SavedContact> {
    return try {
        val arr = JSONArray(prefs.getString("list", "[]")!!)
        (0 until arr.length()).map { val o = arr.getJSONObject(it); SavedContact(o.optString("name"), o.getString("phone")) }
    } catch (_: Exception) { emptyList() }
}
private fun saveContacts(prefs: SharedPreferences, list: List<SavedContact>) {
    val arr = JSONArray(); list.forEach { arr.put(JSONObject().apply { put("name", it.name); put("phone", it.phone) }) }
    prefs.edit().putString("list", arr.toString()).apply()
}

// ─── OFFERS SCREEN ───
@Composable
fun OffersScreen() {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("DataOffers", Context.MODE_PRIVATE)
    val gson = remember { Gson() }
    var offers by remember {
        mutableStateOf(try {
            gson.fromJson<List<DataOffer>>(prefs.getString("offers", "[]")!!, object : TypeToken<List<DataOffer>>() {}.type)
        } catch (_: Exception) {
            listOf(DataOffer("250MB Daily", 20, "*180*5*2*1#", "ADVANCED", "daily"), DataOffer("1GB Weekly", 50, "*180*5*2*2#", "ADVANCED", "weekly"))
        })
    }
    var showDialog by remember { mutableStateOf(false) }
    var editOffer by remember { mutableStateOf<DataOffer?>(null) }

    Column(Modifier.fillMaxSize().background(Color(0xFFF8F9FA)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Data Offers", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Button(onClick = { editOffer = null; showDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))) {
                Icon(Icons.Default.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(4.dp)); Text("Add")
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(offers) { idx, offer ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(offer.name, fontWeight = FontWeight.Bold)
                                Text("USSD: ${offer.ussdCode} | KSh ${offer.price} | ${offer.executionMode} | ${offer.mode}", fontSize = 12.sp, color = Color.Gray)
                            }
                            Row {
                                IconButton(onClick = { editOffer = offer; showDialog = true }) { Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF1A73E8)) }
                                IconButton(onClick = {
                                    offers = offers.toMutableList().also { it.removeAt(idx) }
                                    prefs.edit().putString("offers", gson.toJson(offers)).apply()
                                }) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFF44336)) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDialog) {
        AddOfferDialog(editOffer, onSave = { offer ->
            offers = offers.toMutableList().also { list ->
                val idx = list.indexOfFirst { it.name == offer.name }
                if (idx >= 0) list[idx] = offer else list.add(offer)
            }
            prefs.edit().putString("offers", gson.toJson(offers)).apply()
            showDialog = false
        }, onDismiss = { showDialog = false })
    }
}

@Composable
fun AddOfferDialog(existing: DataOffer?, onSave: (DataOffer) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var price by remember { mutableStateOf(existing?.price?.toString() ?: "") }
    var code by remember { mutableStateOf(existing?.ussdCode ?: "") }
    var execMode by remember { mutableStateOf(existing?.executionMode ?: "SIMPLE") }
    var mode by remember { mutableStateOf(existing?.mode ?: "daily") }

    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing != null) "Edit Offer" else "Add Offer") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
            OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("USSD code") })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Execution: ")
                FilterChip(selected = execMode == "SIMPLE", onClick = { execMode = "SIMPLE" }, label = { Text("SIMPLE") })
                Spacer(Modifier.width(4.dp))
                FilterChip(selected = execMode == "ADVANCED", onClick = { execMode = "ADVANCED" }, label = { Text("ADVANCED") })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mode: "); listOf("daily", "weekly", "monthly").forEach { FilterChip(selected = mode == it, onClick = { mode = it }, label = { Text(it) }); Spacer(Modifier.width(4.dp)) }
            }
        }
    }, confirmButton = {
        Button(onClick = { val p = price.toIntOrNull() ?: 0; if (p > 0 && code.isNotBlank()) onSave(DataOffer(name.ifBlank { "New" }, p, code, execMode, mode)) }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
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

    Column(Modifier.fillMaxSize().background(Color(0xFFF8F9FA)).padding(16.dp)) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column {
                        SwitchRow("Enable Automation", "Turn ON/OFF SMS & USSD processing", Icons.Default.PowerSettingsNew, auto) {
                            auto = it; prefs.edit().putBoolean("automation_enabled", it).apply()
                            if (it) ctx.startService(Intent(ctx, BalanceChecker::class.java)) else ctx.stopService(Intent(ctx, BalanceChecker::class.java))
                        }
                        Divider(Modifier.padding(horizontal = 16.dp))
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SimCard, null, tint = Color(0xFF1A73E8), modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text("SIM for USSD", fontWeight = FontWeight.Medium); Text(simLabel, color = Color.Gray) }
                            TextButton(onClick = { showSimDlg = true }) { Text("Change") }
                        }
                        Divider(Modifier.padding(horizontal = 16.dp))
                        SwitchRow("Auto-save contacts", "Save numbers from M-PESA messages", Icons.Default.Contacts, saveContacts) {
                            saveContacts = it; prefs.edit().putBoolean("auto_save_contacts", it).apply()
                        }
                        Divider(Modifier.padding(horizontal = 16.dp))
                        SwitchRow("Auto-retry failed", "Retry failed transactions every 2 min", Icons.Default.RestartAlt, autoRetry) {
                            autoRetry = it; prefs.edit().putBoolean("auto_retry", it).apply()
                            if (it) UnderMaintenanceRetryReceiver.schedule(ctx) else UnderMaintenanceRetryReceiver.cancel(ctx)
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Bingwa Mobile v1.0", fontWeight = FontWeight.Bold)
                        Text("Powered by Victor Ngetich", color = Color(0xFF1A73E8), fontSize = 12.sp)
                    }
                }
            }
        }
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Select SIM") }, text = {
        if (list.isEmpty()) Text("No SIMs found or permission missing.")
        else Column { list.forEach { sim ->
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = sim.subscriptionId == cur, onClick = { onSelect(sim.subscriptionId) })
                Spacer(Modifier.width(8.dp)); Text("${sim.displayName} (Slot ${sim.simSlotIndex+1})")
            }
        } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
fun SwitchRow(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF5F5F5)), contentAlignment = Alignment.Center) { Icon(icon, title, tint = Color(0xFF1A73E8), modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(desc, fontSize = 13.sp, color = Color.Gray) }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF1A73E8)))
    }
}
