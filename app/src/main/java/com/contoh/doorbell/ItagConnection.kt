package com.contoh.doorbell

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID

/**
 * Mengelola koneksi GATT ke iTag.
 * - Tombol iTag dibaca lewat notifikasi pada karakteristik yang mendukung NOTIFY.
 * - Bel iTag dibunyikan lewat Immediate Alert (0x1802/0x2A06) atau karakteristik FFE1 pada clone umum.
 */
@SuppressLint("MissingPermission")
class ItagConnection(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onStatus(status: Int, pesan: String)
        fun onTombolDitekan()
        fun onInfo(info: String)
    }

    companion object {
        const val TERPUTUS = 0
        const val MENYAMBUNG = 1
        const val TERHUBUNG = 2

        private val UUID_IMMEDIATE_ALERT = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb")
        private val UUID_ALERT_LEVEL = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb")
        private val UUID_CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val UUID_LAYANAN_FFE0 = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        private val UUID_KARAKTERISTIK_FFE1 = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    }

    private val bluetooth = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val handler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var karakteristikAlert: BluetoothGattCharacteristic? = null
    private val hentikanBunyi = Runnable { kirimNilai(0x00) }

    fun sambung(mac: String) {
        tutup()
        handler.post { listener.onStatus(MENYAMBUNG, "Menghubungkan ke iTag...") }
        val perangkat = bluetooth.getRemoteDevice(mac)
        gatt = perangkat.connectGatt(context, true, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun tutup() {
        handler.removeCallbacks(hentikanBunyi)
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        karakteristikAlert = null
    }

    /** Mengembalikan false jika iTag belum siap. */
    fun bunyikan(): Boolean {
        if (gatt == null || karakteristikAlert == null) return false
        kirimNilai(0x02)
        handler.removeCallbacks(hentikanBunyi)
        handler.postDelayed(hentikanBunyi, 3000L)
        return true
    }

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    handler.post { listener.onStatus(MENYAMBUNG, "Terhubung, menyiapkan tombol...") }
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    karakteristikAlert = null
                    handler.post { listener.onStatus(TERPUTUS, "Terputus, mencoba menyambung lagi...") }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            aktifkanTombol(g)
            karakteristikAlert = cariKarakteristikAlert(g)

            val info = buildString {
                g.services.forEach { layanan ->
                    appendLine("Layanan ${layanan.uuid}")
                    layanan.characteristics.forEach { c ->
                        appendLine("  ${c.uuid} (properti ${c.properties})")
                    }
                }
            }
            handler.post {
                listener.onInfo(info)
                listener.onStatus(TERHUBUNG, "iTag siap. Tekan tombol di iTag untuk bel.")
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, karakteristik: BluetoothGattCharacteristic) {
            handler.post { listener.onTombolDitekan() }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            karakteristik: BluetoothGattCharacteristic,
            nilai: ByteArray
        ) {
            handler.post { listener.onTombolDitekan() }
        }
    }

    private fun aktifkanTombol(g: BluetoothGatt) {
        val semua = g.services.flatMap { it.characteristics }
        val tombol = semua.firstOrNull { it.uuid == UUID_KARAKTERISTIK_FFE1 && punyaProperti(it, BluetoothGattCharacteristic.PROPERTY_NOTIFY) }
            ?: semua.firstOrNull { punyaProperti(it, BluetoothGattCharacteristic.PROPERTY_NOTIFY) }
            ?: return

        g.setCharacteristicNotification(tombol, true)
        val cccd = tombol.getDescriptor(UUID_CCCD) ?: return
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        g.writeDescriptor(cccd)
    }

    private fun cariKarakteristikAlert(g: BluetoothGatt): BluetoothGattCharacteristic? {
        g.getService(UUID_IMMEDIATE_ALERT)?.getCharacteristic(UUID_ALERT_LEVEL)?.let { return it }

        val layanan = g.getService(UUID_LAYANAN_FFE0) ?: return null
        val ffe1 = layanan.getCharacteristic(UUID_KARAKTERISTIK_FFE1)
        if (ffe1 != null && punyaWrite(ffe1)) return ffe1
        return layanan.characteristics.firstOrNull { punyaWrite(it) }
    }

    private fun kirimNilai(nilai: Int) {
        val g = gatt ?: return
        val k = karakteristikAlert ?: return
        k.writeType = if (punyaProperti(k, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        k.value = byteArrayOf(nilai.toByte())
        g.writeCharacteristic(k)
    }

    private fun punyaProperti(k: BluetoothGattCharacteristic, p: Int): Boolean =
        (k.properties and p) != 0

    private fun punyaWrite(k: BluetoothGattCharacteristic): Boolean =
        punyaProperti(k, BluetoothGattCharacteristic.PROPERTY_WRITE) ||
            punyaProperti(k, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
}
