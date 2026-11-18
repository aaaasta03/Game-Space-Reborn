package com.reborn.gamespace

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rikka.shizuku.Shizuku

private val Background = Color(0xFF101218)
private val Panel = Color(0xFF1A1E24)
private val Accent = Color(0xFF8B7CFF)
private val Mint = Color(0xFF66E0B5)
private val Muted = Color(0xFF9AA3B2)

class MainActivity : ComponentActivity() {
    private val requestCode = 1001
    private var shizukuState by mutableStateOf("Checking Shizuku…")
    private var serviceInfo by mutableStateOf("UserService not connected")
    private var service: IGameSpaceService? = null
    private var serviceBound = false

    private val binderReceived = Shizuku.OnBinderReceivedListener { refreshShizukuState() }
    private val binderDead = Shizuku.OnBinderDeadListener { shizukuState = "Shizuku service disconnected" }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == requestCode) {
            if (result == PackageManager.PERMISSION_GRANTED) {
                shizukuState = "Permission granted · ready"
                bindShellService()
            } else shizukuState = "Permission denied"
        }
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IGameSpaceService.Stub.asInterface(binder)
            serviceInfo = "Shell UserService connected · UID check available"
            runDiagnostic(1)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            serviceInfo = "UserService disconnected"
        }
    }

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(packageName, ShellUserService::class.java.name))
            .daemon(false)
            .processNameSuffix("gamespace-shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
            .tag("gamespace-reborn-readonly-v1")
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refreshShizukuState()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Accent, secondary = Mint, background = Background, surface = Panel
            )) {
                GameSpaceScreen(
                    shizukuState = shizukuState,
                    serviceInfo = serviceInfo,
                    gameInstalled = isGameInstalled(),
                    onRequestShizuku = ::requestShizuku,
                    onConnectDiagnostics = ::connectDiagnostics,
                    onDiagnostic = ::runDiagnostic,
                    onLaunchGame = ::launchGame
                )
            }
        }
    }


    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        if (serviceBound) {
            try { Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true) } catch (_: Throwable) {}
            serviceBound = false
        }
        super.onDestroy()
    }


    private fun refreshShizukuState() {
        shizukuState = try {
            if (!Shizuku.pingBinder()) "Shizuku binder unavailable · start Shizuku first"
            else if (Shizuku.isPreV11()) "Unsupported Shizuku version · v11+ required"
            else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) "Permission granted · ready (UID ${Shizuku.getUid()})"
            else "Shizuku running · permission not granted"
        } catch (_: Throwable) { "Shizuku not detected · start service" }
    }

    private fun requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                shizukuState = "Shizuku binder unavailable. Start the Shizuku service."
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                shizukuState = "Permission already granted"
                return
            }
            Shizuku.requestPermission(requestCode)
            shizukuState = "Permission request sent to Shizuku"
        } catch (e: Throwable) { shizukuState = "Permission error: ${e.message}" }
    }

    private fun connectDiagnostics() {
        try {
            if (!Shizuku.pingBinder()) { shizukuState = "Shizuku binder unavailable"; return }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                requestShizuku(); return
            }
            if (Shizuku.getVersion() < 10) { shizukuState = "Shizuku v10+ required for UserService"; return }
            Shizuku.bindUserService(userServiceArgs, userServiceConnection)
            serviceBound = true
            serviceInfo = "Starting UserService…"
        } catch (e: Throwable) { serviceInfo = "Service error: ${e.message}" }
    }

    private fun runDiagnostic(id: Int) {
        val svc = service
        if (svc == null) { serviceInfo = "Connect Shizuku diagnostics first"; return }
        Thread {
            try {
                val result = svc.runDiagnostic(id)
                runOnUiThread { serviceInfo = result }
            } catch (e: RemoteException) {
                runOnUiThread { serviceInfo = "Diagnostic IPC error: ${e.message}" }
            }
        }.start()
    }

    private fun isGameInstalled(): Boolean = try {
        packageManager.getPackageInfo("com.dts.freefiremax", 0); true
    } catch (_: Exception) { false }

    private fun launchGame() {
        val launch = packageManager.getLaunchIntentForPackage("com.dts.freefiremax")
        if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        else serviceInfo = "Free Fire MAX not detected (com.dts.freefiremax)"
    }
}


