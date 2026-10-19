package com.contoh.doorbell

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper

data class Perangkat(val mac: String, val nama: String, val sinyal: Int) {
    val adalahItag: Boolean
        get() = nama.contains("itag", ignoreCase = true)
}

@SuppressLint("MissingPermission")
class PemindaiItag(context: Context) {

    var onUpdate: ((List<Perangkat>) -> Unit)? = null
    var onSelesai: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val pemindai: BluetoothLeScanner? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bluetoothLeScanner
    private val temuan = LinkedHashMap<String, Perangkat>()
    private var berjalan = false

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val nama = result.device.name ?: result.scanRecord?.deviceName ?: return
            val mac = result.device.address
            temuan[mac] = Perangkat(mac, nama, result.rssi)
            val daftar = urutkan()
            handler.post { onUpdate?.invoke(daftar) }
        }
    }

    private val hentikanOtomatis = Runnable { berhenti() }

    fun mulai() {
        if (berjalan || pemindai == null) return
        temuan.clear()
        val setting = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        pemindai.startScan(null, setting, callback)
        berjalan = true
        handler.postDelayed(hentikanOtomatis, 12_000L)
    }

    fun berhenti() {
        if (!berjalan) return
        handler.removeCallbacks(hentikanOtomatis)
        pemindai?.stopScan(callback)
        berjalan = false
        onSelesai?.invoke()
    }

    private fun urutkan(): List<Perangkat> =
        temuan.values.sortedWith(
            compareByDescending<Perangkat> { it.adalahItag }.thenByDescending { it.sinyal }
        )
}
