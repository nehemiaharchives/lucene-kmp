package org.gnit.lucenekmp.util

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign

internal actual fun unsignedIdToStringPlatform(id: ByteArray): String {
    return BigInteger.fromByteArray(id, Sign.POSITIVE).toString()
}
