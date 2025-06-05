package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.index.FieldInfo.Companion.verifySameDocValuesSkipIndex
import org.gnit.lucenekmp.index.FieldInfo.Companion.verifySameDocValuesType
import org.gnit.lucenekmp.index.FieldInfo.Companion.verifySameIndexOptions
import org.gnit.lucenekmp.index.FieldInfo.Companion.verifySameOmitNorms
import org.gnit.lucenekmp.index.FieldInfo.Companion.verifySamePointsOptions
import org.gnit.lucenekmp.index.FieldInfo.Companion.verifySameStoreTermVectors
import org.gnit.lucenekmp.index.FieldInfo.Companion.verifySameVectorOptions
import org.gnit.lucenekmp.internal.hppc.IntObjectHashMap
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.util.CollectionUtil
import kotlin.jvm.JvmRecord


/**
 * Collection of [FieldInfo]s (accessible by number or by name).
 *
 * @lucene.experimental
 */
open class FieldInfos(infos: Array<FieldInfo>) : Iterable<FieldInfo> {
    private val hasFreq: Boolean
    private val hasPostings: Boolean
    private val hasProx: Boolean
    private val hasPayloads: Boolean
    private val hasOffsets: Boolean
    private val hasTermVectors: Boolean
    private val hasNorms: Boolean
    private val hasDocValues: Boolean
    private val hasPointValues: Boolean
    private val hasVectorValues: Boolean

    /** Returns the soft-deletes field name if exists; otherwise returns null  */
    val softDeletesField: String

    /** Returns the parent document field name if exists; otherwise returns null  */
    val parentField: String

    // used only by fieldInfo(int)
    private val byNumber: Array<FieldInfo>
    private val byName: MutableMap<String, FieldInfo>

    /** Iterator in ascending order of field number.  */
    private var values: MutableCollection<FieldInfo> = mutableListOf()

