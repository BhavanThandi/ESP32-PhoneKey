package com.example.phonekey

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattDescriptor

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    val log = mutableStateListOf<String>()
    val status = mutableStateOf("Idle")
    val lockValue = mutableStateOf<Int?>(null)   // null = unknown
    val pendingTarget = mutableStateOf<Int?>(null)   // null = idle, 0/1 = awaiting that state
    private val main = Handler(Looper.getMainLooper())
    private val adapter by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val scanner get() = adapter?.bluetoothLeScanner

    private var scanning = false
    var foundDevice: BluetoothDevice? = null
        private set
    private var gatt: BluetoothGatt? = null
    private var lockState: BluetoothGattCharacteristic? = null
    private var command: BluetoothGattCharacteristic? = null
    fun logLine(line: String) = main.post {
        log.add(line)
        if (log.size > 60) log.removeAt(0)
    }

    private fun setStatus(s: String) = main.post { status.value = s }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            logLine("Found ${device.name ?: "unnamed"} @ ${device.address}  rssi ${result.rssi}")
            foundDevice = device
            setStatus("Found ${device.name ?: device.address}")
            stopScan()
        }

        override fun onScanFailed(errorCode: Int) {
            logLine("Scan FAILED, error code $errorCode")
            setStatus("Scan failed")
            scanning = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                logLine("Connection error, status $status")
                setStatus("Error $status")
                g.close()
                gatt = null
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    logLine("Connected - discovering services")
                    setStatus("Discovering")
                    g.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    logLine("Disconnected")
                    setStatus("Disconnected")
                    g.close()
                    gatt = null
                    lockState = null
                    command = null
                    clearPending()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                logLine("Service discovery failed, status $status")
                return
            }
            logLine("Services discovered: ${g.services.size}")

            // ---- YOU WRITE THIS ----
            // 1. val service = g.getService(BleUuids.SERVICE)
            val service = g.getService(BleUuids.SERVICE)
            //    if null -> logLine("Service NOT found"); return
            if (service == null) {
                logLine("Service NOT found")
                return
            } else {
                logLine("Service found")
            }
            // 2. lockState = service.getCharacteristic(BleUuids.LOCK_STATE)
            lockState = service.getCharacteristic(BleUuids.LOCK_STATE)
            if (lockState == null) {
                logLine("lockState NOT found")
                return
            } else {
                logLine("LockState found")
            }
            // 3. command   = service.getCharacteristic(BleUuids.COMMAND)
            command = service.getCharacteristic(BleUuids.COMMAND)
            if (command == null) {
                logLine("Command NOT found")
                return
            } else {
                logLine("Command found")
            }
            // ---- YOU WRITE THIS: the two-part subscribe ----
            lockState?.let { characteristic ->

                val localOk = g.setCharacteristicNotification(characteristic, true)
                logLine("Local notification routing: $localOk")

                val cccd = characteristic.getDescriptor(BleUuids.CCCD)
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(cccd)
                // ------------------------// 4. logLine whether each is null or found

                // ------------------------
            }

        }

        override fun onDescriptorWrite(
            g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logLine("Subscribed to lockState")
                setStatus("Subscribed")
            } else {
                logLine("CCCD write FAILED, status $status")
            }
            // Chained: only now is it safe to start the next operation.
            lockState?.let { g.readCharacteristic(it) }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                logLine("Read failed, status $status")
                return
            }
            val b = c.value?.firstOrNull()?.toInt()
            logLine("Read lockState = $b")
            main.post { lockValue.value = b }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt, c: BluetoothGattCharacteristic
        ) {
            // ---- YOU WRITE THIS ----
            // Same shape as onCharacteristicRead's body, minus the status check
            // (notifications carry no status). Pull the first byte out of
            // c.value, log it, and update lockValue via main.post { }.
            // ------------------------
            val b = c.value?.firstOrNull()?.toInt()
            logLine("Notify lockState = $b")
            main.post { lockValue.value = b }
            clearPending()
        }
    }

    fun startScan() {
        if (scanning) return

        // ---- YOU WRITE THIS ----
        // Build a filter that only accepts advertisements carrying your
        // service UUID. Look at ScanFilter.Builder():
        //   .setServiceUuid(ParcelUuid(...))  then .build()
        val filters = listOf<ScanFilter>(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(BleUuids.SERVICE)).build()
        )
        // ------------------------

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanning = true
        log.clear()
        logLine("Scanning...")
        setStatus("Scanning")
        scanner?.startScan(filters, settings, scanCallback)

        main.postDelayed({
            if (scanning) {
                stopScan()
                logLine("Scan timed out - nothing found")
                setStatus("Not found")
            }
        }, 10_000)
    }

    fun stopScan() {
        if (!scanning) return
        scanning = false
        scanner?.stopScan(scanCallback)
    }

    fun connect() {
        val device = foundDevice ?: run {
            logLine("No device - scan first")
            return
        }
        logLine("Connecting to ${device.address}")
        setStatus("Connecting")
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect()
    }

    private val pendingTimeout = Runnable {
        if (pendingTarget.value != null) {
            logLine("No confirmation within 3s")
            pendingTarget.value = null
        }
    }

    private fun clearPending() {
        main.removeCallbacks(pendingTimeout)
        main.post { pendingTarget.value = null }
    }

    @Suppress("DEPRECATION")
    fun sendCommand(value: Int) {
        val g = gatt
        val c = command
        if (g == null || c == null) {
            logLine("Not connected")
            return
        }
        c.value = byteArrayOf(value.toByte())
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val ok = g.writeCharacteristic(c)
        logLine("Sent command $value (queued: $ok)")
        if (!ok) return

        main.post { pendingTarget.value = value }
        main.removeCallbacks(pendingTimeout)
        main.postDelayed(pendingTimeout, 3_000)
    }
}