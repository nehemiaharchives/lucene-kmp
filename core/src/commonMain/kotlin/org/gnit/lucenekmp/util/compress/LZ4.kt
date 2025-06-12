package org.gnit.lucenekmp.util.compress

import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.numberOfLeadingZeros
import org.gnit.lucenekmp.jdkport.toUnsignedInt
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.BitUtil
import kotlin.experimental.and
import kotlin.math.max
import kotlin.math.min

/**
 * LZ4 compression and decompression routines.
 *
 *
 * https://github.com/lz4/lz4/tree/dev/lib http://fastcompression.blogspot.fr/p/lz4.html
 *
 *
 * The high-compression option is a simpler version of the one of the original algorithm, and
 * only retains a better hash table that remembers about more occurrences of a previous 4-bytes
 * sequence, and removes all the logic about handling of the case when overlapping matches are
 * found.
 */
object LZ4 {
    /**
     * Window size: this is the maximum supported distance between two strings so that LZ4 can replace
     * the second one by a reference to the first one.
     */
    const val MAX_DISTANCE: Int = 1 shl 16 // maximum distance of a reference

    const val MEMORY_USAGE: Int = 14
    const val MIN_MATCH: Int = 4 // minimum length of a match
    const val LAST_LITERALS: Int = 5 // the last 5 bytes must be encoded as literals
    const val HASH_LOG_HC: Int = 15 // log size of the dictionary for compressHC
    const val HASH_TABLE_SIZE_HC: Int = 1 shl HASH_LOG_HC

    private fun hash(i: Int, hashBits: Int): Int {
        return (i * -1640531535) ushr (32 - hashBits)
    }

    private fun hashHC(i: Int): Int {
        return hash(i, HASH_LOG_HC)
    }

    private fun readInt(buf: ByteArray, i: Int): Int {
        // According to LZ4's algorithm the endianness does not matter at all:
        return BitUtil.VH_NATIVE_INT.get(buf, i)
    }

    private fun commonBytes(b: ByteArray, o1: Int, o2: Int, limit: Int): Int {
        require(o1 < o2)
        // never -1 because lengths always differ
        return Arrays.mismatch(b, o1, limit, b, o2, limit)
    }

    /**
     * Decompress at least `decompressedLen` bytes into `dest[dOff:]`. Please note that
     * `dest` must be large enough to be able to hold **all** decompressed data (meaning that
     * you need to know the total decompressed length). If the given bytes were compressed using a
     * preset dictionary then the same dictionary must be provided in `dest[dOff-dictLen:dOff]`.
     */
    @Throws(IOException::class)
    fun decompress(compressed: DataInput, decompressedLen: Int, dest: ByteArray, dOff: Int): Int {
        var dOff = dOff
        val destEnd = dOff + decompressedLen

        do {
            // literals
            val token: Int = compressed.readByte().toInt() and 0xFF
            var literalLen = token ushr 4

            if (literalLen != 0) {
                if (literalLen == 0x0F) {
                    var len: Byte
                    while ((compressed.readByte().also { len = it }) == 0xFF.toByte()) {
                        literalLen += 0xFF
                    }
                    literalLen += len.toInt() and 0xFF
                }
                compressed.readBytes(dest, dOff, literalLen)
                dOff += literalLen
            }

            if (dOff >= destEnd) {
                break
            }

            // matches
            val matchDec: Int = compressed.readShort().toInt() and 0xFFFF
            require(matchDec > 0)

            var matchLen = token and 0x0F
            if (matchLen == 0x0F) {
                var len: Int
                while ((compressed.readByte().also { len = it.toInt() }) == 0xFF.toByte()) {
                    matchLen += 0xFF
                }
                matchLen += len and 0xFF
            }
            matchLen += MIN_MATCH

            // copying a multiple of 8 bytes can make decompression from 5% to 10% faster
            val fastLen = (matchLen + 7) and -0x8
            if (matchDec < matchLen || dOff + fastLen > destEnd) {
                // overlap -> naive incremental copy
                var ref = dOff - matchDec
                val end = dOff + matchLen
                while (dOff < end) {
                    dest[dOff] = dest[ref]
                    ++ref
                    ++dOff
                }
            } else {
                // no overlap -> arraycopy
                /*java.lang.System.arraycopy(dest, dOff - matchDec, dest, dOff, fastLen)*/
                dest.copyInto(
                    destination = dest,
                    destinationOffset = dOff,
                    startIndex = dOff - matchDec,
                    endIndex = dOff - matchDec + fastLen,
                )
                dOff += matchLen
            }
        } while (dOff < destEnd)

        return dOff
    }

