package org.gnit.lucenekmp.util

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import dev.scottpierce.envvar.EnvVar
import org.gnit.lucenekmp.jdkport.*
import kotlin.time.Clock.System
import kotlin.time.ExperimentalTime

/**
 * Methods for manipulating strings.
 *
 * @lucene.internal
 */
@OptIn(ExperimentalTime::class)
object StringHelper {
    /**
     * Compares two [BytesRef], element by element, and returns the number of elements common to
     * both arrays (from the start of each). This method assumes currentTerm comes after priorTerm.
     *
     * @param priorTerm The first [BytesRef] to compare
     * @param currentTerm The second [BytesRef] to compare
     * @return The number of common elements (from the start of each).
     */
    fun bytesDifference(priorTerm: BytesRef, currentTerm: BytesRef?): Int {
        val mismatch: Int =
            Arrays.mismatch(
                priorTerm.bytes,
                priorTerm.offset,
                priorTerm.offset + priorTerm.length,
                currentTerm!!.bytes,
                currentTerm.offset,
                currentTerm.offset + currentTerm.length
            )
        require(mismatch >= 0) { "terms out of order: priorTerm=$priorTerm,currentTerm=$currentTerm" }
        return mismatch
    }

    /**
     * Returns the length of `currentTerm` needed for use as a sort key. so that [ ][BytesRef.compareTo] still returns the same result. This method assumes currentTerm
     * comes after priorTerm.
     */
    fun sortKeyLength(priorTerm: BytesRef, currentTerm: BytesRef): Int {
        return bytesDifference(priorTerm, currentTerm) + 1
    }

    /**
     * Returns `true` iff the ref starts with the given prefix. Otherwise `false
    ` * .
     *
     * @param ref the `byte[]` to test
     * @param prefix the expected prefix
     * @return Returns `true` iff the ref starts with the given prefix. Otherwise `
     * false`.
     */
    fun startsWith(ref: ByteArray, prefix: BytesRef): Boolean {
        // not long enough to start with the prefix
        if (ref.size < prefix.length) {
            return false
        }
        return Arrays.equals(
            ref, 0, prefix.length, prefix.bytes, prefix.offset, prefix.offset + prefix.length
        )
    }

    /**
     * Returns `true` iff the ref starts with the given prefix. Otherwise `false
    ` * .
     *
     * @param ref the [BytesRef] to test
     * @param prefix the expected prefix
     * @return Returns `true` iff the ref starts with the given prefix. Otherwise `
     * false`.
     */
    fun startsWith(ref: BytesRef, prefix: BytesRef): Boolean {
        // not long enough to start with the prefix
        if (ref.length < prefix.length) {
            return false
        }
        return Arrays.equals(
            ref.bytes,
            ref.offset,
            ref.offset + prefix.length,
            prefix.bytes,
            prefix.offset,
            prefix.offset + prefix.length
        )
    }

    /**
     * Returns `true` iff the ref ends with the given suffix. Otherwise `false`.
     *
     * @param ref the [BytesRef] to test
     * @param suffix the expected suffix
     * @return Returns `true` iff the ref ends with the given suffix. Otherwise `false
    ` * .
     */
    fun endsWith(ref: BytesRef, suffix: BytesRef): Boolean {
        val startAt = ref.length - suffix.length
        // not long enough to start with the suffix
        if (startAt < 0) {
            return false
        }
        return Arrays.equals(
            ref.bytes,
            ref.offset + startAt,
            ref.offset + startAt + suffix.length,
            suffix.bytes,
            suffix.offset,
            suffix.offset + suffix.length
        )
    }

    /** Pass this as the seed to [.murmurhash3_x86_32].  */ // Poached from Guava: set a different salt/seed
    // for each JVM instance, to frustrate hash key collision
    // denial of service attacks, and to catch any places that
    // somehow rely on hash function/order across JVM
    // instances:
    var GOOD_FAST_HASH_SEED: Int = 0

    init {
        val prop: String? = EnvVar["tests.seed"] /*java.lang.System.getProperty("tests.seed")*/
        if (prop != null) {
            // So if there is a test failure that relied on hash
            // order, we remain reproducible based on the test seed:
            GOOD_FAST_HASH_SEED = prop.hashCode()
        } else {
            GOOD_FAST_HASH_SEED = System.now().toEpochMilliseconds().toInt()
        }
    }

