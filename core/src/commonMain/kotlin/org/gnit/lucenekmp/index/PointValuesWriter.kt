package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.MutablePointTree
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.codecs.PointsWriter
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.PagedBytes

/** Buffers up pending byte[][] value(s) per doc, then flushes when segment flushes.  */
internal class PointValuesWriter(
    bytesUsed: Counter,
    private val fieldInfo: FieldInfo
) {
    private val bytes: PagedBytes = PagedBytes(12)
    private val bytesOut: DataOutput = bytes.dataOutput
    private val iwBytesUsed: Counter = bytesUsed
    private var docIDs: IntArray
    private var numPoints = 0

    /**
     * Get number of buffered documents
     *
     * @return number of buffered documents
     */
    var numDocs: Int = 0
        private set
    private var lastDocID = -1
    private val packedBytesLength: Int

    init {
        docIDs = IntArray(16)
        iwBytesUsed.addAndGet((16 * Int.SIZE_BYTES).toLong())
        packedBytesLength = fieldInfo.pointDimensionCount * fieldInfo.pointNumBytes
    }

    // TODO: if exactly the same value is added to exactly the same doc, should we dedup?
    @Throws(IOException::class)
    fun addPackedValue(docID: Int, value: BytesRef) {
        requireNotNull(value) { "field=" + fieldInfo.name + ": point value must not be null" }
        require(value.length == packedBytesLength) {
            ("field="
                    + fieldInfo.name
                    + ": this field's value has length="
                    + value.length
                    + " but should be "
                    + (fieldInfo.pointDimensionCount * fieldInfo.pointNumBytes))
        }

        if (docIDs.size == numPoints) {
            docIDs = ArrayUtil.grow(docIDs, numPoints + 1)
            iwBytesUsed.addAndGet((docIDs.size - numPoints) * Int.SIZE_BYTES.toLong())
        }
        val bytesRamBytesUsedBefore: Long = bytes.ramBytesUsed()
        bytesOut.writeBytes(value.bytes, value.offset, value.length)
        iwBytesUsed.addAndGet(bytes.ramBytesUsed() - bytesRamBytesUsedBefore)
        docIDs[numPoints] = docID
        if (docID != lastDocID) {
            numDocs++
            lastDocID = docID
        }

        numPoints++
    }

    @Throws(IOException::class)
    fun flush(
        state: SegmentWriteState?,
        sortMap: Sorter.DocMap?,
        writer: PointsWriter
    ) {
        val bytesReader: PagedBytes.Reader = bytes.freeze(false)
        val points: MutablePointTree =
            object : MutablePointTree() {
                val ords: IntArray = IntArray(numPoints)
                var temp: IntArray? = null

                init {
                    for (i in 0..<numPoints) {
                        ords[i] = i
                    }
                }

                override fun size(): Long {
                    return numPoints.toLong()
                }

                @Throws(IOException::class)
                override fun visitDocValues(visitor: PointValues.IntersectVisitor) {
                    val scratch = BytesRef()
                    val packedValue = ByteArray(packedBytesLength)
                    for (i in 0..<numPoints) {
                        getValue(i, scratch)
                        assert(scratch.length == packedValue.size)
                        System.arraycopy(scratch.bytes, scratch.offset, packedValue, 0, packedBytesLength)
                        visitor.visit(getDocID(i), packedValue)
                    }
                }

                override fun swap(i: Int, j: Int) {
                    val tmp = ords[i]
                    ords[i] = ords[j]
                    ords[j] = tmp
                }

                override fun getDocID(i: Int): Int {
                    return docIDs[ords[i]]
                }

                override fun getValue(i: Int, packedValue: BytesRef) {
                    val offset = packedBytesLength.toLong() * ords[i]
                    bytesReader.fillSlice(packedValue, offset, packedBytesLength)
                }

                override fun getByteAt(i: Int, k: Int): Byte {
                    val offset = packedBytesLength.toLong() * ords[i] + k
                    return bytesReader.getByte(offset)
                }

                override fun save(i: Int, j: Int) {
                    if (temp == null) {
                        temp = IntArray(ords.size)
                    }
                    temp!![j] = ords[i]
                }

                override fun restore(i: Int, j: Int) {
                    if (temp != null) {
                        System.arraycopy(temp!!, i, ords, i, j - i)
                    }
                }
            }
        val values = if (sortMap == null) {
            points
        } else {
            MutableSortingPointValues(points, sortMap)
        }
        val reader: PointsReader =
            object : PointsReader() {
                override fun getValues(fieldName: String): PointValues {
                    require(fieldName == fieldInfo.name != false) { "fieldName must be the same" }
                    return object : PointValues() {
                        override val pointTree: PointTree
                            get() = values

                        override val minPackedValue: ByteArray
                            get() {
                                throw UnsupportedOperationException()
                            }

                        override val maxPackedValue: ByteArray
                            get() {
                                throw UnsupportedOperationException()
                            }

                        override val numDimensions: Int
                            get() {
                                throw UnsupportedOperationException()
                            }

                        override val numIndexDimensions: Int
                            get() {
                                throw UnsupportedOperationException()
                            }

                        override val bytesPerDimension: Int
                            get() {
                                throw UnsupportedOperationException()
                            }

                        override fun size(): Long {
                            throw UnsupportedOperationException()
                        }

                        override val docCount: Int
                            get() {
                                throw UnsupportedOperationException()
                            }
                    }
                }

                override fun checkIntegrity() {
                    throw UnsupportedOperationException()
                }

                override fun close() {}
            }
        writer.writeField(fieldInfo, reader)
    }

    internal class MutableSortingPointValues(
        private val `in`: MutablePointTree,
        private val docMap: Sorter.DocMap
    ) : MutablePointTree() {

        override fun size(): Long {
            return `in`.size()
        }

        @Throws(IOException::class)
        override fun visitDocValues(visitor: PointValues.IntersectVisitor) {
            `in`.visitDocValues(
                object : PointValues.IntersectVisitor {
                    @Throws(IOException::class)
                    override fun visit(docID: Int) {
                        visitor.visit(docMap.oldToNew(docID))
                    }

                    @Throws(IOException::class)
                    override fun visit(docID: Int, packedValue: ByteArray) {
                        visitor.visit(docMap.oldToNew(docID), packedValue)
                    }

                    override fun compare(
                        minPackedValue: ByteArray,
                        maxPackedValue: ByteArray
                    ): PointValues.Relation {
                        return visitor.compare(minPackedValue, maxPackedValue)
                    }
                })
        }

        override fun getValue(i: Int, packedValue: BytesRef) {
            `in`.getValue(i, packedValue)
        }

        override fun getByteAt(i: Int, k: Int): Byte {
            return `in`.getByteAt(i, k)
        }

        override fun getDocID(i: Int): Int {
            return docMap.oldToNew(`in`.getDocID(i))
        }

        override fun swap(i: Int, j: Int) {
            `in`.swap(i, j)
        }

        override fun save(i: Int, j: Int) {
            `in`.save(i, j)
        }

        override fun restore(i: Int, j: Int) {
            `in`.restore(i, j)
        }
    }
}
