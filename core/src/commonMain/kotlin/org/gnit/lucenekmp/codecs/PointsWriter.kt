package org.gnit.lucenekmp.codecs


import kotlinx.io.IOException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.MergeState

/**
 * Abstract API to write points
 *
 * @lucene.experimental
 */
abstract class PointsWriter
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : AutoCloseable {
    /** Write all values contained in the provided reader  */
    @Throws(IOException::class)
    abstract fun writeField(fieldInfo: FieldInfo, values: PointsReader)

    /**
     * Default naive merge implementation for one field: it just re-indexes all the values from the
     * incoming segment. The default codec overrides this for 1D fields and uses a faster but more
     * complex implementation.
     */
    @Throws(IOException::class)
    protected fun mergeOneField(mergeState: MergeState, fieldInfo: FieldInfo) {
        var maxPointCount: Long = 0
        for (i in 0..<mergeState.pointsReaders.size) {
            val pointsReader: PointsReader? = mergeState.pointsReaders[i]
            if (pointsReader != null) {
                val readerFieldInfo: FieldInfo? = mergeState.fieldInfos[i]!!.fieldInfo(fieldInfo.name)
                if (readerFieldInfo != null && readerFieldInfo.pointDimensionCount > 0) {
                    val values: PointValues? = pointsReader.getValues(fieldInfo.name)
                    if (values != null) {
                        maxPointCount += values.size()
                    }
                }
            }
        }
        val finalMaxPointCount = maxPointCount
        writeField(
            fieldInfo,
            object : PointsReader() {
                @Throws(IOException::class)
                override fun close() {
                }

                override fun getValues(fieldName: String): PointValues {
                    require(fieldName == fieldInfo.name != false) { "field name must match the field being merged" }

                    return object : PointValues() {
                        override val pointTree: PointTree
                            get() = object : PointTree {
                                override fun clone(): PointTree {
                                    throw UnsupportedOperationException()
                                }

                                override fun moveToChild(): Boolean {
                                    return false
                                }

                                override fun moveToSibling(): Boolean {
                                    return false
                                }

                                override fun moveToParent(): Boolean {
                                    return false
                                }

                                override val minPackedValue: ByteArray
                                    get() {
                                        throw UnsupportedOperationException()
                                    }

                                override val maxPackedValue: ByteArray
                                    get() {
                                        throw UnsupportedOperationException()
                                    }

                                override fun size(): Long {
                                    return finalMaxPointCount
                                }

                                override fun visitDocIDs(visitor: IntersectVisitor) {
                                    throw UnsupportedOperationException()
                                }

                                @Throws(IOException::class)
                                override fun visitDocValues(mergedVisitor: IntersectVisitor) {
                                    for (i in 0..<mergeState.pointsReaders.size) {
                                        val pointsReader: PointsReader? = mergeState.pointsReaders[i]
                                        if (pointsReader == null) {
                                            // This segment has no points
                                            continue
                                        }
                                        val readerFieldInfo: FieldInfo? = mergeState.fieldInfos[i]!!.fieldInfo(fieldName)
                                        if (readerFieldInfo == null) {
                                            // This segment never saw this field
                                            continue
                                        }

                                        if (readerFieldInfo.pointDimensionCount == 0) {
                                            // This segment saw this field, but the field did not index points in it:
                                            continue
                                        }

                                        val values: PointValues? = pointsReader.getValues(fieldName)
                                        if (values == null) {
                                            continue
                                        }
                                        val docMap: MergeState.DocMap = mergeState.docMaps!![i]
                                        values
                                            .pointTree
                                            .visitDocValues(
                                                object : IntersectVisitor {
                                                    override fun visit(docID: Int) {
                                                        // Should never be called during #visitDocValues()
                                                        throw IllegalStateException()
                                                    }

                                                    @Throws(IOException::class)
                                                    override fun visit(docID: Int, packedValue: ByteArray) {
                                                        val newDocID: Int = docMap.get(docID)
                                                        if (newDocID != -1) {
                                                            // Not deleted:
                                                            mergedVisitor.visit(newDocID, packedValue)
                                                        }
                                                    }

                                                    override fun compare(
                                                        minPackedValue: ByteArray, maxPackedValue: ByteArray
                                                    ): Relation {
                                                        // Forces this segment's PointsReader to always visit all docs +
                                                        // values:
                                                        return Relation.CELL_CROSSES_QUERY
                                                    }
                                                })
                                    }
                                }
                            }

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
                            return finalMaxPointCount
                        }

                        override val docCount: Int
                            get() {
                                throw UnsupportedOperationException()
                            }
                    }
                }

                @Throws(IOException::class)
                override fun checkIntegrity() {
                    throw UnsupportedOperationException()
                }
            })
    }

    /**
     * Default merge implementation to merge incoming points readers by visiting all their points and
     * adding to this writer
     */
    @Throws(IOException::class)
    open fun merge(mergeState: MergeState) {
        // check each incoming reader
        for (reader in mergeState.pointsReaders) {
            reader?.checkIntegrity()
        }
        // merge field at a time
        for (fieldInfo in mergeState.mergeFieldInfos!!) {
            if (fieldInfo.pointDimensionCount != 0) {
                mergeOneField(mergeState, fieldInfo)
            }
        }
        finish()
    }

    /** Called once at the end before close  */
    @Throws(IOException::class)
    abstract fun finish()
}
