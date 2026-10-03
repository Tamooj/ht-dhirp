package com.highcentrality.htdhirp.app

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.highcentrality.htdhirp.core.ChirpImageFile
import com.highcentrality.htdhirp.core.CloneSession
import com.highcentrality.htdhirp.core.ProtocolException
import com.highcentrality.htdhirp.core.RadioImage
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * M0b spike: prove the phone can talk to the radio. READ ONLY.
 * Connect, handshake, read the whole image, show a few decoded channels,
 * and save a CHIRP-compatible .img to Downloads for comparison on a PC.
 */
class SpikeActivity : Activity() {
    private lateinit var usb: UsbManager
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private val io = Executors.newSingleThreadExecutor()

    private var port: UsbSerialPort? = null
    private var session: CloneSession? = null

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (!granted) {
                say("USB permission denied.")
                return
            }
            say("USB permission granted.")
            io.execute { openAndHandshake() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usb = getSystemService(USB_SERVICE) as UsbManager
        buildUi()

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(permissionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(permissionReceiver, filter)
        }

        say("ht-dhirp spike 0.0.1 (read only)")
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) say("Launched by cable attach.")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(permissionReceiver)
        closePort()
        io.shutdown()
    }

    private fun buildUi() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Before you start: radio ON, cable plugged in fully, antenna OFF or dummy load " +
                "connected (a radio can key its transmitter if communication goes wrong). " +
                "This spike only reads the radio."
            textSize = 14f
        })
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        buttons.addView(Button(this).apply {
            text = "Connect + handshake"
            setOnClickListener { connect() }
        })
        buttons.addView(Button(this).apply {
            text = "Read radio"
            setOnClickListener { io.execute { readRadio() } }
        })
        root.addView(buttons)
        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
        }
        scroll = ScrollView(this).apply { addView(logView) }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun say(msg: String) {
        runOnUiThread {
            logView.append(msg + "\n")
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun connect() {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usb)
        if (drivers.isEmpty()) {
            say("No supported USB serial device found. Devices seen:")
            usb.deviceList.values.forEach { say("  ${describe(it)}") }
            return
        }
        val device = drivers.first().device
        say("Found ${describe(device)}")
        if (usb.hasPermission(device)) {
            io.execute { openAndHandshake() }
        } else {
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION).setPackage(packageName), flags)
            say("Requesting USB permission...")
            usb.requestPermission(device, pi)
        }
    }

    private fun openAndHandshake() {
        try {
            closePort()
            val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usb).firstOrNull()
                ?: return say("Cable no longer present.")
            val connection = usb.openDevice(driver.device) ?: return say("Could not open the USB device (permission?).")
            val p = driver.ports.first()
            p.open(connection)
            p.setParameters(BAUD, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port = p
            say("Port open at $BAUD 8N1 (${driver.javaClass.simpleName}). Handshaking...")

            val s = CloneSession(UsbSerialTransport(p), timeoutMs = 1500)
            val info = s.handshake()
            session = s
            say("Handshake OK.")
            say("  device info: ${info.infoHex}")
            say("  model:       ${info.modelText}")
            say("  key index:   ${s.keyIndex}")
        } catch (e: Exception) {
            say("ERROR during connect: ${e.message ?: e.javaClass.simpleName}")
            closePort()
        }
    }

    private fun readRadio() {
        val s = session ?: return say("Not connected: tap \"Connect + handshake\" first.")
        try {
            say("Reading radio...")
            val started = System.currentTimeMillis()
            var lastReport = 0
            val data = s.readImage { done, total ->
                if (done - lastReport >= 100 || done == total) {
                    lastReport = done
                    say("  $done / $total blocks")
                }
            }
            say("Read ${data.size} bytes in ${System.currentTimeMillis() - started} ms.")
            say("SHA-256: ${sha256(data)}")

            val image = RadioImage(data)
            val used = image.usedSlots()
            say("Channels in use: ${used.size}")
            for (slot in used.take(PREVIEW_CHANNELS)) {
                val ch = runCatching { image.channel(slot) }.getOrNull()
                say(if (ch == null) "  #${slot + 1}: (unreadable)" else
                    "  #%d %-12s %.4f MHz".format(slot + 1, ch.name, ch.rxHz / 1e6))
            }

            val name = "ht-dhirp_uv5rm_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.img"
            saveToDownloads(name, ChirpImageFile.create(data, METADATA_JSON).serialize())
            say("Saved Downloads/$name")
            say("Compare it with a desktop CHIRP read of the same radio.")
        } catch (e: ProtocolException) {
            say("PROTOCOL ERROR: ${e.message}")
            closePort()
        } catch (e: Exception) {
            say("ERROR during read: ${e.message ?: e.javaClass.simpleName}")
            closePort()
        }
    }

    private fun saveToDownloads(name: String, bytes: ByteArray) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("could not create file in Downloads")
        contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
    }

    private fun closePort() {
        session = null
        try {
            port?.close()
        } catch (_: Exception) {
        }
        port = null
    }

    private fun describe(d: UsbDevice) =
        "%04X:%04X %s".format(d.vendorId, d.productId, d.productName ?: "")

    private fun sha256(b: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private companion object {
        const val ACTION_USB_PERMISSION = "com.highcentrality.htdhirp.USB_PERMISSION"
        const val BAUD = 115200
        const val PREVIEW_CHANNELS = 8
        const val METADATA_JSON =
            """{"rclass": "DynamicRadioAlias", "vendor": "Baofeng", "model": "5RM", "variant": "", "chirp_version": "next-20260807"}"""
    }
}