    /**
     * Returns the MurmurHash3_x86_32 hash. Original source/tests at
     * https://github.com/yonik/java_util/
     */
    fun murmurhash3_x86_32(data: ByteArray, offset: Int, len: Int, seed: Int): Int {
        val c1 = -0x3361d2af
        val c2 = 0x1b873593

        var h1 = seed
        val roundedEnd = offset + (len and -0x4) // round down to 4 byte block

        var i = offset
        while (i < roundedEnd) {
            // little endian load order
            var k1 = /*BitUtil.VH_LE_INT.get(data, i) as Int*/ data.getIntLE(
                offset = i,
                value = 0
            )
            k1 *= c1
            k1 = Int.rotateLeft(k1, 15)
            k1 *= c2

            h1 = h1 xor k1
            h1 = Int.rotateLeft(h1, 13)
            h1 = h1 * 5 + -0x19ab949c
            i += 4
        }

        // tail
        var k1 = 0

        when (len and 0x03) {
            3 -> {
                k1 = (data[roundedEnd + 2].toInt() and 0xff) shl 16
                k1 = k1 or ((data[roundedEnd + 1].toInt() and 0xff) shl 8)
                k1 = k1 or (data[roundedEnd].toInt() and 0xff)
                k1 *= c1
                k1 = Int.rotateLeft(k1, 15)
                k1 *= c2
                h1 = h1 xor k1
            }

            2 -> {
                k1 = k1 or ((data[roundedEnd + 1].toInt() and 0xff) shl 8)
                k1 = k1 or (data[roundedEnd].toInt() and 0xff)
                k1 *= c1
                k1 = Int.rotateLeft(k1, 15)
                k1 *= c2
                h1 = h1 xor k1
            }

            1 -> {
                k1 = k1 or (data[roundedEnd].toInt() and 0xff)
                k1 *= c1
                k1 = Int.rotateLeft(k1, 15)
                k1 *= c2
                h1 = h1 xor k1
            }
        }

        // finalization
        h1 = h1 xor len

        // fmix(h1);
        h1 = h1 xor (h1 ushr 16)
        h1 *= -0x7a143595
        h1 = h1 xor (h1 ushr 13)
        h1 *= -0x3d4d51cb
        h1 = h1 xor (h1 ushr 16)

        return h1
    }

    fun murmurhash3_x86_32(bytes: BytesRef, seed: Int): Int {
        return murmurhash3_x86_32(bytes.bytes, bytes.offset, bytes.length, seed)
    }

    /**
     * Generates 128-bit hash from the byte array with the given offset, length and seed.
     *
     *
     * The code is adopted from Apache Commons ([link](https://commons.apache.org/proper/commons-codec/jacoco/org.apache.commons.codec.digest/MurmurHash3.java.html))
     *
     * @param data The input byte array
     * @param offset The first element of array
     * @param length The length of array
     * @param seed The initial seed value
     * @return The 128-bit hash (2 longs)
     */
    fun murmurhash3_x64_128(
        data: ByteArray, offset: Int, length: Int, seed: Int
    ): LongArray {
        // Use an unsigned 32-bit integer as the seed
        return murmurhash3_x64_128(data, offset, length, seed.toLong() and 0xFFFFFFFFL)
    }

