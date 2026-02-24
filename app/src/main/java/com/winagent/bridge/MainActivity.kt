package com.winagent.bridge

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(vm)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val appCtx = remember(ctx) { ctx.applicationContext }

    val live by vm.sendLiveCalls.collectAsState()
    val missed by vm.sendMissedCalls.collectAsState()
    val notif by vm.sendNotifications.collectAsState()
    val paired by vm.pairedPc.collectAsState()

    LaunchedEffect(paired) { vm.refreshPairingStatus() }

    var tab by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // After the permission prompt, try starting the background bridge service.
        BridgeForegroundService.start(appCtx)
    }

    // Ask for required runtime permissions on first open.
    var askedAtStartup by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (askedAtStartup) return@LaunchedEffect
        askedAtStartup = true

        val perms = mutableListOf(
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.READ_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = perms.filter {
            ContextCompat.checkSelfPermission(appCtx, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            BridgeForegroundService.start(appCtx)
        }
    }

    // If notification listener access is not enabled, prompt the user once.
    var showNotifAccessDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        showNotifAccessDialog = !vm.isNotificationListenerEnabled()
    }

    fun requestCallPermissionsIfNeeded() {
        val perms = mutableListOf(
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.READ_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    Scaffold(
        topBar = {
        }
    ) { pad ->
        if (showNotifAccessDialog) {
            AlertDialog(
                onDismissRequest = { showNotifAccessDialog = false },
                title = { Text("Notifications Access") },
                text = { Text("Bildirimleri okuyup gönderebilmem için Notification Access vermen gerekiyor.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showNotifAccessDialog = false
                            appCtx.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    ) { Text("Open Settings") }
                },
                dismissButton = {
                    TextButton(onClick = { showNotifAccessDialog = false }) { Text("Later") }
                }
            )
        }

        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PairingCard(
                status = vm.pairingStatus,
                paired = paired,
                onFind = { vm.startDiscovery() },
                onClear = { vm.clearPair() },
                onTrigger = { vm.triggerSendNow() }
            )

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Call Log") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Notifications") })
            }

            when (tab) {
                0 -> CallsTab(
                    live = live,
                    missed = missed,
                    hasPhoneState = vm.hasPermission(android.Manifest.permission.READ_PHONE_STATE),
                    hasCallLog = vm.hasPermission(android.Manifest.permission.READ_CALL_LOG),
                    onRequestPermissions = { requestCallPermissionsIfNeeded() },
                    onLiveChanged = {
                        vm.setSendLiveCalls(it)
                    },
                    onMissedChanged = {
                        vm.setSendMissedCalls(it)
                    }
                )

                1 -> NotificationsTab(
                    enabled = notif,
                    listenerEnabled = vm.isNotificationListenerEnabled(),
                    onOpenSettings = {
                        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    onChanged = { vm.setSendNotifications(it) }
                )
            }
        }
    }
}

@Composable
private fun PairingCard(
    status: String,
    paired: PairedPc?,
    onFind: () -> Unit,
    onClear: () -> Unit,
    onTrigger: () -> Unit
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pairing Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(status, style = MaterialTheme.typography.bodyMedium)

            if (paired != null) {
                val token = paired.token
                if (!token.isNullOrBlank()) {
                    Text("Token: $token", style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onFind) { Text("SCAN") }
                OutlinedButton(onClick = onTrigger, enabled = paired != null) { Text("Trigger") }
                OutlinedButton(onClick = onClear, enabled = paired != null) { Text("Unpair") }
            }
        }
    }
}

@Composable
private fun CallsTab(
    live: Boolean,
    missed: Boolean,
    hasPhoneState: Boolean,
    hasCallLog: Boolean,
    onRequestPermissions: () -> Unit,
    onLiveChanged: (Boolean) -> Unit,
    onMissedChanged: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Permissions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("READ_PHONE_STATE: ${if (hasPhoneState) "OK" else "NO ACCESS"}")
                Text("READ_CALL_LOG: ${if (hasCallLog) "OK" else "NO ACCESS"}")
                OutlinedButton(onClick = onRequestPermissions) { Text("Ask for permission") }
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = live, onCheckedChange = onLiveChanged)
                    Spacer(Modifier.width(8.dp))
                    Text("Send phone ringing notification")
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = missed, onCheckedChange = onMissedChanged)
                    Spacer(Modifier.width(8.dp))
                    Text("Send missed calls")
                }
            }
        }
    }
}

@Composable
private fun NotificationsTab(
    enabled: Boolean,
    listenerEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onChanged: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Notifications Access", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Notification Listener: ${if (listenerEnabled) "OK" else "NO ACCESS"}")
                OutlinedButton(onClick = onOpenSettings) { Text("Open Settings") }
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = enabled, onCheckedChange = onChanged)
                    Spacer(Modifier.width(8.dp))
                    Text("Send notifications")
                }
            }
        }
    }
}