    /**
     * Constructs a new FieldInfos from an array of FieldInfo objects. The array can be used directly
     * as the backing structure.
     */
    init {
        var hasTermVectors = false
        var hasPostings = false
        var hasProx = false
        var hasPayloads = false
        var hasOffsets = false
        var hasFreq = false
        var hasNorms = false
        var hasDocValues = false
        var hasPointValues = false
        var hasVectorValues = false
        var softDeletesField: String? = null
        var parentField: String? = null

        byName = CollectionUtil.newHashMap(infos.size)
        var maxFieldNumber = -1
        var fieldNumberStrictlyAscending = true
        for (info in infos) {
            val fieldNumber: Int = info.number
            require(fieldNumber >= 0) { "illegal field number: " + info.number + " for field " + info.name }
            if (maxFieldNumber < fieldNumber) {
                maxFieldNumber = fieldNumber
            } else {
                fieldNumberStrictlyAscending = false
            }
            val previous: FieldInfo? = byName.put(info.name, info)
            require(previous == null) {
                ("duplicate field names: "
                        + previous!!.number
                        + " and "
                        + info.number
                        + " have: "
                        + info.name)
            }

            hasTermVectors = hasTermVectors or info.hasTermVectors()
            hasPostings = hasPostings or (info.indexOptions !== IndexOptions.NONE)
            hasProx = hasProx or (info.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
            hasFreq = hasFreq or (info.indexOptions !== IndexOptions.DOCS)
            hasOffsets = hasOffsets or
                    (info.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
            hasNorms = hasNorms or info.hasNorms()
            hasDocValues = hasDocValues or (info.docValuesType !== DocValuesType.NONE)
            hasPayloads = hasPayloads or info.hasPayloads()
            hasPointValues = hasPointValues or (info.pointDimensionCount != 0)
            hasVectorValues = hasVectorValues or (info.vectorDimension != 0)
            if (info.isSoftDeletesField) {
                require(!(softDeletesField != null && softDeletesField == info.name == false)) { "multiple soft-deletes fields [" + info.name + ", " + softDeletesField + "]" }
                softDeletesField = info.name
            }
            if (info.isParentField) {
                require(!(parentField != null && parentField == info.name == false)) { "multiple parent fields [" + info.name + ", " + parentField + "]" }
                parentField = info.name
            }
        }

        this.hasTermVectors = hasTermVectors
        this.hasPostings = hasPostings
        this.hasProx = hasProx
        this.hasPayloads = hasPayloads
        this.hasOffsets = hasOffsets
        this.hasFreq = hasFreq
        this.hasNorms = hasNorms
        this.hasDocValues = hasDocValues
        this.hasPointValues = hasPointValues
        this.hasVectorValues = hasVectorValues
        this.softDeletesField = softDeletesField!!
        this.parentField = parentField!!

        if (fieldNumberStrictlyAscending && maxFieldNumber == infos.size - 1) {
            // The input FieldInfo[] contains all fields numbered from 0 to infos.length - 1, and they are
            // sorted, use it directly. This is an optimization when reading a segment with all fields
            // since the FieldInfo[] is sorted.
            byNumber = infos
            values = mutableListOf<FieldInfo>(*byNumber)
        } else {
            byNumber = kotlin.arrayOfNulls<FieldInfo>(maxFieldNumber + 1) as Array<FieldInfo>
            for (fieldInfo in infos) {
                val existing: FieldInfo = byNumber[fieldInfo.number]
                require(existing == null) {
                    ("duplicate field numbers: "
                            + existing.name
                            + " and "
                            + fieldInfo.name
                            + " have: "
                            + fieldInfo.number)
                }
                byNumber[fieldInfo.number] = fieldInfo
            }
            if (maxFieldNumber == infos.size - 1) {
                // No fields are missing, use byNumber.
                values = mutableListOf<FieldInfo>(*byNumber)
            } else {
                if (!fieldNumberStrictlyAscending) {
                    // The below code is faster than
                    // Arrays.stream(byNumber).filter(Objects::nonNull).toList(),
                    // mainly when the input FieldInfo[] is small compared to maxFieldNumber.
                    Arrays.sort<FieldInfo>(
                        infos
                    ) { fi1: FieldInfo, fi2: FieldInfo ->
                        Int.compare(
                            fi1.number,
                            fi2.number
                        )
                    }
                }
                values = mutableListOf(*infos)
            }
        }
    }

    /** Returns true if any fields have freqs  */
    open fun hasFreq(): Boolean {
        return hasFreq
    }

    /** Returns true if any fields have postings  */
    open fun hasPostings(): Boolean {
        return hasPostings
    }

    /** Returns true if any fields have positions  */
    open fun hasProx(): Boolean {
        return hasProx
    }

    /** Returns true if any fields have payloads  */
    open fun hasPayloads(): Boolean {
        return hasPayloads
    }

    /** Returns true if any fields have offsets  */
    open fun hasOffsets(): Boolean {
        return hasOffsets
    }

    /** Returns true if any fields have term vectors  */
    open fun hasTermVectors(): Boolean {
        return hasTermVectors
    }

    /** Returns true if any fields have norms  */
    open fun hasNorms(): Boolean {
        return hasNorms
    }

    /** Returns true if any fields have DocValues  */
    open fun hasDocValues(): Boolean {
        return hasDocValues
    }

    /** Returns true if any fields have PointValues  */
    open fun hasPointValues(): Boolean {
        return hasPointValues
    }

    /** Returns true if any fields have vector values  */
    fun hasVectorValues(): Boolean {
        return hasVectorValues
    }

    /** Returns the number of fields  */
    open fun size(): Int {
        return byName.size
    }

    /**
     * Returns an iterator over all the fieldinfo objects present, ordered by ascending field number
     */
    // TODO: what happens if in fact a different order is used
    override fun iterator(): MutableIterator<FieldInfo> {
        return values.iterator()
    }

    /**
     * Return the fieldinfo object referenced by the field name
     *
     * @return the FieldInfo object or null when the given fieldName doesn't exist.
     */
    open fun fieldInfo(fieldName: String): FieldInfo? {
        return byName[fieldName]
    }

    /**
     * Return the fieldinfo object referenced by the fieldNumber.
     *
     * @param fieldNumber field's number.
     * @return the FieldInfo object or null when the given fieldNumber doesn't exist.
     * @throws IllegalArgumentException if fieldNumber is negative
     */
    open fun fieldInfo(fieldNumber: Int): FieldInfo? {
        require(fieldNumber >= 0) { "Illegal field number: $fieldNumber" }
        return if (fieldNumber >= byNumber.size) null else byNumber[fieldNumber]
    }

    @JvmRecord
    private data class FieldDimensions(
        val dimensionCount: Int,
        val indexDimensionCount: Int,
        val dimensionNumBytes: Int
    )

    @JvmRecord
    private data class FieldVectorProperties(
        val numDimensions: Int,
        val vectorEncoding: VectorEncoding,
        val similarityFunction: VectorSimilarityFunction
    )

    @JvmRecord
    private data class IndexOptionsProperties(val storeTermVectors: Boolean, val omitNorms: Boolean)

    // We use this to enforce that a given field never
    // changes DV type, even across segments / IndexWriter
    // sessions:
    @JvmRecord
    private data class FieldProperties(
        val number: Int,
        val indexOptions: IndexOptions,
        val indexOptionsProperties: IndexOptionsProperties?,
        val docValuesType: DocValuesType,
        val docValuesSkipIndex: DocValuesSkipIndexType,
        val fieldDimensions: FieldDimensions,
        val fieldVectorProperties: FieldVectorProperties
    )

    class FieldNumbers(softDeletesFieldName: String?, parentFieldName: String?) {
        private val numberToName: IntObjectHashMap<String>
        private val fieldProperties: MutableMap<String, FieldProperties>

        // TODO: we should similarly catch an attempt to turn
        // norms back on after they were already committed; today
        // we silently discard the norm but this is badly trappy
        private var lowestUnassignedFieldNumber = -1

        // The soft-deletes field from IWC to enforce a single soft-deletes field
        internal val softDeletesFieldName: String?

        // The parent document field from IWC to mark parent document when indexing
        internal val parentFieldName: String?

        init {
            this.numberToName = IntObjectHashMap()
            this.fieldProperties = mutableMapOf<String, FieldProperties>()
            this.softDeletesFieldName = softDeletesFieldName
            this.parentFieldName = parentFieldName
            require(!(softDeletesFieldName != null && parentFieldName != null && parentFieldName == softDeletesFieldName)) {
                ("parent document and soft-deletes field can't be the same field \""
                        + parentFieldName
                        + "\"")
            }
        }

        fun verifyFieldInfo(fi: FieldInfo) {
            val fieldName: String = fi.name
            verifySoftDeletedFieldName(fieldName, fi.isSoftDeletesField)
            verifyParentFieldName(fieldName, fi.isParentField)
            if (fieldProperties.containsKey(fieldName)) {
                verifySameSchema(fi)
            }
        }

        /**
         * Returns the global field number for the given field name. If the name does not exist yet, it
         * tries to add it with the given preferred field number assigned, if possible, otherwise the
         * first unassigned field number is used as the field number.
         */
        fun addOrGet(fi: FieldInfo): Int {
            val fieldName: String = fi.name
            verifySoftDeletedFieldName(fieldName, fi.isSoftDeletesField)
            verifyParentFieldName(fieldName, fi.isParentField)
            var fieldProperties = this.fieldProperties[fieldName]

            if (fieldProperties != null) {
                verifySameSchema(fi)
            } else { // first time we see this field in this index
                val fieldNumber: Int
                if (fi.number != -1 && numberToName.containsKey(fi.number) == false) {
                    // cool - we can use this number globally
                    fieldNumber = fi.number
                } else {
                    // find a new FieldNumber
                    while (numberToName.containsKey(++lowestUnassignedFieldNumber)) {
                        // might not be up to date - lets do the work once needed
                    }
                    fieldNumber = lowestUnassignedFieldNumber
                }
                require(fieldNumber >= 0)
                numberToName.put(fieldNumber, fieldName)
                fieldProperties =
                    FieldProperties(
                        fieldNumber,
                        fi.indexOptions,
                        if (fi.indexOptions != IndexOptions.NONE)
                            IndexOptionsProperties(fi.hasTermVectors(), fi.omitsNorms())
                        else
                            null,
                        fi.docValuesType,
                        fi.docValuesSkipIndexType(),
                        FieldDimensions(
                            fi.pointDimensionCount,
                            fi.pointIndexDimensionCount,
                            fi.pointNumBytes
                        ),
                        FieldVectorProperties(
                            fi.vectorDimension,
                            fi.vectorEncoding,
                            fi.vectorSimilarityFunction
                        )
                    )
                this.fieldProperties.put(fieldName, fieldProperties)
            }
            return fieldProperties.number
        }

        private fun verifySoftDeletedFieldName(fieldName: String, isSoftDeletesField: Boolean) {
            if (isSoftDeletesField) {
                requireNotNull(softDeletesFieldName != null) {
                    ("this index has ["
                            + fieldName
                            + "] as soft-deletes already but soft-deletes field is not configured in IWC")
                }
                require(fieldName == softDeletesFieldName != false) {
                    ("cannot configure ["
                            + softDeletesFieldName
                            + "] as soft-deletes; this index uses ["
                            + fieldName
                            + "] as soft-deletes already")
                }
            } else require(fieldName != softDeletesFieldName) {
                ("cannot configure ["
                        + softDeletesFieldName
                        + "] as soft-deletes; this index uses ["
                        + fieldName
                        + "] as non-soft-deletes already")
            }
        }

        private fun verifyParentFieldName(fieldName: String, isParentField: Boolean) {
            if (isParentField) {
                requireNotNull(parentFieldName != null) {
                    ("can't add field ["
                            + fieldName
                            + "] as parent document field; this IndexWriter has no parent document field configured")
                }
                require(fieldName == parentFieldName != false) {
                    ("can't add field ["
                            + fieldName
                            + "] as parent document field; this IndexWriter is configured with ["
                            + parentFieldName
                            + "] as parent document field")
                }
            } else require(fieldName != parentFieldName) {
                ("can't add ["
                        + fieldName
                        + "] as non parent document field; this IndexWriter is configured with ["
                        + parentFieldName
                        + "] as parent document field")
            }
        }

        private fun verifySameSchema(fi: FieldInfo) {
            val fieldName: String = fi.name
            val fieldProperties: FieldProperties = this.fieldProperties[fieldName]!!
            val currentOpts = fieldProperties.indexOptions
            verifySameIndexOptions(fieldName, currentOpts, fi.indexOptions)
            if (currentOpts !== IndexOptions.NONE) {
                val curStoreTermVector = fieldProperties.indexOptionsProperties!!.storeTermVectors
                verifySameStoreTermVectors(fieldName, curStoreTermVector, fi.hasTermVectors())
                val curOmitNorms = fieldProperties.indexOptionsProperties.omitNorms
                verifySameOmitNorms(fieldName, curOmitNorms, fi.omitsNorms())
            }

            val currentDVType = fieldProperties.docValuesType
            verifySameDocValuesType(fieldName, currentDVType, fi.docValuesType)
            val currentDocValuesSkipIndex = fieldProperties.docValuesSkipIndex
            verifySameDocValuesSkipIndex(
                fieldName, currentDocValuesSkipIndex, fi.docValuesSkipIndexType()
            )

            val dims = fieldProperties.fieldDimensions
            verifySamePointsOptions(
                fieldName,
                dims.dimensionCount,
                dims.indexDimensionCount,
                dims.dimensionNumBytes,
                fi.pointDimensionCount,
                fi.pointIndexDimensionCount,
                fi.pointNumBytes
            )

            val props = fieldProperties.fieldVectorProperties
            verifySameVectorOptions(
                fieldName,
                props.numDimensions,
                props.vectorEncoding,
                props.similarityFunction,
                fi.vectorDimension,
                fi.vectorEncoding,
                fi.vectorSimilarityFunction
            )
        }


        /**
         * This function is called from [IndexWriter] to verify if doc values of the field can be
         * updated. If the field with this name already exists, we verify that it is a doc values-only
         * field. If the field doesn't exist and the parameter fieldMustExist is false, we create a new
         * field in the global field numbers.
         *
         * @param fieldName - name of the field
         * @param dvType - expected doc values type
         * @param fieldMustExist – if the field must exist.
         * @throws IllegalArgumentException if the field must exist, but it doesn't, or if the field
         * exists, but it is not doc values only field with the provided doc values type.
         */
        fun verifyOrCreateDvOnlyField(
            fieldName: String, dvType: DocValuesType, fieldMustExist: Boolean
        ) {
            if (fieldProperties.containsKey(fieldName) == false) {
                require(!fieldMustExist) {
                    ("Can't update ["
                            + dvType
                            + "] doc values; the field ["
                            + fieldName
                            + "] doesn't exist.")
                }

                // create dv only field
                val fi =
                    FieldInfo(
                        fieldName,
                        -1,
                        false,
                        false,
                        false,
                        IndexOptions.NONE,
                        dvType,
                        DocValuesSkipIndexType.NONE,
                        -1,
                        mutableMapOf(),
                        0,
                        0,
                        0,
                        0,
                        VectorEncoding.FLOAT32,
                        VectorSimilarityFunction.EUCLIDEAN,
                        (softDeletesFieldName != null && softDeletesFieldName == fieldName),
                        (parentFieldName != null && parentFieldName == fieldName)
                    )
                addOrGet(fi)
            } else {
                // verify that field is doc values only field with the give doc values type
                val fieldProperties: FieldProperties = this.fieldProperties[fieldName]!!
                val fieldDvType = fieldProperties.docValuesType
                require(dvType === fieldDvType) {
                    ("Can't update ["
                            + dvType
                            + "] doc values; the field ["
                            + fieldName
                            + "] has inconsistent doc values' type of ["
                            + fieldDvType
                            + "].")
                }
                val hasDocValuesSkipIndex = fieldProperties.docValuesSkipIndex
                require(hasDocValuesSkipIndex === DocValuesSkipIndexType.NONE) {
                    ("Can't update ["
                            + dvType
                            + "] doc values; the field ["
                            + fieldName
                            + "] must be doc values only field, bit it has doc values skip index")
                }
                val fdimensions: FieldDimensions = fieldProperties.fieldDimensions
                require(!(fdimensions != null && fdimensions.dimensionCount != 0)) {
                    ("Can't update ["
                            + dvType
                            + "] doc values; the field ["
                            + fieldName
                            + "] must be doc values only field, but is also indexed with points.")
                }
                val ioptions = fieldProperties.indexOptions
                require(!(ioptions != null && ioptions !== IndexOptions.NONE)) {
                    ("Can't update ["
                            + dvType
                            + "] doc values; the field ["
                            + fieldName
                            + "] must be doc values only field, but is also indexed with postings.")
                }
                val fvp: FieldVectorProperties = fieldProperties.fieldVectorProperties
                require(!(fvp != null && fvp.numDimensions != 0)) {
                    ("Can't update ["
                            + dvType
                            + "] doc values; the field ["
                            + fieldName
                            + "] must be doc values only field, but is also indexed with vectors.")
                }
            }
        }

        /**
         * Construct a new FieldInfo based on the options in global field numbers. This method is not
         * synchronized as all the options it uses are not modifiable.
         *
         * @param fieldName name of the field
         * @param dvType doc values type
         * @param newFieldNumber a new field number
         * @return `null` if `fieldName` doesn't exist in the map or is not of the same
         * `dvType` returns a new FieldInfo based based on the options in global field numbers
         */
        fun constructFieldInfo(fieldName: String, dvType: DocValuesType, newFieldNumber: Int): FieldInfo? {

            val fieldProperties = this.fieldProperties[fieldName]

            if (fieldProperties == null) return null
            val dvType0 = fieldProperties.docValuesType
            if (dvType !== dvType0) return null
            val isSoftDeletesField = fieldName == softDeletesFieldName
            val isParentField = fieldName == parentFieldName
            return FieldInfo(
                fieldName,
                newFieldNumber,
                false,
                false,
                false,
                IndexOptions.NONE,
                dvType,
                DocValuesSkipIndexType.NONE,
                -1,
                mutableMapOf(),
                0,
                0,
                0,
                0,
                VectorEncoding.FLOAT32,
                VectorSimilarityFunction.EUCLIDEAN,
                isSoftDeletesField,
                isParentField
            )
        }

        val fieldNames: MutableSet<String>
            get() = fieldProperties.keys

        fun clear() {
            numberToName.clear()
            fieldProperties.clear()
            lowestUnassignedFieldNumber = -1
        }
    }

    internal class Builder(globalFieldNumbers: FieldNumbers) {
        private val byName: MutableMap<String, FieldInfo> = mutableMapOf()
        val globalFieldNumbers: FieldNumbers
        private var finished = false

        /** Creates a new instance with the given [FieldNumbers].  */
        init {
            checkNotNull(globalFieldNumbers)
            this.globalFieldNumbers = globalFieldNumbers
        }

        val softDeletesFieldName: String?
            get() = globalFieldNumbers.softDeletesFieldName

        val parentFieldName: String?
            /**
             * Returns the name of the parent document field or <tt>null</tt> if no parent field is
             * configured
             */
            get() = globalFieldNumbers.parentFieldName

        /**
         * Adds the provided FieldInfo to this Builder if this field doesn't exist in this Builder. Also
         * adds a new field with its schema options to the global FieldNumbers if the field doesn't
         * exist globally in the index. The field number is reused if possible for consistent field
         * numbers across segments.
         *
         *
         * If the field already exists: 1) the provided FieldInfo's schema is checked against the
         * existing field and 2) the provided FieldInfo's attributes are added to the existing
         * FieldInfo's attributes.
         *
         * @param fi – FieldInfo to add
         * @return The existing FieldInfo if the field with this name already exists in the builder, or
         * a new constructed FieldInfo with the same schema as provided and a consistent global
         * field number.
         * @throws IllegalArgumentException if there already exists field with this name in Builder but
         * with a different schema
         * @throws IllegalArgumentException if there already exists field with this name globally but
         * with a different schema.
         * @throws IllegalStateException if the Builder is already finished building and doesn't accept
         * new fields.
         */
        fun add(fi: FieldInfo): FieldInfo {
            return add(fi, -1)
        }

        /**
         * Adds the provided FieldInfo with the provided dvGen to this Builder if this field doesn't
         * exist in this Builder. Also adds a new field with its schema options to the global
         * FieldNumbers if the field doesn't exist globally in the index. The field number is reused if
         * possible for consistent field numbers across segments.
         *
         *
         * If the field already exists: 1) the provided FieldInfo's schema is checked against the
         * existing field and 2) the provided FieldInfo's attributes are added to the existing
         * FieldInfo's attributes.
         *
         * @param fi – FieldInfo to add
         * @param dvGen – doc values generation of the FieldInfo to add
         * @return The existing FieldInfo if the field with this name already exists in the builder, or
         * a new constructed FieldInfo with the same schema as provided and a consistent global
         * field number.
         * @throws IllegalArgumentException if there already exists field with this name in Builder but
         * with a different schema
         * @throws IllegalArgumentException if there already exists field with this name globally but
         * with a different schema.
         * @throws IllegalStateException if the Builder is already finished building and doesn't accept
         * new fields.
         */
        fun add(fi: FieldInfo, dvGen: Long): FieldInfo {
            val curFi = fieldInfo(fi.name)
            val attributes: MutableMap<String, String> = fi.attributes()
            if (curFi != null) {
                curFi.verifySameSchema(fi)
                if (attributes != null) {
                    attributes.forEach { entry ->
                        val key = entry.key
                        val value = entry.value
                        curFi.putAttribute(key, value)
                    }
                }
                if (fi.hasPayloads()) {
                    curFi.setStorePayloads()
                }
                return curFi
            }
            // This field wasn't yet added to this in-RAM segment's FieldInfo,
            // so now we get a global number for this field.
            // If the field was seen before then we'll get the same name and number,
            // else we'll allocate a new one
            require(assertNotFinished())
            val fieldNumber = globalFieldNumbers.addOrGet(fi)
            val fiNew =
                FieldInfo(
                    fi.name,
                    fieldNumber,
                    fi.hasTermVectors(),
                    fi.omitsNorms(),
                    fi.hasPayloads(),
                    fi.indexOptions,
                    fi.docValuesType,
                    fi.docValuesSkipIndexType(),
                    dvGen,  // original attributes is UnmodifiableMap
                    attributes,
                    fi.pointDimensionCount,
                    fi.pointIndexDimensionCount,
                    fi.pointNumBytes,
                    fi.vectorDimension,
                    fi.vectorEncoding,
                    fi.vectorSimilarityFunction,
                    fi.isSoftDeletesField,
                    fi.isParentField
                )
            byName.put(fiNew.name, fiNew)
            return fiNew
        }

        fun fieldInfo(fieldName: String): FieldInfo? {
            return byName[fieldName]
        }

        /** Called only from assert  */
        private fun assertNotFinished(): Boolean {
            check(!finished) { "FieldInfos.Builder was already finished; cannot add new fields" }
            return true
        }

        fun finish(): FieldInfos {
            finished = true
            return FieldInfos(byName.values.toTypedArray<FieldInfo>())
        }
    }

    companion object {
        /** An instance without any fields.  */
        val EMPTY: FieldInfos = FieldInfos(emptyArray())

        /**
         * Call this to get the (merged) FieldInfos for a composite reader.
         *
         *
         * NOTE: the returned field numbers will likely not correspond to the actual field numbers in
         * the underlying readers, and codec metadata ([FieldInfo.getAttribute] will be
         * unavailable.
         */
        fun getMergedFieldInfos(reader: IndexReader): FieldInfos {
            val leaves = reader.leaves()
            if (leaves.isEmpty()) {
                return EMPTY
            } else if (leaves.size == 1) {
                return leaves[0].reader().fieldInfos
            } else {
                val softDeletesField: String? =
                    leaves.firstNotNullOfOrNull { l: LeafReaderContext -> l.reader().fieldInfos.softDeletesField }

                /* originally java version was like this but not sure to port like this
                * leaves.stream()
                      .map(l -> l.reader().getFieldInfos().getSoftDeletesField())
                      .filter(Objects::nonNull)
                      .findAny()
                      .orElse(null);
                * */

                val parentField = getAndValidateParentField(leaves)
                val builder = Builder(FieldNumbers(softDeletesField, parentField))
                for (ctx in leaves) {
                    for (fieldInfo in ctx.reader().fieldInfos) {
                        builder.add(fieldInfo)
                    }
                }
                return builder.finish()
            }
        }

        private fun getAndValidateParentField(leaves: MutableList<LeafReaderContext>): String? {
            var set = false
            var theField: String? = null
            for (ctx in leaves) {
                val field: String = ctx.reader().fieldInfos.parentField
                check(!(set && field == theField == false)) {
                    ("expected parent doc field to be \""
                            + theField
                            + " \" across all segments but found a segment with different field \""
                            + field
                            + "\"")
                }
                theField = field
                set = true
            }
            return theField
        }

        /** Returns a set of names of fields that have a terms index. The order is undefined.  */
        fun getIndexedFields(reader: IndexReader): MutableCollection<String> {
            return reader.leaves()
                .flatMap { leaf ->
                    leaf.reader().fieldInfos
                        .filter { fi -> fi.indexOptions !== IndexOptions.NONE }
                }
                .map { fi -> fi.name }
                .toMutableSet()
        }
    }
}
