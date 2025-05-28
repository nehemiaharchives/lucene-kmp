package org.gnit.lucenekmp.codecs.perfield

import okio.IOException
import org.gnit.lucenekmp.codecs.KnnFieldVectorsWriter
import org.gnit.lucenekmp.codecs.KnnVectorsFormat
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.KnnVectorsWriter
import org.gnit.lucenekmp.codecs.hnsw.HnswGraphProvider
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.Sorter
import org.gnit.lucenekmp.internal.hppc.IntObjectHashMap
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.hnsw.HnswGraph

/**
 * Enables per field numeric vector support.
 *
 *
 * Note, when extending this class, the name ([.getName]) is written into the index. In
 * order for the field to be read, the name must resolve to your implementation via [ ][.forName]. This method uses Java's [Service Provider Interface][ServiceLoader] to
 * resolve format names.
 *
 *
 * Files written by each numeric vectors format have an additional suffix containing the format
 * name. For example, in a per-field configuration instead of `_1.dat` filenames would
 * look like `_1_Lucene40_0.dat`.
 *
 * @see ServiceLoader
 *
 * @lucene.experimental
 */
abstract class PerFieldKnnVectorsFormat
/** Sole constructor.  */
protected constructor() : KnnVectorsFormat(PER_FIELD_NAME) {
    @Throws(IOException::class)
    override fun fieldsWriter(state: SegmentWriteState): KnnVectorsWriter {
        return this.FieldsWriter(state)
    }

    @Throws(IOException::class)
    override fun fieldsReader(state: SegmentReadState): KnnVectorsReader {
        return FieldsReader(state)
    }

    override fun getMaxDimensions(fieldName: String): Int {
        return getKnnVectorsFormatForField(fieldName).getMaxDimensions(fieldName)
    }

    /**
     * Returns the numeric vector format that should be used for writing new segments of `field
    ` * .
     *
     *
     * The field to format mapping is written to the index, so this method is only invoked when
     * writing, not when reading.
     */
    abstract fun getKnnVectorsFormatForField(field: String): KnnVectorsFormat

    private inner class FieldsWriter(private val segmentWriteState: SegmentWriteState) : KnnVectorsWriter() {
        private val formats: MutableMap<KnnVectorsFormat, WriterAndSuffix> = HashMap()
        private val suffixes: MutableMap<String, Int> = HashMap()

        @Throws(IOException::class)
        override fun addField(fieldInfo: FieldInfo): KnnFieldVectorsWriter<*> {
            val writer: KnnVectorsWriter = getInstance(fieldInfo)
            return writer.addField(fieldInfo)
        }

        @Throws(IOException::class)
        override fun flush(maxDoc: Int, sortMap: Sorter.DocMap?) {
            for (was in formats.values) {
                was.writer.flush(maxDoc, sortMap)
            }
        }

        @Throws(IOException::class)
        override fun mergeOneField(fieldInfo: FieldInfo, mergeState: MergeState) {
            getInstance(fieldInfo).mergeOneField(fieldInfo, mergeState)
        }

        @Throws(IOException::class)
        override fun finish() {
            for (was in formats.values) {
                was.writer.finish()
            }
        }

        @Throws(IOException::class)
        override fun close() {
            IOUtils.close(formats.values)
        }

        @Throws(IOException::class)
        fun getInstance(field: FieldInfo): KnnVectorsWriter {
            val format: KnnVectorsFormat = getKnnVectorsFormatForField(field.name)
            checkNotNull(format) { "invalid null KnnVectorsFormat for field=\"" + field.name + "\"" }
            val formatName: String = format.name

            field.putAttribute(PER_FIELD_FORMAT_KEY, formatName)
            var suffix: Int?

            var writerAndSuffix = formats[format]
            if (writerAndSuffix == null) {
                // First time we are seeing this format; create a new instance

                suffix = suffixes[formatName]
                suffix = if (suffix == null) {
                    0
                } else {
                    suffix + 1
                }
                suffixes.put(formatName, suffix)

                val segmentSuffix =
                    getFullSegmentSuffix(
                        segmentWriteState.segmentSuffix, getSuffix(formatName, suffix.toString())
                    )
                writerAndSuffix =
                    WriterAndSuffix(
                        format.fieldsWriter(SegmentWriteState(segmentWriteState, segmentSuffix)),
                        suffix
                    )
                formats.put(format, writerAndSuffix)
            } else {
                // we've already seen this format, so just grab its suffix
                require(suffixes.containsKey(formatName))
                suffix = writerAndSuffix.suffix
            }
            field.putAttribute(PER_FIELD_SUFFIX_KEY, suffix.toString())
            return writerAndSuffix.writer
        }

        override fun ramBytesUsed(): Long {
            var total: Long = 0
            for (was in formats.values) {
                total += was.writer.ramBytesUsed()
            }
            return total
        }
    }

    /** VectorReader that can wrap multiple delegate readers, selected by field.  */
    class FieldsReader : KnnVectorsReader, HnswGraphProvider {
        private val fields: IntObjectHashMap<KnnVectorsReader> = IntObjectHashMap()
        private val fieldInfos: FieldInfos

        /**
         * Create a FieldsReader over a segment, opening VectorReaders for each KnnVectorsFormat
         * specified by the indexed numeric vector fields.
         *
         * @param readState defines the fields
         * @throws IOException if one of the delegate readers throws
         */
        constructor(readState: SegmentReadState) {
            this.fieldInfos = readState.fieldInfos
            // Init each unique format:
            var success = false
            val formats: MutableMap<String, KnnVectorsReader> = HashMap()
            try {
                // Read field name -> format name
                for (fi in readState.fieldInfos) {
                    if (fi.hasVectorValues()) {
                        val fieldName: String = fi.name
                        val formatName: String? = fi.getAttribute(PER_FIELD_FORMAT_KEY)
                        if (formatName != null) {
                            // null formatName means the field is in fieldInfos, but has no vectors!
                            val suffix: String = fi.getAttribute(PER_FIELD_SUFFIX_KEY)!!
                            checkNotNull(suffix) { "missing attribute: $PER_FIELD_SUFFIX_KEY for field: $fieldName" }
                            val format: KnnVectorsFormat = forName(formatName)
                            val segmentSuffix =
                                getFullSegmentSuffix(readState.segmentSuffix, getSuffix(formatName, suffix))
                            if (!formats.containsKey(segmentSuffix)) {
                                formats.put(
                                    segmentSuffix,
                                    format.fieldsReader(SegmentReadState(readState, segmentSuffix))
                                )
                            }
                            fields.put(fi.number, formats[segmentSuffix])
                        }
                    }
                }
                success = true
            } finally {
                if (!success) {
                    IOUtils.closeWhileHandlingException(formats.values)
                }
            }
        }

        private constructor(fieldsReader: FieldsReader) {
            this.fieldInfos = fieldsReader.fieldInfos
            for (fi in this.fieldInfos) {
                if (fi.hasVectorValues() && fieldsReader.fields.containsKey(fi.number)) {
                    this.fields.put(fi.number, fieldsReader.fields[fi.number]!!.mergeInstance)
                }
            }
        }

        override val mergeInstance: KnnVectorsReader
            get() = FieldsReader(this)

        @Throws(IOException::class)
        override fun finishMerge() {
            for (knnVectorReader in fields.values()) {
                knnVectorReader!!.value!!.finishMerge()
            }
        }

        /**
         * Return the underlying VectorReader for the given field
         *
         * @param field the name of a numeric vector field
         */
        fun getFieldReader(field: String): KnnVectorsReader? {
            val info: FieldInfo? = fieldInfos.fieldInfo(field)
            if (info == null) {
                return null
            }
            return fields[info.number]
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            for (cursor in fields.values()) {
                cursor!!.value!!.checkIntegrity()
            }
        }

        @Throws(IOException::class)
        override fun getFloatVectorValues(field: String): FloatVectorValues? {
            val info: FieldInfo? = fieldInfos.fieldInfo(field)
            var reader: KnnVectorsReader? = null
            if (info != null) {
                reader = fields[info.number]
            }
            if (info == null || reader == null) {
                return null
            }
            return reader.getFloatVectorValues(field)
        }

        @Throws(IOException::class)
        override fun getByteVectorValues(field: String): ByteVectorValues? {
            val info: FieldInfo? = fieldInfos.fieldInfo(field)
            var reader: KnnVectorsReader? = null

            if(info != null) {
                reader = fields[info.number]
            }

            if (info == null || reader == null) {
                return null
            }
            return reader.getByteVectorValues(field)
        }

        @Throws(IOException::class)
        override fun search(
            field: String,
            target: FloatArray,
            knnCollector: KnnCollector,
            acceptDocs: Bits
        ) {
            val info: FieldInfo? = fieldInfos.fieldInfo(field)
            var reader: KnnVectorsReader? = null
            if(info != null) {
                reader = fields[info.number]
            }

            if (info == null || reader == null) {
                return
            }
            reader.search(field, target, knnCollector, acceptDocs)
        }

        @Throws(IOException::class)
        override fun search(field: String, target: ByteArray, knnCollector: KnnCollector, acceptDocs: Bits) {
            val info: FieldInfo? = fieldInfos.fieldInfo(field)
            var reader: KnnVectorsReader? = null

            if(info != null) {
                reader = fields[info.number]
            }

            if (info == null || reader == null) {
                return
            }
            reader.search(field, target, knnCollector, acceptDocs)
        }

        @Throws(IOException::class)
        override fun getGraph(field: String): HnswGraph? {
            val info: FieldInfo? = fieldInfos.fieldInfo(field)
            val knnVectorsReader: KnnVectorsReader = fields[info!!.number]!!
            return if (knnVectorsReader is HnswGraphProvider) {
                (knnVectorsReader as HnswGraphProvider).getGraph(field)
            } else {
                null
            }
        }

        @Throws(IOException::class)
        override fun close() {
            val readers: MutableList<KnnVectorsReader> = mutableListOf()
            for (cursor in fields.values()) {
                readers.add(cursor!!.value!!)
            }
            IOUtils.close(readers)
        }
    }

    private class WriterAndSuffix(val writer: KnnVectorsWriter, val suffix: Int) : AutoCloseable {
        override fun close() {
            writer.close()
        }
    }

    companion object {
        /** Name of this [KnnVectorsFormat].  */
        const val PER_FIELD_NAME: String = "PerFieldVectors90"

        /** [FieldInfo] attribute name used to store the format name for each field.  */
        val PER_FIELD_FORMAT_KEY: String = PerFieldKnnVectorsFormat::class.simpleName + ".format"

        /** [FieldInfo] attribute name used to store the segment suffix name for each field.  */
        val PER_FIELD_SUFFIX_KEY: String = PerFieldKnnVectorsFormat::class.simpleName + ".suffix"

        fun getSuffix(formatName: String, suffix: String): String {
            return formatName + "_" + suffix
        }

        fun getFullSegmentSuffix(outerSegmentSuffix: String, segmentSuffix: String): String {
            return if (outerSegmentSuffix.isEmpty()) {
                segmentSuffix
            } else {
                outerSegmentSuffix + "_" + segmentSuffix
            }
        }
    }
}