    @Throws(IOException::class)
    private fun encodeLen(l: Int, out: DataOutput) {
        var l = l
        while (l >= 0xFF) {
            out.writeByte(0xFF.toByte())
            l -= 0xFF
        }
        out.writeByte(l.toByte())
    }

    @Throws(IOException::class)
    private fun encodeLiterals(
        bytes: ByteArray, token: Int, anchor: Int, literalLen: Int, out: DataOutput
    ) {
        out.writeByte(token.toByte())

        // encode literal length
        if (literalLen >= 0x0F) {
            encodeLen(literalLen - 0x0F, out)
        }

        // encode literals
        out.writeBytes(bytes, anchor, literalLen)
    }

    @Throws(IOException::class)
    private fun encodeLastLiterals(bytes: ByteArray, anchor: Int, literalLen: Int, out: DataOutput) {
        val token = min(literalLen, 0x0F) shl 4
        encodeLiterals(bytes, token, anchor, literalLen, out)
    }

    @Throws(IOException::class)
    private fun encodeSequence(
        bytes: ByteArray, anchor: Int, matchRef: Int, matchOff: Int, matchLen: Int, out: DataOutput
    ) {
        val literalLen = matchOff - anchor
        require(matchLen >= 4)
        // encode token
        val token = (min(literalLen, 0x0F) shl 4) or min(matchLen - 4, 0x0F)
        encodeLiterals(bytes, token, anchor, literalLen, out)

        // encode match dec
        val matchDec = matchOff - matchRef
        require(matchDec > 0 && matchDec < 1 shl 16)
        out.writeShort(matchDec.toShort())

        // encode match len
        if (matchLen >= MIN_MATCH + 0x0F) {
            encodeLen(matchLen - 0x0F - MIN_MATCH, out)
        }
    }

    /**
     * Compress `bytes[off:off+len]` into `out` using at most 16kB of memory. `ht`
     * shouldn't be shared across threads but can safely be reused.
     */
    @Throws(IOException::class)
    fun compress(bytes: ByteArray, off: Int, len: Int, out: DataOutput, ht: HashTable) {
        compressWithDictionary(bytes, off, 0, len, out, ht)
    }

    /**
     * Compress `bytes[dictOff+dictLen:dictOff+dictLen+len]` into `out` using at most 16kB
     * of memory. `bytes[dictOff:dictOff+dictLen]` will be used as a dictionary. `dictLen`
     * must not be greater than [64kB][LZ4.MAX_DISTANCE], the maximum window size.
     *
     *
     * `ht` shouldn't be shared across threads but can safely be reused.
     */
    @Throws(IOException::class)
    fun compressWithDictionary(
        bytes: ByteArray, dictOff: Int, dictLen: Int, len: Int, out: DataOutput, ht: HashTable
    ) {
        Objects.checkFromIndexSize(dictOff, dictLen, bytes.size)
        Objects.checkFromIndexSize(dictOff + dictLen, len, bytes.size)
        require(dictLen <= MAX_DISTANCE) { "dictLen must not be greater than 64kB, but got $dictLen" }

        val end = dictOff + dictLen + len

        var off = dictOff + dictLen
        var anchor = off

        if (len > LAST_LITERALS + MIN_MATCH) {
            val limit = end - LAST_LITERALS
            val matchLimit = limit - MIN_MATCH
            ht.reset(bytes, dictOff, dictLen + len)
            ht.initDictionary(dictLen)

            main@ while (off <= limit) {
                // find a match
                var ref: Int
                while (true) {
                    if (off >= matchLimit) {
                        break@main
                    }
                    ref = ht.get(off)
                    if (ref != -1) {
                        require(ref >= dictOff && ref < off)
                        require(readInt(bytes, ref) == readInt(bytes, off))
                        break
                    }
                    ++off
                }

                // compute match length
                var matchLen = MIN_MATCH + commonBytes(bytes, ref + MIN_MATCH, off + MIN_MATCH, limit)

                // try to find a better match
                var r = ht.previous(ref)
                val min = max(off - MAX_DISTANCE + 1, dictOff)
                while (r >= min
                ) {
                    require(readInt(bytes, r) == readInt(bytes, off))
                    val rMatchLen = MIN_MATCH + commonBytes(bytes, r + MIN_MATCH, off + MIN_MATCH, limit)
                    if (rMatchLen > matchLen) {
                        ref = r
                        matchLen = rMatchLen
                    }
                    r = ht.previous(r)
                }

                encodeSequence(bytes, anchor, ref, off, matchLen, out)
                off += matchLen
                anchor = off
            }
        }

        // last literals
        val literalLen = end - anchor
        require(literalLen >= LAST_LITERALS || literalLen == len)
        encodeLastLiterals(bytes, anchor, end - anchor, out)
    }

