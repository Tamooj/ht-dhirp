package com.highcentrality.htdhirp.app

import android.os.SystemClock
import com.highcentrality.htdhirp.core.SerialTransport
import com.hoho.android.usbserial.driver.UsbSerialPort

/** Adapts a `usb-serial-for-android` port to the core library's byte pipe. */
class UsbSerialTransport(private val port: UsbSerialPort) : SerialTransport {
    private val buf = ByteArray(4096)

    /** Bytes received beyond what the last [read] asked for. */
    private var carry = ByteArray(0)

    override fun write(data: ByteArray) {
        port.write(data, WRITE_TIMEOUT_MS)
    }

    override fun read(count: Int, timeoutMs: Long): ByteArray {
        val out = ByteArray(count)
        var got = 0
        if (carry.isNotEmpty()) {
            val n = minOf(carry.size, count)
            carry.copyInto(out, 0, 0, n)
            carry = carry.copyOfRange(n, carry.size)
            got = n
        }
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (got < count) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) break
            val n = port.read(buf, remaining.toInt())
            if (n <= 0) continue
            val take = minOf(n, count - got)
            buf.copyInto(out, got, 0, take)
            got += take
            if (n > take) carry += buf.copyOfRange(take, n)
        }
        return if (got == count) out else out.copyOf(got)
    }

    override fun discardInput() {
        carry = ByteArray(0)
        try {
            port.purgeHwBuffers(false, true)
        } catch (_: UnsupportedOperationException) {
            // Driver can't purge; the drain loop below still empties it.
        }
        while (port.read(buf, DRAIN_TIMEOUT_MS) > 0) { /* discard */ }
    }

    private companion object {
        const val WRITE_TIMEOUT_MS = 2000
        const val DRAIN_TIMEOUT_MS = 50
    }
}
