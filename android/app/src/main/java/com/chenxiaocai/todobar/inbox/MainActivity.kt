package com.chenxiaocai.todobar.inbox

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF356859))) { InboxApp() } }
    }
}

class InboxViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as InboxApplication
    val items = app.database.items
    var pairing by mutableStateOf(app.secureStore.load())
        private set
    var message by mutableStateOf<String?>(null)
        private set

    init {
        if (pairing != null && app.database.pending().isNotEmpty()) SyncScheduler.enqueue(app)
    }

    fun add(value: String) {
        runCatching { app.database.add(value); SyncScheduler.enqueue(app) }
            .onFailure { message = "无法保存：${it.localizedMessage}" }
    }
    fun delete(id: String) = app.database.deletePending(id)
    fun clearDelivered() = app.database.clearDelivered()
    fun sync() { SyncScheduler.enqueue(app); message = "已提交一次同步；失败不会自动重试" }

    fun pair(qrValue: String) {
        viewModelScope.launch {
            message = "正在家庭局域网中查找 Mac…"
            runCatching {
                withContext(Dispatchers.IO) {
                    val ticket = SecureStore.parseTicket(qrValue)
                    val ssid = currentSSID(app)
                    val endpoint = BonjourDiscovery(app).find() ?: error("没有找到 ToDoBar Sync；请确认 Mac 已唤醒并运行应用")
                    val deviceID = UUID.randomUUID().toString()
                    val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                    val key = SyncClient(endpoint).pair(ticket, deviceID, deviceName)
                    PairingState(ticket.serverID, deviceID, deviceName, ssid, key).also(app.secureStore::save)
                }
            }.onSuccess { pairing = it; message = "绑定成功，已记住 Wi‑Fi：${it.ssid}"; SyncScheduler.enqueue(app) }
                .onFailure { message = "绑定失败：${it.localizedMessage}" }
        }
    }

    fun unbind() {
        val state = pairing ?: return
        viewModelScope.launch {
            val remote = withContext(Dispatchers.IO) {
                runCatching {
                    val endpoint = BonjourDiscovery(app).find() ?: error("Mac unreachable")
                    SyncClient(endpoint).unbind(state)
                }.isSuccess
            }
            app.secureStore.clear(); pairing = null
            message = if (remote) "手机和 Mac 已解除绑定" else "Mac 当前不可达；已清除手机绑定，Mac 端可单独撤销旧设备"
        }
    }

    private fun currentSSID(context: Context): String {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) { "需要位置权限才能读取当前 Wi‑Fi 名称" }
        val ssid = context.getSystemService(WifiManager::class.java).connectionInfo.ssid.trim('"')
        check(ssid.isNotBlank() && ssid != WifiManager.UNKNOWN_SSID) { "无法读取当前 Wi‑Fi 名称" }
        return ssid
    }
}

@Composable private fun InboxApp(vm: InboxViewModel = viewModel()) {
    val context = LocalContext.current
    var scanning by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        scanning = result.isNotEmpty() && result.values.all { it }
        if (!scanning) android.widget.Toast.makeText(context, "扫码和读取家庭 Wi‑Fi 需要相机与位置权限", android.widget.Toast.LENGTH_LONG).show()
    }
    Surface(Modifier.fillMaxSize()) {
        if (scanning) {
            QrScanner(onResult = { scanning = false; vm.pair(it) }, onCancel = { scanning = false })
        } else if (vm.pairing == null) {
            PairScreen(message = vm.message, onScan = {
                val permissions = buildList {
                    add(Manifest.permission.CAMERA)
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
                }.toTypedArray()
                if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) scanning = true else permissionLauncher.launch(permissions)
            })
        } else {
            InboxScreen(vm)
        }
    }
}

@Composable private fun PairScreen(message: String?, onScan: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("ToDoBar 收集箱", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("在 Mac 的 ToDoBar Sync 中打开“手机同步”，然后扫描配对二维码。待办只在家庭局域网内传输。")
        Spacer(Modifier.height(24.dp)); Button(onClick = onScan) { Text("扫描配对二维码") }
        message?.let { Spacer(Modifier.height(16.dp)); Text(it, color = MaterialTheme.colorScheme.secondary) }
    }
}

@Composable private fun InboxScreen(vm: InboxViewModel) {
    val all by vm.items.collectAsStateWithLifecycle()
    val pending = all.filter { it.deliveredAt == null }
    val delivered = all.filter { it.deliveredAt != null }.reversed()
    var input by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var showUnbind by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ToDoBar 收集箱", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::sync) { Text("同步") }
            TextButton(onClick = { showUnbind = true }) { Text("解绑") }
        }
        OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), label = { Text("想到的事情") }, singleLine = false)
        Button(onClick = { if (input.isNotBlank()) { vm.add(input); input = "" } }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("记下来") }
        vm.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 8.dp)) }
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("待发送 ${pending.size}") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("已送达 ${delivered.size}") })
        }
        if (tab == 0) ItemList(pending, true, vm::delete) else Column(Modifier.weight(1f)) {
            TextButton(onClick = vm::clearDelivered, modifier = Modifier.align(Alignment.End)) { Text("清空已送达记录") }
            ItemList(delivered, false, {})
        }
    }
    if (showUnbind) AlertDialog(onDismissRequest = { showUnbind = false }, title = { Text("解除绑定？") }, text = { Text("待发送与历史记录会保留；下次需要重新扫码。") }, confirmButton = { TextButton(onClick = { showUnbind = false; vm.unbind() }) { Text("解除") } }, dismissButton = { TextButton(onClick = { showUnbind = false }) { Text("取消") } })
}

@Composable private fun ItemList(values: List<InboxItem>, pending: Boolean, onDelete: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) { items(values, key = { it.id }) { item ->
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(item.text); Text(DateFormat.getDateTimeInstance().format(Date(item.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) }
            if (pending) TextButton(onClick = { onDelete(item.id) }) { Text("删除") }
        }; HorizontalDivider()
    } }
}
