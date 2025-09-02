package org.gnit.lucenekmp.codecs.perfield


import okio.IOException
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.Terms

/** Utility class creating a new [MergeState] to be restricted to a set of fields.  */
internal object PerFieldMergeState {
    /**
     * Create a new MergeState from the given [MergeState] instance with restricted fields.
     *
     * @param fields The fields to keep in the new instance.
     * @return The new MergeState with restricted fields
     */
    fun restrictFields(`in`: MergeState, fields: MutableCollection<String>): MergeState {
        val fieldInfos: Array<FieldInfos?> = kotlin.arrayOfNulls(`in`.fieldInfos.size)
        for (i in 0..<`in`.fieldInfos.size) {
            fieldInfos[i] = FilterFieldInfos(`in`.fieldInfos[i]!!, fields)
        }
        val fieldsProducers: Array<FieldsProducer?> = kotlin.arrayOfNulls(`in`.fieldsProducers.size)
        for (i in 0..<`in`.fieldsProducers.size) {
            fieldsProducers[i] =
                if (`in`.fieldsProducers[i] == null)
                    null
                else
                    FilterFieldsProducer(`in`.fieldsProducers[i]!!, fields)
        }
        val mergeFieldInfos = FilterFieldInfos(`in`.mergeFieldInfos!!, fields)
        return MergeState(
            `in`.docMaps!!,
            `in`.segmentInfo,
            mergeFieldInfos,
            `in`.storedFieldsReaders,
            `in`.termVectorsReaders,
            `in`.normsProducers,
            `in`.docValuesProducers,
            fieldInfos,
            `in`.liveDocs,
            fieldsProducers,
            `in`.pointsReaders,
            `in`.knnVectorsReaders,
            `in`.maxDocs,
            `in`.infoStream,
            `in`.intraMergeTaskExecutor,
            `in`.needsIndexSort
        )
    }

    private class FilterFieldInfos(src: FieldInfos, filterFields: MutableCollection<String>) :
        FieldInfos(toArray(src)) {
        private val filteredNames: MutableSet<String>
        private val filtered: MutableList<FieldInfo>

        // Copy of the private fields from FieldInfos
        // Renamed so as to be less confusing about which fields we're referring to
        private val filteredHasVectors: Boolean
        private val filteredHasPostings: Boolean
        private val filteredHasProx: Boolean
        private val filteredHasPayloads: Boolean
        private val filteredHasOffsets: Boolean
        private val filteredHasFreq: Boolean
        private val filteredHasNorms: Boolean
        private val filteredHasDocValues: Boolean
        private val filteredHasPointValues: Boolean

        init {
            // Copy all the input FieldInfo objects since the field numbering must be kept consistent
            var hasVectors = false
            var hasPostings = false
            var hasProx = false
            var hasPayloads = false
            var hasOffsets = false
            var hasFreq = false
            var hasNorms = false
            var hasDocValues = false
            var hasPointValues = false

            this.filteredNames = HashSet<String>(filterFields)
            this.filtered = ArrayList<FieldInfo>(filterFields.size)
            for (fi in src) {
                if (this.filteredNames.contains(fi.name)) {
                    this.filtered.add(fi)
                    hasVectors = hasVectors or fi.hasTermVectors()
                    hasPostings = hasPostings or (fi.indexOptions !== IndexOptions.NONE)
                    hasProx =
                        hasProx or (fi.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
                    hasFreq = hasFreq or (fi.indexOptions !== IndexOptions.DOCS)
                    hasOffsets = hasOffsets or
                            (fi.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
                    hasNorms = hasNorms or fi.hasNorms()
                    hasDocValues = hasDocValues or (fi.docValuesType !== DocValuesType.NONE)
                    hasPayloads = hasPayloads or fi.hasPayloads()
                    hasPointValues = hasPointValues or (fi.pointDimensionCount != 0)
                }
            }

            this.filteredHasVectors = hasVectors
            this.filteredHasPostings = hasPostings
            this.filteredHasProx = hasProx
            this.filteredHasPayloads = hasPayloads
            this.filteredHasOffsets = hasOffsets
            this.filteredHasFreq = hasFreq
            this.filteredHasNorms = hasNorms
            this.filteredHasDocValues = hasDocValues
            this.filteredHasPointValues = hasPointValues
        }

        override fun iterator(): MutableIterator<FieldInfo> {
            return filtered.iterator()
        }

        override fun hasFreq(): Boolean {
            return filteredHasFreq
        }

        override fun hasPostings(): Boolean {
            return filteredHasPostings
        }

        override fun hasProx(): Boolean {
            return filteredHasProx
        }

        override fun hasPayloads(): Boolean {
            return filteredHasPayloads
        }

        override fun hasOffsets(): Boolean {
            return filteredHasOffsets
        }

        override fun hasTermVectors(): Boolean {
            return filteredHasVectors
        }

        override fun hasNorms(): Boolean {
            return filteredHasNorms
        }

        override fun hasDocValues(): Boolean {
            return filteredHasDocValues
        }

        override fun hasPointValues(): Boolean {
            return filteredHasPointValues
        }

        override fun size(): Int {
            return filtered.size
        }

        override fun fieldInfo(fieldName: String?): FieldInfo? {
            require(filteredNames.contains(fieldName)) {
                ("The field named '"
                        + fieldName
                        + "' is not accessible in the current "
                        + "merge context, available ones are: "
                        + filteredNames)
            }
            return super.fieldInfo(fieldName)
        }

        override fun fieldInfo(fieldNumber: Int): FieldInfo {
            val res: FieldInfo = super.fieldInfo(fieldNumber)!!
            require(filteredNames.contains(res.name)) {
                ("The field named '"
                        + res.name
                        + "' numbered '"
                        + fieldNumber
                        + "' is not "
                        + "accessible in the current merge context, available ones are: "
                        + filteredNames)
            }
            return res
        }

        companion object {
            private fun toArray(src: FieldInfos): Array<FieldInfo> {
                val res: Array<FieldInfo?> = kotlin.arrayOfNulls(src.size())
                var i = 0
                for (fi in src) {
                    res[i++] = fi
                }
                return res as Array<FieldInfo>
            }
        }
    }

    private class FilterFieldsProducer(private val `in`: FieldsProducer, filterFields: MutableCollection<String>) :
        FieldsProducer() {
        private val filtered: MutableList<String> = ArrayList(filterFields)

        override fun iterator(): MutableIterator<String> {
            return filtered.iterator()
        }

        @Throws(IOException::class)
        override fun terms(field: String?): Terms? {
            require(filtered.contains(field)) {
                ("The field named '"
                        + field
                        + "' is not accessible in the current "
                        + "merge context, available ones are: "
                        + filtered)
            }
            return `in`.terms(field)
        }

        override fun size(): Int {
            return filtered.size
        }

        override fun close() {
            `in`.close()
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            `in`.checkIntegrity()
        }
    }
}
