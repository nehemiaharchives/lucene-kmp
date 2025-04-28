package org.gnit.lucenekmp.codecs.perfield


import kotlinx.io.IOException
import org.gnit.lucenekmp.codecs.DocValuesConsumer
import org.gnit.lucenekmp.codecs.DocValuesFormat
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.DocValuesSkipper
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.internal.hppc.IntObjectHashMap
import org.gnit.lucenekmp.util.IOUtils

/**
 * Enables per field docvalues support.
 *
 *
 * Note, when extending this class, the name ([.getName]) is written into the index. In
 * order for the field to be read, the name must resolve to your implementation via [ ][.forName]. This method uses Java's [Service Provider Interface][ServiceLoader] to
 * resolve format names.
 *
 *
 * Files written by each docvalues format have an additional suffix containing the format name.
 * For example, in a per-field configuration instead of `_1.dat` filenames would look
 * like `_1_Lucene40_0.dat`.
 *
 * @see ServiceLoader
 *
 * @lucene.experimental
 */
abstract class PerFieldDocValuesFormat
/** Sole constructor.  */
protected constructor() : DocValuesFormat(PER_FIELD_NAME) {
    @Throws(IOException::class)
    override fun fieldsConsumer(state: SegmentWriteState): DocValuesConsumer {
        return FieldsWriter(state)
    }

    internal class ConsumerAndSuffix(val consumer: DocValuesConsumer, val suffix: Int) : AutoCloseable {
        @Throws(IOException::class)
        override fun close() {
            consumer.close()
        }

    }

    private inner class FieldsWriter(state: SegmentWriteState) : DocValuesConsumer() {
        private val formats: MutableMap<DocValuesFormat, ConsumerAndSuffix> =
            HashMap<DocValuesFormat, ConsumerAndSuffix>()
        private val suffixes: MutableMap<String, Int> = HashMap<String, Int>()

        private val segmentWriteState: SegmentWriteState = state

        @Throws(IOException::class)
        override fun addNumericField(field: FieldInfo, valuesProducer: DocValuesProducer) {
            getInstance(field).addNumericField(field, valuesProducer)
        }

        @Throws(IOException::class)
        override fun addBinaryField(field: FieldInfo, valuesProducer: DocValuesProducer) {
            getInstance(field).addBinaryField(field, valuesProducer)
        }

        @Throws(IOException::class)
        override fun addSortedField(field: FieldInfo, valuesProducer: DocValuesProducer) {
            getInstance(field).addSortedField(field, valuesProducer)
        }

        @Throws(IOException::class)
        override fun addSortedNumericField(field: FieldInfo, valuesProducer: DocValuesProducer) {
            getInstance(field).addSortedNumericField(field, valuesProducer)
        }

        @Throws(IOException::class)
        override fun addSortedSetField(field: FieldInfo, valuesProducer: DocValuesProducer) {
            getInstance(field).addSortedSetField(field, valuesProducer)
        }

        @Throws(IOException::class)
        override fun merge(mergeState: MergeState) {
            val consumersToField: MutableMap<DocValuesConsumer, MutableCollection<String>> =
                mutableMapOf<DocValuesConsumer, MutableCollection<String>>()

            // Group each consumer by the fields it handles
            for (fi in mergeState.mergeFieldInfos!!) {
                if (fi.docValuesType === DocValuesType.NONE) {
                    continue
                }
                // merge should ignore current format for the fields being merged
                val consumer: DocValuesConsumer = getInstance(fi, true)
                var fieldsForConsumer = consumersToField[consumer]
                if (fieldsForConsumer == null) {
                    fieldsForConsumer = ArrayList<String>()
                    consumersToField.put(consumer, fieldsForConsumer)
                }
                fieldsForConsumer.add(fi.name)
            }

            // Delegate the merge to the appropriate consumer
            for (e in consumersToField.entries) {
                e.key.merge(PerFieldMergeState.restrictFields(mergeState, e.value))
            }
        }

        @Throws(IOException::class)
        fun getInstance(field: FieldInfo): DocValuesConsumer {
            return getInstance(field, false)
        }

        /**
         * DocValuesConsumer for the given field.
         *
         * @param field - FieldInfo object.
         * @param ignoreCurrentFormat - ignore the existing format attributes.
         * @return DocValuesConsumer for the field.
         * @throws IOException if there is a low-level IO error
         */
        @Throws(IOException::class)
        fun getInstance(field: FieldInfo, ignoreCurrentFormat: Boolean): DocValuesConsumer {
            var format: DocValuesFormat? = null
            if (field.docValuesGen != -1L) {
                var formatName: String? = null
                if (ignoreCurrentFormat == false) {
                    formatName = field.getAttribute(PER_FIELD_FORMAT_KEY)
                }
                // this means the field never existed in that segment, yet is applied updates
                if (formatName != null) {
                    format = forName(formatName)
                }
            }
            if (format == null) {
                format = getDocValuesFormatForField(field.name)
            }
            checkNotNull(format) { "invalid null DocValuesFormat for field=\"" + field.name + "\"" }
            val formatName: String = format.name

            field.putAttribute(PER_FIELD_FORMAT_KEY, formatName)
            var suffix: Int? = null

            var consumer = formats[format]
            if (consumer == null) {
                // First time we are seeing this format; create a new instance

                if (field.docValuesGen != -1L) {
                    var suffixAtt: String? = null
                    if (!ignoreCurrentFormat) {
                        suffixAtt = field.getAttribute(PER_FIELD_SUFFIX_KEY)
                    }
                    // even when dvGen is != -1, it can still be a new field, that never
                    // existed in the segment, and therefore doesn't have the recorded
                    // attributes yet.
                    if (suffixAtt != null) {
                        suffix = suffixAtt.toInt()
                    }
                }

                if (suffix == null) {
                    // bump the suffix
                    suffix = suffixes[formatName]
                    suffix = if (suffix == null) {
                        0
                    } else {
                        suffix + 1
                    }
                }
                suffixes.put(formatName, suffix)

                val segmentSuffix =
                    getFullSegmentSuffix(
                        segmentWriteState.segmentSuffix, getSuffix(formatName, suffix.toString())
                    )
                consumer =
                    ConsumerAndSuffix(
                        format.fieldsConsumer(SegmentWriteState(segmentWriteState, segmentSuffix)),
                        suffix
                    )
                formats.put(format, consumer)
            } else {
                // we've already seen this format, so just grab its suffix
                require(suffixes.containsKey(formatName))
                suffix = consumer.suffix
            }

            field.putAttribute(PER_FIELD_SUFFIX_KEY, suffix.toString())
            // TODO: we should only provide the "slice" of FIS
            // that this DVF actually sees ...
            return consumer.consumer
        }

        @Throws(IOException::class)
        override fun close() {
            // Close all subs
            IOUtils.close(formats.values)
        }
    }

    private class FieldsReader : DocValuesProducer {
        private val fields: IntObjectHashMap<DocValuesProducer> = IntObjectHashMap()
        private val formats: MutableMap<String, DocValuesProducer> = HashMap<String, DocValuesProducer>()

        // clone for merge
        constructor(other: FieldsReader) {
            val oldToNew: MutableMap<DocValuesProducer, DocValuesProducer> =
                mutableMapOf<DocValuesProducer, DocValuesProducer>()
            // First clone all formats
            for (ent in other.formats.entries) {
                val values: DocValuesProducer = ent.value.mergeInstance
                formats.put(ent.key, values)
                oldToNew.put(ent.value, values)
            }

            // Then rebuild fields:
            for (ent in other.fields) {
                val producer: DocValuesProducer = checkNotNull(oldToNew[ent.value])
                fields.put(ent.key, producer)
            }
        }

        constructor(readState: SegmentReadState) {
            // Init each unique format:

            var success = false
            try {
                // Read field name -> format name
                for (fi in readState.fieldInfos) {
                    if (fi.docValuesType !== DocValuesType.NONE) {
                        val fieldName: String = fi.name
                        val formatName: String? = fi.getAttribute(PER_FIELD_FORMAT_KEY)
                        if (formatName != null) {
                            // null formatName means the field is in fieldInfos, but has no docvalues!
                            val suffix: String? = fi.getAttribute(PER_FIELD_SUFFIX_KEY)
                            checkNotNull(suffix) { "missing attribute: $PER_FIELD_SUFFIX_KEY for field: $fieldName" }
                            val format: DocValuesFormat = forName(formatName)
                            val segmentSuffix =
                                getFullSegmentSuffix(readState.segmentSuffix, getSuffix(formatName, suffix))
                            if (!formats.containsKey(segmentSuffix)) {
                                formats.put(
                                    segmentSuffix,
                                    format.fieldsProducer(SegmentReadState(readState, segmentSuffix))
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

        @Throws(IOException::class)
        override fun getNumeric(field: FieldInfo): NumericDocValues {
            val producer: DocValuesProducer? = fields[field.number]
            return producer!!.getNumeric(field)
        }

        @Throws(IOException::class)
        override fun getBinary(field: FieldInfo): BinaryDocValues {
            val producer: DocValuesProducer? = fields.get(field.number)
            return producer!!.getBinary(field)
        }

        @Throws(IOException::class)
        override fun getSorted(field: FieldInfo): SortedDocValues {
            val producer: DocValuesProducer? = fields.get(field.number)
            return producer!!.getSorted(field)
        }

        @Throws(IOException::class)
        override fun getSortedNumeric(field: FieldInfo): SortedNumericDocValues {
            val producer: DocValuesProducer? = fields[field.number]
            return producer!!.getSortedNumeric(field)
        }

        @Throws(IOException::class)
        override fun getSortedSet(field: FieldInfo): SortedSetDocValues {
            val producer: DocValuesProducer? = fields[field.number]
            return producer!!.getSortedSet(field)
        }

        @Throws(IOException::class)
        override fun getSkipper(field: FieldInfo): DocValuesSkipper? {
            val producer: DocValuesProducer? = fields[field.number]
            return producer!!.getSkipper(field)
        }

        @Throws(IOException::class)
        override fun close() {
            IOUtils.close(formats.values)
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            for (format in formats.values) {
                format.checkIntegrity()
            }
        }

        override val mergeInstance: DocValuesProducer
            get() = FieldsReader(this)

        override fun toString(): String {
            return "PerFieldDocValues(formats=" + formats.size + ")"
        }
    }

    @Throws(IOException::class)
    override fun fieldsProducer(state: SegmentReadState): DocValuesProducer {
        return FieldsReader(state)
    }

    /**
     * Returns the doc values format that should be used for writing new segments of `field
    ` * .
     *
     *
     * The field to format mapping is written to the index, so this method is only invoked when
     * writing, not when reading.
     */
    abstract fun getDocValuesFormatForField(field: String): DocValuesFormat

    companion object {
        /** Name of this [DocValuesFormat].  */
        const val PER_FIELD_NAME: String = "PerFieldDV40"

        /** [FieldInfo] attribute name used to store the format name for each field.  */
        val PER_FIELD_FORMAT_KEY: String = PerFieldDocValuesFormat::class.simpleName + ".format"

        /** [FieldInfo] attribute name used to store the segment suffix name for each field.  */
        val PER_FIELD_SUFFIX_KEY: String = PerFieldDocValuesFormat::class.simpleName + ".suffix"

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
