package org.gnit.lucenekmp.tests.index

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.DocValuesSkipIndexType
import org.gnit.lucenekmp.index.DocValuesSkipper
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.FilterLeafReader
import org.gnit.lucenekmp.index.Impact
import org.gnit.lucenekmp.index.Impacts
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.PointValues.IntersectVisitor
import org.gnit.lucenekmp.index.PointValues.Relation
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.index.StoredFieldVisitor
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.index.TermVectors
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.internal.tests.IndexPackageAccess
import org.gnit.lucenekmp.internal.tests.TestSecrets
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.CloseableThreadLocal
import org.gnit.lucenekmp.util.IOBooleanSupplier
import org.gnit.lucenekmp.util.VirtualMethod
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import kotlin.math.max


/** A [FilterLeafReader] that can be used to apply additional checks for tests.  */
class AssertingLeafReader(`in`: LeafReader) :
    FilterLeafReader(`in`) {
    @Throws(IOException::class)
    override fun terms(field: String?): Terms? {
        val terms: Terms? = super.terms(field)
        return if (terms == null) null else AssertingTerms(terms)
    }

    @Throws(IOException::class)
    override fun termVectors(): TermVectors {
        return AssertingTermVectors(super.termVectors())
    }

    @Throws(IOException::class)
    override fun storedFields(): StoredFields {
        return AssertingStoredFields(super.storedFields())
    }

    /** Wraps a StoredFields but with additional asserts  */
    class AssertingStoredFields(`in`: StoredFields) :
        StoredFields() {
        private val `in`: StoredFields
        private val creationThread: Job? = currentJob()

        init {
            this.`in` = `in`
        }

        @Throws(IOException::class)
        override fun prefetch(docID: Int) {
            assertThread("StoredFields", creationThread)
            `in`.prefetch(docID)
        }

        @Throws(IOException::class)
        override fun document(docID: Int, visitor: StoredFieldVisitor) {
            assertThread("StoredFields", creationThread)
            `in`.document(docID, visitor)
        }
    }

    /** Wraps a TermVectors but with additional asserts  */
    class AssertingTermVectors(`in`: TermVectors) :
        TermVectors() {
        private val `in`: TermVectors
        private val creationThread: Job? = currentJob()

        init {
            this.`in` = `in`
        }

        @Throws(IOException::class)
        override fun prefetch(docID: Int) {
            assertThread("TermVectors", creationThread)
            `in`.prefetch(docID)
        }

        @Throws(IOException::class)
        override fun get(doc: Int): Fields? {
            assertThread("TermVectors", creationThread)
            val fields: Fields? = `in`.get(doc)
            return if (fields == null) null else AssertingFields(fields)
        }
    }

    /** Wraps a Fields but with additional asserts  */
    class AssertingFields(`in`: Fields) :
        FilterLeafReader.FilterFields(`in`) {
        private val creationThread: Job? = currentJob()

        override fun iterator(): MutableIterator<String> {
            assertThread("Fields", creationThread)
            val iterator: MutableIterator<String> = checkNotNull(super.iterator())
            return iterator
        }

        @Throws(IOException::class)
        override fun terms(field: String?): Terms? {
            assertThread("Fields", creationThread)
            val terms: Terms? = super.terms(field)
            return if (terms == null) null else AssertingTerms(terms)
        }
    }

    /** Wraps a Terms but with additional asserts  */
    class AssertingTerms(`in`: Terms) :
        FilterLeafReader.FilterTerms(`in`) {
        private val creationThread: Job? = currentJob()

        @Throws(IOException::class)
        override fun intersect(
            automaton: CompiledAutomaton,
            bytes: BytesRef?
        ): TermsEnum {
            assertThread("Terms", creationThread)
            val termsEnum: TermsEnum =
                checkNotNull(`in`.intersect(automaton, bytes))
            assert(bytes == null || bytes.isValid())
            return AssertingTermsEnum(termsEnum, hasFreqs())
        }

        override val min: BytesRef?
            get() {
                assertThread("Terms", creationThread)
                val v: BytesRef? = `in`.min
                assert(v == null || v.isValid())
                return v
            }

        override val max: BytesRef?
            get() {
                assertThread("Terms", creationThread)
                val v: BytesRef? = `in`.max
                assert(v == null || v.isValid())
                return v
            }

        override val docCount: Int
            get() {
                assertThread("Terms", creationThread)
                val docCount: Int = `in`.docCount
                assert(docCount > 0)
                return docCount
            }

        override val sumDocFreq: Long
            get() {
                assertThread("Terms", creationThread)
                val sumDf: Long = `in`.sumDocFreq
                assert(sumDf >= this.docCount)
                return sumDf
            }

        override val sumTotalTermFreq: Long
            get() {
                assertThread("Terms", creationThread)
                val sumTtf: Long = `in`.sumTotalTermFreq
                if (hasFreqs() == false) {
                    assert(sumTtf == `in`.sumDocFreq)
                }
                assert(sumTtf >= this.sumDocFreq)
                return sumTtf
            }

        @Throws(IOException::class)
        override fun iterator(): TermsEnum {
            assertThread("Terms", creationThread)
            val termsEnum: TermsEnum = checkNotNull(super.iterator())
            return AssertingTermsEnum(termsEnum, hasFreqs())
        }

        override fun toString(): String {
            return "AssertingTerms(" + `in` + ")"
        }
    }

    init {
        // check some basic reader sanity
        assert(`in`.maxDoc() >= 0)
        assert(`in`.numDocs() <= `in`.maxDoc())
        assert(`in`.numDeletedDocs() + `in`.numDocs() == `in`.maxDoc())
        assert(!`in`.hasDeletions() || `in`.numDeletedDocs() > 0 && `in`.numDocs() < `in`.maxDoc())

        val coreCacheHelper: CacheHelper? = `in`.coreCacheHelper
        if (coreCacheHelper != null) {
            runBlocking{
                coreCacheHelper.addClosedListener(
                    IndexReader.ClosedListener { cacheKey: CacheKey ->
                        val expectedKey: Any = coreCacheHelper.key
                        assert(
                            expectedKey === cacheKey
                        ) {
                            ("Core closed listener called on a different key "
                                    + expectedKey
                                    + " <> "
                                    + cacheKey)
                        }
                    })
            }
        }

        val readerCacheHelper: CacheHelper? =
            `in`.readerCacheHelper
        if (readerCacheHelper != null) {
            runBlocking {  readerCacheHelper.addClosedListener { cacheKey: CacheKey ->
                val expectedKey: Any = readerCacheHelper.key
                assert(
                    expectedKey === cacheKey
                ) {
                    ("Core closed listener called on a different key "
                            + expectedKey
                            + " <> "
                            + cacheKey)
                }
            }}
        }
    }

    internal class AssertingTermsEnum(
        `in`: TermsEnum,
        private val hasFreqs: Boolean
    ) : FilterLeafReader.FilterTermsEnum(`in`) {
        private val creationThread: Job? = currentJob()

        private enum class State {
            INITIAL,
            POSITIONED,
            UNPOSITIONED,
            TWO_PHASE_SEEKING
        }

        private var state = State.INITIAL
        private val delegateOverridesSeekExact: Boolean

        init {
            delegateOverridesSeekExact = SEEK_EXACT.isOverriddenAsOf(`in`::class)
        }

        @Throws(IOException::class)
        override fun postings(
            reuse: PostingsEnum?,
            flags: Int
        ): PostingsEnum {
            assertThread("Terms enums", creationThread)
            assert(state == State.POSITIONED) { "docs(...) called on unpositioned TermsEnum" }

            // reuse if the codec reused
            val actualReuse: PostingsEnum?
            if (reuse is AssertingPostingsEnum) {
                actualReuse = reuse.unwrap()
            } else {
                actualReuse = null
            }
            val docs: PostingsEnum =
                checkNotNull(super.postings(actualReuse, flags))
            if (docs === actualReuse) {
                // codec reused, reset asserting state
                (reuse as AssertingPostingsEnum).reset()
                return reuse
            } else {
                return AssertingPostingsEnum(docs)
            }
        }

        @Throws(IOException::class)
        override fun impacts(flags: Int): ImpactsEnum {
            assertThread("Terms enums", creationThread)
            assert(state == State.POSITIONED) { "docs(...) called on unpositioned TermsEnum" }
            assert((flags and PostingsEnum.FREQS.toInt()) != 0) { "Freqs should be requested on impacts" }

            return AssertingImpactsEnum(super.impacts(flags))
        }

        // TODO: we should separately track if we are 'at the end'
        // someone should not call next() after it returns null!!!!
        @Throws(IOException::class)
        override fun next(): BytesRef? {
            assertThread("Terms enums", creationThread)
            assert(
                state == State.INITIAL || state == State.POSITIONED
            ) { "next() called on unpositioned TermsEnum" }
            val result: BytesRef? = super.next()
            if (result == null) {
                state = State.UNPOSITIONED
            } else {
                assert(result.isValid())
                state = State.POSITIONED
            }
            return result
        }

        @Throws(IOException::class)
        override fun ord(): Long {
            assertThread("Terms enums", creationThread)
            assert(state == State.POSITIONED) { "ord() called on unpositioned TermsEnum" }
            return super.ord()
        }

        @Throws(IOException::class)
        override fun docFreq(): Int {
            assertThread("Terms enums", creationThread)
            assert(state == State.POSITIONED) { "docFreq() called on unpositioned TermsEnum" }
            val df: Int = super.docFreq()
            assert(df > 0)
            return df
        }

        @Throws(IOException::class)
        override fun totalTermFreq(): Long {
            assertThread("Terms enums", creationThread)
            assert(state == State.POSITIONED) { "totalTermFreq() called on unpositioned TermsEnum" }
            val ttf: Long = super.totalTermFreq()
            if (hasFreqs) {
                assert(ttf >= docFreq())
            } else {
                assert(ttf == docFreq().toLong())
            }
            return ttf
        }

        @Throws(IOException::class)
        override fun term(): BytesRef? {
            assertThread("Terms enums", creationThread)
            assert(state == State.POSITIONED) { "term() called on unpositioned TermsEnum" }
            val ret: BytesRef? = super.term()
            assert(ret == null || ret.isValid())
            return ret
        }

        @Throws(IOException::class)
        override fun seekExact(ord: Long) {
            assertThread("Terms enums", creationThread)
            assert(state != State.TWO_PHASE_SEEKING) { "Unfinished two-phase seeking" }
            super.seekExact(ord)
            state = State.POSITIONED
        }

        @Throws(IOException::class)
        override fun seekCeil(term: BytesRef): TermsEnum.SeekStatus {
            assertThread("Terms enums", creationThread)
            assert(state != State.TWO_PHASE_SEEKING) { "Unfinished two-phase seeking" }
            assert(term.isValid())
            val result: TermsEnum.SeekStatus = super.seekCeil(term)
            if (result == TermsEnum.SeekStatus.END) {
                state = State.UNPOSITIONED
            } else {
                state = State.POSITIONED
            }
            return result
        }

        @Throws(IOException::class)
        override fun seekExact(text: BytesRef): Boolean {
            assertThread("Terms enums", creationThread)
            assert(state != State.TWO_PHASE_SEEKING) { "Unfinished two-phase seeking" }
            assert(text.isValid())
            val result: Boolean
            if (delegateOverridesSeekExact) {
                result = `in`.seekExact(text)
            } else {
                result = super.seekExact(text)
            }
            if (result) {
                state = State.POSITIONED
            } else {
                state = State.UNPOSITIONED
            }
            return result
        }

        @Throws(IOException::class)
        override fun prepareSeekExact(text: BytesRef): IOBooleanSupplier? {
            assertThread("Terms enums", creationThread)
            assert(state != State.TWO_PHASE_SEEKING) { "Unfinished two-phase seeking" }
            assert(text.isValid())
            val `in`: IOBooleanSupplier? = this.`in`.prepareSeekExact(text)
            if (`in` == null) {
                return null
            }
            state = State.TWO_PHASE_SEEKING
            return IOBooleanSupplier {
                val exists: Boolean = `in`.get()
                if (exists) {
                    state = State.POSITIONED
                } else {
                    state = State.UNPOSITIONED
                }
                exists
            }
        }

        @Throws(IOException::class)
        override fun termState(): TermState {
            assertThread("Terms enums", creationThread)
            assert(state == State.POSITIONED) { "termState() called on unpositioned TermsEnum" }
            return `in`.termState()
        }

        @Throws(IOException::class)
        override fun seekExact(
            term: BytesRef,
            state: TermState
        ) {
            assertThread("Terms enums", creationThread)
            assert(this.state != State.TWO_PHASE_SEEKING) { "Unfinished two-phase seeking" }
            assert(term.isValid())
            `in`.seekExact(term, state)
            this.state = State.POSITIONED
        }

        override fun toString(): String {
            return "AssertingTermsEnum(" + `in` + ")"
        }

        fun reset() {
            state = State.INITIAL
        }
    }

    internal enum class DocsEnumState {
        START,
        ITERATING,
        FINISHED
    }

    /** Wraps a docsenum with additional checks  */
    class AssertingPostingsEnum(`in`: PostingsEnum) :
        FilterLeafReader.FilterPostingsEnum(`in`) {
        private val creationThread: Job? = currentJob()
        private var state = DocsEnumState.START
        var positionCount: Int = 0
        var positionMax: Int = 0
        private var doc: Int

        init {
            this.doc = `in`.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            assertThread("Docs enums", creationThread)
            assert(state != DocsEnumState.FINISHED) { "nextDoc() called after NO_MORE_DOCS" }
            val nextDoc: Int = super.nextDoc()
            assert(nextDoc > doc) { "backwards nextDoc from " + doc + " to " + nextDoc + " " + `in` }
            if (nextDoc == DocIdSetIterator.NO_MORE_DOCS) {
                state = DocsEnumState.FINISHED
                positionMax = 0
            } else {
                state = DocsEnumState.ITERATING
                positionMax = super.freq()
            }
            positionCount = 0
            assert(super.docID() == nextDoc)
            return nextDoc.also { doc = it }
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            assertThread("Docs enums", creationThread)
            assert(state != DocsEnumState.FINISHED) { "advance() called after NO_MORE_DOCS" }
            assert(target > doc) { "target must be > docID(), got " + target + " <= " + doc }
            val advanced: Int = super.advance(target)
            assert(advanced >= target) { "backwards advance from: " + target + " to: " + advanced }
            if (advanced == DocIdSetIterator.NO_MORE_DOCS) {
                state = DocsEnumState.FINISHED
                positionMax = 0
            } else {
                state = DocsEnumState.ITERATING
                positionMax = super.freq()
            }
            positionCount = 0
            assert(super.docID() == advanced)
            return advanced.also { doc = it }
        }

        override fun docID(): Int {
            assertThread("Docs enums", creationThread)
            assert(
                doc == super.docID()
            ) { " invalid docID() in " + `in`::class.simpleName + " " + super.docID() + " instead of " + doc }
            return doc
        }

        @Throws(IOException::class)
        override fun freq(): Int {
            assertThread("Docs enums", creationThread)
            assert(state != DocsEnumState.START) { "freq() called before nextDoc()/advance()" }
            assert(state != DocsEnumState.FINISHED) { "freq() called after NO_MORE_DOCS" }
            val freq: Int = super.freq()
            assert(freq > 0)
            return freq
        }

        @Throws(IOException::class)
        override fun nextPosition(): Int {
            assert(state != DocsEnumState.START) { "nextPosition() called before nextDoc()/advance()" }
            assert(state != DocsEnumState.FINISHED) { "nextPosition() called after NO_MORE_DOCS" }
            assert(positionCount < positionMax) { "nextPosition() called more than freq() times!" }
            val position: Int = super.nextPosition()
            assert(position >= 0 || position == -1) { "invalid position: " + position }
            positionCount++
            return position
        }

        @Throws(IOException::class)
        override fun startOffset(): Int {
            assert(state != DocsEnumState.START) { "startOffset() called before nextDoc()/advance()" }
            assert(state != DocsEnumState.FINISHED) { "startOffset() called after NO_MORE_DOCS" }
            assert(positionCount > 0) { "startOffset() called before nextPosition()!" }
            return super.startOffset()
        }

        @Throws(IOException::class)
        override fun endOffset(): Int {
            assert(state != DocsEnumState.START) { "endOffset() called before nextDoc()/advance()" }
            assert(state != DocsEnumState.FINISHED) { "endOffset() called after NO_MORE_DOCS" }
            assert(positionCount > 0) { "endOffset() called before nextPosition()!" }
            return super.endOffset()
        }

        override val payload: BytesRef?
            get() {
                assert(state != DocsEnumState.START) { "getPayload() called before nextDoc()/advance()" }
                assert(state != DocsEnumState.FINISHED) { "getPayload() called after NO_MORE_DOCS" }
                assert(positionCount > 0) { "getPayload() called before nextPosition()!" }
                val payload: BytesRef? = super.payload
                assert(
                    payload == null || payload.length > 0
                ) { "getPayload() returned payload with invalid length!" }
                return payload
            }

        fun reset() {
            state = DocsEnumState.START
            doc = `in`.docID()
            positionMax = 0
            positionCount = positionMax
        }
    }

    /** Wraps a [ImpactsEnum] with additional checks  */
    class AssertingImpactsEnum internal constructor(impacts: ImpactsEnum) :
        ImpactsEnum() {
        private val assertingPostings: AssertingPostingsEnum
        private val `in`: ImpactsEnum
        var lastShallowTarget = -1

        init {
            `in` = impacts
            // inherit checks from AssertingPostingsEnum
            assertingPostings = AssertingPostingsEnum(impacts)
        }

        @Throws(IOException::class)
        override fun advanceShallow(target: Int) {
            assert(
                target >= lastShallowTarget
            ) {
                ("called on decreasing targets: target = "
                        + target
                        + " < last target = "
                        + lastShallowTarget)
            }
            assert(target >= docID()) { "target = " + target + " < docID = " + docID() }
            lastShallowTarget = target
            `in`.advanceShallow(target)
        }

        override val impacts: Impacts
            get() {
                assert(
                    docID() >= 0 || lastShallowTarget >= 0
                ) { "Cannot get impacts until the iterator is positioned or advanceShallow has been called" }
                val impacts: Impacts = `in`.impacts
                INDEX_PACKAGE_ACCESS.checkImpacts(impacts, max(docID(), lastShallowTarget))
                return AssertingImpacts(impacts, this)
            }

        @Throws(IOException::class)
        override fun freq(): Int {
            return assertingPostings.freq()
        }

        @Throws(IOException::class)
        override fun nextPosition(): Int {
            return assertingPostings.nextPosition()
        }

        @Throws(IOException::class)
        override fun startOffset(): Int {
            return assertingPostings.startOffset()
        }

        @Throws(IOException::class)
        override fun endOffset(): Int {
            return assertingPostings.endOffset()
        }

        override val payload: BytesRef?
            get() = assertingPostings.payload

        override fun docID(): Int {
            return assertingPostings.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            assert(
                docID() + 1 >= lastShallowTarget
            ) { "target = ${(docID() + 1)} < last shallow target = $lastShallowTarget" }
            return assertingPostings.nextDoc()
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            assert(
                target >= lastShallowTarget
            ) { "target = $target < last shallow target = $lastShallowTarget" }
            return assertingPostings.advance(target)
        }

        override fun cost(): Long {
            return assertingPostings.cost()
        }

        companion object {
            private val INDEX_PACKAGE_ACCESS: IndexPackageAccess =
                TestSecrets.getIndexPackageAccess()
        }
    }

    internal class AssertingImpacts(
        `in`: Impacts,
        impactsEnum: AssertingImpactsEnum
    ) : Impacts() {
        private val `in`: Impacts
        private val impactsEnum: AssertingImpactsEnum
        private val validFor: Int

        init {
            this.`in` = `in`
            this.impactsEnum = impactsEnum
            validFor = max(impactsEnum.docID(), impactsEnum.lastShallowTarget)
        }

        override fun numLevels(): Int {
            assert(
                validFor == max(impactsEnum.docID(), impactsEnum.lastShallowTarget)
            ) { "Cannot reuse impacts after advancing the iterator" }
            return `in`.numLevels()
        }

        override fun getDocIdUpTo(level: Int): Int {
            assert(
                validFor == max(impactsEnum.docID(), impactsEnum.lastShallowTarget)
            ) { "Cannot reuse impacts after advancing the iterator" }
            return `in`.getDocIdUpTo(level)
        }

        override fun getImpacts(level: Int): MutableList<Impact> {
            assert(
                validFor == max(impactsEnum.docID(), impactsEnum.lastShallowTarget)
            ) { "Cannot reuse impacts after advancing the iterator" }
            val impacts: MutableList<Impact> = `in`.getImpacts(level)
            assert(
                impacts.size <= 1 || impacts is RandomAccess
            ) { "impact lists longer than 1 should implement RandomAccess but saw impacts = $impacts" }
            return impacts
        }
    }

    /** Wraps a NumericDocValues but with additional asserts  */
    class AssertingNumericDocValues(`in`: NumericDocValues, maxDoc: Int) :
        NumericDocValues() {
        private val creationThread: Job? = currentJob()
        private val `in`: NumericDocValues
        private val maxDoc: Int
        private var lastDocID = -1
        private var exists = false

        init {
            this.`in` = `in`
            this.maxDoc = maxDoc
            // should start unpositioned:
            assert(`in`.docID() == -1)
        }

        override fun docID(): Int {
            assertThread("Numeric doc values", creationThread)
            return `in`.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            assertThread("Numeric doc values", creationThread)
            val docID: Int = `in`.nextDoc()
            assert(docID > lastDocID)
            assert(docID == DocIdSetIterator.NO_MORE_DOCS || docID < maxDoc)
            assert(docID == `in`.docID())
            lastDocID = docID
            exists = docID != DocIdSetIterator.NO_MORE_DOCS
            return docID
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            assertThread("Numeric doc values", creationThread)
            assert(target >= 0)
            assert(target > `in`.docID())
            val docID: Int = `in`.advance(target)
            assert(docID >= target)
            assert(docID == DocIdSetIterator.NO_MORE_DOCS || docID < maxDoc)
            lastDocID = docID
            exists = docID != DocIdSetIterator.NO_MORE_DOCS
            return docID
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            assertThread("Numeric doc values", creationThread)
            assert(target >= 0)
            assert(target >= `in`.docID())
            assert(target < maxDoc)
            exists = `in`.advanceExact(target)
            assert(`in`.docID() == target)
            lastDocID = target
            return exists
        }

        override fun cost(): Long {
            assertThread("Numeric doc values", creationThread)
            val cost: Long = `in`.cost()
            assert(cost >= 0)
            return cost
        }

        @Throws(IOException::class)
        override fun longValue(): Long {
            assertThread("Numeric doc values", creationThread)
            assert(exists)
            return `in`.longValue()
        }

        override fun toString(): String {
            return "AssertingNumericDocValues(" + `in` + ")"
        }
    }

    /** Wraps a BinaryDocValues but with additional asserts  */
    class AssertingBinaryDocValues(`in`: BinaryDocValues, maxDoc: Int) :
        BinaryDocValues() {
        private val creationThread: Job? = currentJob()
        private val `in`: BinaryDocValues
        private val maxDoc: Int
        private var lastDocID = -1
        private var exists = false

        init {
            this.`in` = `in`
            this.maxDoc = maxDoc
            // should start unpositioned:
            assert(`in`.docID() == -1)
        }

        override fun docID(): Int {
            assertThread("Binary doc values", creationThread)
            return `in`.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            assertThread("Binary doc values", creationThread)
            val docID: Int = `in`.nextDoc()
            assert(docID > lastDocID)
            assert(docID == DocIdSetIterator.NO_MORE_DOCS || docID < maxDoc)
            assert(docID == `in`.docID())
            lastDocID = docID
            exists = docID != DocIdSetIterator.NO_MORE_DOCS
            return docID
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            assertThread("Binary doc values", creationThread)
            assert(target >= 0)
            assert(target > `in`.docID())
            val docID: Int = `in`.advance(target)
            assert(docID >= target)
            assert(docID == DocIdSetIterator.NO_MORE_DOCS || docID < maxDoc)
            lastDocID = docID
            exists = docID != DocIdSetIterator.NO_MORE_DOCS
            return docID
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            assertThread("Numeric doc values", creationThread)
            assert(target >= 0)
            assert(target >= `in`.docID())
            assert(target < maxDoc)
            exists = `in`.advanceExact(target)
            assert(`in`.docID() == target)
            lastDocID = target
            return exists
        }

        override fun cost(): Long {
            assertThread("Binary doc values", creationThread)
            val cost: Long = `in`.cost()
            assert(cost >= 0)
            return cost
        }

        @Throws(IOException::class)
        override fun binaryValue(): BytesRef? {
            assertThread("Binary doc values", creationThread)
            assert(exists)
            return `in`.binaryValue()
        }

        override fun toString(): String {
            return "AssertingBinaryDocValues($`in`)"
        }
    }

    /** Wraps a SortedDocValues but with additional asserts  */
    class AssertingSortedDocValues(`in`: SortedDocValues, maxDoc: Int) :
        SortedDocValues() {
        private val creationThread: Job? = currentJob()
        private val `in`: SortedDocValues
        private val maxDoc: Int
        override val valueCount: Int
        get(): Int {
            assertThread("Sorted doc values", creationThread)
            val valueCount: Int = `in`.valueCount
            assert(
                valueCount == field // should not change
            )
            return valueCount
        }

        private var lastDocID = -1
        private var exists = false

        init {
            this.`in` = `in`
            this.maxDoc = maxDoc
            this.valueCount = `in`.valueCount
            assert(valueCount in 0..maxDoc)
        }

        override fun docID(): Int {
            assertThread("Sorted doc values", creationThread)
            return `in`.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            assertThread("Sorted doc values", creationThread)
            val docID: Int = `in`.nextDoc()
            assert(docID > lastDocID)
            assert(docID == DocIdSetIterator.NO_MORE_DOCS || docID < maxDoc)
            assert(docID == `in`.docID())
            lastDocID = docID
            exists = docID != DocIdSetIterator.NO_MORE_DOCS
            return docID
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            assertThread("Sorted doc values", creationThread)
            assert(target >= 0)
            assert(target > `in`.docID())
            val docID: Int = `in`.advance(target)
            assert(docID >= target)
            assert(docID == DocIdSetIterator.NO_MORE_DOCS || docID < maxDoc)
            lastDocID = docID
            exists = docID != DocIdSetIterator.NO_MORE_DOCS
            return docID
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            assertThread("Numeric doc values", creationThread)
            assert(target >= 0)
            assert(target >= `in`.docID())
            assert(target < maxDoc)
            exists = `in`.advanceExact(target)
            assert(`in`.docID() == target)
            lastDocID = target
            return exists
        }

        override fun cost(): Long {
            assertThread("Sorted doc values", creationThread)
            val cost: Long = `in`.cost()
            assert(cost >= 0)
            return cost
        }

        @Throws(IOException::class)
        override fun ordValue(): Int {
            assertThread("Sorted doc values", creationThread)
            assert(exists)
            val ord: Int = `in`.ordValue()
            assert(ord >= -1 && ord < valueCount)
            return ord
        }

        @Throws(IOException::class)
        override fun lookupOrd(ord: Int): BytesRef? {
            assertThread("Sorted doc values", creationThread)
            assert(ord in 0..<valueCount)
            val result: BytesRef? = `in`.lookupOrd(ord)
            assert(result!!.isValid())
            return result
        }

        /*override fun getValueCount(): Int {
            assertThread("Sorted doc values", creationThread)
            val valueCount: Int = `in`.valueCount
            assert(
                valueCount == this.valueCount // should not change
            )
            return valueCount
        }*/

        @Throws(IOException::class)
        override fun lookupTerm(key: BytesRef): Int {
            assertThread("Sorted doc values", creationThread)
            assert(key.isValid())
            val result: Int = `in`.lookupTerm(key)
            assert(result < valueCount)
            assert(key.isValid())
            return result
        }
    }

    /** Wraps a SortedNumericDocValues but with additional asserts  */
    class AssertingSortedNumericDocValues private constructor(
        `in`: SortedNumericDocValues,
        maxDoc: Int
    ) : SortedNumericDocValues() {
        private val creationThread: Job? = currentJob()
        private val `in`: SortedNumericDocValues
        private val maxDoc: Int
        private var lastDocID = -1
        private var valueUpto = 0
        private var exists = false

        init {
            this.`in` = `in`
            this.maxDoc = maxDoc
        }

        override fun docID(): Int {
            return `in`.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            assertThread("Sorted numeric doc values", creationThread)
            val docID: Int = `in`.nextDoc()
            assert(docID > lastDocID)
            assert(docID == DocIdSetIterator.NO_MORE_DOCS || docID < maxDoc)
            assert(docID == `in`.docID())
            lastDocID = docID
            valueUpto = 0
            exists = docID != DocIdSetIterator.NO_MORE_DOCS
            return docID
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            assertThread("Sorted numeric doc values", creationThread)
            assert(target >= 0)
            assert(target > `in`.docID())
            val docID: Int = `in`.advance(target)
            assert(docID == `in`.docID())
            assert(docID >= target)
            assert(docID == DocIdSetIterator.NO_MORE_DOCS || docID < maxDoc)
            lastDocID = docID
            valueUpto = 0
            exists = docID != DocIdSetIterator.NO_MORE_DOCS
            return docID
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            assertThread("Numeric doc values", creationThread)
            assert(target >= 0)
            assert(target >= `in`.docID())
            assert(target < maxDoc)
            exists = `in`.advanceExact(target)
            assert(`in`.docID() == target)
            lastDocID = target
            valueUpto = 0
            return exists
        }

        override fun cost(): Long {
            assertThread("Sorted numeric doc values", creationThread)
            val cost: Long = `in`.cost()
            assert(cost >= 0)
            return cost
        }

        @Throws(IOException::class)
        override fun nextValue(): Long {
            assertThread("Sorted numeric doc values", creationThread)
            assert(exists)
            assert(
                valueUpto < `in`.docValueCount()
            ) { "valueUpto=" + valueUpto + " in.docValueCount()=" + `in`.docValueCount() }
            valueUpto++
            return `in`.nextValue()
        }

        override fun docValueCount(): Int {
            assertThread("Sorted numeric doc values", creationThread)
            assert(exists)
            assert(`in`.docValueCount() > 0)
            return `in`.docValueCount()
        }

        companion object {
            fun create(
                `in`: SortedNumericDocValues,
                maxDoc: Int
            ): SortedNumericDocValues {
                val singleDocValues: NumericDocValues? = DocValues.unwrapSingleton(`in`)
                if (singleDocValues == null) {
                    return AssertingSortedNumericDocValues(`in`, maxDoc)
                } else {
                    val assertingDocValues: NumericDocValues =
                        AssertingNumericDocValues(singleDocValues, maxDoc)
                    return DocValues.singleton(assertingDocValues)
                }
            }
        }
    }

    /** Wraps a SortedSetDocValues but with additional asserts  */
    class AssertingSortedSetDocValues(
        `in`: SortedSetDocValues,
        maxDoc: Int
    ) : SortedSetDocValues() {
        private val creationThread: Job? = currentJob()
        private val `in`: SortedSetDocValues
        private val maxDoc: Int
        override val valueCount: Long
            get(): Long {
                assertThread("Sorted set doc values", creationThread)
                val valueCount: Long = `in`.valueCount
                assert(
                    valueCount == field // should not change
                )
                return valueCount
            }

        private var lastDocID = -1
        private var ordsRetrieved = 0
        private var exists = false

        init {
            this.`in` = `in`
            this.maxDoc = maxDoc
            this.valueCount = `in`.valueCount
            assert(valueCount >= 0)
        }

        override fun docID(): Int {
            assertThread("Sorted set doc values", creationThread)
            return `in`.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            assertThread("Sorted set doc values", creationThread)
            val docID: Int = `in`.nextDoc()
            assert(docID > lastDocID)
            assert(docID == DocIdSetIterator.NO_MORE_DOCS || docID < maxDoc)
            assert(docID == `in`.docID())
            lastDocID = docID
            exists = docID != DocIdSetIterator.NO_MORE_DOCS
            ordsRetrieved = 0
            return docID
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            assertThread("Sorted set doc values", creationThread)
            assert(target >= 0)
            assert(target > `in`.docID())
            val docID: Int = `in`.advance(target)
            assert(docID == `in`.docID())
            assert(docID >= target)
            assert(docID == DocIdSetIterator.NO_MORE_DOCS || docID < maxDoc)
            lastDocID = docID
            exists = docID != DocIdSetIterator.NO_MORE_DOCS
            ordsRetrieved = 0
            return docID
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            assertThread("Numeric doc values", creationThread)
            assert(target >= 0)
            assert(target >= `in`.docID())
            assert(target < maxDoc)
            exists = `in`.advanceExact(target)
            assert(`in`.docID() == target)
            lastDocID = target
            ordsRetrieved = 0
            return exists
        }

        override fun cost(): Long {
            assertThread("Sorted set doc values", creationThread)
            val cost: Long = `in`.cost()
            assert(cost >= 0)
            return cost
        }

        @Throws(IOException::class)
        override fun nextOrd(): Long {
            assertThread("Sorted set doc values", creationThread)
            assert(exists)
            assert(ordsRetrieved < docValueCount())
            ordsRetrieved++
            val ord: Long = `in`.nextOrd()
            assert(ord < valueCount)
            return ord
        }

        override fun docValueCount(): Int {
            return `in`.docValueCount()
        }

        @Throws(IOException::class)
        override fun lookupOrd(ord: Long): BytesRef? {
            assertThread("Sorted set doc values", creationThread)
            assert(ord in 0..<valueCount)
            val result: BytesRef? = `in`.lookupOrd(ord)
            assert(result!!.isValid())
            return result
        }

        /*override fun getValueCount(): Long {
            assertThread("Sorted set doc values", creationThread)
            val valueCount: Long = `in`.valueCount
            assert(
                valueCount == this.valueCount // should not change
            )
            return valueCount
        }*/

        @Throws(IOException::class)
        override fun lookupTerm(key: BytesRef): Long {
            assertThread("Sorted set doc values", creationThread)
            assert(key.isValid())
            val result: Long = `in`.lookupTerm(key)
            assert(result < valueCount)
            assert(key.isValid())
            return result
        }

        companion object {
            fun create(
                `in`: SortedSetDocValues,
                maxDoc: Int
            ): SortedSetDocValues {
                val singleDocValues: SortedDocValues? =
                    DocValues.unwrapSingleton(`in`)
                if (singleDocValues == null) {
                    return AssertingSortedSetDocValues(`in`, maxDoc)
                } else {
                    val assertingDocValues: SortedDocValues =
                        AssertingSortedDocValues(singleDocValues, maxDoc)
                    return DocValues.singleton(assertingDocValues)
                }
            }
        }
    }

    /** Wraps a DocValuesSkipper but with additional asserts  */
    class AssertingDocValuesSkipper(`in`: DocValuesSkipper) :
        DocValuesSkipper() {
        private val creationThread: Job? = currentJob()
        private val `in`: DocValuesSkipper

        /** Sole constructor  */
        init {
            this.`in` = `in`
            assert(minDocID(0) == -1)
            assert(maxDocID(0) == -1)
        }

        @Throws(IOException::class)
        override fun advance(target: Int) {
            assertThread("Doc values skipper", creationThread)
            assert(
                target > maxDocID(0)
            ) { "Illegal to call advance() on a target that is not beyond the current interval" }
            `in`.advance(target)
            assert(`in`.minDocID(0) <= `in`.maxDocID(0))
        }

        private fun iterating(): Boolean {
            return maxDocID(0) != -1 && minDocID(0) != -1 && maxDocID(0) != DocIdSetIterator.NO_MORE_DOCS && minDocID(
                0
            ) != DocIdSetIterator.NO_MORE_DOCS
        }

        override fun numLevels(): Int {
            assertThread("Doc values skipper", creationThread)
            return `in`.numLevels()
        }

        override fun minDocID(level: Int): Int {
            assertThread("Doc values skipper", creationThread)
            Objects.checkIndex(level, numLevels())
            val minDocID: Int = `in`.minDocID(level)
            assert(minDocID <= `in`.maxDocID(level))
            if (level > 0) {
                assert(minDocID <= `in`.minDocID(level - 1))
            }
            return minDocID
        }

        override fun maxDocID(level: Int): Int {
            assertThread("Doc values skipper", creationThread)
            Objects.checkIndex(level, numLevels())
            val maxDocID: Int = `in`.maxDocID(level)

            assert(maxDocID >= `in`.minDocID(level))
            if (level > 0) {
                assert(maxDocID >= `in`.maxDocID(level - 1))
            }
            return maxDocID
        }

        override fun minValue(level: Int): Long {
            assertThread("Doc values skipper", creationThread)
            assert(iterating()) { "Unpositioned iterator" }
            Objects.checkIndex(level, numLevels())
            return `in`.minValue(level)
        }

        override fun maxValue(level: Int): Long {
            assertThread("Doc values skipper", creationThread)
            assert(iterating()) { "Unpositioned iterator" }
            Objects.checkIndex(level, numLevels())
            return `in`.maxValue(level)
        }

        override fun docCount(level: Int): Int {
            assertThread("Doc values skipper", creationThread)
            assert(iterating()) { "Unpositioned iterator" }
            Objects.checkIndex(level, numLevels())
            return `in`.docCount(level)
        }

        override fun minValue(): Long {
            assertThread("Doc values skipper", creationThread)
            return `in`.minValue()
        }

        override fun maxValue(): Long {
            assertThread("Doc values skipper", creationThread)
            return `in`.maxValue()
        }

        override fun docCount(): Int {
            assertThread("Doc values skipper", creationThread)
            return `in`.docCount()
        }
    }

    /** Wraps a SortedSetDocValues but with additional asserts  */
    class AssertingPointValues(`in`: PointValues, maxDoc: Int) :
        PointValues() {
        private val creationThread: Job? = currentJob()
        private val `in`: PointValues

        /** Sole constructor.  */
        init {
            this.`in` = `in`
            assertStats(maxDoc)
        }

        val wrapped: PointValues
            get() = `in`

        private fun assertStats(maxDoc: Int) {
            assert(`in`.size() > 0)
            assert(`in`.docCount > 0)
            assert(`in`.docCount <= `in`.size())
            assert(`in`.docCount <= maxDoc)
        }

        override val pointTree: PointValues.PointTree
            get() {
                assertThread("Points", creationThread)
                return AssertingPointTree(`in`, `in`.pointTree)
            }

        override val minPackedValue: ByteArray
            get() {
                assertThread("Points", creationThread)
                return `in`.minPackedValue
            }

        override val maxPackedValue: ByteArray
            get() {
                assertThread("Points", creationThread)
                return `in`.maxPackedValue
            }

        override val numDimensions: Int
            get() {
                assertThread("Points", creationThread)
                return `in`.numDimensions
            }

        override val numIndexDimensions: Int
            get() {
                assertThread("Points", creationThread)
                return `in`.numIndexDimensions
            }

        override val bytesPerDimension: Int
            get() {
                assertThread("Points", creationThread)
                return `in`.bytesPerDimension
            }

        override fun size(): Long {
            assertThread("Points", creationThread)
            return `in`.size()
        }

        override val docCount: Int
            get() {
                assertThread("Points", creationThread)
                return `in`.docCount
            }
    }

    internal class AssertingPointTree(
        pointValues: PointValues,
        `in`: PointValues.PointTree
    ) : PointValues.PointTree {
        val pointValues: PointValues
        val `in`: PointValues.PointTree

        init {
            this.pointValues = pointValues
            this.`in` = `in`
        }

        override fun clone(): PointValues.PointTree {
            return AssertingPointTree(pointValues, `in`.clone())
        }

        @Throws(IOException::class)
        override fun moveToChild(): Boolean {
            return `in`.moveToChild()
        }

        @Throws(IOException::class)
        override fun moveToSibling(): Boolean {
            return `in`.moveToSibling()
        }

        @Throws(IOException::class)
        override fun moveToParent(): Boolean {
            return `in`.moveToParent()
        }

        override val minPackedValue: ByteArray
            get() = `in`.minPackedValue

        override val maxPackedValue: ByteArray
            get() = `in`.maxPackedValue

        override fun size(): Long {
            val size: Long = `in`.size()
            assert(size > 0)
            return size
        }

        @Throws(IOException::class)
        override fun visitDocIDs(visitor: IntersectVisitor) {
            `in`.visitDocIDs(
                AssertingIntersectVisitor(
                    pointValues.numDimensions,
                    pointValues.numIndexDimensions,
                    pointValues.bytesPerDimension,
                    visitor
                )
            )
        }

        @Throws(IOException::class)
        override fun visitDocValues(visitor: IntersectVisitor) {
            `in`.visitDocValues(
                AssertingIntersectVisitor(
                    pointValues.numDimensions,
                    pointValues.numIndexDimensions,
                    pointValues.bytesPerDimension,
                    visitor
                )
            )
        }
    }

    /**
     * Validates in the 1D case that all points are visited in order, and point values are in bounds
     * of the last cell checked
     */
    internal class AssertingIntersectVisitor(
        numDataDims: Int,
        numIndexDims: Int,
        bytesPerDim: Int,
        `in`: IntersectVisitor
    ) : IntersectVisitor {
        val `in`: IntersectVisitor
        val numDataDims: Int
        val numIndexDims: Int
        val bytesPerDim: Int
        val lastDocValue: ByteArray?
        val lastMinPackedValue: ByteArray
        val lastMaxPackedValue: ByteArray
        private var lastCompareResult: Relation? = null
        private var lastDocID = -1
        private var docBudget = 0

        init {
            this.`in` = `in`
            this.numDataDims = numDataDims
            this.numIndexDims = numIndexDims
            this.bytesPerDim = bytesPerDim
            lastMaxPackedValue = ByteArray(numDataDims * bytesPerDim)
            lastMinPackedValue = ByteArray(numDataDims * bytesPerDim)
            if (numDataDims == 1) {
                lastDocValue = ByteArray(bytesPerDim)
            } else {
                lastDocValue = null
            }
        }

        @Throws(IOException::class)
        override fun visit(docID: Int) {
            assert(--docBudget >= 0) { "called add() more times than the last call to grow() reserved" }

            // This method, not filtering each hit, should only be invoked when the cell is inside the
            // query shape:
            assert(lastCompareResult == null || lastCompareResult == Relation.CELL_INSIDE_QUERY)
            `in`.visit(docID)
        }

        @Throws(IOException::class)
        override fun visit(docID: Int, packedValue: ByteArray) {
            assert(--docBudget >= 0) { "called add() more times than the last call to grow() reserved" }

            // This method, to filter each doc's value, should only be invoked when the cell crosses the
            // query shape:
            assert(
                lastCompareResult == null
                        || lastCompareResult == Relation.CELL_CROSSES_QUERY
            )

            if (lastCompareResult != null) {
                // This doc's packed value should be contained in the last cell passed to compare:
                for (dim in 0..<numIndexDims) {
                    assert(
                        Arrays.compareUnsigned(
                            lastMinPackedValue,
                            dim * bytesPerDim,
                            dim * bytesPerDim + bytesPerDim,
                            packedValue,
                            dim * bytesPerDim,
                            dim * bytesPerDim + bytesPerDim
                        )
                                <= 0
                    ) {
                        "dim=$dim of $numDataDims value=" + BytesRef(
                            packedValue
                        )
                    }
                    assert(
                        Arrays.compareUnsigned(
                            lastMaxPackedValue,
                            dim * bytesPerDim,
                            dim * bytesPerDim + bytesPerDim,
                            packedValue,
                            dim * bytesPerDim,
                            dim * bytesPerDim + bytesPerDim
                        )
                                >= 0
                    ) {
                        "dim=$dim of $numDataDims value=" + BytesRef(
                            packedValue
                        )
                    }
                }
                lastCompareResult = null
            }

            // TODO: we should assert that this "matches" whatever relation the last call to compare had
            // returned
            assert(packedValue.size == numDataDims * bytesPerDim)
            if (numDataDims == 1) {
                val cmp: Int = Arrays.compareUnsigned(
                    lastDocValue!!,
                    0,
                    bytesPerDim,
                    packedValue,
                    0,
                    bytesPerDim
                )
                if (cmp < 0) {
                    // ok
                } else if (cmp == 0) {
                    assert(lastDocID <= docID) { "doc ids are out of order when point values are the same!" }
                } else {
                    // out of order!
                    assert(false) { "point values are out of order" }
                }
                System.arraycopy(packedValue, 0, lastDocValue, 0, bytesPerDim)
                lastDocID = docID
            }
            `in`.visit(docID, packedValue)
        }

        override fun grow(count: Int) {
            `in`.grow(count)
            docBudget = count
        }

        override fun compare(
            minPackedValue: ByteArray,
            maxPackedValue: ByteArray
        ): Relation {
            for (dim in 0..<numIndexDims) {
                assert(
                    Arrays.compareUnsigned(
                        minPackedValue,
                        dim * bytesPerDim,
                        dim * bytesPerDim + bytesPerDim,
                        maxPackedValue,
                        dim * bytesPerDim,
                        dim * bytesPerDim + bytesPerDim
                    )
                            <= 0
                )
            }
            System.arraycopy(
                maxPackedValue,
                0,
                lastMaxPackedValue,
                0,
                numIndexDims * bytesPerDim
            )
            System.arraycopy(
                minPackedValue,
                0,
                lastMinPackedValue,
                0,
                numIndexDims * bytesPerDim
            )
            lastCompareResult = `in`.compare(minPackedValue, maxPackedValue)
            return lastCompareResult!!
        }
    }

    @Throws(IOException::class)
    override fun getNumericDocValues(field: String): NumericDocValues? {
        val dv: NumericDocValues? = super.getNumericDocValues(field)
        val fi: FieldInfo? = fieldInfos.fieldInfo(field)
        if (dv != null) {
            checkNotNull(fi)
            assert(fi.docValuesType == DocValuesType.NUMERIC)
            return AssertingNumericDocValues(dv, maxDoc())
        } else {
            assert(fi == null || fi.docValuesType != DocValuesType.NUMERIC)
            return null
        }
    }

    @Throws(IOException::class)
    override fun getBinaryDocValues(field: String): BinaryDocValues? {
        val dv: BinaryDocValues? = super.getBinaryDocValues(field)
        val fi: FieldInfo? = fieldInfos.fieldInfo(field)
        if (dv != null) {
            checkNotNull(fi)
            assert(fi.docValuesType == DocValuesType.BINARY)
            return AssertingBinaryDocValues(dv, maxDoc())
        } else {
            assert(fi == null || fi.docValuesType != DocValuesType.BINARY)
            return null
        }
    }

    @Throws(IOException::class)
    override fun getSortedDocValues(field: String): SortedDocValues? {
        val dv: SortedDocValues? = super.getSortedDocValues(field)
        val fi: FieldInfo? = fieldInfos.fieldInfo(field)
        if (dv != null) {
            checkNotNull(fi)
            assert(fi.docValuesType == DocValuesType.SORTED)
            return AssertingSortedDocValues(dv, maxDoc())
        } else {
            assert(fi == null || fi.docValuesType != DocValuesType.SORTED)
            return null
        }
    }

    @Throws(IOException::class)
    override fun getSortedNumericDocValues(field: String): SortedNumericDocValues? {
        val fi: FieldInfo? = fieldInfos.fieldInfo(field)
        val dv: SortedNumericDocValues? =
            super.getSortedNumericDocValues(field)
        if (dv != null) {
            checkNotNull(fi)
            assert(fi.docValuesType == DocValuesType.SORTED_NUMERIC)
            return AssertingSortedNumericDocValues.Companion.create(dv, maxDoc())
        } else {
            assert(fi == null || fi.docValuesType != DocValuesType.SORTED_NUMERIC)
            return null
        }
    }

    @Throws(IOException::class)
    override fun getSortedSetDocValues(field: String): SortedSetDocValues? {
        val dv: SortedSetDocValues? = super.getSortedSetDocValues(field)
        val fi: FieldInfo? = fieldInfos.fieldInfo(field)
        if (dv != null) {
            checkNotNull(fi)
            assert(fi.docValuesType == DocValuesType.SORTED_SET)
            return AssertingSortedSetDocValues(dv, maxDoc())
        } else {
            assert(fi == null || fi.docValuesType != DocValuesType.SORTED_SET)
            return null
        }
    }

    @Throws(IOException::class)
    override fun getDocValuesSkipper(field: String): DocValuesSkipper? {
        val skipper: DocValuesSkipper? = super.getDocValuesSkipper(field)
        val fi: FieldInfo? = fieldInfos.fieldInfo(field)
        if (skipper != null) {
            assert(fi!!.docValuesSkipIndexType() !== DocValuesSkipIndexType.NONE)
            return AssertingDocValuesSkipper(skipper)
        } else {
            assert(fi == null || fi.docValuesSkipIndexType() === DocValuesSkipIndexType.NONE)
            return null
        }
    }

    @Throws(IOException::class)
    override fun getNormValues(field: String): NumericDocValues? {
        val dv: NumericDocValues? = super.getNormValues(field)
        val fi: FieldInfo? = fieldInfos.fieldInfo(field)
        if (dv != null) {
            checkNotNull(fi)
            assert(fi.hasNorms())
            return AssertingNumericDocValues(dv, maxDoc())
        } else {
            assert(fi == null || fi.hasNorms() == false)
            return null
        }
    }

    @Throws(IOException::class)
    override fun getPointValues(field: String): PointValues? {
        val values: PointValues? = delegate.getPointValues(field)
        if (values == null) {
            return null
        }
        return AssertingPointValues(values, maxDoc())
    }

    /** Wraps a Bits but with additional asserts  */
    class AssertingBits(val `in`: Bits) : Bits {
        private val creationThread: Job? = currentJob()

        override fun get(index: Int): Boolean {
            assertThread("Bits", creationThread)
            assert(index >= 0 && index < length())
            return `in`.get(index)
        }

        override fun length(): Int {
            assertThread("Bits", creationThread)
            return `in`.length()
        }
    }

    override val liveDocs: Bits
        get() {
            var liveDocs: Bits? = super.liveDocs
            if (liveDocs != null) {
                assert(maxDoc() == liveDocs.length())
                liveDocs = AssertingBits(liveDocs)
            } else {
                assert(maxDoc() == numDocs())
                assert(!hasDeletions())
            }
            return liveDocs!!
        }

    override val coreCacheHelper: CacheHelper?
        // we don't change behavior of the reader: just validate the API.
        get() = delegate.coreCacheHelper

    override val readerCacheHelper: CacheHelper?
        get() = delegate.readerCacheHelper

    companion object {
        // Mirrors java.lang.Thread.currentThread() checks by tracking the current coroutine Job
        // via a CloseableThreadLocal. If no Job is bound, checks are skipped (best-effort).
        private val currentJobLocal = CloseableThreadLocal<Job?>()

        internal inline fun <T> withCurrentJob(job: Job?, block: () -> T): T {
            val previous = currentJobLocal.get()
            currentJobLocal.set(job)
            try {
                return block()
            } finally {
                currentJobLocal.set(previous)
            }
        }

        private fun currentJob(): Job? = currentJobLocal.get()

        private fun assertThread(`object`: String, creationThread: Job?) {
            val current = currentJob()
            if (creationThread == null || current == null) {
                return
            }
            if (creationThread !== current) {
                throw AssertionError(
                    (`object`
                            + " are only supposed to be consumed in "
                            + "the thread in which they have been acquired. But was acquired in "
                            + creationThread
                            + " and consumed in "
                            + current
                            + ".")
                )
            }
        }

        val SEEK_EXACT: VirtualMethod<TermsEnum> =
            VirtualMethod<TermsEnum>(
                TermsEnum::class,
                "seekExact",
                BytesRef::class
            )
    }
}
