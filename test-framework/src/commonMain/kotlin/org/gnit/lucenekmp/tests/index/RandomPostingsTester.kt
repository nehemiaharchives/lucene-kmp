package org.gnit.lucenekmp.tests.index

import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.IOException
import okio.Path
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.index.BaseTermsEnum
import org.gnit.lucenekmp.index.DocValuesSkipIndexType
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.Impact
import org.gnit.lucenekmp.index.Impacts
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.internal.tests.IndexPackageAccess
import org.gnit.lucenekmp.internal.tests.TestSecrets
import org.gnit.lucenekmp.jdkport.EnumSet
import org.gnit.lucenekmp.jdkport.SortedMap
import org.gnit.lucenekmp.jdkport.TreeMap
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FlushInfo
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.tests.util.shouldRunExhaustivePostingsFormatChecks
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.UnicodeUtil
import org.gnit.lucenekmp.util.Version
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import org.gnit.lucenekmp.util.automaton.Operations
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


/** Helper class extracted from BasePostingsFormatTestCase to exercise a postings format.  */
class RandomPostingsTester(random: Random) {
    /** Which features to test.  */
    enum class Option {
        // Sometimes use .advance():
        SKIPPING,

        // Sometimes reuse the PostingsEnum across terms:
        REUSE_ENUMS,

        // Sometimes pass non-null live docs:
        LIVE_DOCS,

        // Sometimes seek to term using previously saved TermState:
        TERM_STATE,

        // Sometimes don't fully consume docs from the enum
        PARTIAL_DOC_CONSUME,

        // Sometimes don't fully consume positions at each doc
        PARTIAL_POS_CONSUME,

        // Check DocIdSetIterator#intoBitSet
        INTO_BIT_SET,

        // Sometimes check payloads
        PAYLOADS,

        // Test w/ multiple threads
        THREADS
    }

    private val totalPostings: Long = 0
    private val totalPayloadBytes: Long = 0

    // Holds all postings:
    private val fields: MutableMap<String, SortedMap<BytesRef, SeedAndOrd>>

    private val fieldInfos: FieldInfos

    var allTerms: MutableList<FieldAndTerm>
    private var maxDoc: Int

    val random: Random

    /** Given the same random seed this always enumerates the same random postings  */
    class SeedPostings(
        seed: Long,
        minDocFreq: Int,
        maxDocFreq: Int,
        options: IndexOptions,
        private val allowPayloads: Boolean
    ) : PostingsEnum() {
        // Used only to generate docIDs; this way if you pull w/
        // or w/o positions you get the same docID sequence:
        private val docRandom: Random
        private val random: Random
        var docFreq: Int
        private val maxDocSpacing: Int
        private val payloadSize: Int
        private val fixedPayloads: Boolean
        override val payload: BytesRef?
        fun get(): BytesRef? {
            return if (payload!!.length == 0) null else payload
        }

        private val doPositions: Boolean

        private var docID = -1
        private var freq = 0
        var upto: Int = 0

        private var pos = 0
        private var offset = 0
        private var startOffset = 0
        private var endOffset = 0
        private var posSpacing = 0
        private var posUpto = 0

        init {
            random = Random(seed)
            docRandom = Random(random.nextLong())
            docFreq = TestUtil.nextInt(random, minDocFreq, maxDocFreq)

            // TODO: more realistic to inversely tie this to numDocs:
            maxDocSpacing = TestUtil.nextInt(random, 1, 100)

            if (random.nextInt(10) == 7) {
                // 10% of the time create big payloads:
                payloadSize = 1 + random.nextInt(3)
            } else {
                payloadSize = 1 + random.nextInt(1)
            }

            fixedPayloads = random.nextBoolean()
            val payloadBytes = ByteArray(payloadSize)
            payload = BytesRef(payloadBytes)
            doPositions =
                IndexOptions.DOCS_AND_FREQS_AND_POSITIONS <= options
        }

        override fun nextDoc(): Int {
            while (true) {
                _nextDoc()
                return docID
            }
        }

        private fun _nextDoc(): Int {
            if (docID == -1) {
                docID = 0
            }
            // Must consume random:
            while (posUpto < freq) {
                nextPosition()
            }

            if (upto < docFreq) {
                if (upto == 0 && docRandom.nextBoolean()) {
                    // Sometimes index docID = 0
                } else if (maxDocSpacing == 1) {
                    docID++
                } else {
                    // TODO: sometimes have a biggish gap here!
                    docID += TestUtil.nextInt(
                        docRandom,
                        1,
                        maxDocSpacing
                    )
                }

                if (random.nextInt(200) == 17) {
                    freq = TestUtil.nextInt(random, 1, 1000)
                } else if (random.nextInt(10) == 17) {
                    freq = TestUtil.nextInt(random, 1, 20)
                } else {
                    freq = TestUtil.nextInt(random, 1, 4)
                }

                pos = 0
                offset = 0
                posUpto = 0
                posSpacing = TestUtil.nextInt(random, 1, 100)

                upto++
                return docID
            } else {
                return NO_MORE_DOCS.also { docID = it }
            }
        }

        override fun docID(): Int {
            return docID
        }

        override fun freq(): Int {
            return freq
        }

        override fun nextPosition(): Int {
            if (!doPositions) {
                posUpto = freq
                return -1
            }
            assert(posUpto < freq)

            if (posUpto == 0 && random.nextBoolean()) {
                // Sometimes index pos = 0
            } else if (posSpacing == 1) {
                pos++
            } else {
                pos += TestUtil.nextInt(random, 1, posSpacing)
            }

            if (payloadSize != 0) {
                if (fixedPayloads) {
                    payload!!.length = payloadSize
                    random.nextBytes(payload.bytes)
                } else {
                    val thisPayloadSize: Int = random.nextInt(payloadSize)
                    if (thisPayloadSize != 0) {
                        payload!!.length = payloadSize
                        random.nextBytes(payload.bytes)
                    } else {
                        payload!!.length = 0
                    }
                }
            } else {
                payload!!.length = 0
            }
            if (!allowPayloads) {
                payload.length = 0
            }

            startOffset = offset + random.nextInt(5)
            endOffset = startOffset + random.nextInt(10)
            offset = endOffset

            posUpto++
            return pos
        }

        override fun startOffset(): Int {
            return startOffset
        }

        override fun endOffset(): Int {
            return endOffset
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            return slowAdvance(target)
        }

        override fun cost(): Long {
            return docFreq.toLong()
        }
    }

    /** Holds one field, term and ord.  */
    class FieldAndTerm(val field: String, term: BytesRef, val ord: Long) {
        val term: BytesRef

        init {
            this.term = BytesRef.deepCopyOf(term)
        }
    }

    private class SeedAndOrd(val seed: Long) {
        var ord: Long = 0
    }

    private class SeedFields(
        fields: MutableMap<String, SortedMap<BytesRef, SeedAndOrd>>,
        fieldInfos: FieldInfos,
        maxAllowed: IndexOptions,
        allowPayloads: Boolean
    ) : Fields() {
        val fields: MutableMap<String, SortedMap<BytesRef, SeedAndOrd>>
        val fieldInfos: FieldInfos
        val maxAllowed: IndexOptions
        val allowPayloads: Boolean

        init {
            this.fields = fields
            this.fieldInfos = fieldInfos
            this.maxAllowed = maxAllowed
            this.allowPayloads = allowPayloads
        }

        override fun iterator(): MutableIterator<String> {
            return fields.keys.iterator()
        }

        override fun terms(field: String?): Terms? {
            val terms: SortedMap<BytesRef, SeedAndOrd>? = fields[field]
            if (terms == null) {
                return null
            } else {
                return SeedTerms(terms, fieldInfos.fieldInfo(field)!!, maxAllowed, allowPayloads)
            }
        }

        override fun size(): Int {
            return fields.size
        }
    }

