package org.gnit.lucenekmp.util

internal actual fun unsignedIdToStringPlatform(id: ByteArray): String {
    if (id.isEmpty()) {
        return "0"
    }
    val quotient = id.copyOf()
    var firstNonZero = 0
    while (firstNonZero < quotient.size && quotient[firstNonZero] == 0.toByte()) {
        firstNonZero++
    }
    if (firstNonZero == quotient.size) {
        return "0"
    }

    val digits = CharArray(39)
    var digitCount = 0
    while (firstNonZero < quotient.size) {
        var remainder = 0
        for (i in firstNonZero until quotient.size) {
            val value = (remainder shl 8) + (quotient[i].toInt() and 0xFF)
            quotient[i] = (value / 10).toByte()
            remainder = value % 10
        }
        digits[digitCount++] = ('0'.code + remainder).toChar()
        while (firstNonZero < quotient.size && quotient[firstNonZero] == 0.toByte()) {
            firstNonZero++
        }
    }
    return buildString(digitCount) {
        for (i in digitCount - 1 downTo 0) {
            append(digits[i])
        }
    }
}