    private fun murmurhash3_x64_128(
        data: ByteArray, offset: Int, length: Int, seed: Long
    ): LongArray {
        var h1 = seed
        var h2 = seed
        val nblocks = length shr 4

        // Constants for 128-bit variant
        val C1 = -0x783c846eeebdac2bL
        val C2 = 0x4cf5ad432745937fL
        val R1 = 31
        val R2 = 27
        val R3 = 33
        val M = 5
        val N1 = 0x52dce729
        val N2 = 0x38495ab5

        // body
        for (i in 0..<nblocks) {
            val index = offset + (i shl 4)
            var k1 = /*BitUtil.VH_LE_LONG.get(data, index) as Long*/ data.getLongLE(offset = index)
            var k2 = /*BitUtil.VH_LE_LONG.get(data, index + 8) as Long*/ data.getLongLE(
                offset = index + 8
            )

            // mix functions for k1
            k1 *= C1
            k1 = Long.rotateLeft(k1, R1)
            k1 *= C2
            h1 = h1 xor k1
            h1 = Long.rotateLeft(h1, R2)
            h1 += h2
            h1 = h1 * M + N1

            // mix functions for k2
            k2 *= C2
            k2 = Long.rotateLeft(k2, R3)
            k2 *= C1
            h2 = h2 xor k2
            h2 = Long.rotateLeft(h2, R1)
            h2 += h1
            h2 = h2 * M + N2
        }

        // tail
        var k1: Long = 0
        var k2: Long = 0
        val index = offset + (nblocks shl 4)
        when (length and 0x0F) {
            15 -> {
                k2 = k2 xor ((data[index + 14].toLong() and 0xffL) shl 48)
                k2 = k2 xor ((data[index + 13].toLong() and 0xffL) shl 40)
                k2 = k2 xor ((data[index + 12].toLong() and 0xffL) shl 32)
                k2 = k2 xor ((data[index + 11].toLong() and 0xffL) shl 24)
                k2 = k2 xor ((data[index + 10].toLong() and 0xffL) shl 16)
                k2 = k2 xor ((data[index + 9].toLong() and 0xffL) shl 8)
                k2 = k2 xor (data[index + 8].toInt() and 0xff).toLong()
                k2 *= C2
                k2 = Long.rotateLeft(k2, R3)
                k2 *= C1
                h2 = h2 xor k2

                k1 = k1 xor ((data[index + 7].toLong() and 0xffL) shl 56)
                k1 = k1 xor ((data[index + 6].toLong() and 0xffL) shl 48)
                k1 = k1 xor ((data[index + 5].toLong() and 0xffL) shl 40)
                k1 = k1 xor ((data[index + 4].toLong() and 0xffL) shl 32)
                k1 = k1 xor ((data[index + 3].toLong() and 0xffL) shl 24)
                k1 = k1 xor ((data[index + 2].toLong() and 0xffL) shl 16)
                k1 = k1 xor ((data[index + 1].toLong() and 0xffL) shl 8)
                k1 = k1 xor (data[index].toInt() and 0xff).toLong()
                k1 *= C1
                k1 = Long.rotateLeft(k1, R1)
                k1 *= C2
                h1 = h1 xor k1
            }

            14 -> {
                k2 = k2 xor ((data[index + 13].toLong() and 0xffL) shl 40)
                k2 = k2 xor ((data[index + 12].toLong() and 0xffL) shl 32)
                k2 = k2 xor ((data[index + 11].toLong() and 0xffL) shl 24)
                k2 = k2 xor ((data[index + 10].toLong() and 0xffL) shl 16)
                k2 = k2 xor ((data[index + 9].toLong() and 0xffL) shl 8)
                k2 = k2 xor (data[index + 8].toInt() and 0xff).toLong()
                k2 *= C2
                k2 = Long.rotateLeft(k2, R3)
                k2 *= C1
                h2 = h2 xor k2

                k1 = k1 xor ((data[index + 7].toLong() and 0xffL) shl 56)
                k1 = k1 xor ((data[index + 6].toLong() and 0xffL) shl 48)
                k1 = k1 xor ((data[index + 5].toLong() and 0xffL) shl 40)
                k1 = k1 xor ((data[index + 4].toLong() and 0xffL) shl 32)
                k1 = k1 xor ((data[index + 3].toLong() and 0xffL) shl 24)
                k1 = k1 xor ((data[index + 2].toLong() and 0xffL) shl 16)
                k1 = k1 xor ((data[index + 1].toLong() and 0xffL) shl 8)
                k1 = k1 xor (data[index].toInt() and 0xff).toLong()
                k1 *= C1
                k1 = Long.rotateLeft(k1, R1)
                k1 *= C2
                h1 = h1 xor k1
            }

            13 -> {
                k2 = k2 xor ((data[index + 12].toLong() and 0xffL) shl 32)
                k2 = k2 xor ((data[index + 11].toLong() and 0xffL) shl 24)
                k2 = k2 xor ((data[index + 10].toLong() and 0xffL) shl 16)
                k2 = k2 xor ((data[index + 9].toLong() and 0xffL) shl 8)
                k2 = k2 xor (data[index + 8].toInt() and 0xff).toLong()
                k2 *= C2
                k2 = Long.rotateLeft(k2, R3)
                k2 *= C1
                h2 = h2 xor k2

                k1 = k1 xor ((data[index + 7].toLong() and 0xffL) shl 56)
                k1 = k1 xor ((data[index + 6].toLong() and 0xffL) shl 48)
                k1 = k1 xor ((data[index + 5].toLong() and 0xffL) shl 40)
                k1 = k1 xor ((data[index + 4].toLong() and 0xffL) shl 32)
                k1 = k1 xor ((data[index + 3].toLong() and 0xffL) shl 24)
                k1 = k1 xor ((data[index + 2].toLong() and 0xffL) shl 16)
                k1 = k1 xor ((data[index + 1].toLong() and 0xffL) shl 8)
                k1 = k1 xor (data[index].toInt() and 0xff).toLong()
                k1 *= C1
                k1 = Long.rotateLeft(k1, R1)
                k1 *= C2
                h1 = h1 xor k1
            }

            12 -> {
                k2 = k2 xor ((data[index + 11].toLong() and 0xffL) shl 24)
                k2 = k2 xor ((data[index + 10].toLong() and 0xffL) shl 16)
                k2 = k2 xor ((data[index + 9].toLong() and 0xffL) shl 8)
                k2 = k2 xor (data[index + 8].toInt() and 0xff).toLong()
                k2 *= C2
                k2 = Long.rotateLeft(k2, R3)
                k2 *= C1
                h2 = h2 xor k2

                k1 = k1 xor ((data[index + 7].toLong() and 0xffL) shl 56)
                k1 = k1 xor ((data[index + 6].toLong() and 0xffL) shl 48)
                k1 = k1 xor ((data[index + 5].toLong() and 0xffL) shl 40)
                k1 = k1 xor ((data[index + 4].toLong() and 0xffL) shl 32)
                k1 = k1 xor ((data[index + 3].toLong() and 0xffL) shl 24)
                k1 = k1 xor ((data[index + 2].toLong() and 0xffL) shl 16)
                k1 = k1 xor ((data[index + 1].toLong() and 0xffL) shl 8)
                k1 = k1 xor (data[index].toInt() and 0xff).toLong()
                k1 *= C1
                k1 = Long.rotateLeft(k1, R1)
                k1 *= C2
                h1 = h1 xor k1
            }

            11 -> {
                k2 = k2 xor ((data[index + 10].toLong() and 0xffL) shl 16)
                k2 = k2 xor ((data[index + 9].toLong() and 0xffL) shl 8)
                k2 = k2 xor (data[index + 8].toInt() and 0xff).toLong()
                k2 *= C2
                k2 = Long.rotateLeft(k2, R3)
                k2 *= C1
                h2 = h2 xor k2

                k1 = k1 xor ((data[index + 7].toLong() and 0xffL) shl 56)
                k1 = k1 xor ((data[index + 6].toLong() and 0xffL) shl 48)
                k1 = k1 xor ((data[index + 5].toLong() and 0xffL) shl 40)
                k1 = k1 xor ((data[index + 4].toLong() and 0xffL) shl 32)
                k1 = k1 xor ((data[index + 3].toLong() and 0xffL) shl 24)
                k1 = k1 xor ((data[index + 2].toLong() and 0xffL) shl 16)
                k1 = k1 xor ((data[index + 1].toLong() and 0xffL) shl 8)
                k1 = k1 xor (data[index].toInt() and 0xff).toLong()
                k1 *= C1
                k1 = Long.rotateLeft(k1, R1)
                k1 *= C2
                h1 = h1 xor k1
            }

            10 -> {
                k2 = k2 xor ((data[index + 9].toLong() and 0xffL) shl 8)
                k2 = k2 xor (data[index + 8].toInt() and 0xff).toLong()
                k2 *= C2
                k2 = Long.rotateLeft(k2, R3)
                k2 *= C1
                h2 = h2 xor k2

                k1 = k1 xor ((data[index + 7].toLong() and 0xffL) shl 56)
                k1 = k1 xor ((data[index + 6].toLong() and 0xffL) shl 48)
                k1 = k1 xor ((data[index + 5].toLong() and 0xffL) shl 40)
                k1 = k1 xor ((data[index + 4].toLong() and 0xffL) shl 32)
                k1 = k1 xor ((data[index + 3].toLong() and 0xffL) shl 24)
                k1 = k1 xor ((data[index + 2].toLong() and 0xffL) shl 16)
                k1 = k1 xor ((data[index + 1].toLong() and 0xffL) shl 8)
                k1 = k1 xor (data[index].toInt() and 0xff).toLong()
                k1 *= C1
                k1 = Long.rotateLeft(k1, R1)
                k1 *= C2
                h1 = h1 xor k1
            }

            9 -> {
                k2 = k2 xor (data[index + 8].toInt() and 0xff).toLong()
                k2 *= C2
                k2 = Long.rotateLeft(k2, R3)
                k2 *= C1
                h2 = h2 xor k2

                k1 = k1 xor ((data[index + 7].toLong() and 0xffL) shl 56)
                k1 = k1 xor ((data[index + 6].toLong() and 0xffL) shl 48)
                k1 = k1 xor ((data[index + 5].toLong() and 0xffL) shl 40)
                k1 = k1 xor ((data[index + 4].toLong() and 0xffL) shl 32)
                k1 = k1 xor ((data[index + 3].toLong() and 0xffL) shl 24)
                k1 = k1 xor ((data[index + 2].toLong() and 0xffL) shl 16)
                k1 = k1 xor ((data[index + 1].toLong() and 0xffL) shl 8)
                k1 = k1 xor (data[index].toInt() and 0xff).toLong()
                k1 *= C1
                k1 = Long.rotateLeft(k1, R1)
                k1 *= C2
                h1 = h1 xor k1
            }

            8 -> {
                k1 = k1 xor ((data[index + 7].toLong() and 0xffL) shl 56)
                k1 = k1 xor ((data[index + 6].toLong() and 0xffL) shl 48)
                k1 = k1 xor ((data[index + 5].toLong() and 0xffL) shl 40)
                k1 = k1 xor ((data[index + 4].toLong() and 0xffL) shl 32)
                k1 = k1 xor ((data[index + 3].toLong() and 0xffL) shl 24)
                k1 = k1 xor ((data[index + 2].toLong() and 0xffL) shl 16)
                k1 = k1 xor ((data[index + 1].toLong() and 0xffL) shl 8)
                k1 = k1 xor (data[index].toInt() and 0xff).toLong()
                k1 *= C1
                k1 = Long.rotateLeft(k1, R1)
                k1 *= C2
                h1 = h1 xor k1
            }

            7 -> {
                k1 = k1 xor ((data[index + 6].toLong() and 0xffL) shl 48)
                k1 = k1 xor ((data[index + 5].toLong() and 0xffL) shl 40)
                k1 = k1 xor ((data[index + 4].toLong() and 0xffL) shl 32)
                k1 = k1 xor ((data[index + 3].toLong() and 0xffL) shl 24)
                k1 = k1 xor ((data[index + 2].toLong() and 0xffL) shl 16)
                k1 = k1 xor ((data[index + 1].toLong() and 0xffL) shl 8)
                k1 = k1 xor (data[index].toInt() and 0xff).toLong()
                k1 *= C1
                k1 = Long.rotateLeft(k1, R1)
                k1 *= C2
                h1 = h1 xor k1
            }

            6 -> {
                k1 = k1 xor ((data[index + 5].toLong() and 0xffL) shl 40)
                k1 = k1 xor ((data[index + 4].toLong() and 0xffL) shl 32)
                k1 = k1 xor ((data[index + 3].toLong() and 0xffL) shl 24)
                k1 = k1 xor ((data[index + 2].toLong() and 0xffL) shl 16)
                k1 = k1 xor ((data[index + 1].toLong() and 0xffL) shl 8)
                k1 = k1 xor (data[index].toInt() and 0xff).toLong()
                k1 *= C1
                k1 = Long.rotateLeft(k1, R1)
                k1 *= C2
                h1 = h1 xor k1
            }

            5 -> {
                k1 = k1 xor ((data[index + 4].toLong() and 0xffL) shl 32)
                k1 = k1 xor ((data[index + 3].toLong() and 0xffL) shl 24)
                k1 = k1 xor ((data[index + 2].toLong() and 0xffL) shl 16)
                k1 = k1 xor ((data[index + 1].toLong() and 0xffL) shl 8)
                k1 = k1 xor (data[index].toInt() and 0xff).toLong()
                k1 *= C1
                k1 = Long.rotateLeft(k1, R1)
                k1 *= C2
                h1 = h1 xor k1
            }

            4 -> {
                k1 = k1 xor ((data[index + 3].toLong() and 0xffL) shl 24)
                k1 = k1 xor ((data[index + 2].toLong() and 0xffL) shl 16)
                k1 = k1 xor ((data[index + 1].toLong() and 0xffL) shl 8)
                k1 = k1 xor (data[index].toInt() and 0xff).toLong()
                k1 *= C1
                k1 = Long.rotateLeft(k1, R1)
                k1 *= C2
                h1 = h1 xor k1
            }

            3 -> {
                k1 = k1 xor ((data[index + 2].toLong() and 0xffL) shl 16)
                k1 = k1 xor ((data[index + 1].toLong() and 0xffL) shl 8)
                k1 = k1 xor (data[index].toInt() and 0xff).toLong()
                k1 *= C1
                k1 = Long.rotateLeft(k1, R1)
                k1 *= C2
                h1 = h1 xor k1
            }

            2 -> {
                k1 = k1 xor ((data[index + 1].toLong() and 0xffL) shl 8)
                k1 = k1 xor (data[index].toInt() and 0xff).toLong()
                k1 *= C1
                k1 = Long.rotateLeft(k1, R1)
                k1 *= C2
                h1 = h1 xor k1
            }

            1 -> {
                k1 = k1 xor (data[index].toInt() and 0xff).toLong()
                k1 *= C1
                k1 = Long.rotateLeft(k1, R1)
                k1 *= C2
                h1 = h1 xor k1
            }
        }

        // finalization
        h1 = h1 xor length.toLong()
        h2 = h2 xor length.toLong()

        h1 += h2
        h2 += h1

        h1 = fmix64(h1)
        h2 = fmix64(h2)

        h1 += h2
        h2 += h1

        return longArrayOf(h1, h2)
    }

