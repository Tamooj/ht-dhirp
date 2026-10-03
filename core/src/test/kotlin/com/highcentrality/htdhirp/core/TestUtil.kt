package com.highcentrality.htdhirp.core

fun hex(s: String): ByteArray =
    s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()