    private class SeedTerms(
        terms: SortedMap<BytesRef, SeedAndOrd>,
        fieldInfo: FieldInfo,
        maxAllowed: IndexOptions,
        allowPayloads: Boolean
    ) : Terms() {
        val terms: SortedMap<BytesRef, SeedAndOrd>
        val fieldInfo: FieldInfo
        val maxAllowed: IndexOptions
        val allowPayloads: Boolean

        init {
            this.terms = terms
            this.fieldInfo = fieldInfo
            this.maxAllowed = maxAllowed
            this.allowPayloads = allowPayloads
        }

        override fun iterator(): TermsEnum {
            val termsEnum = SeedTermsEnum(terms, maxAllowed, allowPayloads)
            termsEnum.reset()

            return termsEnum
        }

        override fun size(): Long {
            return terms.size.toLong()
        }

        override val sumTotalTermFreq: Long
            get() {
                throw UnsupportedOperationException()
            }

        override val sumDocFreq: Long
            get() {
                throw UnsupportedOperationException()
            }

        override val docCount: Int
            get() {
                throw UnsupportedOperationException()
            }

        override fun hasFreqs(): Boolean {
            return fieldInfo.indexOptions
                .compareTo(IndexOptions.DOCS_AND_FREQS) >= 0
        }

        override fun hasOffsets(): Boolean {
            return (fieldInfo
                .indexOptions
                .compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
                    >= 0)
        }

        override fun hasPositions(): Boolean {
            return fieldInfo.indexOptions
                .compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0
        }

        override fun hasPayloads(): Boolean {
            return allowPayloads && fieldInfo.hasPayloads()
        }
    }

    private class SeedTermsEnum(
        terms: SortedMap<BytesRef, SeedAndOrd>,
        maxAllowed: IndexOptions,
        allowPayloads: Boolean
    ) : BaseTermsEnum() {
        val terms: SortedMap<BytesRef, SeedAndOrd>
        val maxAllowed: IndexOptions
        val allowPayloads: Boolean

        private var iterator: MutableIterator<MutableMap.MutableEntry<BytesRef, SeedAndOrd>>? = null

        private var current: MutableMap.MutableEntry<BytesRef, SeedAndOrd>? = null

        init {
            this.terms = terms
            this.maxAllowed = maxAllowed
            this.allowPayloads = allowPayloads
        }

        fun reset() {
            iterator = terms.entries.iterator()
        }

        override fun seekCeil(text: BytesRef): SeekStatus {
            val tailMap: SortedMap<BytesRef, SeedAndOrd> = terms.tailMap(text)!!
            if (tailMap.isEmpty()) {
                return SeekStatus.END
            } else {
                iterator = tailMap.entries.iterator()
                current = iterator!!.next()
                if (tailMap.firstKey() == text) {
                    return SeekStatus.FOUND
                } else {
                    return SeekStatus.NOT_FOUND
                }
            }
        }

        override fun next(): BytesRef? {
            if (iterator!!.hasNext()) {
                current = iterator!!.next()
                return term()
            } else {
                return null
            }
        }

        override fun seekExact(ord: Long) {
            throw UnsupportedOperationException()
        }

        override fun term(): BytesRef {
            return current!!.key
        }

        override fun ord(): Long {
            return current!!.value.ord
        }

        override fun docFreq(): Int {
            throw UnsupportedOperationException()
        }

        override fun totalTermFreq(): Long {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun postings(
            reuse: PostingsEnum?,
            flags: Int
        ): PostingsEnum? {
            if (PostingsEnum.featureRequested(
                    flags,
                    PostingsEnum.POSITIONS
                )
            ) {
                if (maxAllowed.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) < 0) {
                    return null
                }
                if (PostingsEnum.featureRequested(
                        flags,
                        PostingsEnum.OFFSETS
                    )
                    && maxAllowed.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS) < 0
                ) {
                    return null
                }
                if (PostingsEnum.featureRequested(
                        flags,
                        PostingsEnum.PAYLOADS
                    ) && allowPayloads == false
                ) {
                    return null
                }
            }
            if (PostingsEnum.featureRequested(
                    flags,
                    PostingsEnum.FREQS
                )
                && maxAllowed.compareTo(IndexOptions.DOCS_AND_FREQS) < 0
            ) {
                return null
            }
            return getSeedPostings(
                current!!.key.utf8ToString(), current!!.value.seed, maxAllowed, allowPayloads
            )
        }

