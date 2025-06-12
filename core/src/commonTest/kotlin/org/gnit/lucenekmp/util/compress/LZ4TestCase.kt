package org.gnit.lucenekmp.util.compress

import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals

abstract class LZ4TestCase : LuceneTestCase() {

    protected abstract fun newHashTable(): LZ4.HashTable

    protected class AssertingHashTable(private val `in`: LZ4.HashTable) : LZ4.HashTable() {
        override fun reset(b: ByteArray, off: Int, len: Int) {
            `in`.reset(b, off, len)
            assertTrue(`in`.assertReset())
        }

        override fun initDictionary(dictLen: Int) {
            assertTrue(`in`.assertReset())
            `in`.initDictionary(dictLen)
        }

        override fun get(off: Int): Int {
            return `in`.get(off)
        }

        override fun previous(off: Int): Int {
            return `in`.previous(off)
        }

        override fun assertReset(): Boolean {
            throw UnsupportedOperationException()
        }
    }

    private fun doTest(data: ByteArray, hashTable: LZ4.HashTable) {
        val offset = if (data.size >= (1 shl 16) || random().nextBoolean()) {
            random().nextInt(10)
        } else {
            (1 shl 16) - data.size / 2
        }
        val copy = ByteArray(data.size + offset + random().nextInt(10))
        data.copyInto(copy, offset)
        doTest(copy, offset, data.size, hashTable)
    }

    private fun doTest(data: ByteArray, offset: Int, length: Int, hashTable: LZ4.HashTable) {
        val out = ByteBuffersDataOutput()
        LZ4.compress(data, offset, length, out, hashTable)
        val compressed = out.toArrayCopy()

        var off = 0
        var decompressedOff = 0
        while (true) {
            val token = compressed[off++].toInt() and 0xFF
            var literalLen = token ushr 4
            if (literalLen == 0x0F) {
                while (compressed[off] == 0xFF.toByte()) {
                    literalLen += 0xFF
                    ++off
                }
                literalLen += compressed[off++].toInt() and 0xFF
            }
            off += literalLen
            decompressedOff += literalLen

            if (off == compressed.size) {
                assertEquals(length, decompressedOff)
                assertTrue(literalLen >= LZ4.LAST_LITERALS || literalLen == length,
                    "lastLiterals=$literalLen, bytes=$length")
                break
            }

            val matchDec = (compressed[off++].toInt() and 0xFF) or ((compressed[off++].toInt() and 0xFF) shl 8)
            assertTrue(matchDec > 0 && matchDec <= decompressedOff, "$matchDec $decompressedOff")

            var matchLen = token and 0x0F
            if (matchLen == 0x0F) {
                while (compressed[off] == 0xFF.toByte()) {
                    matchLen += 0xFF
                    ++off
                }
                matchLen += compressed[off++].toInt() and 0xFF
            }
            matchLen += LZ4.MIN_MATCH

            if (decompressedOff + matchLen < length - LZ4.LAST_LITERALS) {
                val moreCommonBytes = data[offset + decompressedOff + matchLen] ==
                        data[offset + decompressedOff - matchDec + matchLen]
                val nextSequenceHasLiterals = ((compressed[off].toInt() and 0xFF) ushr 4) != 0
                assertTrue(!(moreCommonBytes && nextSequenceHasLiterals))
            }

            decompressedOff += matchLen
        }
        assertEquals(length, decompressedOff)

        val out2 = ByteBuffersDataOutput()
        LZ4.compress(data, offset, length, out2, hashTable)
        assertContentEquals(compressed, out2.toArrayCopy())

        var restored = ByteArray(length + random().nextInt(10))
        LZ4.decompress(ByteArrayDataInput(compressed), length, restored, 0)
        assertContentEquals(
            ArrayUtil.copyOfSubArray(data, offset, offset + length),
            ArrayUtil.copyOfSubArray(restored, 0, length)
        )

        val restoreOffset = TestUtil.nextInt(random(), 1, 10)
        restored = ByteArray(restoreOffset + length + random().nextInt(10))
        LZ4.decompress(ByteArrayDataInput(compressed), length, restored, restoreOffset)
        assertContentEquals(
            ArrayUtil.copyOfSubArray(data, offset, offset + length),
            ArrayUtil.copyOfSubArray(restored, restoreOffset, restoreOffset + length)
        )
    }