    /** A record of previous occurrences of sequences of 4 bytes.  */
    abstract class HashTable {
        /** Reset this hash table in order to compress the given content.  */
        abstract fun reset(b: ByteArray, off: Int, len: Int)

        /** Init `dictLen` bytes to be used as a dictionary.  */
        abstract fun initDictionary(dictLen: Int)

        /**
         * Advance the cursor to `off` and return an index that stored the same 4 bytes as `b[o:o+4)`. This may only be called on strictly increasing sequences of offsets. A return
         * value of `-1` indicates that no other index could be found.
         */
        abstract fun get(off: Int): Int

        /**
         * Return an index that less than `off` and stores the same 4 bytes. Unlike [.get],
         * it doesn't need to be called on increasing offsets. A return value of `-1` indicates
         * that no other index could be found.
         */
        abstract fun previous(off: Int): Int

        // For testing
        abstract fun assertReset(): Boolean
    }

    private abstract class Table {
        abstract fun set(offset: Int, value: Int)

        abstract fun getAndSet(offset: Int, value: Int): Int

        abstract val bitsPerValue: Int

        abstract fun size(): Int
    }

    /**
     * 16 bits per offset. This is by far the most commonly used table since it gets used whenever
     * compressing inputs whose size is <= 64kB.
     */
    private class Table16(size: Int) : Table() {
        private val table: ShortArray = ShortArray(size)

        override fun set(index: Int, value: Int) {
            require(value >= 0 && value < 1 shl 16)
            table[index] = value.toShort()
        }

        override fun getAndSet(index: Int, value: Int): Int {
            val prev: Int = Short.toUnsignedInt(table[index])
            set(index, value)
            return prev
        }

        override val bitsPerValue: Int
            get() {
                return Short.SIZE_BITS
            }

        override fun size(): Int {
            return table.size
        }
    }

    /** 32 bits per value, only used when inputs exceed 64kB, e.g. very large stored fields.  */
    private class Table32(size: Int) : Table() {
        private val table: IntArray = IntArray(size)

        override fun set(index: Int, value: Int) {
            table[index] = value
        }

        override fun getAndSet(index: Int, value: Int): Int {
            val prev = table[index]
            set(index, value)
            return prev
        }

        override val bitsPerValue: Int
            get() {
                return Int.SIZE_BITS
            }

        override fun size(): Int {
            return table.size
        }
    }

    /**
     * Simple lossy [HashTable] that only stores the last occurrence for each hash on `2^14` bytes of memory.
     */
    class FastCompressionHashTable
    /** Sole constructor  */
        : HashTable() {
        private lateinit var bytes: ByteArray
        private var base = 0
        private var lastOff = 0
        private var end = 0
        private var hashLog = 0
        private var hashTable: Table? = null

        override fun reset(bytes: ByteArray, off: Int, len: Int) {
            Objects.checkFromIndexSize(off, len, bytes.size)
            this.bytes = bytes
            this.base = off
            this.end = off + len
            val bitsPerOffset: Int = if (len - LAST_LITERALS < 1 shl Short.SIZE_BITS) {
                Short.SIZE_BITS
            } else {
                Int.SIZE_BITS
            }
            val bitsPerOffsetLog: Int = 32 - Int.numberOfLeadingZeros(bitsPerOffset - 1)
            hashLog = MEMORY_USAGE + 3 - bitsPerOffsetLog
            if (hashTable == null || hashTable!!.size() < 1 shl hashLog || hashTable!!.bitsPerValue < bitsPerOffset) {
                if (bitsPerOffset > Short.SIZE_BITS) {
                    require(bitsPerOffset == Int.SIZE_BITS)
                    hashTable = Table32(1 shl hashLog)
                } else {
                    require(bitsPerOffset == Short.SIZE_BITS)
                    hashTable = Table16(1 shl hashLog)
                }
            } else {
                // Avoid calling hashTable.clear(), this makes it costly to compress many short sequences
                // otherwise.
                // Instead, get() checks that references are less than the current offset.
            }
            this.lastOff = off - 1
        }

        override fun initDictionary(dictLen: Int) {
            for (i in 0..<dictLen) {
                val v = readInt(bytes, base + i)
                val h = hash(v, hashLog)
                hashTable!!.set(h, i)
            }
            lastOff += dictLen
        }

        override fun get(off: Int): Int {
            require(off > lastOff)
            require(off < end)

            val v = readInt(bytes, off)
            val h = hash(v, hashLog)

            val ref = base + hashTable!!.getAndSet(h, off - base)
            lastOff = off

            return if (ref < off && off - ref < MAX_DISTANCE && readInt(bytes, ref) == v) {
                ref
            } else {
                -1
            }
        }

        override fun previous(off: Int): Int {
            return -1
        }

        override fun assertReset(): Boolean {
            return true
        }
    }

