package com.highcentrality.htdhirp.core

/**
 * Minimal byte pipe to the radio. Implemented over `usb-serial-for-android` on the
 * phone, and by an in-memory fake in tests, so the protocol logic is the same in both.
 */
interface SerialTransport {
    fun write(data: ByteArray)

    /** Reads up to [count] bytes, returning fewer only if [timeoutMs] expires first. */
    fun read(count: Int, timeoutMs: Long): ByteArray

    /** Drops anything received but not yet read. */
    fun discardInput()
}

class ProtocolException(message: String) : Exception(message)