@Composable
private fun GameSpaceScreen(
    shizukuState: String,
    serviceInfo: String,
    gameInstalled: Boolean,
    onRequestShizuku: () -> Unit,
    onConnectDiagnostics: () -> Unit,
    onDiagnostic: (Int) -> Unit,
    onLaunchGame: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("game_profiles", MODE_PRIVATE) }
    var touchNotes by remember { mutableStateOf(prefs.getBoolean("touch_notes", false)) }
    var notificationTrim by remember { mutableStateOf(prefs.getBoolean("notification_trim", false)) }
    var focusProfile by remember { mutableStateOf(prefs.getBoolean("focus_profile", true)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(Modifier.padding(top = 26.dp)) {
                Text("GAME SPACE", color = Mint, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Text("REBORN", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineLarge)
                Text("Gaming control center · Shell bridge", color = Muted)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconLabel(Icons.Default.Gamepad, "GAME PROFILE", Mint)
                    }
                    Text("Free Fire MAX", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (gameInstalled) "Package detected: com.dts.freefiremax" else "Game not found · Install Free Fire MAX to enable launch", color = Muted)
                    Button(onClick = onLaunchGame, modifier = Modifier.fillMaxWidth()) { Text(if (gameInstalled) "LAUNCH GAME" else "CHECK / LAUNCH GAME") }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconLabel(Icons.Default.Bolt, "SHIZUKU BRIDGE", Accent)
                    Text(shizukuState, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Button(onClick = onRequestShizuku, modifier = Modifier.fillMaxWidth()) { Text("REQUEST SHIZUKU PERMISSION") }
                    Button(onClick = onConnectDiagnostics, modifier = Modifier.fillMaxWidth()) { Text("CONNECT READ-ONLY DIAGNOSTICS") }
                    Text("No root. Shell access is only used for fixed read-only diagnostics in this build.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconLabel(Icons.Default.Tune, "GAME PROFILE OPTIONS", Mint)
                    ProfileSwitch("Focus profile", "Profile preference only; no system changes yet", focusProfile) {
                        focusProfile = it; prefs.edit().putBoolean("focus_profile", it).apply()
                    }
                    ProfileSwitch("Touch layout notes", "Save a personal touch-profile flag", touchNotes) {
                        touchNotes = it; prefs.edit().putBoolean("touch_notes", it).apply()
                    }
                    ProfileSwitch("Notification trim", "Preference placeholder; not blocking notifications", notificationTrim) {
                        notificationTrim = it; prefs.edit().putBoolean("notification_trim", it).apply()
                    }
                    Text("These toggles currently store preferences only. They do not apply FPS, touch, CPU, GPU or notification policy changes.", color = Color(0xFFFFC979), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconLabel(Icons.Default.Memory, "READ-ONLY DEVICE CHECKS", Accent)
                    Text("Choose a diagnostic", color = Color.White, fontWeight = FontWeight.Bold)
                    Button(onClick = { onDiagnostic(1) }, modifier = Modifier.fillMaxWidth()) { Text("Shell UID / PID") }
                    Button(onClick = { onDiagnostic(2) }, modifier = Modifier.fillMaxWidth()) { Text("Memory information (/proc/meminfo)") }
                    Button(onClick = { onDiagnostic(3) }, modifier = Modifier.fillMaxWidth()) { Text("Build fingerprint") }
                    Button(onClick = { onDiagnostic(4) }, modifier = Modifier.fillMaxWidth()) { Text("Android release property") }
                    Text(serviceInfo, color = Color(0xFFB7C5D7), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconLabel(Icons.Default.Thermostat, "OPTIMIZATION STATUS", Mint)
                    Text("System controls: not yet implemented or tested on RMX5003", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("FPS overrides · refresh rate · thermal policy · touch injection · CPU/GPU clocks · memory tuning", color = Muted)
                    Text("No performance gain is claimed by this starter build.", color = Color(0xFFFFC979))
                }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}


@Composable
private fun IconLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = tint)
        Text(label, color = tint, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}


@Composable
private fun ProfileSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}


private const val MODE_PRIVATE = 0
