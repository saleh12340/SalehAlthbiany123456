package com.example.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.*

sealed class PrinterConnectionState {
    object Disconnected : PrinterConnectionState()
    object Connecting : PrinterConnectionState()
    data class Connected(val deviceName: String, val address: String) : PrinterConnectionState()
    data class Error(val message: String) : PrinterConnectionState()
}

data class BluetoothPrinterDevice(
    val name: String,
    val address: String,
    val isPaired: Boolean = true
)

class BluetoothPrinterManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("printer_prefs", Context.MODE_PRIVATE)

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var currentSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private val _connectionState = MutableStateFlow<PrinterConnectionState>(PrinterConnectionState.Disconnected)
    val connectionState: StateFlow<PrinterConnectionState> = _connectionState.asStateFlow()

    private val _paperSize = MutableStateFlow(prefs.getString("paper_size", "58mm") ?: "58mm")
    val paperSize: StateFlow<String> = _paperSize.asStateFlow()

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    companion object {
        private const val TAG = "BluetoothPrinter"
        const val PREF_LAST_DEVICE_ADDRESS = "last_printer_address"
        const val PREF_LAST_DEVICE_NAME = "last_printer_name"
        const val PREF_PAPER_SIZE = "paper_size"
        const val PREF_AUTO_PRINT = "auto_print"
    }

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun setPaperSize(size: String) {
        prefs.edit().putString(PREF_PAPER_SIZE, size).apply()
        _paperSize.value = size
    }

    fun isAutoPrintEnabled(): Boolean {
        return prefs.getBoolean(PREF_AUTO_PRINT, false)
    }

    fun setAutoPrintEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_PRINT, enabled).apply()
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothPrinterDevice> {
        if (!isBluetoothSupported() || !isBluetoothEnabled()) return emptyList()
        return try {
            bluetoothAdapter?.bondedDevices?.map { device ->
                BluetoothPrinterDevice(
                    name = device.name ?: "جهاز طابعة غير معروف",
                    address = device.address,
                    isPaired = true
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting paired devices", e)
            emptyList()
        }
    }

    fun getLastSavedPrinterAddress(): String? = prefs.getString(PREF_LAST_DEVICE_ADDRESS, null)
    fun getLastSavedPrinterName(): String? = prefs.getString(PREF_LAST_DEVICE_NAME, null)

    @SuppressLint("MissingPermission")
    suspend fun connect(deviceAddress: String, deviceName: String = "طابعة حرارية"): Boolean =
        withContext(Dispatchers.IO) {
            if (bluetoothAdapter == null) {
                _connectionState.value = PrinterConnectionState.Error("البلوتوث غير مدعوم على هذا الجهاز")
                return@withContext false
            }

            if (!bluetoothAdapter.isEnabled) {
                _connectionState.value = PrinterConnectionState.Error("يرجى تشغيل البلوتوث أولاً")
                return@withContext false
            }

            _connectionState.value = PrinterConnectionState.Connecting

            disconnect()

            try {
                val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)
                // Cancel discovery before connecting
                try {
                    bluetoothAdapter.cancelDiscovery()
                } catch (e: Exception) {
                    Log.w(TAG, "Cancel discovery warning: ${e.message}")
                }

                val socket = device.createRfcommSocketToServiceRecord(sppUuid)
                socket.connect()

                currentSocket = socket
                outputStream = socket.outputStream

                // Save last successfully connected printer
                prefs.edit()
                    .putString(PREF_LAST_DEVICE_ADDRESS, deviceAddress)
                    .putString(PREF_LAST_DEVICE_NAME, deviceName)
                    .apply()

                _connectionState.value = PrinterConnectionState.Connected(deviceName, deviceAddress)
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed to $deviceAddress", e)
                disconnect()
                _connectionState.value = PrinterConnectionState.Error("فشل الاتصال بالطابعة: ${e.localizedMessage ?: "تأكد من تشغيل الطابعة واقترانها"}")
                return@withContext false
            }
        }

    fun disconnect() {
        try {
            outputStream?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing stream", e)
        }
        try {
            currentSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket", e)
        }
        outputStream = null
        currentSocket = null
        _connectionState.value = PrinterConnectionState.Disconnected
    }

    suspend fun sendData(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val stream = outputStream
        if (stream == null || currentSocket?.isConnected != true) {
            // Attempt auto-reconnect to last saved printer if available
            val lastAddress = getLastSavedPrinterAddress()
            val lastName = getLastSavedPrinterName() ?: "طابعة حرارية"
            if (lastAddress != null) {
                val reconnected = connect(lastAddress, lastName)
                if (reconnected && outputStream != null) {
                    return@withContext try {
                        outputStream?.write(bytes)
                        outputStream?.flush()
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error writing data after reconnect", e)
                        false
                    }
                }
            }
            _connectionState.value = PrinterConnectionState.Error("الطابعة غير متصلة، يرجى الاتصال بالطابعة")
            return@withContext false
        }

        try {
            stream.write(bytes)
            stream.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send data to printer", e)
            _connectionState.value = PrinterConnectionState.Error("فشل إرسال البيانات للطباعة: ${e.message}")
            disconnect()
            false
        }
    }

    suspend fun printBitmap(bitmap: Bitmap): Boolean {
        val rasterCommands = EscPosReceiptFormatter.decodeBitmapToEscPos(bitmap)
        return sendData(rasterCommands)
    }

    suspend fun printTestReceipt(): Boolean = withContext(Dispatchers.IO) {
        val testBitmap = EscPosReceiptFormatter.generateTestReceiptBitmap(
            paperWidth = if (_paperSize.value == "80mm") 576 else 384
        )
        return@withContext printBitmap(testBitmap)
    }
}
