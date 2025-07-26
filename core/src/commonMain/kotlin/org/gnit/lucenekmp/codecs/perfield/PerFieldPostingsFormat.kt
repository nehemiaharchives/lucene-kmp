package org.gnit.lucenekmp.codecs.perfield

import okio.IOException
import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.FilterLeafReader.FilterFields
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.util.CollectionUtil
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.MergedIterator

/**
 * Enables per field postings support.
 *
 *
 * Note, when extending this class, the name ([.getName]) is written into the index. In
 * order for the field to be read, the name must resolve to your implementation via [ ][.forName]. This method uses Java's [Service Provider Interface][ServiceLoader] to
 * resolve format names.
 *
 *
 * Files written by each posting format have an additional suffix containing the format name. For
 * example, in a per-field configuration instead of `_1.prx` filenames would look like
 * `_1_Lucene40_0.prx`.
 *
 * @see ServiceLoader
 *
 * @lucene.experimental
 */
abstract class PerFieldPostingsFormat
/** Sole constructor.  */
protected constructor() : PostingsFormat(PER_FIELD_NAME) {
    /**
     * Group of fields written by one PostingsFormat
     *
     * @param state Custom SegmentWriteState for this group of fields, with the segmentSuffix
     * uniqueified for this PostingsFormat
     */
    internal class FieldsGroup(val fields: MutableList<String>, val suffix: Int, val state: SegmentWriteState) {
        internal class Builder(val suffix: Int, val state: SegmentWriteState) {
            val fields: MutableSet<String> = HashSet()

            fun addField(field: String): Builder {
                fields.add(field)
                return this
            }

            fun build(): FieldsGroup {
                val fieldList: MutableList<String> = ArrayList(fields)
                fieldList.sort()
                return FieldsGroup(fieldList, suffix, state)
            }
        }

    }

    private inner class FieldsWriter(val writeState: SegmentWriteState) : FieldsConsumer() {
        val toClose: MutableList<AutoCloseable> = ArrayList()

        @Throws(IOException::class)
        override fun write(fields: Fields, norms: NormsProducer) {
            val formatToGroups: MutableMap<PostingsFormat, FieldsGroup> = buildFieldsGroupMapping(fields)

            // Write postings
            var success = false
            try {
                for (ent in formatToGroups.entries) {
                    val format: PostingsFormat = ent.key
                    val group: FieldsGroup = ent.value

                    // Exposes only the fields from this group:
                    val maskedFields: Fields =
                        object : FilterFields(fields) {
                            override fun iterator(): MutableIterator<String> {
                                return group.fields.iterator()
                            }
                        }

                    val consumer: FieldsConsumer = format.fieldsConsumer(group.state)
                    toClose.add(consumer)
                    consumer.write(maskedFields, norms)
                }
                success = true
            } finally {
                if (!success) {
                    IOUtils.closeWhileHandlingException(toClose)
                }
            }
        }

        @Throws(IOException::class)
        override fun merge(mergeState: MergeState, norms: NormsProducer) {
            val mutableIterators: Array<MutableIterator<String>> = mergeState.fieldsProducers
                .filterNotNull()
                .map { it.iterator() }
                .toTypedArray()

            val indexedFieldNames: Iterable<String> = MergedIterator(
                removeDuplicates = true,
                iterators = mutableIterators
            )

            val formatToGroups: MutableMap<PostingsFormat, FieldsGroup> = buildFieldsGroupMapping(indexedFieldNames)

            // Merge postings
            var success = false
            try {
                for (ent in formatToGroups.entries) {
                    val format: PostingsFormat = ent.key
                    val group: FieldsGroup = ent.value

                    val consumer: FieldsConsumer = format.fieldsConsumer(group.state)
                    toClose.add(consumer)
                    consumer.merge(PerFieldMergeState.restrictFields(mergeState, group.fields), norms)
                }
                success = true
            } finally {
                if (!success) {
                    IOUtils.closeWhileHandlingException(toClose)
                }
            }
        }

        fun buildFieldsGroupMapping(
            indexedFieldNames: Iterable<String>
        ): MutableMap<PostingsFormat, FieldsGroup> {
            // Maps a PostingsFormat instance to the suffix it should use
            val formatToGroupBuilders: MutableMap<PostingsFormat, FieldsGroup.Builder> =
                HashMap()

            // Holds last suffix of each PostingFormat name
            val suffixes: MutableMap<String, Int> = HashMap()

            // Assign field -> PostingsFormat
            for (field in indexedFieldNames) {
                val fieldInfo: FieldInfo? = writeState.fieldInfos!!.fieldInfo(field)
                // TODO: This should check current format from the field attribute
                val format: PostingsFormat = getPostingsFormatForField(field)

                checkNotNull(format) { "invalid null PostingsFormat for field=\"$field\"" }
                val formatName: String = format.name

                var groupBuilder = formatToGroupBuilders[format]
                if (groupBuilder == null) {
                    // First time we are seeing this format; create a new instance

                    // bump the suffix

                    var suffix = suffixes[formatName]
                    suffix = if (suffix == null) {
                        0
                    } else {
                        suffix + 1
                    }
                    suffixes.put(formatName, suffix)

                    val segmentSuffix =
                        getFullSegmentSuffix(
                            field, writeState.segmentSuffix, getSuffix(formatName, suffix.toString())
                        )
                    groupBuilder =
                        FieldsGroup.Builder(suffix, SegmentWriteState(writeState, segmentSuffix))
                    formatToGroupBuilders.put(format, groupBuilder)
                } else {
                    // we've already seen this format, so just grab its suffix
                    check(suffixes.containsKey(formatName)) { "no suffix for format name: " + formatName + ", expected: " + groupBuilder.suffix }
                }

                groupBuilder.addField(field)

                fieldInfo!!.putAttribute(PER_FIELD_FORMAT_KEY, formatName)
                fieldInfo.putAttribute(PER_FIELD_SUFFIX_KEY, groupBuilder.suffix.toString())
            }

            val formatToGroups: MutableMap<PostingsFormat, FieldsGroup> =
                CollectionUtil.newHashMap(formatToGroupBuilders.size)
            formatToGroupBuilders.forEach { (postingsFormat: PostingsFormat, builder: FieldsGroup.Builder) ->
                formatToGroups.put(
                    postingsFormat,
                    builder.build()
                )
            }
            return formatToGroups
        }

        override fun close() {
            IOUtils.close(toClose)
        }
    }

    private class FieldsReader : FieldsProducer {
        private val fields: MutableMap<String, FieldsProducer> =
            HashMap() //	TreeMap not available in kotlin common as it is jvm only. Suitable when sorted key order is needed, such as for range queries
        private val formats: MutableMap<String, FieldsProducer> =
            HashMap() //	Efficient for quick lookups without ordering requirements
        private val segment: String

        // clone for merge
        constructor(other: FieldsReader) {
            val oldToNew: MutableMap<FieldsProducer, FieldsProducer> =
                HashMap()
            // First clone all formats
            for (ent in other.formats.entries) {
                val values: FieldsProducer = ent.value.mergeInstance
                formats.put(ent.key, values)
                oldToNew.put(ent.value, values)
            }

            // Then rebuild fields:
            for (ent in other.fields.entries) {
                val producer: FieldsProducer = checkNotNull(oldToNew[ent.value])
                fields.put(ent.key, producer)
            }

            segment = other.segment
        }

        constructor(readState: SegmentReadState) {
            // Read _X.per and init each format:

            var success = false
            try {
                // Read field name -> format name
                for (fi in readState.fieldInfos) {
                    if (fi.indexOptions !== IndexOptions.NONE) {
                        val fieldName: String = fi.name
                        val formatName: String? = fi.getAttribute(PER_FIELD_FORMAT_KEY)
                        if (formatName != null) {
                            // null formatName means the field is in fieldInfos, but has no postings!
                            val suffix: String? = fi.getAttribute(PER_FIELD_SUFFIX_KEY)
                            checkNotNull(suffix) { "missing attribute: $PER_FIELD_SUFFIX_KEY for field: $fieldName" }
                            val format: PostingsFormat = forName(formatName)
                            val segmentSuffix = getSuffix(formatName, suffix)
                            if (!formats.containsKey(segmentSuffix)) {
                                formats.put(
                                    segmentSuffix,
                                    format.fieldsProducer(SegmentReadState(readState, segmentSuffix))
                                )
                            }
                            fields.put(fieldName, formats[segmentSuffix]!!)
                        }
                    }
                }
                success = true
            } finally {
                if (!success) {
                    IOUtils.closeWhileHandlingException(formats.values)
                }
            }

            this.segment = readState.segmentInfo.name
        }

        override fun iterator(): MutableIterator<String> {
            return fields.keys.iterator()
        }

        @Throws(IOException::class)
        override fun terms(field: String?): Terms? {
            val fieldsProducer: FieldsProducer? = fields[field]
            return fieldsProducer?.terms(field)
        }

        override fun size(): Int {
            return fields.size
        }

        override fun close() {
            IOUtils.close(formats.values)
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            for (producer in formats.values) {
                producer.checkIntegrity()
            }
        }

        override val mergeInstance: FieldsProducer
            get() = FieldsReader(this)

        override fun toString(): String {
            return "PerFieldPostings(segment=" + segment + " formats=" + formats.size + ")"
        }
    }

    @Throws(IOException::class)
    override fun fieldsConsumer(state: SegmentWriteState): FieldsConsumer {
        return this.FieldsWriter(state)
    }

    @Throws(IOException::class)
    override fun fieldsProducer(state: SegmentReadState): FieldsProducer {
        return FieldsReader(state)
    }

    /**
     * Returns the postings format that should be used for writing new segments of `field`.
     *
     *
     * The field to format mapping is written to the index, so this method is only invoked when
     * writing, not when reading.
     */
    abstract fun getPostingsFormatForField(field: String): PostingsFormat

    companion object {
        /** Name of this [PostingsFormat].  */
        const val PER_FIELD_NAME: String = "PerField40"

        /** [FieldInfo] attribute name used to store the format name for each field.  */
        val PER_FIELD_FORMAT_KEY: String = PerFieldPostingsFormat::class.simpleName + ".format"

        /** [FieldInfo] attribute name used to store the segment suffix name for each field.  */
        val PER_FIELD_SUFFIX_KEY: String = PerFieldPostingsFormat::class.simpleName + ".suffix"

        fun getSuffix(formatName: String, suffix: String): String {
            return formatName + "_" + suffix
        }

        fun getFullSegmentSuffix(
            fieldName: String, outerSegmentSuffix: String, segmentSuffix: String
        ): String {
            if (outerSegmentSuffix.isEmpty()) {
                return segmentSuffix
            } else {
                // TODO: support embedding; I think it should work but
                // we need a test confirm to confirm
                // return outerSegmentSuffix + "_" + segmentSuffix;
                throw IllegalStateException(
                    ("cannot embed PerFieldPostingsFormat inside itself (field \""
                            + fieldName
                            + "\" returned PerFieldPostingsFormat)")
                )
            }
        }
    }
}