    /**
     * Performs the final avalanche mix step of the 64-bit hash function.
     *
     * @param hash The current hash
     * @return The final hash
     */
    private fun fmix64(hash: Long): Long {
        var hash = hash
        hash = hash xor (hash ushr 33)
        hash *= -0xae502812aa7333L
        hash = hash xor (hash ushr 33)
        hash *= -0x3b314601e57a13adL
        hash = hash xor (hash ushr 33)
        return hash
    }

    /**
     * Generates 128-bit hash from the byte array with the given offset, length and seed.
     *
     *
     * The code is adopted from Apache Commons ([link](https://commons.apache.org/proper/commons-codec/jacoco/org.apache.commons.codec.digest/MurmurHash3.java.html))
     *
     * @param data The input data
     * @return The 128-bit hash (2 longs)
     */
    fun murmurhash3_x64_128(data: BytesRef): LongArray {
        return murmurhash3_x64_128(data.bytes, data.offset, data.length, 104729)
    }

    // Holds 128 bit unsigned value:
    private var nextId: BigInteger? = null

    private var mask128: BigInteger? = null
    private val idLock = Any()

    init {
        // 128 bit unsigned mask
        val maskBytes128 = ByteArray(16)
        Arrays.fill(maskBytes128, 0xff.toByte())

        mask128 = BigInteger.fromByteArray(maskBytes128, Sign.POSITIVE)

        var prop: String? = /*java.lang.System.getProperty("tests.seed")*/ EnvVar["tests.seed"]

        // State for xorshift128:
        var x0: Long? = null
        var x1: Long? = null

        if (prop != null) {
            // So if there is a test failure that somehow relied on this id,
            // we remain reproducible based on the test seed:
            if (prop.length > 8) {
                prop = prop.substring(prop.length - 8)
            }
            x0 = prop.toLong(16)
            x1 = x0
        } else {
            // seed from /dev/urandom, if its available
            /*try {
                java.io.DataInputStream(java.nio.file.Files.newInputStream(java.nio.file.Paths.get("/dev/urandom")))
                    .use { `is` ->
                        x0 = `is`.readLong()
                        x1 = `is`.readLong()
                    }
            } catch (unavailable: java.lang.Exception) {
                // may not be available on this platform
                // fall back to lower quality randomness from 3 different sources:
                x0 = java.lang.System.nanoTime()
                x1 = StringHelper::class.java.hashCode().toLong() shl 32

                val sb: StringBuilder = StringBuilder()
                // Properties can vary across JVM instances:
                try {
                    val p: java.util.Properties = java.lang.System.getProperties()
                    for (s in p.stringPropertyNames()) {
                        sb.append(s)
                        sb.append(p.getProperty(s))
                    }
                    x1 = x1 or sb.toString().hashCode().toLong()
                } catch (notallowed: java.lang.SecurityException) {
                    // getting Properties requires wildcard read-write: may not be allowed
                    x1 = x1 or java.lang.StringBuffer::class.java.hashCode().toLong()
                }
            }*/

            x0 = System.now().toEpochMilliseconds()
            x1 = StringHelper::class.hashCode().toLong() shl 32
        }

        // Use a few iterations of xorshift128 to scatter the seed
        // in case multiple Lucene instances starting up "near" the same
        // nanoTime, since we use ++ (mod 2^128) for full period cycle:
        for (i in 0..9) {
            var s1 = x0
            val s0 = x1
            x0 = s0
            s1 = s1!! xor (s1 shl 23) // a
            x1 = s1 xor s0!! xor (s1 ushr 17) xor (s0 ushr 26) // b, c
        }

        // 64-bit unsigned mask
        val maskBytes64 = ByteArray(8)
        Arrays.fill(maskBytes64, 0xff.toByte())


        //val mask64: BigInteger = (one shl 128) - one
        val mask64: BigInteger = BigInteger.fromByteArray(
            source = maskBytes64,
            sign = Sign.POSITIVE,
        )

        // First make unsigned versions of x0, x1:
        val unsignedX0: BigInteger = BigInteger.valueOf(x0).and(mask64)
        val unsignedX1: BigInteger = BigInteger.valueOf(x1).and(mask64)

        // Concatentate bits of x0 and x1, as unsigned 128 bit integer:
        /*nextId = unsignedX0.shiftLeft(64).or(unsignedX1)*/
        nextId = unsignedX0 shl 64 or unsignedX1
    }

