package com.contoh.doorbell

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), ItagService.Pendengar {

    companion object {
        private const val KODE_PILIH_MP3 = 101
        private const val KODE_IZIN = 102
    }

    private lateinit var kartuStatus: LinearLayout
    private lateinit var dot: View
    private lateinit var txtStatus: TextView
    private lateinit var txtMac: TextView
    private lateinit var txtSuara: TextView
    private lateinit var bel: FrameLayout
    private lateinit var txtTerakhir: TextView
    private lateinit var kartuPindai: LinearLayout
    private lateinit var txtPindai: TextView
    private lateinit var listPerangkat: ListView
    private lateinit var adapterPerangkat: PerangkatAdapter

    private val pemindai by lazy { PemindaiItag(this) }
    private var aksiTertunda: (() -> Unit)? = null
    private var animasiDot: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        kartuStatus = findViewById(R.id.kartuStatus)
        dot = findViewById(R.id.dot)
        txtStatus = findViewById(R.id.txtStatus)
        txtMac = findViewById(R.id.txtMac)
        txtSuara = findViewById(R.id.txtSuara)
        bel = findViewById(R.id.bel)
        txtTerakhir = findViewById(R.id.txtTerakhir)
        kartuPindai = findViewById(R.id.kartuPindai)
        txtPindai = findViewById(R.id.txtPindai)
        listPerangkat = findViewById(R.id.listPerangkat)

        gayaTombol(findViewById<Button>(R.id.btnPilih), getColor(R.color.biru), getColor(R.color.biru2), R.drawable.ic_bluetooth)
        gayaTombol(findViewById<Button>(R.id.btnMp3), getColor(R.color.ungu), getColor(R.color.pink), R.drawable.ic_musik)
        gayaTombol(findViewById<Button>(R.id.btnTes), getColor(R.color.hijau), getColor(R.color.hijau2), R.drawable.ic_play)
        gayaTombol(findViewById<Button>(R.id.btnHentikan), getColor(R.color.merah), getColor(R.color.merah2), R.drawable.ic_stop)
        gayaTombol(findViewById<Button>(R.id.btnSambung), getColor(R.color.teal), getColor(R.color.teal2), R.drawable.ic_bluetooth)
        gayaTombol(findViewById<Button>(R.id.btnPutus), getColor(R.color.oranye), getColor(R.color.oranye2), R.drawable.ic_silang)
        gayaTombol(findViewById<Button>(R.id.btnLupakan), getColor(R.color.abu), getColor(R.color.abu2), R.drawable.ic_silang)

        // Ukuran tombol bel menyesuaikan lebar layar
        val lebarBel = (resources.configuration.screenWidthDp * 0.5f).coerceIn(150f, 220f).toInt()
        bel.layoutParams = LinearLayout.LayoutParams(dp(lebarBel), dp(lebarBel)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        bel.setOnClickListener { tekanBel() }
        efekTekan(bel)

        kartuStatus.setOnLongClickListener {
            tampilkanInfo()
            true
        }

        findViewById<Button>(R.id.btnPilih).setOnClickListener { mulaiPindai() }
        findViewById<Button>(R.id.btnMp3).setOnClickListener { bukaPemilihMp3() }
        findViewById<Button>(R.id.btnTes).setOnClickListener { PemutarBel.mainkan(applicationContext) }
        findViewById<Button>(R.id.btnHentikan).setOnClickListener { hentikanMusik() }
        findViewById<Button>(R.id.btnSambung).setOnClickListener { sambungUlang() }
        findViewById<Button>(R.id.btnPutus).setOnClickListener { putusItag() }
        findViewById<Button>(R.id.btnLupakan).setOnClickListener { konfirmasiLupakan() }
        findViewById<TextView>(R.id.btnTutupPindai).setOnClickListener {
            pemindai.berhenti()
            kartuPindai.visibility = View.GONE
        }

        adapterPerangkat = PerangkatAdapter(this) { pilihPerangkat(it) }
        listPerangkat.adapter = adapterPerangkat

        perbaruiTampilan()

        // Sambung otomatis hanya jika pengguna belum menekan Putuskan
        if (Pengaturan.itagTersimpan(this) != null && Pengaturan.inginTersambung(this)) {
            pastikanIzin { jalankanServis(ItagService.AKSI_SAMBUNG) }
        }
    }

    override fun onResume() {
        super.onResume()
        ItagService.pendengar = this
        perbaruiTampilan()
    }

    override fun onPause() {
        super.onPause()
        if (ItagService.pendengar === this) ItagService.pendengar = null
    }

    override fun onDestroy() {
        super.onDestroy()
        pemindai.berhenti()
    }

    // ---------- Callback dari layanan ----------

    override fun onStatus(status: Int, pesan: String) {
        txtStatus.text = pesan
        warnaDot(status)
    }

    override fun onBelDitekan() {
        val waktu = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        txtTerakhir.text = "Terakhir ditekan pukul $waktu"
        animasiBel()
    }

    // ---------- Tampilan ----------

    private fun perbaruiTampilan() {
        val tersimpan = Pengaturan.itagTersimpan(this)
        txtMac.text = if (tersimpan != null) "${tersimpan.nama} • ${tersimpan.mac}" else "Belum ada iTag dipilih"
        txtSuara.text = if (Pengaturan.adaSuaraKustom(this)) {
            "Bunyi: ${Pengaturan.namaSuara(this) ?: "file mp3"}"
        } else {
            "Bunyi: bel bawaan"
        }
        if (tersimpan == null) {
            onStatus(ItagConnection.TERPUTUS, "Ketuk Pilih iTag untuk memulai")
        } else if (!Pengaturan.inginTersambung(this)) {
            onStatus(ItagConnection.TERPUTUS, "iTag diputuskan. Ketuk Sambungkan untuk menyambung lagi.")
        }
    }

    private fun warnaDot(status: Int) {
        val warna = when (status) {
            ItagConnection.TERHUBUNG -> getColor(R.color.hijau)
            ItagConnection.MENYAMBUNG -> getColor(R.color.oranye)
            else -> getColor(R.color.merah)
        }
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(warna)
        }

        animasiDot?.cancel()
        if (status == ItagConnection.TERHUBUNG) {
            dot.alpha = 1f
            animasiDot = null
        } else {
            animasiDot = ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.2f).apply {
                duration = 800
                repeatMode = ObjectAnimator.REVERSE
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        }
    }

    private fun animasiBel() {
        bel.animate().scaleX(1.06f).scaleY(1.06f).setDuration(90).withEndAction {
            bel.animate().rotation(14f).setDuration(70).withEndAction {
                bel.animate().rotation(-14f).setDuration(110).withEndAction {
                    bel.animate().rotation(0f).scaleX(1f).scaleY(1f)
                        .setDuration(320)
                        .setInterpolator(OvershootInterpolator(2f))
                        .start()
                }.start()
            }.start()
        }.start()
    }

    private fun tampilkanInfo() {
        AlertDialog.Builder(this)
            .setTitle("Info GATT iTag")
            .setMessage(ItagService.infoTerakhir)
            .setPositiveButton("Tutup", null)
            .show()
    }

    // ---------- Aksi pengguna ----------

    private fun tekanBel() {
        if (Pengaturan.itagTersimpan(this) == null) {
            Toast.makeText(this, "Pilih iTag terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        pastikanIzin { jalankanServis(ItagService.AKSI_BUNYIKAN) }
    }

    private fun hentikanMusik() {
        PemutarBel.hentikan()
        Toast.makeText(this, "Musik dihentikan", Toast.LENGTH_SHORT).show()
    }

    private fun sambungUlang() {
        if (Pengaturan.itagTersimpan(this) == null) {
            Toast.makeText(this, "Pilih iTag terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        pastikanIzin { jalankanServis(ItagService.AKSI_SAMBUNG) }
    }

    private fun putusItag() {
        jalankanServis(ItagService.AKSI_PUTUS)
        Toast.makeText(this, "iTag diputuskan", Toast.LENGTH_SHORT).show()
        perbaruiTampilan()
    }

    private fun konfirmasiLupakan() {
        AlertDialog.Builder(this)
            .setTitle("Lupakan iTag?")
            .setMessage("iTag akan dihapus dari aplikasi. Kamu perlu memilih ulang untuk menyambung lagi.")
            .setPositiveButton("Lupakan") { _, _ ->
                jalankanServis(ItagService.AKSI_LUPAKAN)
                perbaruiTampilan()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun mulaiPindai() {
        if (!bluetoothAktif()) return
        pastikanIzin {
            adapterPerangkat.setData(emptyList())
            txtPindai.text = "Mencari perangkat di sekitar..."
            kartuPindai.visibility = View.VISIBLE
            kartuPindai.alpha = 0f
            kartuPindai.animate().alpha(1f).setDuration(250).start()

            pemindai.onUpdate = { daftar ->
                adapterPerangkat.setData(daftar)
                txtPindai.text = if (daftar.isEmpty()) {
                    "Mencari perangkat di sekitar..."
                } else {
                    "Ketuk iTag untuk memilih"
                }
            }
            pemindai.onSelesai = {
                txtPindai.text = if (adapterPerangkat.count == 0) {
                    "Tidak ada perangkat ditemukan. Pastikan iTag dekat dan menyala."
                } else {
                    "Pencarian selesai. Ketuk iTag untuk memilih."
                }
            }
            pemindai.mulai()
        }
    }

    private fun pilihPerangkat(p: Perangkat) {
        pemindai.berhenti()
        kartuPindai.visibility = View.GONE
        Pengaturan.simpanItag(this, p.mac, p.nama)
        jalankanServis(ItagService.AKSI_SAMBUNG, p.mac)
        perbaruiTampilan()
        Toast.makeText(this, "${p.nama} dipilih", Toast.LENGTH_SHORT).show()
    }

    private fun bluetoothAktif(): Boolean {
        val adapter: BluetoothAdapter? = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null) {
            Toast.makeText(this, "Perangkat ini tidak punya Bluetooth", Toast.LENGTH_LONG).show()
            return false
        }
        if (!adapter.isEnabled) {
            Toast.makeText(this, "Nyalakan Bluetooth dulu", Toast.LENGTH_SHORT).show()
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return false
        }
        return true
    }

    private fun jalankanServis(aksi: String, mac: String? = null) {
        val intent = Intent(this, ItagService::class.java).apply {
            action = aksi
            if (mac != null) putExtra(ItagService.EXTRA_MAC, mac)
        }
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // ---------- Izin ----------

    private fun izinBluetooth(): List<String> =
        if (Build.VERSION.SDK_INT >= 31) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun pastikanIzin(lanjut: () -> Unit) {
        val semua = izinBluetooth().toMutableList()
        if (Build.VERSION.SDK_INT >= 33) semua.add(Manifest.permission.POST_NOTIFICATIONS)

        val belum = semua.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (belum.isEmpty()) {
            lanjut()
            return
        }
        aksiTertunda = lanjut
        requestPermissions(belum.toTypedArray(), KODE_IZIN)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != KODE_IZIN) return

        val aksi = aksiTertunda
        aksiTertunda = null
        val btOk = izinBluetooth().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (btOk) {
            aksi?.invoke()
        } else {
            Toast.makeText(this, "Izin Bluetooth diperlukan agar iTag bisa dipakai", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- MP3 ----------

    private fun bukaPemilihMp3() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, KODE_PILIH_MP3)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == KODE_PILIH_MP3 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val berhasil = Pengaturan.salinSuara(this, uri, namaFile(uri))
            Toast.makeText(
                this,
                if (berhasil) "Bel MP3 tersimpan" else "Gagal menyalin MP3",
                Toast.LENGTH_SHORT
            ).show()
            perbaruiTampilan()
        }
    }

    private fun namaFile(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return uri.lastPathSegment ?: "file dipilih"
    }
}
