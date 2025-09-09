package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.codecs.DocValuesConsumer
import org.gnit.lucenekmp.codecs.DocValuesFormat
import org.gnit.lucenekmp.codecs.KnnFieldVectorsWriter
import org.gnit.lucenekmp.codecs.NormsConsumer
import org.gnit.lucenekmp.codecs.NormsFormat
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PointsFormat
import org.gnit.lucenekmp.codecs.PointsWriter
import org.gnit.lucenekmp.document.InvertableType
import org.gnit.lucenekmp.document.KnnByteVectorField
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.document.StoredValue
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.ByteBlockPool
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefHash.MaxBytesLengthExceededException
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.IntBlockPool
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.Version
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Default general purpose indexing chain, which handles indexing of all types of fields.  */
class IndexingChain(
    private val indexCreatedVersionMajor: Int,
    segmentInfo: SegmentInfo,
    directory: Directory,
    fieldInfos: FieldInfos.Builder,
    indexWriterConfig: LiveIndexWriterConfig,
    abortingExceptionConsumer: (Throwable) -> Unit,
) : Accountable {
    val bytesUsed: Counter = Counter.newCounter()
    val fieldInfosBuilder: FieldInfos.Builder

    // Writes postings and term vectors:
    val termsHash: TermsHash

    // Shared pool for doc-value terms
    val docValuesBytePool: ByteBlockPool

    // Writes stored fields
    val storedFieldsConsumer: StoredFieldsConsumer
    val vectorValuesConsumer: VectorValuesConsumer
    val termVectorsWriter: TermVectorsConsumer

    // NOTE: I tried using Hash Map<String,PerField>
    // but it was ~2% slower on Wiki and Geonames with Java
    // 1.7.0_25:
    private var fieldHash = kotlin.arrayOfNulls<PerField>(2)
    private var hashMask = 1

    private var totalFieldCount = 0
    private var nextFieldGen: Long = 0

    // Holds fields seen in each document
    private var fields: Array<PerField> = kotlin.arrayOfNulls<PerField>(1) as Array<PerField>
    private var docFields = kotlin.arrayOfNulls<PerField>(2)
    private val infoStream: InfoStream
    private val byteBlockAllocator = ByteBlockPool.DirectTrackingAllocator(bytesUsed)
    private val indexWriterConfig: LiveIndexWriterConfig
    private val abortingExceptionConsumer: (Throwable) -> Unit
    private var hasHitAbortingException = false

    init {
        val intBlockAllocator: IntBlockPool.Allocator = IntBlockAllocator(bytesUsed)
        this.indexWriterConfig = indexWriterConfig
        assert(segmentInfo.indexSort === indexWriterConfig.indexSort)
        this.fieldInfosBuilder = fieldInfos
        this.infoStream = indexWriterConfig.infoStream
        this.abortingExceptionConsumer = abortingExceptionConsumer
        this.vectorValuesConsumer =
            VectorValuesConsumer(
                indexWriterConfig.codec,
                directory,
                segmentInfo,
                infoStream
            )

        if (segmentInfo.indexSort == null) {
            storedFieldsConsumer =
                StoredFieldsConsumer(indexWriterConfig.codec, directory, segmentInfo)
            termVectorsWriter =
                TermVectorsConsumer(
                    intBlockAllocator,
                    byteBlockAllocator,
                    directory,
                    segmentInfo,
                    indexWriterConfig.codec
                )
        } else {
            storedFieldsConsumer =
                SortingStoredFieldsConsumer(
                    indexWriterConfig.codec,
                    directory,
                    segmentInfo
                )
            termVectorsWriter =
                SortingTermVectorsConsumer(
                    intBlockAllocator,
                    byteBlockAllocator,
                    directory,
                    segmentInfo,
                    indexWriterConfig.codec
                )
        }
        termsHash =
            FreqProxTermsWriter(
                intBlockAllocator, byteBlockAllocator, bytesUsed, termVectorsWriter
            )
        docValuesBytePool = ByteBlockPool(byteBlockAllocator)
    }

    private fun onAbortingException(th: Throwable) {
        checkNotNull(th)
        this.hasHitAbortingException = true
        abortingExceptionConsumer(th)
    }

    private val docValuesLeafReader: LeafReader
        get() = object : DocValuesLeafReader() {
            override fun getNumericDocValues(field: String): NumericDocValues? {
                val pf: PerField = getPerField(field)
                if (pf == null) {
                    return null
                }
                if (pf.fieldInfo!!.docValuesType == DocValuesType.NUMERIC) {
                    return pf.docValuesWriter!!.docValues as NumericDocValues
                }
                return null
            }

            override fun getBinaryDocValues(field: String): BinaryDocValues? {
                val pf: PerField = getPerField(field)
                if (pf == null) {
                    return null
                }
                if (pf.fieldInfo!!.docValuesType == DocValuesType.BINARY) {
                    return pf.docValuesWriter!!.docValues as BinaryDocValues
                }
                return null
            }

            @Throws(IOException::class)
            override fun getSortedDocValues(field: String): SortedDocValues? {
                val pf: PerField = getPerField(field)
                if (pf == null) {
                    return null
                }
                if (pf.fieldInfo!!.docValuesType == DocValuesType.SORTED) {
                    return pf.docValuesWriter!!.docValues as SortedDocValues
                }
                return null
            }

            @Throws(IOException::class)
            override fun getSortedNumericDocValues(field: String): SortedNumericDocValues? {
                val pf: PerField = getPerField(field)
                if (pf == null) {
                    return null
                }
                if (pf.fieldInfo!!.docValuesType == DocValuesType.SORTED_NUMERIC) {
                    return pf.docValuesWriter!!.docValues as SortedNumericDocValues
                }
                return null
            }

            @Throws(IOException::class)
            override fun getSortedSetDocValues(field: String): SortedSetDocValues? {
                val pf: PerField = getPerField(field)
                if (pf == null) {
                    return null
                }
                if (pf.fieldInfo!!.docValuesType == DocValuesType.SORTED_SET) {
                    return pf.docValuesWriter!!.docValues as SortedSetDocValues
                }
                return null
            }

            override val fieldInfos: FieldInfos
                get() = fieldInfosBuilder.finish()
        }

    @Throws(IOException::class)
    private fun maybeSortSegment(state: SegmentWriteState): Sorter.DocMap? {
        val indexSort: Sort? = state.segmentInfo.indexSort
        if (indexSort == null) {
            return null
        }

        val docValuesReader: LeafReader = this.docValuesLeafReader
        var comparatorWrapper: (IndexSorter.DocComparator) -> IndexSorter.DocComparator =
            { input: IndexSorter.DocComparator -> input }

        if (state.segmentInfo.hasBlocks && state.fieldInfos!!.parentField != null) {
            val readerValues: DocIdSetIterator? =
                docValuesReader.getNumericDocValues(state.fieldInfos.parentField)
            if (readerValues == null) {
                throw CorruptIndexException(
                    "missing doc values for parent field \"" + state.fieldInfos.parentField + "\"",
                    "IndexingChain"
                )
            }
            val parents: BitSet = BitSet.of(readerValues, state.segmentInfo.maxDoc())
            comparatorWrapper =
                { `in`: IndexSorter.DocComparator ->
                    IndexSorter.DocComparator { docID1: Int, docID2: Int ->
                        `in`.compare(
                            parents.nextSetBit(docID1),
                            parents.nextSetBit(docID2)
                        )
                    }
                }
        }
        if (state.segmentInfo.hasBlocks
            && state.fieldInfos!!.parentField == null && indexCreatedVersionMajor >= Version.LUCENE_10_0_0.major
        ) {
            throw CorruptIndexException(
                "parent field is not set but the index has blocks and uses index sorting. indexCreatedVersionMajor: "
                        + indexCreatedVersionMajor,
                "IndexingChain"
            )
        }
        val comparators: MutableList<IndexSorter.DocComparator> = mutableListOf()
        for (i in indexSort.sort.indices) {
            val sortField: SortField = indexSort.sort[i]
            val sorter: IndexSorter? = sortField.getIndexSorter()
            if (sorter == null) {
                throw UnsupportedOperationException("Cannot sort index using sort field $sortField")
            }

            val docComparator: IndexSorter.DocComparator =
                sorter.getDocComparator(docValuesReader, state.segmentInfo.maxDoc())
            comparators.add(comparatorWrapper(docComparator))
        }
        val sorter = Sorter(indexSort)
        // returns null if the documents are already sorted
        return sorter.sort(
            state.segmentInfo.maxDoc(),
            comparators.toTypedArray(),
        )
    }

    @OptIn(ExperimentalTime::class)
    @Throws(IOException::class)
    fun flush(state: SegmentWriteState): Sorter.DocMap? {
        // NOTE: caller (DocumentsWriterPerThread) handles
        // aborting on any exception from this method

        val sortMap: Sorter.DocMap? = maybeSortSegment(state)
        val maxDoc: Int = state.segmentInfo.maxDoc()
        var t0: Instant = Clock.System.now()
        writeNorms(state, sortMap)
        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW",
                (Clock.System.now() - t0).toString(unit = DurationUnit.MILLISECONDS) + " ms to write norms"
            )
        }
        val readState =
            SegmentReadState(
                state.directory,
                state.segmentInfo,
                state.fieldInfos!!,
                IOContext.DEFAULT,
                state.segmentSuffix
            )

        t0 = Clock.System.now()
        writeDocValues(state, sortMap)
        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW",
                (Clock.System.now() - t0).toString(unit = DurationUnit.MILLISECONDS) + " ms to write docValues"
            )
        }

        t0 = Clock.System.now()
        writePoints(state, sortMap)
        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW",
                (Clock.System.now() - t0).toString(unit = DurationUnit.MILLISECONDS) + " ms to write points"
            )
        }

        t0 = Clock.System.now()
        vectorValuesConsumer.flush(state, sortMap)
        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW",
                (Clock.System.now() - t0).toString(unit = DurationUnit.MILLISECONDS) + " ms to write vectors"
            )
        }

        // it's possible all docs hit non-aborting exceptions...
        t0 = Clock.System.now()
        storedFieldsConsumer.finish(maxDoc)
        storedFieldsConsumer.flush(state, sortMap)
        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW",
                (Clock.System.now() - t0).toString(unit = DurationUnit.MILLISECONDS) + " ms to finish stored fields"
            )
        }

        t0 = Clock.System.now()
        val fieldsToFlush: MutableMap<String, TermsHashPerField> = mutableMapOf()
        for (i in fieldHash.indices) {
            var perField = fieldHash[i]
            while (perField != null) {
                if (perField.invertState != null) {
                    fieldsToFlush.put(perField.fieldInfo!!.name, perField.termsHashPerField!!)
                }
                perField = perField.next
            }
        }

        val norms = if (readState.fieldInfos.hasNorms()) {
            state.segmentInfo.codec.normsFormat().normsProducer(readState)
        } else {
            null
        }

        var normsMergeInstance: NormsProducer? = null
        if (norms != null) {
            // Use the merge instance in order to reuse the same IndexInput for all terms
            normsMergeInstance = norms.mergeInstance
        }
        val emptyNormsProducer = object : NormsProducer() {
            override fun getNorms(field: FieldInfo): NumericDocValues {
                throw UnsupportedOperationException("No norms")
            }

            override fun checkIntegrity() {}

            override fun close() {}
        }
        termsHash.flush(fieldsToFlush, state, sortMap, normsMergeInstance ?: emptyNormsProducer)


        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW",
                (Clock.System.now() - t0).toString(unit = DurationUnit.MILLISECONDS) + " ms to write postings and finish vectors"
            )
        }

        // Important to save after asking consumer to flush so
        // consumer can alter the FieldInfo* if necessary.  EG,
        // FreqProxTermsWriter does this with
        // FieldInfo.storePayload.
        t0 = Clock.System.now()
        indexWriterConfig
            .codec
            .fieldInfosFormat()
            .write(state.directory, state.segmentInfo, "", state.fieldInfos, IOContext.DEFAULT)
        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW",
                (Clock.System.now() - t0).toString(unit = DurationUnit.MILLISECONDS) + " ms to write fieldInfos"
            )
        }

        return sortMap
    }

    /** Writes all buffered points.  */
    @Throws(IOException::class)
    private fun writePoints(
        state: SegmentWriteState,
        sortMap: Sorter.DocMap?
    ) {
        var pointsWriter: PointsWriter? = null
        var success = false
        try {
            for (i in fieldHash.indices) {
                var perField = fieldHash[i]
                while (perField != null) {
                    if (perField.pointValuesWriter != null) {
                        // We could have initialized pointValuesWriter, but failed to write even a single doc
                        if (perField.fieldInfo!!.pointDimensionCount > 0) {
                            if (pointsWriter == null) {
                                // lazy init
                                val fmt: PointsFormat =
                                    state.segmentInfo.codec.pointsFormat()
                                checkNotNull(fmt) {
                                    ("field=\""
                                            + perField.fieldInfo!!.name
                                            + "\" was indexed as points but codec does not support points")
                                }
                                pointsWriter = fmt.fieldsWriter(state)
                            }
                            perField.pointValuesWriter!!.flush(state, sortMap, pointsWriter)
                        }
                        perField.pointValuesWriter = null
                    }
                    perField = perField.next
                }
            }
            if (pointsWriter != null) {
                pointsWriter.finish()
            }
            success = true
        } finally {
            if (success) {
                IOUtils.close(pointsWriter)
            } else {
                IOUtils.closeWhileHandlingException(pointsWriter)
            }
        }
    }

    /** Writes all buffered doc values (called from [.flush]).  */
    @Throws(IOException::class)
    private fun writeDocValues(
        state: SegmentWriteState,
        sortMap: Sorter.DocMap?
    ) {
        var dvConsumer: DocValuesConsumer? = null
        var success = false
        try {
            for (i in fieldHash.indices) {
                var perField = fieldHash[i]
                while (perField != null) {
                    if (perField.docValuesWriter != null) {
                        if (perField.fieldInfo!!.docValuesType == DocValuesType.NONE) {
                            // BUG
                            throw AssertionError(
                                ("segment="
                                        + state.segmentInfo
                                        + ": field=\""
                                        + perField.fieldInfo!!.name
                                        + "\" has no docValues but wrote them")
                            )
                        }
                        if (dvConsumer == null) {
                            // lazy init
                            val fmt: DocValuesFormat =
                                state.segmentInfo.codec.docValuesFormat()
                            dvConsumer = fmt.fieldsConsumer(state)
                        }
                        perField.docValuesWriter!!.flush(state, sortMap, dvConsumer)
                        perField.docValuesWriter = null
                    } else if (perField.fieldInfo != null
                        && perField.fieldInfo!!.docValuesType != DocValuesType.NONE
                    ) {
                        // BUG
                        throw AssertionError(
                            ("segment="
                                    + state.segmentInfo
                                    + ": field=\""
                                    + perField.fieldInfo!!.name
                                    + "\" has docValues but did not write them")
                        )
                    }
                    perField = perField.next
                }
            }

            // TODO: catch missing DV fields here  else we have
            // null/"" depending on how docs landed in segments
            // but we can't detect all cases, and we should leave
            // this behavior undefined. dv is not "schemaless": it's column-stride.
            success = true
        } finally {
            if (success) {
                IOUtils.close(dvConsumer)
            } else {
                IOUtils.closeWhileHandlingException(dvConsumer)
            }
        }

        if (!state.fieldInfos!!.hasDocValues()) {
            if (dvConsumer != null) {
                // BUG
                throw AssertionError(
                    "segment=" + state.segmentInfo + ": fieldInfos has no docValues but wrote them"
                )
            }
        } else if (dvConsumer == null) {
            // BUG
            throw AssertionError(
                "segment=" + state.segmentInfo + ": fieldInfos has docValues but did not wrote them"
            )
        }
    }

    @Throws(IOException::class)
    private fun writeNorms(
        state: SegmentWriteState,
        sortMap: Sorter.DocMap?
    ) {
        var success = false
        var normsConsumer: NormsConsumer? = null
        try {
            if (state.fieldInfos!!.hasNorms()) {
                val normsFormat: NormsFormat =
                    checkNotNull(state.segmentInfo.codec.normsFormat())
                normsConsumer = normsFormat.normsConsumer(state)

                for (fi in state.fieldInfos) {
                    val perField = checkNotNull(getPerField(fi.name))
                    // we must check the final value of omitNorms for the fieldinfo: it could have
                    // changed for this field since the first time we added it.
                    if (!fi.omitsNorms() && fi.indexOptions != IndexOptions.NONE) {
                        checkNotNull(perField.norms) { "field=" + fi.name }
                        perField.norms!!.finish(state.segmentInfo.maxDoc())
                        perField.norms!!.flush(state, sortMap, normsConsumer)
                    }
                }
            }
            success = true
        } finally {
            if (success) {
                IOUtils.close(normsConsumer)
            } else {
                IOUtils.closeWhileHandlingException(normsConsumer)
            }
        }
    }

    @Throws(IOException::class)
    fun abort() {
        // finalizer will e.g. close any open files in the term vectors writer:
        try {
            AutoCloseable { termsHash.abort() }.use { finalizer ->
                storedFieldsConsumer.abort()
                vectorValuesConsumer.abort()
            }
        } finally {
            Arrays.fill(fieldHash, null)
        }
    }

    private fun rehash() {
        val newHashSize = (fieldHash.size * 2)
        assert(newHashSize > fieldHash.size)

        val newHashArray = kotlin.arrayOfNulls<PerField>(newHashSize)

        // Rehash
        val newHashMask = newHashSize - 1
        for (j in fieldHash.indices) {
            var fp0 = fieldHash[j]
            while (fp0 != null) {
                val hashPos2 = fp0.fieldName.hashCode() and newHashMask
                val nextFP0 = fp0.next
                fp0.next = newHashArray[hashPos2]
                newHashArray[hashPos2] = fp0
                fp0 = nextFP0
            }
        }

        fieldHash = newHashArray
        hashMask = newHashMask
    }

    /** Calls StoredFieldsWriter.startDocument, aborting the segment if it hits any exception.  */
    @Throws(IOException::class)
    private fun startStoredFields(docID: Int) {
        try {
            storedFieldsConsumer.startDocument(docID)
        } catch (th: Throwable) {
            onAbortingException(th)
            throw th
        }
    }

    /** Calls StoredFieldsWriter.finishDocument, aborting the segment if it hits any exception.  */
    @Throws(IOException::class)
    private fun finishStoredFields() {
        try {
            storedFieldsConsumer.finishDocument()
        } catch (th: Throwable) {
            onAbortingException(th)
            throw th
        }
    }

    @Throws(IOException::class)
    fun processDocument(docID: Int, document: Iterable<out IndexableField>) {
        // number of unique fields by names (collapses multiple field instances by the same name)
        var fieldCount = 0
        var indexedFieldCount = 0 // number of unique fields indexed with postings
        val fieldGen = nextFieldGen++
        var docFieldIdx = 0

        // NOTE: we need two passes here, in case there are
        // multi-valued fields, because we must process all
        // instances of a given field at once, since the
        // analyzer is free to reuse TokenStream across fields
        // (i.e., we cannot have more than one TokenStream
        // running "at once"):
        termsHash.startDocument()
        startStoredFields(docID)
        try {
            // 1st pass over doc fields – verify that doc schema matches the index schema
            // build schema for each unique doc field
            for (field in document) {
                val fieldType: IndexableFieldType = field.fieldType()
                val isReserved = field::class == ReservedField::class
                val pf =
                    getOrAddPerField(
                        field.name(),
                        false /* we never add reserved fields during indexing should be done during DWPT setup*/
                    )
                require(pf.reserved == isReserved) {
                    ("\""
                            + field.name()
                            + "\" is a reserved field and should not be added to any document")
                }
                if (pf.fieldGen != fieldGen) { // first time we see this field in this document
                    fields[fieldCount++] = pf
                    pf.fieldGen = fieldGen
                    pf.reset(docID)
                }
                if (docFieldIdx >= docFields.size) oversizeDocFields()
                docFields[docFieldIdx++] = pf
                updateDocFieldSchema(field.name(), pf.schema, fieldType)
            }
            // For each field, if it's the first time we see this field in this segment,
            // initialize its FieldInfo.
            // If we have already seen this field, verify that its schema
            // within the current doc matches its schema in the index.
            for (i in 0..<fieldCount) {
                val pf = fields[i]
                if (pf.fieldInfo == null) {
                    initializeFieldInfo(pf)
                } else {
                    pf.schema.assertSameSchema(pf.fieldInfo!!)
                }
            }

            // 2nd pass over doc fields – index each field
            // also count the number of unique fields indexed with postings
            docFieldIdx = 0
            for (field in document) {
                if (processField(docID, field, docFields[docFieldIdx]!!)) {
                    fields[indexedFieldCount] = docFields[docFieldIdx]!!
                    indexedFieldCount++
                }
                docFieldIdx++
            }
        } finally {
            if (hasHitAbortingException == false) {
                // Finish each indexed field name seen in the document:
                for (i in 0..<indexedFieldCount) {
                    fields[i].finish(docID)
                }
                finishStoredFields()
                // TODO: for broken docs, optimize termsHash.finishDocument
                try {
                    termsHash.finishDocument(docID)
                } catch (th: Throwable) {
                    // Must abort, on the possibility that on-disk term
                    // vectors are now corrupt:
                    abortingExceptionConsumer(th)
                    throw th
                }
            }
        }
    }

    private fun oversizeDocFields() {
        val newDocFields =
            kotlin.arrayOfNulls<PerField>(
                ArrayUtil.oversize(
                    docFields.size + 1,
                    RamUsageEstimator.NUM_BYTES_OBJECT_REF
                )
            )
        System.arraycopy(docFields, 0, newDocFields, 0, docFields.size)
        docFields = newDocFields
    }

    @Throws(IOException::class)
    private fun initializeFieldInfo(pf: PerField) {
        // Create and add a new fieldInfo to fieldInfos for this segment.
        // During the creation of FieldInfo there is also verification of the correctness of all its
        // parameters.

        // If the fieldInfo doesn't exist in globalFieldNumbers for the whole index,
        // it will be added there.
        // If the field already exists in globalFieldNumbers (i.e. field present in other segments),
        // we check consistency of its schema with schema for the whole index.

        val s = pf.schema
        if (indexWriterConfig.indexSort != null && s.docValuesType != DocValuesType.NONE) {
            val indexSort: Sort = indexWriterConfig.indexSort!!
            validateIndexSortDVType(indexSort, pf.fieldName, s.docValuesType)
        }
        if (s.vectorDimension != 0) {
            validateMaxVectorDimension(
                pf.fieldName,
                s.vectorDimension,
                indexWriterConfig.codec.knnVectorsFormat().getMaxDimensions(pf.fieldName)
            )
        }
        val fi: FieldInfo =
            fieldInfosBuilder.add(
                FieldInfo(
                    pf.fieldName,
                    -1,
                    s.storeTermVector,
                    s.omitNorms,  // storePayloads is set up during indexing, if payloads were seen
                    false,
                    s.indexOptions,
                    s.docValuesType,
                    s.docValuesSkipIndex,
                    -1,
                    s.attributes,
                    s.pointDimensionCount,
                    s.pointIndexDimensionCount,
                    s.pointNumBytes,
                    s.vectorDimension,
                    s.vectorEncoding,
                    s.vectorSimilarityFunction,
                    pf.fieldName == fieldInfosBuilder.softDeletesFieldName,
                    pf.fieldName == fieldInfosBuilder.parentFieldName
                )
            )
        pf.fieldInfo = fi
        if (fi.indexOptions != IndexOptions.NONE) {
            pf.setInvertState()
        }
        val dvType: DocValuesType = fi.docValuesType
        when (dvType) {
            DocValuesType.NONE -> {}
            DocValuesType.NUMERIC -> pf.docValuesWriter =
                NumericDocValuesWriter(fi, bytesUsed)

            DocValuesType.BINARY -> pf.docValuesWriter =
                BinaryDocValuesWriter(fi, bytesUsed)

            DocValuesType.SORTED -> pf.docValuesWriter =
                SortedDocValuesWriter(fi, bytesUsed, docValuesBytePool)

            DocValuesType.SORTED_NUMERIC -> pf.docValuesWriter =
                SortedNumericDocValuesWriter(fi, bytesUsed)

            DocValuesType.SORTED_SET -> pf.docValuesWriter =
                SortedSetDocValuesWriter(fi, bytesUsed, docValuesBytePool)

            else -> throw AssertionError("unrecognized DocValues.Type: $dvType")
        }
        if (fi.pointDimensionCount != 0) {
            pf.pointValuesWriter = PointValuesWriter(bytesUsed, fi)
        }
        if (fi.vectorDimension != 0) {
            try {
                pf.knnFieldVectorsWriter = vectorValuesConsumer.addField(fi)
            } catch (th: Throwable) {
                onAbortingException(th)
                throw th
            }
        }
    }

    /** Index each field Returns `true`, if we are indexing a unique field with postings  */
    @Throws(IOException::class)
    private fun processField(docID: Int, field: IndexableField, pf: PerField): Boolean {
        val fieldType: IndexableFieldType = field.fieldType()
        var indexedField = false

        // Invert indexed fields
        if (fieldType.indexOptions() != IndexOptions.NONE) {
            if (pf.first) { // first time we see this field in this doc
                pf.invert(docID, field, true)
                pf.first = false
                indexedField = true
            } else {
                pf.invert(docID, field, false)
            }
        }

        // Add stored fields
        if (fieldType.stored()) {
            val storedValue: StoredValue? = field.storedValue()
            requireNotNull(storedValue != null) { "Cannot store a null value" }
            require(
                !(storedValue!!.type == StoredValue.Type.STRING
                        && storedValue.stringValue.length > IndexWriter.MAX_STORED_STRING_LENGTH)
            ) {
                ("stored field \""
                        + field.name()
                        + "\" is too large ("
                        + storedValue.stringValue.length
                        + " characters) to store")
            }
            try {
                storedFieldsConsumer.writeField(pf.fieldInfo!!, storedValue)
            } catch (th: Throwable) {
                onAbortingException(th)
                throw th
            }
        }

        val dvType: DocValuesType = fieldType.docValuesType()
        if (dvType != DocValuesType.NONE) {
            indexDocValue(docID, pf, dvType, field)
        }
        if (fieldType.pointDimensionCount() != 0) {
            pf.pointValuesWriter!!.addPackedValue(docID, field.binaryValue()!!)
        }
        if (fieldType.vectorDimension() != 0) {
            indexVectorValue(docID, pf, fieldType.vectorEncoding(), field)
        }
        return indexedField
    }

    /**
     * Returns a previously created [PerField], absorbing the type information from [ ], and creates a new [PerField] if this field name wasn't seen yet.
     */
    private fun getOrAddPerField(fieldName: String, reserved: Boolean): PerField {
        val hashPos = fieldName.hashCode() and hashMask
        var pf = fieldHash[hashPos]
        while (pf != null && pf.fieldName == fieldName == false) {
            pf = pf.next
        }
        if (pf == null) {
            // first time we encounter field with this name in this segment
            val schema = FieldSchema(fieldName)
            pf =
                PerField(
                    fieldName,
                    indexCreatedVersionMajor,
                    schema,
                    indexWriterConfig.similarity,
                    indexWriterConfig.infoStream,
                    indexWriterConfig.analyzer,
                    reserved
                )
            pf.next = fieldHash[hashPos]
            fieldHash[hashPos] = pf
            totalFieldCount++
            // At most 50% load factor:
            if (totalFieldCount >= fieldHash.size / 2) {
                rehash()
            }
            if (totalFieldCount > fields.size) {
                val newFields: Array<PerField> =
                    kotlin.arrayOfNulls<PerField>(
                        ArrayUtil.oversize(
                            totalFieldCount,
                            RamUsageEstimator.NUM_BYTES_OBJECT_REF
                        )
                    ) as Array<PerField>
                System.arraycopy(fields, 0, newFields, 0, fields.size)
                fields = newFields
            }
        }
        return pf
    }

    @Throws(IOException::class)
    private fun validateIndexSortDVType(
        indexSort: Sort,
        fieldToValidate: String,
        dvType: DocValuesType
    ) {
        for (sortField in indexSort.sort) {
            val sorter: IndexSorter? = sortField.getIndexSorter()
            checkNotNull(sorter) { "Cannot sort index with sort order $sortField" }
            sorter.getDocComparator(
                object : DocValuesLeafReader() {
                    override fun getNumericDocValues(field: String): NumericDocValues {
                        require(!(field == fieldToValidate && dvType != DocValuesType.NUMERIC)) {
                            ("SortField "
                                    + sortField
                                    + " expected field ["
                                    + field
                                    + "] to be NUMERIC but it is ["
                                    + dvType
                                    + "]")
                        }
                        return DocValues.emptyNumeric()
                    }

                    override fun getBinaryDocValues(field: String): BinaryDocValues {
                        require(!(field == fieldToValidate && dvType != DocValuesType.BINARY)) {
                            ("SortField "
                                    + sortField
                                    + " expected field ["
                                    + field
                                    + "] to be BINARY but it is ["
                                    + dvType
                                    + "]")
                        }
                        return DocValues.emptyBinary()
                    }

                    override fun getSortedDocValues(field: String): SortedDocValues {
                        require(!(field == fieldToValidate && dvType != DocValuesType.SORTED)) {
                            ("SortField "
                                    + sortField
                                    + " expected field ["
                                    + field
                                    + "] to be SORTED but it is ["
                                    + dvType
                                    + "]")
                        }
                        return DocValues.emptySorted()
                    }

                    override fun getSortedNumericDocValues(field: String): SortedNumericDocValues {
                        require(
                            !(field == fieldToValidate
                                    && dvType != DocValuesType.SORTED_NUMERIC)
                        ) {
                            ("SortField "
                                    + sortField
                                    + " expected field ["
                                    + field
                                    + "] to be SORTED_NUMERIC but it is ["
                                    + dvType
                                    + "]")
                        }
                        return DocValues.emptySortedNumeric()
                    }

                    override fun getSortedSetDocValues(field: String): SortedSetDocValues {
                        require(!(field == fieldToValidate && dvType != DocValuesType.SORTED_SET)) {
                            ("SortField "
                                    + sortField
                                    + " expected field ["
                                    + field
                                    + "] to be SORTED_SET but it is ["
                                    + dvType
                                    + "]")
                        }
                        return DocValues.emptySortedSet()
                    }

                    override val fieldInfos: FieldInfos
                        get() {
                            throw UnsupportedOperationException()
                        }
                },
                0
            )
        }
    }

    /** Called from processDocument to index one field's doc value  */
    private fun indexDocValue(
        docID: Int,
        fp: PerField,
        dvType: DocValuesType,
        field: IndexableField
    ) {
        when (dvType) {
            DocValuesType.NUMERIC -> {
                requireNotNull(field.numericValue()) { "field=\"" + fp.fieldInfo!!.name + "\": null value not allowed" }
                (fp.docValuesWriter as NumericDocValuesWriter)
                    .addValue(docID, field.numericValue()!!.toLong())
            }

            DocValuesType.BINARY -> (fp.docValuesWriter as BinaryDocValuesWriter).addValue(
                docID,
                field.binaryValue()!!
            )

            DocValuesType.SORTED -> (fp.docValuesWriter as SortedDocValuesWriter).addValue(
                docID,
                field.binaryValue()!!
            )

            DocValuesType.SORTED_NUMERIC -> (fp.docValuesWriter as SortedNumericDocValuesWriter)
                .addValue(docID, field.numericValue()!!.toLong())

            DocValuesType.SORTED_SET -> (fp.docValuesWriter as SortedSetDocValuesWriter).addValue(
                docID,
                field.binaryValue()!!
            )

            DocValuesType.NONE -> throw AssertionError("unrecognized DocValues.Type: $dvType")
            else -> throw AssertionError("unrecognized DocValues.Type: $dvType")
        }
    }

    @Throws(IOException::class)
    private fun indexVectorValue(
        docID: Int,
        pf: PerField,
        vectorEncoding: VectorEncoding,
        field: IndexableField
    ) {
        when (vectorEncoding) {
            VectorEncoding.BYTE -> (pf.knnFieldVectorsWriter as KnnFieldVectorsWriter<ByteArray>)
                .addValue(docID, (field as KnnByteVectorField).vectorValue())

            VectorEncoding.FLOAT32 -> (pf.knnFieldVectorsWriter as KnnFieldVectorsWriter<FloatArray>)
                .addValue(docID, (field as KnnFloatVectorField).vectorValue())
        }
    }

    /** Returns a previously created [PerField], or null if this field name wasn't seen yet.  */
    private fun getPerField(name: String): PerField {
        val hashPos = name.hashCode() and hashMask
        var fp = fieldHash[hashPos]
        while (fp != null && fp.fieldName != name) {
            fp = fp.next
        }
        return fp!!
    }

    override fun ramBytesUsed(): Long {
        return (bytesUsed.get()
                + storedFieldsConsumer.accountable.ramBytesUsed()
                + termVectorsWriter.accountable.ramBytesUsed()
                + vectorValuesConsumer.getAccountable().ramBytesUsed())
    }

    override val childResources: MutableCollection<Accountable>
        get() = mutableListOf(
            storedFieldsConsumer.accountable,
            termVectorsWriter.accountable,
            vectorValuesConsumer.getAccountable()
        )

    /** NOTE: not static: accesses at least docState, termsHash.  */
    private inner class PerField(
        val fieldName: String,
        val indexCreatedVersionMajor: Int,
        val schema: FieldSchema,
        val similarity: Similarity,
        private val infoStream: InfoStream,
        private val analyzer: Analyzer,
        val reserved: Boolean
    ) : Comparable<PerField> {
        var fieldInfo: FieldInfo? = null
            set(value) {
                assert(field == null)
                field = value
            }

        var invertState: FieldInvertState? = null
        var termsHashPerField: TermsHashPerField? = null

        // Non-null if this field ever had doc values in this
        // segment:
        var docValuesWriter: DocValuesWriter<*>? = null

        // Non-null if this field ever had points in this segment:
        var pointValuesWriter: PointValuesWriter? = null

        // Non-null if this field had vectors in this segment
        var knnFieldVectorsWriter: KnnFieldVectorsWriter<*>? = null

        /** We use this to know when a PerField is seen for the first time in the current document.  */
        var fieldGen: Long = -1

        // Used by the hash table
        var next: PerField? = null

        // Lazy init'd:
        var norms: NormValuesWriter? = null

        // reused
        var tokenStream: TokenStream? = null
        var first = false // first in a document

        fun reset(docId: Int) {
            first = true
            schema.reset(docId)
        }

        fun setInvertState() {
            invertState =
                FieldInvertState(
                    indexCreatedVersionMajor, fieldInfo!!.name, fieldInfo!!.indexOptions
                )
            termsHashPerField = termsHash.addField(invertState!!, fieldInfo!!)
            if (!fieldInfo!!.omitsNorms()) {
                assert(norms == null)
                // Even if no documents actually succeed in setting a norm, we still write norms for this
                // segment
                norms = NormValuesWriter(fieldInfo!!, bytesUsed)
            }
            if (fieldInfo!!.hasTermVectors()) {
                termVectorsWriter.setHasVectors()
            }
        }

        override fun compareTo(other: PerField): Int {
            return this.fieldName.compareTo(other.fieldName)
        }

        @Throws(IOException::class)
        fun finish(docID: Int) {
            if (!fieldInfo!!.omitsNorms()) {
                val normValue: Long
                if (invertState!!.length == 0) {
                    // the field exists in this document, but it did not have
                    // any indexed tokens, so we assign a default value of zero
                    // to the norm
                    normValue = 0
                } else {
                    normValue = similarity.computeNorm(invertState!!)
                    check(normValue != 0L) { "Similarity $similarity return 0 for non-empty field" }
                }
                norms!!.addValue(docID, normValue)
            }
            termsHashPerField!!.finish()
        }

        /**
         * Inverts one field for one document; first is true if this is the first time we are seeing
         * this field name in this document.
         */
        @Throws(IOException::class)
        fun invert(docID: Int, field: IndexableField, first: Boolean) {
            assert(field.fieldType().indexOptions() >= IndexOptions.DOCS)

            if (first) {
                // First time we're seeing this field (indexed) in this document
                invertState!!.reset()
            }

            when (field.invertableType()) {
                InvertableType.BINARY -> invertTerm(docID, field, first)
                InvertableType.TOKEN_STREAM -> invertTokenStream(docID, field, first)
                else -> throw AssertionError()
            }
        }

        @Throws(IOException::class)
        fun invertTokenStream(docID: Int, field: IndexableField, first: Boolean) {
            val analyzed = field.fieldType().tokenized() && analyzer != null
            /*
       * To assist people in tracking down problems in analysis components, we wish to write the field name to the infostream
       * when we fail. We expect some caller to eventually deal with the real exception, so we don't want any 'catch' clauses,
       * but rather a finally that takes note of the problem.
       */
            var succeededInProcessingField = false
            try {
                field.tokenStream(analyzer, tokenStream).also { tokenStream = it }.use { stream ->
                    // reset the TokenStream to the first token
                    stream!!.reset()
                    invertState!!.attributeSource = stream
                    termsHashPerField!!.start(field, first)

                    while (stream.incrementToken()) {
                        // If we hit an exception in stream.next below
                        // (which is fairly common, e.g. if analyzer
                        // chokes on a given document), then it's
                        // non-aborting and (above) this one document
                        // will be marked as deleted, but still
                        // consume a docID

                        val posIncr: Int = invertState!!.posIncrAttribute!!.getPositionIncrement()
                        invertState!!.position += posIncr
                        if (invertState!!.position < invertState!!.lastPosition) {
                            require(posIncr != 0) { "first position increment must be > 0 (got 0) for field '" + field.name() + "'" }
                            require(posIncr >= 0) {
                                ("position increment must be >= 0 (got "
                                        + posIncr
                                        + ") for field '"
                                        + field.name()
                                        + "'")
                            }
                            throw IllegalArgumentException(
                                ("position overflowed Integer.MAX_VALUE (got posIncr="
                                        + posIncr
                                        + " lastPosition="
                                        + invertState!!.lastPosition
                                        + " position="
                                        + invertState!!.position
                                        + ") for field '"
                                        + field.name()
                                        + "'")
                            )
                        } else require(invertState!!.position <= IndexWriter.MAX_POSITION) {
                            ("position "
                                    + invertState!!.position
                                    + " is too large for field '"
                                    + field.name()
                                    + "': max allowed position is "
                                    + IndexWriter.MAX_POSITION)
                        }
                        invertState!!.lastPosition = invertState!!.position
                        if (posIncr == 0) {
                            invertState!!.numOverlap++
                        }

                        val startOffset: Int = invertState!!.offset + invertState!!.offsetAttribute!!.startOffset()
                        val endOffset: Int = invertState!!.offset + invertState!!.offsetAttribute!!.endOffset()
                        require(!(startOffset < invertState!!.lastStartOffset || endOffset < startOffset)) {
                            ("startOffset must be non-negative, and endOffset must be >= startOffset, and offsets must not go backwards "
                                    + "startOffset="
                                    + startOffset
                                    + ",endOffset="
                                    + endOffset
                                    + ",lastStartOffset="
                                    + invertState!!.lastStartOffset
                                    + " for field '"
                                    + field.name()
                                    + "'")
                        }
                        invertState!!.lastStartOffset = startOffset

                        try {
                            invertState!!.length =
                                Math.addExact(
                                    invertState!!.length,
                                    invertState!!.termFreqAttribute!!.termFrequency
                                )
                        } catch (ae: ArithmeticException) {
                            throw IllegalArgumentException(
                                "too many tokens for field \"" + field.name() + "\"", ae
                            )
                        }

                        // System.out.println("  term=" + invertState!!.termAttribute);

                        // If we hit an exception in here, we abort
                        // all buffered documents since the last
                        // flush, on the likelihood that the
                        // internal state of the terms hash is now
                        // corrupt and should not be flushed to a
                        // new segment:
                        try {
                            termsHashPerField!!.add(invertState!!.termAttribute!!.bytesRef, docID)
                        } catch (e: MaxBytesLengthExceededException) {
                            val prefix = ByteArray(30)
                            val bigTerm: BytesRef = invertState!!.termAttribute!!.bytesRef
                            System.arraycopy(bigTerm.bytes, bigTerm.offset, prefix, 0, 30)
                            val msg =
                                ("Document contains at least one immense term in field=\""
                                        + fieldInfo!!.name
                                        + "\" (whose UTF8 encoding is longer than the max length "
                                        + IndexWriter.MAX_TERM_LENGTH
                                        + "), all of which were skipped.  Please correct the analyzer to not produce such terms.  The prefix of the first immense term is: '"
                                        + prefix.contentToString() + "...', original message: "
                                        + e.message)
                            if (infoStream.isEnabled("IW")) {
                                infoStream.message("IW", "ERROR: $msg")
                            }
                            // Document will be deleted above:
                            throw IllegalArgumentException(msg, e)
                        } catch (th: Throwable) {
                            onAbortingException(th)
                            throw th
                        }
                    }

                    // trigger streams to perform end-of-stream operations
                    stream.end()

                    // TODO: maybe add some safety then again, it's already checked
                    // when we come back around to the field...
                    invertState!!.position += invertState!!.posIncrAttribute!!.getPositionIncrement()
                    invertState!!.offset += invertState!!.offsetAttribute!!.endOffset()

                    /* if there is an exception coming through, we won't set this to true here:*/
                    succeededInProcessingField = true
                }
            } finally {
                if (!succeededInProcessingField && infoStream.isEnabled("DW")) {
                    infoStream.message(
                        "DW", "An exception was thrown while processing field " + fieldInfo!!.name
                    )
                }
            }

            if (analyzed) {
                invertState!!.position += analyzer.getPositionIncrementGap(fieldInfo!!.name)
                invertState!!.offset += analyzer.getOffsetGap(fieldInfo!!.name)
            }
        }

        @Throws(IOException::class)
        fun invertTerm(docID: Int, field: IndexableField, first: Boolean) {
            val binaryValue: BytesRef? = field.binaryValue()
            requireNotNull(binaryValue) {
                ("Field "
                        + field.name()
                        + " returns TERM for invertableType() and null for binaryValue(), which is illegal")
            }
            val fieldType: IndexableFieldType = field.fieldType()
            require(
                !(fieldType.tokenized()
                        || fieldType.indexOptions() > IndexOptions.DOCS_AND_FREQS || fieldType.storeTermVectorPositions()
                        || fieldType.storeTermVectorOffsets()
                        || fieldType.storeTermVectorPayloads())
            ) {
                ("Fields that are tokenized or index proximity data must produce a non-null TokenStream, but "
                        + field.name()
                        + " did not")
            }
            invertState!!.attributeSource = null
            invertState!!.position++
            invertState!!.length++
            termsHashPerField!!.start(field, first)
            invertState!!.length = Math.addExact(invertState!!.length, 1)
            try {
                termsHashPerField!!.add(binaryValue, docID)
            } catch (e: MaxBytesLengthExceededException) {
                val prefix = ByteArray(30)
                System.arraycopy(binaryValue.bytes, binaryValue.offset, prefix, 0, 30)
                val msg =
                    ("Document contains at least one immense term in field=\""
                            + fieldInfo!!.name
                            + "\" (whose length is longer than the max length "
                            + IndexWriter.MAX_TERM_LENGTH
                            + "), all of which were skipped. The prefix of the first immense term is: '"
                            + prefix.contentToString() + "...'")
                if (infoStream.isEnabled("IW")) {
                    infoStream.message("IW", "ERROR: $msg")
                }
                throw IllegalArgumentException(msg, e)
            }
        }
    }

    fun getHasDocValues(field: String): DocIdSetIterator? {
        val perField: PerField = getPerField(field)
        if (perField != null) {
            if (perField.docValuesWriter != null) {
                if (perField.fieldInfo!!.docValuesType == DocValuesType.NONE) {
                    return null
                }

                return perField.docValuesWriter!!.docValues
            }
        }
        return null
    }

    private class IntBlockAllocator(private val bytesUsed: Counter) :
        IntBlockPool.Allocator(IntBlockPool.INT_BLOCK_SIZE) {

        override val intBlock: IntArray
            /* Allocate another int[] from the shared pool */
            get() {
                val b = IntArray(IntBlockPool.INT_BLOCK_SIZE)
                bytesUsed.addAndGet((IntBlockPool.INT_BLOCK_SIZE * Int.SIZE_BYTES).toLong())
                return b
            }

        override fun recycleIntBlocks(blocks: Array<IntArray>, offset: Int, length: Int) {
            bytesUsed.addAndGet(-(length * (IntBlockPool.INT_BLOCK_SIZE * Int.SIZE_BYTES)).toLong())
        }
    }

    /**
     * A schema of the field in the current document. With every new document this schema is reset. As
     * the document fields are processed, we update the schema with options encountered in this
     * document. Once the processing for the document is done, we compare the built schema of the
     * current document with the corresponding FieldInfo (FieldInfo is built on a first document in
     * the segment where we encounter this field). If there is inconsistency, we raise an error. This
     * ensures that a field has the same data structures across all documents.
     */
    class FieldSchema(val name: String) {
        private var docID = 0
        val attributes: MutableMap<String, String> = mutableMapOf()
        var omitNorms = false
        var storeTermVector = false
        var indexOptions: IndexOptions = IndexOptions.NONE
        var docValuesType: DocValuesType = DocValuesType.NONE
        var docValuesSkipIndex: DocValuesSkipIndexType =
            DocValuesSkipIndexType.NONE
        var pointDimensionCount = 0
        var pointIndexDimensionCount = 0
        var pointNumBytes = 0
        var vectorDimension = 0
        var vectorEncoding: VectorEncoding =
            VectorEncoding.FLOAT32
        var vectorSimilarityFunction: VectorSimilarityFunction =
            VectorSimilarityFunction.EUCLIDEAN

        fun assertSame(label: String, expected: Boolean, given: Boolean) {
            if (expected != given) {
                raiseNotSame(label, expected, given)
            }
        }

        fun assertSame(label: String, expected: Int, given: Int) {
            if (expected != given) {
                raiseNotSame(label, expected, given)
            }
        }

        fun <T : Enum<*>> assertSame(label: String, expected: T, given: T) {
            if (expected !== given) {
                raiseNotSame(label, expected, given)
            }
        }

        fun raiseNotSame(label: String, expected: Any, given: Any) {
            throw IllegalArgumentException(
                (errMsg
                        + "["
                        + name
                        + "] of doc ["
                        + docID
                        + "]. "
                        + label
                        + ": expected '"
                        + expected
                        + "', but it has '"
                        + given
                        + "'.")
            )
        }

        fun updateAttributes(attrs: MutableMap<String, String>) {
            attrs.forEach { (k: String, v: String) -> this.attributes.put(k, v) }
        }

        fun setIndexOptions(
            newIndexOptions: IndexOptions, newOmitNorms: Boolean, newStoreTermVector: Boolean
        ) {
            if (indexOptions == IndexOptions.NONE) {
                indexOptions = newIndexOptions
                omitNorms = newOmitNorms
                storeTermVector = newStoreTermVector
            } else {
                assertSame("index options", indexOptions, newIndexOptions)
                assertSame("omit norms", omitNorms, newOmitNorms)
                assertSame("store term vector", storeTermVector, newStoreTermVector)
            }
        }

        fun setDocValues(
            newDocValuesType: DocValuesType,
            newDocValuesSkipIndex: DocValuesSkipIndexType
        ) {
            if (docValuesType == DocValuesType.NONE) {
                this.docValuesType = newDocValuesType
                this.docValuesSkipIndex = newDocValuesSkipIndex
            } else {
                assertSame("doc values type", docValuesType, newDocValuesType)
                assertSame("doc values skip index type", docValuesSkipIndex, newDocValuesSkipIndex)
            }
        }

        fun setPoints(dimensionCount: Int, indexDimensionCount: Int, numBytes: Int) {
            if (pointIndexDimensionCount == 0) {
                pointDimensionCount = dimensionCount
                pointIndexDimensionCount = indexDimensionCount
                pointNumBytes = numBytes
            } else {
                assertSame("point dimension", pointDimensionCount, dimensionCount)
                assertSame("point index dimension", pointIndexDimensionCount, indexDimensionCount)
                assertSame("point num bytes", pointNumBytes, numBytes)
            }
        }

        fun setVectors(
            encoding: VectorEncoding,
            similarityFunction: VectorSimilarityFunction,
            dimension: Int
        ) {
            if (vectorDimension == 0) {
                this.vectorEncoding = encoding
                this.vectorSimilarityFunction = similarityFunction
                this.vectorDimension = dimension
            } else {
                assertSame("vector encoding", vectorEncoding, encoding)
                assertSame(
                    "vector similarity function",
                    vectorSimilarityFunction,
                    similarityFunction
                )
                assertSame("vector dimension", vectorDimension, dimension)
            }
        }

        fun reset(doc: Int) {
            docID = doc
            omitNorms = false
            storeTermVector = false
            indexOptions = IndexOptions.NONE
            docValuesType = DocValuesType.NONE
            pointDimensionCount = 0
            pointIndexDimensionCount = 0
            pointNumBytes = 0
            vectorDimension = 0
            vectorEncoding = VectorEncoding.FLOAT32
            vectorSimilarityFunction = VectorSimilarityFunction.EUCLIDEAN
        }

        fun assertSameSchema(fi: FieldInfo) {
            assertSame("index options", fi.indexOptions, indexOptions)
            assertSame("omit norms", fi.omitsNorms(), omitNorms)
            assertSame("store term vector", fi.hasTermVectors(), storeTermVector)
            assertSame("doc values type", fi.docValuesType, docValuesType)
            assertSame("doc values skip index type", fi.docValuesSkipIndexType(), docValuesSkipIndex)
            assertSame("vector similarity function", fi.vectorSimilarityFunction, vectorSimilarityFunction)
            assertSame("vector encoding", fi.vectorEncoding, vectorEncoding)
            assertSame("vector dimension", fi.vectorDimension, vectorDimension)
            assertSame("point dimension", fi.pointDimensionCount, pointDimensionCount)
            assertSame("point index dimension", fi.pointIndexDimensionCount, pointIndexDimensionCount)
            assertSame("point num bytes", fi.pointNumBytes, pointNumBytes)
        }

        companion object {
            private const val errMsg = "Inconsistency of field data structures across documents for field "
        }
    }

    /**
     * Wraps the given field in a reserved field and registers it as reserved. Only DWPT should do
     * this to mark fields as private / reserved to prevent this fieldname to be used from the outside
     * of the IW / DWPT eco-system
     */
    fun <T : IndexableField> markAsReserved(field: T): ReservedField<T> {
        getOrAddPerField(field.name(), true)
        return ReservedField(field)
    }

    class ReservedField<T : IndexableField>(val delegate: T) :
        IndexableField {
        override fun name(): String {
            return delegate.name()
        }

        override fun fieldType(): IndexableFieldType {
            return delegate.fieldType()
        }

        override fun tokenStream(
            analyzer: Analyzer,
            reuse: TokenStream?
        ): TokenStream {
            return delegate.tokenStream(analyzer, reuse)!!
        }

        override fun binaryValue(): BytesRef {
            return delegate.binaryValue()!!
        }

        override fun stringValue(): String {
            return delegate.stringValue()!!
        }

        override val charSequenceValue: CharSequence
            get() = delegate.charSequenceValue!!

        override fun readerValue(): Reader {
            return delegate.readerValue()!!
        }

        override fun numericValue(): Number {
            return delegate.numericValue()!!
        }

        override fun storedValue(): StoredValue {
            return delegate.storedValue()!!
        }

        override fun invertableType(): InvertableType {
            return delegate.invertableType()
        }
    }

    companion object {
        // update schema for field as seen in a particular document
        private fun updateDocFieldSchema(
            fieldName: String, schema: FieldSchema, fieldType: IndexableFieldType
        ) {
            if (fieldType.indexOptions() != IndexOptions.NONE) {
                schema.setIndexOptions(
                    fieldType.indexOptions(), fieldType.omitNorms(), fieldType.storeTermVectors()
                )
            } else {
                // TODO: should this be checked when a fieldType is created
                verifyUnIndexedFieldType(fieldName, fieldType)
            }
            if (fieldType.docValuesType() != DocValuesType.NONE) {
                schema.setDocValues(fieldType.docValuesType(), fieldType.docValuesSkipIndexType())
            } else require(fieldType.docValuesSkipIndexType() === DocValuesSkipIndexType.NONE) {
                ("field '"
                        + schema.name
                        + "' cannot have docValuesSkipIndexType="
                        + fieldType.docValuesSkipIndexType()
                        + " without doc values")
            }
            if (fieldType.pointDimensionCount() != 0) {
                schema.setPoints(
                    fieldType.pointDimensionCount(),
                    fieldType.pointIndexDimensionCount(),
                    fieldType.pointNumBytes()
                )
            }
            if (fieldType.vectorDimension() != 0) {
                schema.setVectors(
                    fieldType.vectorEncoding(),
                    fieldType.vectorSimilarityFunction(),
                    fieldType.vectorDimension()
                )
            }
            if (fieldType.attributes != null && !fieldType.attributes!!.isEmpty()) {
                schema.updateAttributes(fieldType.attributes!!)
            }
        }

        private fun verifyUnIndexedFieldType(name: String, ft: IndexableFieldType) {
            require(!ft.storeTermVectors()) {
                ("cannot store term vectors "
                        + "for a field that is not indexed (field=\""
                        + name
                        + "\")")
            }
            require(!ft.storeTermVectorPositions()) {
                ("cannot store term vector positions "
                        + "for a field that is not indexed (field=\""
                        + name
                        + "\")")
            }
            require(!ft.storeTermVectorOffsets()) {
                ("cannot store term vector offsets "
                        + "for a field that is not indexed (field=\""
                        + name
                        + "\")")
            }
            require(!ft.storeTermVectorPayloads()) {
                ("cannot store term vector payloads "
                        + "for a field that is not indexed (field=\""
                        + name
                        + "\")")
            }
        }

        private fun validateMaxVectorDimension(
            fieldName: String, vectorDim: Int, maxVectorDim: Int
        ) {
            require(vectorDim <= maxVectorDim) {
                ("Field ["
                        + fieldName
                        + "] vector's dimensions must be <= ["
                        + maxVectorDim
                        + "]; got "
                        + vectorDim)
            }
        }
    }
}
