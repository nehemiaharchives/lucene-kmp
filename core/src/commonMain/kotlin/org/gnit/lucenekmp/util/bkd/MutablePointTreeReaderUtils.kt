package org.gnit.lucenekmp.util.bkd


import org.gnit.lucenekmp.codecs.MutablePointTree
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.toUnsignedInt
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.ArrayUtil.Companion.ByteArrayComparator
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IntroSelector
import org.gnit.lucenekmp.util.IntroSorter
import org.gnit.lucenekmp.util.RadixSelector
import org.gnit.lucenekmp.util.Selector
import org.gnit.lucenekmp.util.StableMSBRadixSorter
import org.gnit.lucenekmp.util.packed.PackedInts
import kotlin.math.max

/**
 * Utility APIs for sorting and partitioning buffered points.
 *
 * @lucene.internal
 */
object MutablePointTreeReaderUtils {
    /** Sort the given [MutablePointTree] based on its packed value then doc ID.  */
    fun sort(config: BKDConfig, maxDoc: Int, reader: MutablePointTree, from: Int, to: Int) {
        var sortedByDocID = true
        var prevDoc = 0
        for (i in from..<to) {
            val doc: Int = reader.getDocID(i)
            if (doc < prevDoc) {
                sortedByDocID = false
                break
            }
            prevDoc = doc
        }

        // No need to tie break on doc IDs if already sorted by doc ID, since we use a stable sort.
        // This should be a common situation as IndexWriter accumulates data in doc ID order when
        // index sorting is not enabled.
        val bitsPerDocId = if (sortedByDocID) 0 else PackedInts.bitsRequired(maxDoc.toLong() - 1L)
        object : StableMSBRadixSorter(config.packedBytesLength() + (bitsPerDocId + 7) / 8) {
            override fun swap(i: Int, j: Int) {
                reader.swap(i, j)
            }

            override fun save(i: Int, j: Int) {
                reader.save(i, j)
            }

            override fun restore(i: Int, j: Int) {
                reader.restore(i, j)
            }

            override fun byteAt(i: Int, k: Int): Int {
                return if (k < config.packedBytesLength()) {
                    Byte.toUnsignedInt(reader.getByteAt(i, k))
                } else {
                    val shift = bitsPerDocId - ((k - config.packedBytesLength() + 1) shl 3)
                    (reader.getDocID(i) ushr max(0, shift)) and 0xff
                }
            }
        }.sort(from, to)
    }

    /** Sort points on the given dimension.  */
    fun sortByDim(
        config: BKDConfig,
        sortedDim: Int,
        commonPrefixLengths: IntArray?, // TODO never ued. Remove?
        reader: MutablePointTree,
        from: Int,
        to: Int,
        scratch1: BytesRef,
        scratch2: BytesRef
    ) {
        val comparator: ByteArrayComparator = ArrayUtil.getUnsignedComparator(config.bytesPerDim)
        val start: Int = sortedDim * config.bytesPerDim
        // No need for a fancy radix sort here, this is called on the leaves only so
        // there are not many values to sort
        object : IntroSorter() {
            val pivot: BytesRef = scratch1
            var pivotDoc: Int = -1

            override fun swap(i: Int, j: Int) {
                reader.swap(i, j)
            }

            override fun setPivot(i: Int) {
                reader.getValue(i, pivot)
                pivotDoc = reader.getDocID(i)
            }

            override fun comparePivot(j: Int): Int {
                reader.getValue(j, scratch2)
                var cmp: Int =
                    comparator.compare(
                        pivot.bytes, pivot.offset + start, scratch2.bytes, scratch2.offset + start
                    )
                if (cmp == 0) {
                    cmp =
                        Arrays.compareUnsigned(
                            pivot.bytes,
                            pivot.offset + config.packedIndexBytesLength(),
                            pivot.offset + config.packedBytesLength(),
                            scratch2.bytes,
                            scratch2.offset + config.packedIndexBytesLength(),
                            scratch2.offset + config.packedBytesLength()
                        )
                    if (cmp == 0) {
                        cmp = pivotDoc - reader.getDocID(j)
                    }
                }
                return cmp
            }
        }.sort(from, to)
    }

    /**
     * Partition points around `mid`. All values on the left must be less than or equal to it
     * and all values on the right must be greater than or equal to it.
     */
    fun partition(
        config: BKDConfig,
        maxDoc: Int,
        splitDim: Int,
        commonPrefixLen: Int,
        reader: MutablePointTree,
        from: Int,
        to: Int,
        mid: Int,
        scratch1: BytesRef,
        scratch2: BytesRef
    ) {
        val dimOffset: Int = splitDim * config.bytesPerDim + commonPrefixLen
        val dimCmpBytes: Int = config.bytesPerDim - commonPrefixLen
        val dataCmpBytes: Int =
            (config.numDims - config.numIndexDims) * config.bytesPerDim + dimCmpBytes
        val bitsPerDocId: Int = PackedInts.bitsRequired(maxDoc.toLong() - 1L)
        object : RadixSelector(dataCmpBytes + (bitsPerDocId + 7) / 8) {
            override fun getFallbackSelector(k: Int): Selector? {
                val dimStart: Int = splitDim * config.bytesPerDim
                val dataStart =
                    if (k < dimCmpBytes)
                        config.packedIndexBytesLength()
                    else
                        config.packedIndexBytesLength() + k - dimCmpBytes
                val dataEnd: Int = config.numDims * config.bytesPerDim
                val dimComparator: ByteArrayComparator =
                    ArrayUtil.getUnsignedComparator(config.bytesPerDim)
                return object : IntroSelector() {
                    val pivot: BytesRef = scratch1
                    var pivotDoc: Int = 0

                    override fun swap(i: Int, j: Int) {
                        reader.swap(i, j)
                    }

                    override fun setPivot(i: Int) {
                        reader.getValue(i, pivot)
                        pivotDoc = reader.getDocID(i)
                    }

                    override fun comparePivot(j: Int): Int {
                        if (k < dimCmpBytes) {
                            reader.getValue(j, scratch2)
                            val cmp: Int =
                                dimComparator.compare(
                                    pivot.bytes, pivot.offset + dimStart,
                                    scratch2.bytes, scratch2.offset + dimStart
                                )

                            if (cmp != 0) {
                                return cmp
                            }
                        }
                        if (k < dataCmpBytes) {
                            reader.getValue(j, scratch2)
                            val cmp: Int =
                                Arrays.compareUnsigned(
                                    pivot.bytes,
                                    pivot.offset + dataStart,
                                    pivot.offset + dataEnd,
                                    scratch2.bytes,
                                    scratch2.offset + dataStart,
                                    scratch2.offset + dataEnd
                                )
                            if (cmp != 0) {
                                return cmp
                            }
                        }
                        return pivotDoc - reader.getDocID(j)
                    }
                }
            }

            override fun swap(i: Int, j: Int) {
                reader.swap(i, j)
            }

            override fun byteAt(i: Int, k: Int): Int {
                return if (k < dimCmpBytes) {
                    Byte.toUnsignedInt(reader.getByteAt(i, dimOffset + k))
                } else if (k < dataCmpBytes) {
                    Byte.toUnsignedInt(
                        reader.getByteAt(i, config.packedIndexBytesLength() + k - dimCmpBytes)
                    )
                } else {
                    val shift = bitsPerDocId - ((k - dataCmpBytes + 1) shl 3)
                    (reader.getDocID(i) ushr max(0, shift)) and 0xff
                }
            }
        }.select(from, to, mid)
    }
}