        @Throws(IOException::class)
        override fun impacts(flags: Int): ImpactsEnum {
            throw UnsupportedOperationException()
        }
    }

    private class ThreadState {
        // Only used with REUSE option:
        var reusePostingsEnum: PostingsEnum? = null
    }

    private var currentFieldInfos: FieldInfos? = null

    init {
        fields =
            TreeMap<String, SortedMap<BytesRef, SeedAndOrd>>()

        this.random = random

        val numFields: Int = TestUtil.nextInt(random, 1, 5)
        if (LuceneTestCase.VERBOSE) {
            println("TEST: $numFields fields")
        }
        maxDoc = 0

        val fieldInfoArray: Array<FieldInfo> =
            kotlin.arrayOfNulls<FieldInfo>(numFields) as Array<FieldInfo>
        var fieldUpto = 0
        while (fieldUpto < numFields) {
            val field: String = TestUtil.randomSimpleString(random)
            if (fields.containsKey(field)) {
                continue
            }

            fieldInfoArray[fieldUpto] =
                FieldInfo(
                    field,
                    fieldUpto,
                    false,
                    false,
                    true,
                    IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS,
                    DocValuesType.NONE,
                    DocValuesSkipIndexType.NONE,
                    -1,
                    HashMap<String, String>(),
                    0,
                    0,
                    0,
                    0,
                    VectorEncoding.FLOAT32,
                    VectorSimilarityFunction.EUCLIDEAN,
                    false,
                    false
                )
            fieldUpto++

            val postings: SortedMap<BytesRef, SeedAndOrd> = TreeMap()
            fields[field] = postings
            val seenTerms: MutableSet<String> = mutableSetOf()

            val numTerms: Int
            if (random.nextInt(10) == 7) {
                numTerms = LuceneTestCase.atLeast(random, 5) // TODO reduced from 50 to 5 for dev speed
            } else {
                numTerms = TestUtil.nextInt(random, 1, 2) // TODO reduced from 2,20 to 1,2 for dev speed
            }

            while (postings.size < numTerms) {
                val termUpto: Int = postings.size
                // Cannot contain surrogates else default Java string sort order (by UTF16 code unit) is
                // different from Lucene:
                var term: String = TestUtil.randomSimpleString(random)
                if (seenTerms.contains(term)) {
                    continue
                }
                seenTerms.add(term)

                if (LuceneTestCase.TEST_NIGHTLY && termUpto == 0 && fieldUpto == 1) {
                    // Make 1 big term:
                    term = "big_$term"
                } else if (termUpto == 1 && fieldUpto == 1) {
                    // Make 1 medium term:
                    term = "medium_$term"
                } else if (random.nextBoolean()) {
                    // Low freq term:
                    term = "low_$term"
                } else {
                    // Very low freq term (don't multiply by RANDOM_MULTIPLIER):
                    term = "overflow_$term"
                }

                val termSeed: Long = random.nextLong()
                postings[BytesRef(term)] = SeedAndOrd(termSeed)

                // NOTE: sort of silly: we enum all the docs just to
                // get the maxDoc
                val postingsEnum: PostingsEnum =
                    getSeedPostings(term, termSeed, IndexOptions.DOCS, true)
                var doc: Int
                var lastDoc = 0
                while ((postingsEnum.nextDoc()
                        .also { doc = it }) != DocIdSetIterator.NO_MORE_DOCS
                ) {
                    lastDoc = doc
                }
                maxDoc = max(lastDoc, maxDoc)
            }

            // assign ords
            var ord: Long = 0
            for (ent in postings.values) {
                ent.ord = ord++
            }
        }

        fieldInfos = FieldInfos(fieldInfoArray)

        // It's the count, not the last docID:
        maxDoc++

        allTerms = ArrayList<FieldAndTerm>()
        for (fieldEnt in fields.entries) {
            val field = fieldEnt.key
            var ord: Long = 0
            for (termEnt in fieldEnt.value.entries) {
                allTerms.add(FieldAndTerm(field, termEnt.key, ord++))
            }
        }

        if (LuceneTestCase.VERBOSE) {
            println(
                ("TEST: done init postings; "
                        + allTerms.size
                        + " total terms, across "
                        + fieldInfos.size()
                        + " fields")
            )
        }
    }

    // maxAllowed = the "highest" we can index, but we will still
    // randomly index at lower IndexOption
    @Throws(IOException::class)
    fun buildIndex(
        codec: Codec,
        dir: Directory,
        maxAllowed: IndexOptions,
        allowPayloads: Boolean,
        alwaysTestMax: Boolean
    ): FieldsProducer {
        val segmentInfo = SegmentInfo(
                dir,
                Version.LATEST,
                Version.LATEST,
                "_0",
                maxDoc,
                false,
                false,
                codec,
                mutableMapOf(),
                StringHelper.randomId(),
                HashMap(),
                null
            )

        /*val maxIndexOption: Int =
            java.util.Arrays.asList<IndexOptions>(*IndexOptions.entries.toTypedArray())
                .indexOf(maxAllowed)*/
        val maxIndexOption = IndexOptions.entries.indexOf(maxAllowed)

        if (LuceneTestCase.VERBOSE) {
            println("\nTEST: now build index")
        }

        // TODO use allowPayloads
        val newFieldInfoArray: Array<FieldInfo> =
            kotlin.arrayOfNulls<FieldInfo>(fields.size) as Array<FieldInfo>
        for (fieldUpto in 0..<fields.size) {
            val oldFieldInfo: FieldInfo = fieldInfos.fieldInfo(fieldUpto)!!

            // Randomly picked the IndexOptions to index this
            // field with:
            val indexOptions: IndexOptions =
                IndexOptions.entries[if (alwaysTestMax) maxIndexOption else TestUtil.nextInt(
                    random,
                    1,
                    maxIndexOption
                )]
            val doPayloads =
                indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS && allowPayloads

            newFieldInfoArray[fieldUpto] =
                FieldInfo(
                    oldFieldInfo.name,
                    fieldUpto,
                    storeTermVector = false,
                    omitNorms = false,
                    storePayloads = doPayloads,
                    indexOptions = indexOptions,
                    docValues = DocValuesType.NONE,
                    docValuesSkipIndex = DocValuesSkipIndexType.NONE,
                    dvGen = -1,
                    attributes = HashMap(),
                    pointDimensionCount = 0,
                    pointIndexDimensionCount = 0,
                    pointNumBytes = 0,
                    vectorDimension = 0,
                    vectorEncoding = VectorEncoding.FLOAT32,
                    vectorSimilarityFunction = VectorSimilarityFunction.EUCLIDEAN,
                    softDeletesField = false,
                    isParentField = false
                )
        }

        val newFieldInfos = FieldInfos(newFieldInfoArray)

        // Estimate that flushed segment size will be 25% of
        // what we use in RAM:
        val bytes = totalPostings * 8 + totalPayloadBytes

        val writeState = SegmentWriteState(
                null,
                dir,
                segmentInfo,
                newFieldInfos,
                null,
                IOContext(FlushInfo(maxDoc, bytes))
            )

        val seedFields: Fields =
            SeedFields(fields, newFieldInfos, maxAllowed, allowPayloads)

        val fakeNorms: NormsProducer =
            object : NormsProducer() {
                @Throws(IOException::class)
                override fun close() {
                }

                @Throws(IOException::class)
                override fun getNorms(field: FieldInfo): NumericDocValues? {
                    if (newFieldInfos.fieldInfo(field.number)!!.hasNorms()) {
                        return object : NumericDocValues() {
                            var doc: Int = -1

                            @Throws(IOException::class)
                            override fun nextDoc(): Int {
                                if (++doc == segmentInfo.maxDoc()) {
                                    return NO_MORE_DOCS.also {
                                        this.doc = it
                                    }
                                }
                                return doc
                            }

                            override fun docID(): Int {
                                return doc
                            }

                            override fun cost(): Long {
                                return segmentInfo.maxDoc().toLong()
                            }

                            @Throws(IOException::class)
                            override fun advance(target: Int): Int {
                                return (
                                        if (target >= segmentInfo.maxDoc()) NO_MORE_DOCS else target).also {
                                    doc = it
                                }
                            }

                            @Throws(IOException::class)
                            override fun advanceExact(target: Int): Boolean {
                                doc = target
                                return true
                            }

                            @Throws(IOException::class)
                            override fun longValue(): Long {
                                return DOC_TO_NORM(doc)
                            }
                        }
                    } else {
                        return null
                    }
                }

                @Throws(IOException::class)
                override fun checkIntegrity() {
                }
            }
        val consumer: FieldsConsumer =
            codec.postingsFormat().fieldsConsumer(writeState)
        var success = false
        try {
            consumer.write(seedFields, fakeNorms)
            success = true
        } finally {
            if (success) {
                IOUtils.close(consumer)
            } else {
                IOUtils.closeWhileHandlingException(consumer)
            }
        }

        if (LuceneTestCase.VERBOSE) {
            println("TEST: after indexing: files=")
            for (file in dir.listAll()) {
                println("  " + file + ": " + dir.fileLength(file) + " bytes")
            }
        }

        currentFieldInfos = newFieldInfos

        val readState =
            SegmentReadState(
                dir,
                segmentInfo,
                newFieldInfos,
                IOContext.DEFAULT
            )

        return codec.postingsFormat().fieldsProducer(readState)
    }

    @Throws(IOException::class)
    private fun verifyEnum(
        random: Random,
        threadState: ThreadState,
        field: String,
        term: BytesRef,
        termsEnum: TermsEnum,  // Maximum options (docs/freqs/positions/offsets) to test:

        maxTestOptions: IndexOptions,
        maxIndexOptions: IndexOptions,
        options: EnumSet<Option>,
        alwaysTestMax: Boolean
    ) {
        if (LuceneTestCase.VERBOSE) {
            println("  verifyEnum: options=$options maxTestOptions=$maxTestOptions")
        }

        // Make sure TermsEnum really is positioned on the
        // expected term:
        assertEquals(term, termsEnum.term())

        val fieldInfo: FieldInfo = currentFieldInfos!!.fieldInfo(field)!!

        // NOTE: can be empty list if we are using liveDocs:
        val expected =
            getSeedPostings(
                term.utf8ToString(), fields[field]!![term]!!.seed, maxIndexOptions, true
            )
        assertEquals(expected.docFreq.toLong(), termsEnum.docFreq().toLong())

        val allowFreqs =
            fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS
                    && maxTestOptions >= IndexOptions.DOCS_AND_FREQS
        val doCheckFreqs = allowFreqs && (alwaysTestMax || random.nextInt(3) <= 2)

        val allowPositions = fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
                && maxTestOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
        val doCheckPositions = allowPositions && (alwaysTestMax || random.nextInt(3) <= 2)

        val allowOffsets =
            (fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
                    && maxTestOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
        val doCheckOffsets = allowOffsets && (alwaysTestMax || random.nextInt(3) <= 2)

        val doCheckPayloads =
            options.contains(Option.PAYLOADS)
                    && allowPositions
                    && fieldInfo.hasPayloads()
                    && (alwaysTestMax || random.nextInt(3) <= 2)

        var prevPostingsEnum: PostingsEnum? = null

        val postingsEnum: PostingsEnum?

        if (!doCheckPositions) {
            if (allowPositions && random.nextInt(10) == 7) {
                // 10% of the time, even though we will not check positions, pull a DocsAndPositions enum

                if (options.contains(Option.REUSE_ENUMS) && random.nextInt(10) < 9) {
                    prevPostingsEnum = threadState.reusePostingsEnum
                }

                var flags = PostingsEnum.POSITIONS.toInt()
                if (alwaysTestMax || random.nextBoolean()) {
                    flags = flags or PostingsEnum.OFFSETS.toInt()
                }
                if (alwaysTestMax || random.nextBoolean()) {
                    flags = flags or PostingsEnum.PAYLOADS.toInt()
                }

                if (LuceneTestCase.VERBOSE) {
                    println("  get DocsEnum (but we won't check positions) flags=$flags")
                }

                threadState.reusePostingsEnum = termsEnum.postings(prevPostingsEnum, flags)
                postingsEnum = threadState.reusePostingsEnum
            } else {
                if (LuceneTestCase.VERBOSE) {
                    println("  get DocsEnum")
                }
                if (options.contains(Option.REUSE_ENUMS) && random.nextInt(10) < 9) {
                    prevPostingsEnum = threadState.reusePostingsEnum
                }
                threadState.reusePostingsEnum =
                    termsEnum.postings(
                        prevPostingsEnum,
                        (if (doCheckFreqs) PostingsEnum.FREQS else PostingsEnum.NONE).toInt()
                    )
                postingsEnum = threadState.reusePostingsEnum
            }
        } else {
            if (options.contains(Option.REUSE_ENUMS) && random.nextInt(10) < 9) {
                prevPostingsEnum = threadState.reusePostingsEnum
            }

            var flags = PostingsEnum.POSITIONS.toInt()
            if (alwaysTestMax || doCheckOffsets || random.nextInt(3) == 1) {
                flags = flags or PostingsEnum.OFFSETS.toInt()
            }
            if (alwaysTestMax || doCheckPayloads || random.nextInt(3) == 1) {
                flags = flags or PostingsEnum.PAYLOADS.toInt()
            }

            if (LuceneTestCase.VERBOSE) {
                println("  get DocsEnum flags=$flags")
            }

            threadState.reusePostingsEnum = termsEnum.postings(prevPostingsEnum, flags)
            postingsEnum = threadState.reusePostingsEnum
        }

        assertNotNull("null DocsEnum", postingsEnum.toString())
        val initialDocID: Int = postingsEnum!!.docID()
        assertEquals(
            -1,
            initialDocID.toLong(), message = "initial docID should be -1: $postingsEnum"
        )

        if (LuceneTestCase.VERBOSE) {
            if (prevPostingsEnum == null) {
                println("  got enum=$postingsEnum")
            } else if (prevPostingsEnum === postingsEnum) {
                println("  got reuse enum=$postingsEnum")
            } else {
                println(
                    "  got enum=$postingsEnum (reuse of $prevPostingsEnum failed)"
                )
            }
        }

        // 10% of the time don't consume all docs:
        val stopAt: Int
        if (!alwaysTestMax && options.contains(Option.PARTIAL_DOC_CONSUME)
            && expected.docFreq > 1 && random.nextInt(10) == 7
        ) {
            stopAt = random.nextInt(expected.docFreq - 1)
            if (LuceneTestCase.VERBOSE) {
                println(
                    "  will not consume all docs (" + stopAt + " vs " + expected.docFreq + ")"
                )
            }
        } else {
            stopAt = expected.docFreq
            if (LuceneTestCase.VERBOSE) {
                println("  consume all docs")
            }
        }

        val skipChance = if (alwaysTestMax) 0.5 else random.nextDouble()
        val numSkips =
            if (expected.docFreq < 3) 1 else TestUtil.nextInt(
                random,
                1,
                min(20, expected.docFreq / 3)
            )
        val skipInc = expected.docFreq / numSkips
        val skipDocInc = maxDoc / numSkips

        // Sometimes do 100% skipping:
        val doAllSkipping = options.contains(Option.SKIPPING) && random.nextInt(7) == 1

        val freqAskChance = if (alwaysTestMax) 1.0 else random.nextDouble()
        val payloadCheckChance = if (alwaysTestMax) 1.0 else random.nextDouble()
        val offsetCheckChance = if (alwaysTestMax) 1.0 else random.nextDouble()

        if (LuceneTestCase.VERBOSE) {
            if (options.contains(Option.SKIPPING)) {
                println("  skipChance=$skipChance numSkips=$numSkips")
            } else {
                println("  no skipping")
            }
            if (doCheckFreqs) {
                println("  freqAskChance=$freqAskChance")
            }
            if (doCheckPayloads) {
                println("  payloadCheckChance=$payloadCheckChance")
            }
            if (doCheckOffsets) {
                println("  offsetCheckChance=$offsetCheckChance")
            }
        }

        while (expected.upto <= stopAt) {
            if (expected.upto == stopAt) {
                if (stopAt == expected.docFreq) {
                    assertEquals(
                        DocIdSetIterator.NO_MORE_DOCS.toLong(),
                        postingsEnum.nextDoc().toLong(),
                        message = "DocsEnum should have ended but didn't"
                    )

                    // Common bug is to forget to set this.doc=NO_MORE_DOCS in the enum!:
                    assertEquals(
                        DocIdSetIterator.NO_MORE_DOCS.toLong(),
                        postingsEnum.docID().toLong(),
                        message = "DocsEnum should have ended but didn't"
                    )
                }
                break
            }

            if (options.contains(Option.SKIPPING)
                && (doAllSkipping || random.nextDouble() <= skipChance)
            ) {
                var targetDocID = -1
                if (expected.upto < stopAt && random.nextBoolean()) {
                    // Pick target we know exists:
                    val skipCount: Int =
                        TestUtil.nextInt(random, 1, skipInc)
                    for (skip in 0..<skipCount) {
                        if (expected.nextDoc() == DocIdSetIterator.NO_MORE_DOCS) {
                            break
                        }
                    }
                } else {
                    // Pick random target (might not exist):
                    val skipDocIDs: Int =
                        TestUtil.nextInt(random, 1, skipDocInc)
                    if (skipDocIDs > 0) {
                        targetDocID = expected.docID() + skipDocIDs
                        expected.advance(targetDocID)
                    }
                }

                if (expected.upto >= stopAt) {
                    val target =
                        if (random.nextBoolean()) maxDoc else DocIdSetIterator.NO_MORE_DOCS
                    if (LuceneTestCase.VERBOSE) {
                        println("  now advance to end (target=$target)")
                    }
                    assertEquals(
                        DocIdSetIterator.NO_MORE_DOCS.toLong(),
                        postingsEnum.advance(target).toLong(),
                        message = "DocsEnum should have ended but didn't"
                    )
                    break
                } else {
                    if (LuceneTestCase.VERBOSE) {
                        if (targetDocID != -1) {
                            println(
                                ("  now advance to random target="
                                        + targetDocID
                                        + " ("
                                        + expected.upto
                                        + " of "
                                        + stopAt
                                        + ") current="
                                        + postingsEnum.docID())
                            )
                        } else {
                            println(
                                ("  now advance to known-exists target="
                                        + expected.docID()
                                        + " ("
                                        + expected.upto
                                        + " of "
                                        + stopAt
                                        + ") current="
                                        + postingsEnum.docID())
                            )
                        }
                    }
                    val docID: Int =
                        postingsEnum.advance(if (targetDocID != -1) targetDocID else expected.docID())
                    assertEquals(
                        expected.docID().toLong(),
                        docID.toLong(), "docID is wrong"
                    )
                }
            } else {
                expected.nextDoc()
                if (LuceneTestCase.VERBOSE) {
                    println(
                        ("  now nextDoc to "
                                + expected.docID()
                                + " ("
                                + expected.upto
                                + " of "
                                + stopAt
                                + ")")
                    )
                }
                val docID: Int = postingsEnum.nextDoc()
                assertEquals(
                    expected.docID().toLong(),
                    docID.toLong(), message = "docID is wrong"
                )
                if (docID == DocIdSetIterator.NO_MORE_DOCS) {
                    break
                }
            }

            if (doCheckFreqs && random.nextDouble() <= freqAskChance) {
                if (LuceneTestCase.VERBOSE) {
                    println("    now freq()=" + expected.freq())
                }
                val freq: Int = postingsEnum.freq()
                assertEquals(
                    expected.freq().toLong(),
                    freq.toLong(), message = "freq is wrong"
                )
            }

            if (doCheckPositions) {
                val freq: Int = postingsEnum.freq()
                val numPosToConsume: Int
                if (!alwaysTestMax && options.contains(Option.PARTIAL_POS_CONSUME)
                    && random.nextInt(5) == 1
                ) {
                    numPosToConsume = random.nextInt(freq)
                } else {
                    numPosToConsume = freq
                }

                for (i in 0..<numPosToConsume) {
                    val pos = expected.nextPosition()
                    if (LuceneTestCase.VERBOSE) {
                        println("    now nextPosition to $pos")
                    }
                    assertEquals(
                        pos.toLong(),
                        postingsEnum.nextPosition().toLong(), message = "position is wrong"
                    )

                    if (doCheckPayloads) {
                        val expectedPayload: BytesRef? = expected.payload
                        if (random.nextDouble() <= payloadCheckChance) {
                            if (LuceneTestCase.VERBOSE) {
                                println(
                                    "      now check expectedPayload length="
                                            + (if (expectedPayload == null) 0 else expectedPayload.length)
                                )
                            }
                            if (expectedPayload == null || expectedPayload.length == 0) {
                                assertNull(
                                    postingsEnum.payload, message = "should not have payload"
                                )
                            } else {
                                var payload: BytesRef? =
                                    postingsEnum.payload
                                assertNotNull(
                                    payload, message = "should have payload but doesn't"
                                )

                                assertEquals(
                                    expectedPayload.length.toLong(),
                                    payload.length.toLong(), message = "payload length is wrong"
                                )
                                for (byteUpto in 0..<expectedPayload.length) {
                                    assertEquals(
                                        expectedPayload.bytes[expectedPayload.offset + byteUpto].toLong(),
                                        payload.bytes[payload.offset + byteUpto].toLong(),
                                        message = "payload bytes are wrong"
                                    )
                                }

                                // make a deep copy
                                payload = BytesRef.deepCopyOf(payload)
                                assertEquals(
                                    payload,
                                    postingsEnum.payload,
                                    "2nd call to getPayload returns something different!"
                                )
                            }
                        } else {
                            if (LuceneTestCase.VERBOSE) {
                                println(
                                    "      skip check payload length="
                                            + (if (expectedPayload == null) 0 else expectedPayload.length)
                                )
                            }
                        }
                    }

                    if (doCheckOffsets) {
                        if (random.nextDouble() <= offsetCheckChance) {
                            if (LuceneTestCase.VERBOSE) {
                                println(
                                    ("      now check offsets: startOff="
                                            + expected.startOffset()
                                            + " endOffset="
                                            + expected.endOffset())
                                )
                            }
                            assertEquals(
                                expected.startOffset().toLong(),
                                postingsEnum.startOffset().toLong(),
                                message = "startOffset is wrong"
                            )
                            assertEquals(
                                expected.endOffset().toLong(),
                                postingsEnum.endOffset().toLong(), message = "endOffset is wrong"
                            )
                        } else {
                            if (LuceneTestCase.VERBOSE) {
                                println("      skip check offsets")
                            }
                        }
                    } else if (fieldInfo.indexOptions < IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS) {
                        if (LuceneTestCase.VERBOSE) {
                            println("      now check offsets are -1")
                        }
                        assertEquals(
                            -1,
                            postingsEnum.startOffset().toLong(), "startOffset isn't -1"
                        )
                        assertEquals(
                            -1,
                            postingsEnum.endOffset().toLong(), "endOffset isn't -1"
                        )
                    }
                }
            }
        }

        if (options.contains(Option.SKIPPING)) {
            val docToNorm: (Int) -> Long /*java.util.function.IntToLongFunction*/
            if (fieldInfo.hasNorms()) {
                docToNorm = DOC_TO_NORM
            } else {
                docToNorm = /*java.util.function.IntToLongFunction*/ { `_`: Int -> 1L }
            }

            // First check impacts and block uptos
            var max = -1
            var impactsCopy: MutableList<Impact>? = null
            var flags = PostingsEnum.FREQS.toInt()
            if (doCheckPositions) {
                flags = flags or PostingsEnum.POSITIONS.toInt()
                if (doCheckOffsets) {
                    flags = flags or PostingsEnum.OFFSETS.toInt()
                }
                if (doCheckPayloads) {
                    flags = flags or PostingsEnum.PAYLOADS.toInt()
                }
            }

            var impactsEnum: ImpactsEnum = termsEnum.impacts(flags)
            var postings: PostingsEnum? = termsEnum.postings(null, flags)
            var doc: Int = impactsEnum.nextDoc()
            while (true) {
                assertEquals(postings!!.nextDoc().toLong(), doc.toLong())
                if (doc == DocIdSetIterator.NO_MORE_DOCS) {
                    break
                }
                var freq: Int = postings.freq()
                assertEquals(
                    freq.toLong(),
                    impactsEnum.freq().toLong(), "freq is wrong"
                )
                for (i in 0..<freq) {
                    val pos: Int = postings.nextPosition()
                    assertEquals(
                        pos.toLong(),
                        impactsEnum.nextPosition().toLong(), message = "position is wrong"
                    )
                    if (doCheckOffsets) {
                        assertEquals(
                            postings.startOffset().toLong(),
                            impactsEnum.startOffset().toLong(), message = "startOffset is wrong"
                        )
                        assertEquals(
                            postings.endOffset().toLong(),
                            impactsEnum.endOffset().toLong(), message = "endOffset is wrong"
                        )
                    }
                    if (doCheckPayloads) {
                        assertEquals(
                            postings.payload,
                            impactsEnum.payload, message = "payload is wrong"
                        )
                    }
                }
                if (doc > max) {
                    impactsEnum.advanceShallow(doc)
                    val impacts: Impacts = impactsEnum.impacts
                    INDEX_PACKAGE_ACCESS.checkImpacts(impacts, doc)
                    impactsCopy =
                        impacts.getImpacts(0)
                            .map { i: Impact ->
                                Impact(
                                    i.freq,
                                    i.norm
                                )
                            }.toMutableList()
                }
                freq = impactsEnum.freq()
                val norm: Long = docToNorm(doc)
                /*var idx: Int =
                    java.util.Collections.binarySearch<Impact>(
                        impactsCopy,
                        Impact(freq, norm),
                        java.util.Comparator.comparing<Impact, Int>({ i: Impact -> i.freq })
                    )*/
                var idx = impactsCopy!!.binarySearch(Impact(freq, norm), compareBy { it.freq })
                if (idx < 0) {
                    (-1 - idx).also { idx = it }
                }

                if (idx < 0) {
                    idx = -1 - idx
                }
                assertTrue(
                    ("Got "
                            + Impact(freq, norm)
                            + " in postings, but no impact triggers equal or better scores in "
                            + impactsCopy),
                    idx <= impactsCopy.size && impactsCopy[idx].norm <= norm
                )
                impactsEnum.nextDoc().also { doc = it }
            }

            // Now check advancing
            impactsEnum = termsEnum.impacts(flags)
            postings = termsEnum.postings(postings, flags)

            max = -1
            while (true) {
                var doc: Int = impactsEnum.docID()
                val advance: Boolean
                val target: Int
                if (random.nextBoolean()) {
                    advance = false
                    target = doc + 1
                } else {
                    advance = true
                    val delta: Int = min(
                        1 + random.nextInt(512),
                        DocIdSetIterator.NO_MORE_DOCS - doc
                    )
                    target = impactsEnum.docID() + delta
                }

                if (target > max && random.nextBoolean()) {
                    val delta: Int = min(
                        random.nextInt(512),
                        DocIdSetIterator.NO_MORE_DOCS - target
                    )
                    max = target + delta

                    impactsEnum.advanceShallow(target)
                    val impacts: Impacts = impactsEnum.impacts
                    INDEX_PACKAGE_ACCESS.checkImpacts(impacts, target)
                    impactsCopy = mutableListOf(
                        Impact(
                            Int.MAX_VALUE, 1L
                        )
                    )
                    for (level in 0..<impacts.numLevels()) {
                        if (impacts.getDocIdUpTo(level) >= max) {
                            impactsCopy =
                                impacts.getImpacts(level)
                                    .map { i: Impact ->
                                        Impact(
                                            i.freq,
                                            i.norm
                                        )
                                    }.toMutableList()
                            break
                        }
                    }
                }

                if (advance) {
                    doc = impactsEnum.advance(target)
                } else {
                    doc = impactsEnum.nextDoc()
                }

                assertEquals(postings!!.advance(target).toLong(), doc.toLong())
                if (doc == DocIdSetIterator.NO_MORE_DOCS) {
                    break
                }
                var freq: Int = postings.freq()
                assertEquals(
                    freq.toLong(),
                    impactsEnum.freq().toLong(), message = "freq is wrong"
                )
                for (i in 0..<postings.freq()) {
                    val pos: Int = postings.nextPosition()
                    assertEquals(
                        pos.toLong(),
                        impactsEnum.nextPosition().toLong(), message = "position is wrong"
                    )
                    if (doCheckOffsets) {
                        assertEquals(
                            postings.startOffset().toLong(),
                            impactsEnum.startOffset().toLong(), message = "startOffset is wrong"
                        )
                        assertEquals(
                            postings.endOffset().toLong(),
                            impactsEnum.endOffset().toLong(), message = "endOffset is wrong"
                        )
                    }
                    if (doCheckPayloads) {
                        assertEquals(
                            postings.payload,
                            impactsEnum.payload, message = "payload is wrong"
                        )
                    }
                }

                if (doc > max) {
                    val delta: Int = min(
                        1 + random.nextInt(512),
                        DocIdSetIterator.NO_MORE_DOCS - doc
                    )
                    max = doc + delta
                    val impacts: Impacts = impactsEnum.impacts
                    INDEX_PACKAGE_ACCESS.checkImpacts(impacts, doc)
                    impactsCopy = mutableListOf(
                        Impact(
                            Int.MAX_VALUE, 1L
                        )
                    )
                    for (level in 0..<impacts.numLevels()) {
                        if (impacts.getDocIdUpTo(level) >= max) {
                            impactsCopy =
                                impacts.getImpacts(level)
                                    .map { i: Impact ->
                                        Impact(
                                            i.freq,
                                            i.norm
                                        )
                                    }.toMutableList()
                            break
                        }
                    }
                }

                freq = impactsEnum.freq()
                val norm: Long = docToNorm(doc)
                /*var idx: Int =
                    java.util.Collections.binarySearch<Impact>(
                        impactsCopy,
                        Impact(freq, norm),
                        java.util.Comparator.comparing<Impact, Int>({ i: Impact -> i.freq })
                    )*/
                var idx = impactsCopy!!.binarySearch(Impact(freq, norm), compareBy { it.freq })
                if (idx < 0) idx = -idx - 1

                if (idx < 0) {
                    idx = -1 - idx
                }
                assertTrue(
                    ("Got "
                            + Impact(freq, norm)
                            + " in postings, but no impact triggers equal or better scores in "
                            + impactsCopy),
                    idx <= impactsCopy.size && impactsCopy[idx].norm <= norm
                )
            }
        }

        if (options.contains(Option.INTO_BIT_SET)) {
            var flags = PostingsEnum.FREQS.toInt()
            if (doCheckPositions) {
                flags = flags or PostingsEnum.POSITIONS.toInt()
                if (doCheckOffsets) {
                    flags = flags or PostingsEnum.OFFSETS.toInt()
                }
                if (doCheckPayloads) {
                    flags = flags or PostingsEnum.PAYLOADS.toInt()
                }
            }
            var pe1: PostingsEnum? = termsEnum.postings(null, flags)
            if (random.nextBoolean()) {
                pe1!!.advance(maxDoc / 2)
                pe1 = termsEnum.postings(pe1, flags)
            }
            val pe2: PostingsEnum? = termsEnum.postings(null, flags)
            val set1 = FixedBitSet(1024)
            val set2 = FixedBitSet(1024)

            while (true) {
                pe1!!.nextDoc()
                pe2!!.nextDoc()

                val offset: Int =
                    TestUtil.nextInt(
                        random,
                        max(0, pe1.docID() - set1.length()),
                        pe1.docID()
                    )
                val upTo: Int = offset + random.nextInt(set1.length())
                pe1.intoBitSet(upTo, set1, offset)
                var d: Int = pe2.docID()
                while (d < upTo) {
                    set2.set(d - offset)
                    d = pe2.nextDoc()
                }

                assertEquals(set1, set2)
                assertEquals(pe1.docID().toLong(), pe2.docID().toLong())
                if (pe1.docID() == DocIdSetIterator.NO_MORE_DOCS) {
                    break
                }
                set1.clear()
                set2.clear()
            }
        }
    }

    /*private class TestThread(
        random: Random,
        postingsTester: RandomPostingsTester,
        fieldsSource: Fields,
        options: EnumSet<Option>,
        maxTestOptions: IndexOptions,
        maxIndexOptions: IndexOptions,
        alwaysTestMax: Boolean
    ) : java.lang.Thread() {
        private var fieldsSource: Fields?
        private val options: EnumSet<Option>
        private val maxIndexOptions: IndexOptions
        private val maxTestOptions: IndexOptions
        private val alwaysTestMax: Boolean
        private var postingsTester: RandomPostingsTester?
        private val random: Random

        init {
            this.random = random
            this.fieldsSource = fieldsSource
            this.options = options
            this.maxTestOptions = maxTestOptions
            this.maxIndexOptions = maxIndexOptions
            this.alwaysTestMax = alwaysTestMax
            this.postingsTester = postingsTester
        }

        override fun run() {
            try {
                try {
                    postingsTester!!.testTermsOneThread(
                        random,
                        fieldsSource,
                        options,
                        maxTestOptions,
                        maxIndexOptions,
                        alwaysTestMax
                    )
                } catch (t: Throwable) {
                    throw RuntimeException(t)
                }
            } finally {
                fieldsSource = null
                postingsTester = null
            }
        }
    }*/

    private class TestThread(
        random: Random,
        postingsTester: RandomPostingsTester,
        fieldsSource: Fields,
        options: EnumSet<Option>,
        maxTestOptions: IndexOptions,
        maxIndexOptions: IndexOptions,
        alwaysTestMax: Boolean
    ) {
        private var fieldsSource: Fields? = fieldsSource
        private val options: EnumSet<Option> = options
        private val maxIndexOptions: IndexOptions = maxIndexOptions
        private val maxTestOptions: IndexOptions = maxTestOptions
        private val alwaysTestMax: Boolean = alwaysTestMax
        private var postingsTester: RandomPostingsTester? = postingsTester
        private val random: Random = random

        /**
         * Launches the worker in the given [scope] and returns the [Job].
         * Caller can join/cancel the returned Job as needed.
         */
        fun start(scope: kotlinx.coroutines.CoroutineScope): Job {
            return scope.launch {
                try {
                    try {
                        postingsTester!!.testTermsOneThread(
                            random,
                            fieldsSource!!,
                            options,
                            maxTestOptions,
                            maxIndexOptions,
                            alwaysTestMax
                        )
                    } catch (t: Throwable) {
                        throw RuntimeException(t)
                    }
                } finally {
                    fieldsSource = null
                    postingsTester = null
                }
            }
        }

        /**
         * Suspend variant that runs the same logic without launching a Job.
         */
        suspend fun runSuspend() {
            try {
                postingsTester!!.testTermsOneThread(
                    random,
                    fieldsSource!!,
                    options,
                    maxTestOptions,
                    maxIndexOptions,
                    alwaysTestMax
                )
            } finally {
                fieldsSource = null
                postingsTester = null
            }
        }
    }

    fun testTerms(
        fieldsSource: Fields,
        options: EnumSet<Option>,
        maxTestOptions: IndexOptions,
        maxIndexOptions: IndexOptions,
        alwaysTestMax: Boolean
    ) {
        if (options.contains(Option.THREADS)) {
            val numThreads = if (LuceneTestCase.TEST_NIGHTLY) TestUtil.nextInt(random, 2, 5) else 2
            runBlocking {
                val jobs = ArrayList<Job>(numThreads)
                for (threadUpto in 0..<numThreads) {
                    val r = Random(random.nextLong())
                    jobs += launch {
                        try {
                            testTermsOneThread(
                                r,
                                fieldsSource,
                                options,
                                maxTestOptions,
                                maxIndexOptions,
                                alwaysTestMax
                            )
                        } catch (t: Throwable) {
                            throw RuntimeException(t)
                        }
                    }
                }
                jobs.joinAll()
            }
        } else {
            testTermsOneThread(
                random, fieldsSource, options, maxTestOptions, maxIndexOptions, alwaysTestMax
            )
        }
    }

    @Throws(IOException::class)
    private fun testTermsOneThread(
        random: Random,
        fieldsSource: Fields,
        options: EnumSet<Option>,
        maxTestOptions: IndexOptions,
        maxIndexOptions: IndexOptions,
        alwaysTestMax: Boolean
    ) {
        val threadState = ThreadState()

        // Test random terms/fields:
        val termStates: MutableList<TermState> = mutableListOf()
        val termStateTerms: MutableList<FieldAndTerm> = mutableListOf()

        var supportsOrds = true

        //java.util.Collections.shuffle(allTerms, random)
        allTerms.shuffle(random)

        var upto = 0
        while (upto < allTerms.size) {
            var useTermState = termStates.isNotEmpty() && random.nextInt(5) == 1
            val useTermOrd = supportsOrds && useTermState == false && random.nextInt(5) == 1

            val fieldAndTerm: FieldAndTerm
            val termsEnum: TermsEnum

            var termState: TermState? = null

            if (!useTermState) {
                // Seek by random field+term:
                fieldAndTerm = allTerms[upto++]
                if (LuceneTestCase.VERBOSE) {
                    if (useTermOrd) {
                        println(
                            ("\nTEST: seek to term="
                                    + fieldAndTerm.field
                                    + ":"
                                    + fieldAndTerm.term.utf8ToString()
                                    + " using ord="
                                    + fieldAndTerm.ord)
                        )
                    } else {
                        println(
                            ("\nTEST: seek to term="
                                    + fieldAndTerm.field
                                    + ":"
                                    + fieldAndTerm.term.utf8ToString())
                        )
                    }
                }
            } else {
                // Seek by previous saved TermState
                val idx: Int = random.nextInt(termStates.size)
                fieldAndTerm = termStateTerms[idx]
                if (LuceneTestCase.VERBOSE) {
                    println(
                        ("\nTEST: seek using TermState to term="
                                + fieldAndTerm.field
                                + ":"
                                + fieldAndTerm.term.utf8ToString())
                    )
                }
                termState = termStates[idx]
            }

            val terms: Terms? = fieldsSource.terms(fieldAndTerm.field)
            assertNotNull(terms)
            termsEnum = terms.iterator()

            if (!useTermState) {
                if (useTermOrd) {
                    // Try seek by ord sometimes:
                    try {
                        termsEnum.seekExact(fieldAndTerm.ord)
                    } catch (uoe: UnsupportedOperationException) {
                        supportsOrds = false
                        assertTrue(termsEnum.seekExact(fieldAndTerm.term))
                    }
                } else {
                    assertTrue(termsEnum.seekExact(fieldAndTerm.term))
                }
            } else {
                termsEnum.seekExact(fieldAndTerm.term, termState!!)
            }

            // check we really seeked to the right place
            assertEquals(fieldAndTerm.term, termsEnum.term())

            var termOrd: Long
            if (supportsOrds) {
                try {
                    termOrd = termsEnum.ord()
                } catch (uoe: UnsupportedOperationException) {
                    supportsOrds = false
                    termOrd = -1
                }
            } else {
                termOrd = -1
            }

            if (termOrd != -1L) {
                // PostingsFormat supports ords
                assertEquals(fieldAndTerm.ord, termsEnum.ord())
            }

            var savedTermState = false

            if (options.contains(Option.TERM_STATE) && !useTermState && random.nextInt(5) == 1) {
                // Save away this TermState:
                termStates.add(termsEnum.termState())
                termStateTerms.add(fieldAndTerm)
                savedTermState = true
            }

            verifyEnum(
                random,
                threadState,
                fieldAndTerm.field,
                fieldAndTerm.term,
                termsEnum,
                maxTestOptions,
                maxIndexOptions,
                options,
                alwaysTestMax
            )

            // Sometimes save term state after pulling the enum:
            if (options.contains(Option.TERM_STATE)
                && !useTermState && !savedTermState && random.nextInt(5) == 1
            ) {
                // Save away this TermState:
                termStates.add(termsEnum.termState())
                termStateTerms.add(fieldAndTerm)
                useTermState = true
            }

            // 10% of the time make sure you can pull another enum
            // from the same term:
            if (alwaysTestMax || random.nextInt(10) == 7) {
                // Try same term again
                if (LuceneTestCase.VERBOSE) {
                    println("TEST: try enum again on same term")
                }

                verifyEnum(
                    random,
                    threadState,
                    fieldAndTerm.field,
                    fieldAndTerm.term,
                    termsEnum,
                    maxTestOptions,
                    maxIndexOptions,
                    options,
                    alwaysTestMax
                )
            }
        }

        // Test Terms.intersect:
        for (field in fields.keys) {
            while (true) {
                var a: Automaton = AutomatonTestUtil.randomAutomaton(random)
                a = Operations.determinize(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
                val ca = CompiledAutomaton(a, false, true, false)
                if (ca.type != CompiledAutomaton.AUTOMATON_TYPE.NORMAL) {
                    // Keep retrying until we get an A that will really "use" the PF's intersect code:
                    continue
                }

                // System.out.println("A:\n" + a.toDot());
                var startTerm: BytesRef? = null
                if (random.nextBoolean()) {
                    val ras: AutomatonTestUtil.RandomAcceptedStrings =
                        AutomatonTestUtil.RandomAcceptedStrings(
                            a
                        )
                    for (iter in 0..9) { // TODO reduce from 99 to 9 for dev speed
                        val codePoints: IntArray = ras.getRandomAcceptedString(random)
                        if (codePoints.isEmpty()) {
                            continue
                        }
                        startTerm = BytesRef(
                            UnicodeUtil.newString(
                                codePoints,
                                0,
                                codePoints.size
                            )
                        )
                        break
                    }
                    // Don't allow empty string startTerm:
                    if (startTerm == null) {
                        continue
                    }
                }
                val intersected: TermsEnum =
                    fieldsSource.terms(field)!!.intersect(ca, startTerm)

                val intersectedTerms: MutableSet<BytesRef> = mutableSetOf()
                var term: BytesRef?
                while ((intersected.next().also { term = it }) != null) {
                    if (startTerm != null) {
                        // NOTE: not <=
                        assertTrue(startTerm < term!!)
                    }
                    intersectedTerms.add(BytesRef.deepCopyOf(term!!))
                    verifyEnum(
                        random,
                        threadState,
                        field,
                        term,
                        intersected,
                        maxTestOptions,
                        maxIndexOptions,
                        options,
                        alwaysTestMax
                    )
                }

                if (ca.runAutomaton == null) {
                    assertTrue(intersectedTerms.isEmpty())
                } else {
                    for (term2 in fields[field]!!.keys) {
                        val expected: Boolean
                        if (startTerm != null && startTerm >= term2) {
                            expected = false
                        } else {
                            expected =
                                ca.runAutomaton!!.run(term2.bytes, term2.offset, term2.length)
                        }
                        assertEquals(
                            expected,
                            intersectedTerms.contains(term2), message = "term=$term2"
                        )
                    }
                }

                break
            }
        }
    }

    @Throws(Exception::class)
    fun testFields(fields: Fields) {
        val iterator: MutableIterator<String> = fields.iterator()
        while (iterator.hasNext()) {
            iterator.next()
            try {
                iterator.remove()
                throw AssertionError("Fields.iterator() allows for removal")
            } catch (expected: UnsupportedOperationException) {
                // expected;
            }
        }
        assertFalse(iterator.hasNext())
        LuceneTestCase.expectThrows(
            NoSuchElementException::class
        ) { iterator.next() }
    }

    /**
     * Indexes all fields/terms at the specified IndexOptions, and fully tests at that IndexOptions.
     */
    @Throws(Exception::class)
    fun testFull(
        codec: Codec,
        path: Path,
        options: IndexOptions,
        withPayloads: Boolean
    ) {
        val dir: Directory =
            LuceneTestCase.newFSDirectory(path)

        // TODO test thread safety of buildIndex too
        val fieldsProducer: FieldsProducer =
            buildIndex(codec, dir, options, withPayloads, true)

        testFields(fieldsProducer)

        val allOptions: Array<IndexOptions> =
            IndexOptions.entries.toTypedArray()
        /*val maxIndexOption: Int =
            java.util.Arrays.asList<IndexOptions>(*allOptions)
                .indexOf(options)*/
        val maxIndexOption: Int = allOptions.indexOf(options)

        val exhaustiveChecks = shouldRunExhaustivePostingsFormatChecks()
        val optionIndexes = if (exhaustiveChecks) {
            0..maxIndexOption
        } else {
            maxIndexOption..maxIndexOption
        }
        for (i in optionIndexes) {
            val testOptions = EnumSet.allOf<Option>(Option::class)
            if (!exhaustiveChecks) {
                // Threaded verification is disproportionately expensive on Kotlin/Native.
                testOptions.remove(Option.THREADS)
            }
            testTerms(
                fieldsProducer,
                testOptions,
                allOptions[i],
                options,
                true
            )
            if (withPayloads && exhaustiveChecks) {
                // If we indexed w/ payloads, also test enums w/o accessing payloads:
                testTerms(
                    fieldsProducer,
                    EnumSet.complementOf(
                        EnumSet.of<Option>(
                            Option.PAYLOADS
                        )
                    ),
                    allOptions[i],
                    options,
                    true
                )
            }
        }

        fieldsProducer.close()
        dir.close()
    }

    companion object {
        private val INDEX_PACKAGE_ACCESS: IndexPackageAccess =
            TestSecrets.getIndexPackageAccess()
        private val DOC_TO_NORM: (Int) -> Long /*java.util.function.IntToLongFunction*/ =
            /*java.util.function.IntToLongFunction*/ { doc: Int -> 1L + (doc and 0x0f) }

        fun getSeedPostings(
            term: String,
            seed: Long,
            options: IndexOptions,
            allowPayloads: Boolean
        ): SeedPostings {
            val minDocFreq: Int
            val maxDocFreq: Int
            if (term.startsWith("big_")) {
                minDocFreq = LuceneTestCase.RANDOM_MULTIPLIER * 50 // TODO reduced from 50000 to 50 for dev speed
                maxDocFreq = LuceneTestCase.RANDOM_MULTIPLIER * 70 // TODO reduced from 70000 to 70 for dev speed
            } else if (term.startsWith("medium_")) {
                minDocFreq = LuceneTestCase.RANDOM_MULTIPLIER * 10 // TODO reduced from 3000 to 10 for dev speed
                maxDocFreq = LuceneTestCase.RANDOM_MULTIPLIER * 20 // TODO reduced from 6000 to 20 for dev speed
            } else if (term.startsWith("low_")) {
                minDocFreq = LuceneTestCase.RANDOM_MULTIPLIER
                maxDocFreq = LuceneTestCase.RANDOM_MULTIPLIER * 4  // TODO reducecd from 40 to 4 for dev speed
            } else {
                minDocFreq = 1
                maxDocFreq = 3
            }

            return SeedPostings(seed, minDocFreq, maxDocFreq, options, allowPayloads)
        }
    }
}