    private fun doTestWithDictionary(data: ByteArray, hashTable: LZ4.HashTable) {
        val copy = ByteBuffersDataOutput()
        val dictOff = TestUtil.nextInt(random(), 0, 10)
        copy.writeBytes(ByteArray(dictOff))

        var dictLen = 0
        var i = TestUtil.nextInt(random(), 0, data.size)
        while (i < data.size && dictLen < LZ4.MAX_DISTANCE) {
            var l = min(data.size - i, TestUtil.nextInt(random(), 1, 32))
            l = min(l, LZ4.MAX_DISTANCE - dictLen)
            copy.writeBytes(data, i, l)
            dictLen += l
            i += l
            i += TestUtil.nextInt(random(), 1, 32)
        }

        copy.writeBytes(data)
        copy.writeBytes(ByteArray(random().nextInt(10)))

        val copyBytes = copy.toArrayCopy()
        doTestWithDictionary(copyBytes, dictOff, dictLen, data.size, hashTable)
    }

    private fun doTestWithDictionary(data: ByteArray, dictOff: Int, dictLen: Int, length: Int, hashTable: LZ4.HashTable) {
        val out = ByteBuffersDataOutput()
        LZ4.compressWithDictionary(data, dictOff, dictLen, length, out, hashTable)
        val compressed = out.toArrayCopy()

        val out2 = ByteBuffersDataOutput()
        LZ4.compressWithDictionary(data, dictOff, dictLen, length, out2, hashTable)
        assertContentEquals(compressed, out2.toArrayCopy())

        val restoreOffset = TestUtil.nextInt(random(), 1, 10)
        val restored = ByteArray(restoreOffset + dictLen + length + random().nextInt(10))
        java.lang.System.arraycopy(data, dictOff, restored, restoreOffset, dictLen)
        LZ4.decompress(ByteArrayDataInput(compressed), length, restored, dictLen + restoreOffset)
        assertContentEquals(
            ArrayUtil.copyOfSubArray(data, dictOff + dictLen, dictOff + dictLen + length),
            ArrayUtil.copyOfSubArray(restored, restoreOffset + dictLen, restoreOffset + dictLen + length)
        )
    }

    @Test
    fun testEmpty() {
        val data = "".encodeToByteArray()
        doTest(data, newHashTable())
    }

    @Test
    fun testShortLiteralsAndMatchs() {
        val data = "1234562345673456745678910123".encodeToByteArray()
        doTest(data, newHashTable())
        doTestWithDictionary(data, newHashTable())
    }

    @Test
    fun testLongMatchs() {
        val data = ByteArray(TestUtil.nextInt(random(), 300, 1024))
        for (i in data.indices) {
            data[i] = i.toByte()
        }
        doTest(data, newHashTable())
    }

    @Test
    fun testLongLiterals() {
        val data = ByteArray(TestUtil.nextInt(random(), 400, 1024))
        random().nextBytes(data)
        val matchRef = random().nextInt(30)
        val matchOff = TestUtil.nextInt(random(), data.size - 40, data.size - 20)
        val matchLength = TestUtil.nextInt(random(), 4, 10)
        java.lang.System.arraycopy(data, matchRef, data, matchOff, matchLength)
        doTest(data, newHashTable())
    }

    @Test
    fun testMatchRightBeforeLastLiterals() {
        doTest(byteArrayOf(1,2,3,4,1,2,3,4,1,2,3,4,5), newHashTable())
    }

    @Test
    fun testIncompressibleRandom() {
        val b = ByteArray(TestUtil.nextInt(random(), 1, 1 shl 18))
        random().nextBytes(b)
        doTest(b, newHashTable())
        doTestWithDictionary(b, newHashTable())
    }

    @Test
    fun testCompressibleRandom() {
        val b = ByteArray(TestUtil.nextInt(random(), 1, 1 shl 18))
        val base = random().nextInt(256)
        val maxDelta = 1 + random().nextInt(8)
        val r = random()
        for (i in b.indices) {
            b[i] = (base + r.nextInt(maxDelta)).toByte()
        }
        doTest(b, newHashTable())
        doTestWithDictionary(b, newHashTable())
    }