    /**
     * A higher-precision [HashTable]. It stores up to 256 occurrences of 4-bytes sequences in
     * the last `2^16` bytes, which makes it much more likely to find matches than [ ].
     */
    class HighCompressionHashTable : HashTable() {
        private lateinit var bytes: ByteArray
        private var base = 0
        private var next = 0
        private var end = 0
        private val hashTable: IntArray = IntArray(HASH_TABLE_SIZE_HC)
        private val chainTable: ShortArray = ShortArray(MAX_DISTANCE)
        private var attempts = 0

        /** Sole constructor  */
        init {
            Arrays.fill(hashTable, -1)
            Arrays.fill(chainTable, 0xFFFF.toShort())
        }

        override fun reset(bytes: ByteArray, off: Int, len: Int) {
            Objects.checkFromIndexSize(off, len, bytes.size)
            if (end - base < chainTable.size) {
                // The last call to compress was done on less than 64kB, let's not reset
                // the hashTable and only reset the relevant parts of the chainTable.
                // This helps avoid slowing down calling compress() many times on short
                // inputs.
                val startOffset = base and MASK
                val endOffset = if (end == 0) 0 else ((end - 1) and MASK) + 1
                if (startOffset < endOffset) {
                    Arrays.fill(chainTable, startOffset, endOffset, 0xFFFF.toShort())
                } else {
                    Arrays.fill(chainTable, 0, endOffset, 0xFFFF.toShort())
                    Arrays.fill(chainTable, startOffset, chainTable.size, 0xFFFF.toShort())
                }
            } else {
                // The last call to compress was done on a large enough amount of data
                // that it's fine to reset both tables
                Arrays.fill(hashTable, -1)
                Arrays.fill(chainTable, 0xFFFF.toShort())
            }
            this.bytes = bytes
            this.base = off
            this.next = off
            this.end = off + len
        }

        override fun initDictionary(dictLen: Int) {
            require(next == base)
            for (i in 0..<dictLen) {
                addHash(base + i)
            }
            next += dictLen
        }

        override fun get(off: Int): Int {
            require(off >= next)
            require(off < end)

            while (next < off) {
                addHash(next)
                next++
            }

            val v = readInt(bytes, off)
            val h = hashHC(v)

            attempts = 0
            var ref = hashTable[h]
            if (ref >= off) {
                // remainder from a previous call to compress()
                return -1
            }
            val min = max(base, off - MAX_DISTANCE + 1)
            while (ref >= min && attempts < MAX_ATTEMPTS
            ) {
                if (readInt(bytes, ref) == v) {
                    return ref
                }
                ref -= chainTable[ref and MASK].toInt() and 0xFFFF
                attempts++
            }
            return -1
        }

        private fun addHash(off: Int) {
            val v = readInt(bytes, off)
            val h = hashHC(v)
            var delta = off - hashTable[h]
            if (delta <= 0 || delta >= MAX_DISTANCE) {
                delta = MAX_DISTANCE - 1
            }
            chainTable[off and MASK] = delta.toShort()
            hashTable[h] = off
        }

        override fun previous(off: Int): Int {
            val v = readInt(bytes, off)
            var ref = off - (chainTable[off and MASK].toInt() and 0xFFFF)
            while (ref >= base && attempts < MAX_ATTEMPTS
            ) {
                if (readInt(bytes, ref) == v) {
                    return ref
                }
                ref -= chainTable[ref and MASK].toInt() and 0xFFFF
                attempts++
            }
            return -1
        }

        override fun assertReset(): Boolean {
            for (i in chainTable.indices) {
                require(chainTable[i] == 0xFFFF.toShort()) { i }
            }
            return true
        }

        companion object {
            private const val MAX_ATTEMPTS = 256
            private const val MASK = MAX_DISTANCE - 1
        }
    }
}