    /** length in bytes of an ID  */
    const val ID_LENGTH: Int = 16

    /** Generates a non-cryptographic globally unique id.  */
    fun randomId(): ByteArray {
        // NOTE: we don't use Java's UUID.randomUUID() implementation here because:
        //
        //   * It's overkill for our usage: it tries to be cryptographically
        //     secure, whereas for this use we don't care if someone can
        //     guess the IDs.
        //
        //   * It uses SecureRandom, which on Linux can easily take a long time
        //     (I saw ~ 10 seconds just running a Lucene test) when entropy
        //     harvesting is falling behind.
        //
        //   * It loses a few (6) bits to version and variant and it's not clear
        //     what impact that has on the period, whereas the simple ++ (mod 2^128)
        //     we use here is guaranteed to have the full period.

        val bits = nextId?.toByteArray() ?: ByteArray(0)

        // toByteArray() always returns a sign bit, so it may require an extra byte (always zero)
        if (bits.size > ID_LENGTH) {
            require(bits.size == ID_LENGTH + 1)
            require(bits[0].toInt() == 0)
            return ArrayUtil.copyOfSubArray(bits, 1, bits.size)
        } else {
            val result = ByteArray(ID_LENGTH)
            /*java.lang.System.arraycopy(bits, 0, result, result.size - bits.size, bits.size)*/
            bits.copyInto(
                destination = result,
                destinationOffset = result.size - bits.size,
                startIndex = 0,
                endIndex = bits.size
            )
            return result
        }
    }

    /**
     * Helper method to render an ID as a string, for debugging
     *
     *
     * Returns the string `(null)` if the id is null. Otherwise, returns a string
     * representation for debugging. Never throws an exception. The returned string may indicate if
     * the id is definitely invalid.
     */
    fun idToString(id: ByteArray?): String {
        if (id == null) {
            return "(null)"
        } else {
            val sb = StringBuilder()
            sb.append(sb.append(BigInteger.fromByteArray(id, Sign.POSITIVE).toString()))
            if (id.size != ID_LENGTH) {
                sb.append(" (INVALID FORMAT)")
            }
            return sb.toString()
        }
    }

    /**
     * Just converts each int in the incoming [IntsRef] to each byte in the returned [ ], throwing `IllegalArgumentException` if any int value is out of bounds for a
     * byte.
     */
    fun intsRefToBytesRef(ints: IntsRef): BytesRef {
        val bytes = ByteArray(ints.length)
        for (i in 0..<ints.length) {
            val x: Int = ints.ints[ints.offset + i]
            require(!(x < 0 || x > 255)) { "int at pos=$i with value=$x is out-of-bounds for byte" }
            bytes[i] = x.toByte()
        }

        return BytesRef(bytes)
    }
}