    @Test
    fun testLUCENE5201() {
        val data = byteArrayOf(
            14,72,14,85,3,72,14,85,3,72,14,72,14,72,14,85,3,72,14,72,14,72,14,
            72,14,72,14,72,14,85,3,72,14,85,3,72,14,85,3,72,14,85,3,72,14,85,3,
            72,14,50,64,0,46,-1,0,0,0,29,3,85,8,-113,0,68,-97,3,0,2,3,-97,6,0,68,
            -113,0,2,3,-97,6,0,68,-113,0,2,3,85,8,-113,0,68,-97,3,0,2,3,-97,6,0,
            68,-113,0,2,3,-97,6,0,68,-113,0,2,3,-97,6,0,68,-113,0,2,3,-97,6,0,68,
            -113,0,2,3,-97,6,0,68,-113,0,2,3,-97,6,0,68,-113,0,2,3,-97,6,0,68,-113,
            0,50,64,0,47,-105,0,0,0,30,3,-97,6,0,68,-113,0,2,3,-97,6,0,68,-113,0,
            2,3,85,8,-113,0,68,-97,3,0,2,3,85,8,-113,0,68,-97,3,0,2,3,85,8,-113,0,
            68,-97,3,0,2,-97,6,0,2,3,85,8,-113,0,68,-97,3,0,2,3,-97,6,0,68,-113,0,
            2,3,-97,6,0,68,-113,0,120,64,0,48,4,0,0,0,31,34,72,29,72,37,72,35,72,
            45,72,23,72,46,72,20,72,40,72,33,72,25,72,39,72,38,72,26,72,28,72,42,
            72,24,72,27,72,36,72,41,72,32,72,18,72,30,72,22,72,31,72,43,72,19,72,
            34,72,29,72,37,72,35,72,45,72,23,72,46,72,20,72,40,72,33,72,25,72,39,72,
            38,72,26,72,28,72,42,72,24,72,27,72,36,72,41,72,32,72,18,16,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,39,24,32,34,124,0,120,64,0,48,
            80,0,0,0,31,30,72,22,72,31,72,43,72,19,72,34,72,29,72,37,72,35,72,45,72,
            23,72,46,72,20,72,40,72,33,72,25,72,39,72,38,72,26,72,28,72,42,72,24,72,
            27,72,36,72,41,72,32,72,18,72,30,72,22,72,31,72,43,72,19,72,34,72,29,72,
            37,72,35,72,45,72,23,72,46,72,20,72,40,72,33,72,25,72,39,72,38,72,26,72,
            28,72,42,72,24,72,27,72,36,72,41,72,32,72,18,72,30,72,22,72,31,72,43,72,
            19,72,34,72,29,72,37,72,35,72,45,72,23,72,46,72,20,72,40,72,33,72,25,72,
            39,72,38,72,26,72,28,72,42,72,24,72,27,72,36,72,41,72,32,72,18,72,30,72,
            22,72,31,72,43,72,19,72,34,72,29,72,37,72,35,72,45,72,23,72,46,72,20,72,
            40,72,33,72,25,72,39,72,38,72,26,72,28,72,42,72,24,72,27,72,36,72,41,72,
            32,72,18,72,30,72,22,72,31,72,43,72,19,72,34,72,29,72,37,72,35,72,45,72,
            23,72,46,72,20,72,40,72,33,72,25,72,39,72,38,72,26,72,28,72,42,72,24,72,
            27,72,36,72,41,72,32,72,18,72,30,72,22,72,31,72,43,72,19,72,34,72,29,72,
            37,72,35,72,45,72,23,72,46,72,20,72,40,72,33,72,25,72,39,72,38,72,26,72,
            28,72,42,72,24,72,27,72,36,72,41,72,32,72,18,72,30,72,22,72,31,72,43,72,
            19,50,64,0,49,20,0,0,0,32,3,-97,6,0,68,-113,0,2,3,85,8,-113,0,68,-97,3,
            0,2,3,-97,6,0,68,-113,0,2,3,-97,6,0,68,-113,0,2,3,-97,6,0,68,-113,0,2,
            3,-97,6,0,68,-113,0,2,3,-97,6,0,68,-113,0,2,3,85,8,-113,0,68,-113,0,2,
            3,-97,6,0,68,-113,0,2,3,85,8,-113,0,68,-97,3,0,2,3,85,8,-113,0,68,-97,3,
            0,120,64,0,52,-88,0,0,0,39,13,85,5,72,13,85,5,72,13,85,5,72,13,72,13,85,
            5,72,13,85,5,72,13,85,5,72,13,72,13,72,13,85,5,72,13,85,5,72,13,72,13,85,
            5,72,13,85,5,72,13,85,5,72,13,72,13,72,13,72,13,85,5,72,13,85,5,72,13,72,
            13,85,5,72,13,85,5,72,13,85,5,72,13,85,5,72,13,85,5,72,13,85,5,72,13,85,
            5,72,13,85,5,72,13,72,13,72,13,72,13,85,5,72,13,85,5,72,13,85,5,72,13,72,
            13,85,5,72,13,72,13,85,5,72,13,-19,-24,-101,-35
        )
        doTest(data, 9, data.size - 9, newHashTable())
    }

    @Test
    fun testUseDictionary() {
        val b = byteArrayOf(
            1,2,3,4,5,6,
            0,1,2,3,4,5,6,7,8,9,10,11,12
        )
        val dictOff = 0
        val dictLen = 6
        val len = b.size - dictLen

        doTestWithDictionary(b, dictOff, dictLen, len, newHashTable())
        val out = ByteBuffersDataOutput()
        LZ4.compressWithDictionary(b, dictOff, dictLen, len, out, newHashTable())
        assertTrue(out.size() < len)
    }
}

