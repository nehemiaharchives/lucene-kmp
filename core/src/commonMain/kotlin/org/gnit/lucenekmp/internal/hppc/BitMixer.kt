package org.gnit.lucenekmp.internal.hppc

import org.gnit.lucenekmp.jdkport.doubleToLongBits
import org.gnit.lucenekmp.jdkport.floatToIntBits


/**
 * Bit mixing utilities. The purpose of these methods is to evenly distribute key space over int32
 * range.
 *
 *
 * Forked from com.carrotsearch.hppc.BitMixer
 *
 *
 * github: https://github.com/carrotsearch/hppc release: 0.10.0
 *
 * @lucene.internal
 */
object BitMixer {
    // Don't bother mixing very small key domains much.
    fun mix(key: Byte): Int {
        return key * PHI_C32
    }

    fun mix(key: Short): Int {
        return BitMixer.mixPhi(key)
    }

    fun mix(key: Char): Int {
        return BitMixer.mixPhi(key)
    }

    // Better mix for larger key domains.
    fun mix(key: Int): Int {
        return mix32(key)
    }

    fun mix(key: Float): Int {
        return mix32(Float.floatToIntBits(key))
    }

    fun mix(key: Double): Int {
        return mix64(Double.doubleToLongBits(key)).toInt()
    }

    fun mix(key: Long): Int {
        return mix64(key).toInt()
    }

    fun mix(key: Any?): Int {
        return if (key == null) 0 else mix32(key.hashCode())
    }

    /** MH3's plain finalization step.  */
    fun mix32(k: Int): Int {
        var k = k
        k = (k xor (k ushr 16)) * -0x7a143595
        k = (k xor (k ushr 13)) * -0x3d4d51cb
        return k xor (k ushr 16)
    }

    /**
     * Computes David Stafford variant 9 of 64bit mix function (MH3 finalization step, with different
     * shifts and constants).
     *
     *
     * Variant 9 is picked because it contains two 32-bit shifts which could be possibly optimized
     * into better machine code.
     *
     * @see "http://zimbry.blogspot.com/2011/09/better-bit-mixing-improving-on.html"
     */
    fun mix64(z: Long): Long {
        var z = z
        z = (z xor (z ushr 32)) * 0x4cd6944c5cc20b6dL
        z = (z xor (z ushr 29)) * -0x3ed3a4e62cda617L
        return z xor (z ushr 32)
    }

    /*
   * Golden ratio bit mixers.
   */
    private const val PHI_C32 = -0x61c88647
    private const val PHI_C64 = -0x61c8864680b583ebL

    fun mixPhi(k: Byte): Int {
        val h = k * PHI_C32
        return h xor (h ushr 16)
    }

    fun mixPhi(k: Char): Int {
        val h = k.code * PHI_C32
        return h xor (h ushr 16)
    }

    fun mixPhi(k: Short): Int {
        val h = k * PHI_C32
        return h xor (h ushr 16)
    }

    fun mixPhi(k: Int): Int {
        val h = k * PHI_C32
        return h xor (h ushr 16)
    }

    fun mixPhi(k: Float): Int {
        val h: Int = Float.floatToIntBits(k) * PHI_C32
        return h xor (h ushr 16)
    }

    fun mixPhi(k: Double): Int {
        val h: Long = Double.doubleToLongBits(k) * PHI_C64
        return (h xor (h ushr 32)).toInt()
    }

    fun mixPhi(k: Long): Int {
        val h = k * PHI_C64
        return (h xor (h ushr 32)).toInt()
    }

    fun mixPhi(k: Any?): Int {
        val h = (if (k == null) 0 else k.hashCode() * PHI_C32)
        return h xor (h ushr 16)
    }
}
