package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.TermsEnum.SeekStatus
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.compareUnsigned
import org.gnit.lucenekmp.jdkport.signum
import org.gnit.lucenekmp.jdkport.toUnsignedLong
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.getIntBE
import org.gnit.lucenekmp.util.getLongBE
import org.gnit.lucenekmp.util.getShortBE


/**
 * Wrapper around a [TermsEnum] and an integer that identifies it. All operations that move
 * the current position of the [TermsEnum] must be performed via this wrapper class, not
 * directly on the wrapped [TermsEnum].
 */
internal class TermsEnumIndex(var termsEnum: TermsEnum, val subIndex: Int) {
    private var currentTerm: BytesRef? = null
    private var currentTermPrefix8: Long = 0

    fun term(): BytesRef? {
        return currentTerm
    }

    private fun setTerm(term: BytesRef?) {
        currentTerm = term
        if (currentTerm == null) {
            currentTermPrefix8 = 0
        } else {
            currentTermPrefix8 = prefix8ToComparableUnsignedLong(currentTerm!!)
        }
    }

    @Throws(IOException::class)
    fun next(): BytesRef {
        val term: BytesRef = termsEnum.next()!!
        setTerm(term)
        return term
    }

    @Throws(IOException::class)
    fun seekCeil(term: BytesRef): SeekStatus {
        val status: SeekStatus = termsEnum.seekCeil(term)
        if (status === SeekStatus.END) {
            setTerm(null)
        } else {
            setTerm(termsEnum.term())
        }
        return status
    }

    @Throws(IOException::class)
    fun seekExact(term: BytesRef): Boolean {
        val found: Boolean = termsEnum.seekExact(term)
        if (found) {
            setTerm(termsEnum.term())
        } else {
            setTerm(null)
        }
        return found
    }

    @Throws(IOException::class)
    fun seekExact(ord: Long) {
        termsEnum.seekExact(ord)
        setTerm(termsEnum.term())
    }

    @Throws(IOException::class)
    fun reset(tei: TermsEnumIndex) {
        termsEnum = tei.termsEnum
        currentTerm = tei.currentTerm
        currentTermPrefix8 = tei.currentTermPrefix8
    }

    fun compareTermTo(that: TermsEnumIndex): Int {
        if (currentTermPrefix8 != that.currentTermPrefix8) {
            val cmp: Int = Long.compareUnsigned(currentTermPrefix8, that.currentTermPrefix8)
            require(
                Int.signum(cmp)
                        == Int.signum(
                    Arrays.compareUnsigned(
                        currentTerm!!.bytes,
                        currentTerm!!.offset,
                        currentTerm!!.offset + currentTerm!!.length,
                        that.currentTerm!!.bytes,
                        that.currentTerm!!.offset,
                        that.currentTerm!!.offset + that.currentTerm!!.length
                    )
                )
            )
            return cmp
        }

        return Arrays.compareUnsigned(
            currentTerm!!.bytes,
            currentTerm!!.offset,
            currentTerm!!.offset + currentTerm!!.length,
            that.currentTerm!!.bytes,
            that.currentTerm!!.offset,
            that.currentTerm!!.offset + that.currentTerm!!.length
        )
    }

    override fun toString(): String {
        return "$termsEnum subIndex=$subIndex term=$currentTerm"
    }

    /** Wrapper around a term that allows for quick equals comparisons.  */
    internal class TermState {
        internal val term: BytesRefBuilder = BytesRefBuilder()
        internal var termPrefix8: Long = 0

        fun copyFrom(tei: TermsEnumIndex) {
            term.copyBytes(tei.term()!!)
            termPrefix8 = tei.currentTermPrefix8
        }
    }

    fun termEquals(that: TermState): Boolean {
        if (currentTermPrefix8 != that.termPrefix8) {
            return false
        }
        return Arrays.equals(
            currentTerm!!.bytes,
            currentTerm!!.offset,
            currentTerm!!.offset + currentTerm!!.length,
            that.term.bytes(),
            0,
            that.term.length()
        )
    }

    companion object {
        val EMPTY_ARRAY: Array<TermsEnumIndex> = emptyArray()

        /**
         * Copy the first 8 bytes of the given term as a comparable unsigned long. In case the term has
         * less than 8 bytes, missing bytes will be replaced with zeroes. Note that two terms that produce
         * the same long could still be different due to the fact that missing bytes are replaced with
         * zeroes, e.g. `[1, 0]` and `[1]` get mapped to the same long.
         */
        fun prefix8ToComparableUnsignedLong(term: BytesRef): Long {
            // Use Big Endian so that longs are comparable
            if (term.length >= Long.SIZE_BYTES) {
                /*BitUtil.VH_BE_LONG.get(term.bytes, term.offset) as Long*/
                return term.bytes.getLongBE(
                    term.offset
                )
            } else {
                var l: Long
                var o: Int
                if (Int.SIZE_BYTES <= term.length) {
                    /*l = (BitUtil.VH_BE_INT.get(term.bytes, term.offset) as Int).toLong()*/
                    l = term.bytes.getIntBE(
                        term.offset
                    ).toLong()
                    o = Int.SIZE_BYTES
                } else {
                    l = 0
                    o = 0
                }
                if (o + Short.SIZE_BYTES <= term.length) {
                    l =
                        ((l shl Short.SIZE_BITS)
                                or Short.toUnsignedLong(
                            /*BitUtil.VH_BE_SHORT.get(term.bytes, term.offset + o) as Short*/
                                    term.bytes.getShortBE(
                                        term.offset + o
                                    )
                        ))
                    o += Short.SIZE_BYTES
                }
                if (o < term.length) {
                    l = (l shl Byte.SIZE_BITS) or Byte.toUnsignedLong(term.bytes[term.offset + o])
                }
                l = l shl ((Long.SIZE_BYTES - term.length) shl 3)
                return l
            }
        }
    }
}
