package com.example.phonekey

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    ReadinessScreen(Modifier.padding(padding))
                }
            }
        }
    }
}

/** Legacy model below Android 12, modern model at 12 and above. */
val requiredPermissions: Array<String>
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

fun hasPermissions(context: Context): Boolean =
    requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

fun isBluetoothOn(context: Context): Boolean {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    return manager.adapter?.isEnabled == true
}

/** Only required below Android 12, where BLE scanning counts as location access. */
fun isLocationOn(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return true
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

@Composable
fun ReadinessScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var permissionsOk by remember { mutableStateOf(hasPermissions(context)) }
    var bluetoothOk by remember { mutableStateOf(isBluetoothOn(context)) }
    var locationOk by remember { mutableStateOf(isLocationOn(context)) }

    val ble = remember { BleManager(context) }
    val allReady = permissionsOk && bluetoothOk && locationOk

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsOk = result.values.all { it }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("PhoneKey", style = MaterialTheme.typography.headlineMedium)
        Text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")

        HorizontalDivider()

        StatusRow("Permissions", permissionsOk)
        StatusRow("Bluetooth on", bluetoothOk)
        StatusRow("Location services", locationOk)

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { permissionLauncher.launch(requiredPermissions) },
            enabled = !permissionsOk
        ) { Text("Grant permissions") }

        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }) { Text("Open location settings") }

        Button(onClick = {
            permissionsOk = hasPermissions(context)
            bluetoothOk = isBluetoothOn(context)
            locationOk = isLocationOn(context)
        }) { Text("Re-check") }

        Button(
            onClick = { ble.startScan() },
            enabled = allReady
        ) { Text("Scan") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ble.connect() }, enabled = allReady) { Text("Connect") }
            Button(onClick = { ble.disconnect() }, enabled = allReady) { Text("Disconnect") }
        }

        val lockState = ble.lockValue.value
        val awaiting = ble.pendingTarget.value

        Text(
            when {
                awaiting == 1 -> "Unlocking…"
                awaiting == 0 -> "Locking…"
                lockState == 0 -> "LOCKED"
                lockState == 1 -> "UNLOCKED"
                else -> "Unknown"
            },
            style = MaterialTheme.typography.headlineSmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { ble.sendCommand(1) },
                enabled = awaiting == null && lockState == 0
            ) { Text("UNLOCK") }
            Button(
                onClick = { ble.sendCommand(0) },
                enabled = awaiting == null && lockState == 1
            ) { Text("LOCK") }
        }

        HorizontalDivider()
        Text(ble.status.value, style = MaterialTheme.typography.titleMedium)

        Column(Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())) {
            ble.log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun StatusRow(label: String, ok: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (ok) "✅" else "❌")
        Text(label)
    }
}