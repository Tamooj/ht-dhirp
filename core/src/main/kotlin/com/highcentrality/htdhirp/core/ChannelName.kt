package com.highcentrality.htdhirp.core

// The accepted character set follows CHIRP's baofeng_uv17Pro.py (https://chirpmyradio.com, GPLv3).
// No CHIRP source is copied.

/** What a UV-5RM channel name may contain: letters, digits, space and a set of symbols, up to 12 characters. */
object ChannelName {
    const val MAX_LENGTH = 12
    private const val SYMBOLS = "!@#\$%^&*()+-=[]:\";'<>?,./"

    fun isValidChar(c: Char): Boolean =
        c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == ' ' || c in SYMBOLS

    fun isValid(name: String): Boolean = name.length <= MAX_LENGTH && name.all(::isValidChar)

    /** Truncates to [MAX_LENGTH] and replaces characters the radio can't show with '-'. */
    fun sanitize(name: String): String =
        name.take(MAX_LENGTH).map { if (isValidChar(it)) it else '-' }.joinToString("")
}
