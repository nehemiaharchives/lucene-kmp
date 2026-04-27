package org.gnit.lucenekmp.codecs.memory

import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.index.BaseTermsEnum
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.OrdTermState
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.SlowImpactsEnum
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.Collections
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.TreeMap
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.automaton.ByteRunnable
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import org.gnit.lucenekmp.util.automaton.Transition
import org.gnit.lucenekmp.util.automaton.TransitionAccessor

// TODO:
//   - build depth-N prefix hash?
//   - or: longer dense skip lists than just next byte?

/**
 * Wraps {@link Lucene101PostingsFormat} format for on-disk storage, but then at read time loads and
 * stores all terms and postings directly in RAM as byte[], int[].
 *
 * <p><b>WARNING</b>: This is exceptionally RAM intensive: it makes no effort to compress the
 * postings data, storing terms as separate byte[] and postings as separate int[], but as a result
 * it gives substantial increase in search performance.
 *
 * <p>This postings format supports {@link TermsEnum#ord} and {@link TermsEnum#seekExact(long)}.
 *
 * <p>Because this holds all term bytes as a single byte[], you cannot have more than 2.1GB worth of
 * term bytes in a single segment.
 *
 * @lucene.experimental
 */
class DirectPostingsFormat(
    private val minSkipCount: Int = DEFAULT_MIN_SKIP_COUNT,
    private val lowFreqCutoff: Int = DEFAULT_LOW_FREQ_CUTOFF
) : PostingsFormat("Direct") {

    companion object {
        private const val DEFAULT_MIN_SKIP_COUNT: Int = 8
        private const val DEFAULT_LOW_FREQ_CUTOFF: Int = 32

        private class DirectFields : FieldsProducer {
            private val fields: MutableMap<String, DirectField> = TreeMap()

            constructor(state: SegmentReadState, fields: Fields, minSkipCount: Int, lowFreqCutoff: Int) {
                fields.forEach { field ->
                    this.fields.put(
                        field,
                        DirectField(
                            state,
                            field,
                            fields.terms(field),
                            minSkipCount,
                            lowFreqCutoff
                        )
                    )
                }
            }

            override fun iterator(): MutableIterator<String> {
                return Collections.unmodifiableSet(fields.keys).iterator()
            }

            override fun terms(field: String?): Terms? {
                return fields[field]
            }

            override fun size(): Int {
                return fields.size
            }

            override fun close() {}

            override fun checkIntegrity() {
                // if we read entirely into ram, we already validated.
                // otherwise returned the raw postings reader
            }

            override fun toString(): String {
                return "${this::class.simpleName}(fields=${fields.size})"
            }
        }

        private class DirectField(
            state: SegmentReadState,
            field: String,
            termsIn: Terms?,
            minSkipCount: Int,
            lowFreqCutoff: Int
        ) : Terms(), Accountable {

            companion object {
                private val BASE_RAM_BYTES_USED =
                    RamUsageEstimator.shallowSizeOfInstance(DirectField::class)
            }

            private abstract class TermAndSkip : Accountable {
                var skips: IntArray? = null
            }

            private class LowFreqTerm(
                val postings: IntArray?,
                val payloads: ByteArray?,
                val docFreq: Int,
                val totalTermFreq: Int
            ) : TermAndSkip() {
                companion object {
                    private val BASE_RAM_BYTES_USED =
                        RamUsageEstimator.shallowSizeOfInstance(HighFreqTerm::class)
                }

                override fun ramBytesUsed(): Long {
                    return BASE_RAM_BYTES_USED +
                        (if (postings != null) RamUsageEstimator.sizeOf(postings) else 0L) +
                        (if (payloads != null) RamUsageEstimator.sizeOf(payloads) else 0L)
                }
            }

            // TODO: maybe specialize into prx/no-prx/no-frq cases?
            private class HighFreqTerm(
                val docIDs: IntArray?,
                val freqs: IntArray?,
                val positions: Array<IntArray?>?,
                val payloads: Array<Array<ByteArray?>?>?,
                val totalTermFreq: Long
            ) : TermAndSkip() {
                companion object {
                    private val BASE_RAM_BYTES_USED =
                        RamUsageEstimator.shallowSizeOfInstance(HighFreqTerm::class)
                }

                override fun ramBytesUsed(): Long {
                    var sizeInBytes = BASE_RAM_BYTES_USED
                    sizeInBytes += if (docIDs != null) RamUsageEstimator.sizeOf(docIDs) else 0L
                    sizeInBytes += if (freqs != null) RamUsageEstimator.sizeOf(freqs) else 0L

                    if (positions != null) {
                        sizeInBytes += RamUsageEstimator.shallowSizeOf(positions)
                        for (position in positions) {
                            sizeInBytes += if (position != null) RamUsageEstimator.sizeOf(position) else 0L
                        }
                    }

                    if (payloads != null) {
                        sizeInBytes += RamUsageEstimator.shallowSizeOf(payloads)
                        for (payload in payloads) {
                            if (payload != null) {
                                sizeInBytes += RamUsageEstimator.shallowSizeOf(payload)
                                for (pload in payload) {
                                    sizeInBytes += if (pload != null) RamUsageEstimator.sizeOf(pload) else 0L
                                }
                            }
                        }
                    }

                    return sizeInBytes
                }
            }

            private lateinit var termBytes: ByteArray
            private lateinit var termOffsets: IntArray
            private lateinit var skips: IntArray
            private lateinit var skipOffsets: IntArray
            private lateinit var terms: Array<TermAndSkip>
            private var hasFreq: Boolean = false
            private var hasPos: Boolean = false
            private var hasOffsets: Boolean = false
            private var hasPayloads: Boolean = false
            override var sumTotalTermFreq: Long = 0L
                private set
            override var docCount: Int = 0
                private set
            override var sumDocFreq: Long = 0L
                private set
            private var skipCount: Int = 0

            // TODO: maybe make a separate builder?  These are only
            // used during load:
            private var count: Int = 0
            private var sameCounts: IntArray = IntArray(10)
            private var minSkipCount: Int = 0

            private class IntArrayWriter {
                private var ints: IntArray = IntArray(10)
                private var upto: Int = 0

                fun add(value: Int) {
                    if (ints.size == upto) {
                        ints = ArrayUtil.grow(ints)
                    }
                    ints[upto++] = value
                }

                fun get(): IntArray {
                    val arr = IntArray(upto)
                    System.arraycopy(ints, 0, arr, 0, upto)
                    upto = 0
                    return arr
                }
            }

            init {
                val fieldInfo: FieldInfo = requireNotNull(state.fieldInfos.fieldInfo(field))
                val termsIn: Terms = requireNotNull(termsIn)

                sumTotalTermFreq = termsIn.sumTotalTermFreq
                sumDocFreq = termsIn.sumDocFreq
                docCount = termsIn.docCount

                val numTerms = termsIn.size().toInt()
                require(numTerms != -1) { "codec does not provide Terms.size()" }
                terms = Array(numTerms) { LowFreqTerm(null, null, 0, 0) }
                termOffsets = IntArray(1 + numTerms)

                var termBytes = ByteArray(1024)

                this.minSkipCount = minSkipCount

                hasFreq = fieldInfo.indexOptions > IndexOptions.DOCS
                hasPos = fieldInfo.indexOptions > IndexOptions.DOCS_AND_FREQS
                hasOffsets = fieldInfo.indexOptions > IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
                hasPayloads = fieldInfo.hasPayloads()

                var term: BytesRef?
                var postingsEnum: PostingsEnum? = null
                var docsAndPositionsEnum: PostingsEnum? = null
                val termsEnum = termsIn.iterator()
                var termOffset = 0

                val scratch = IntArrayWriter()
                val ros = ByteBuffersDataOutput.newResettableInstance()

                while (termsEnum.next().also { term = it } != null) {
                    val term = requireNotNull(term)
                    val docFreq = termsEnum.docFreq()
                    val totalTermFreq = termsEnum.totalTermFreq()

                    termOffsets[count] = termOffset

                    if (termBytes.size < termOffset + term.length) {
                        termBytes = ArrayUtil.grow(termBytes, termOffset + term.length)
                    }
                    System.arraycopy(term.bytes, term.offset, termBytes, termOffset, term.length)
                    termOffset += term.length
                    termOffsets[count + 1] = termOffset

                    if (hasPos) {
                        docsAndPositionsEnum = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.ALL.toInt())
                    } else {
                        postingsEnum = termsEnum.postings(postingsEnum)
                    }

                    val postingsEnum2 = if (hasPos) docsAndPositionsEnum else postingsEnum
                    var docID: Int

                    val ent: TermAndSkip

                    if (docFreq <= lowFreqCutoff) {
                        ros.reset()

                        while (requireNotNull(postingsEnum2).nextDoc().also { docID = it } != DocIdSetIterator.NO_MORE_DOCS) {
                            scratch.add(docID)
                            if (hasFreq) {
                                val freq = postingsEnum2.freq()
                                scratch.add(freq)
                                if (hasPos) {
                                    for (pos in 0..<freq) {
                                        scratch.add(requireNotNull(docsAndPositionsEnum).nextPosition())
                                        if (hasOffsets) {
                                            scratch.add(docsAndPositionsEnum.startOffset())
                                            scratch.add(docsAndPositionsEnum.endOffset())
                                        }
                                        if (hasPayloads) {
                                            val payload = docsAndPositionsEnum.payload
                                            if (payload != null) {
                                                scratch.add(payload.length)
                                                ros.writeBytes(payload.bytes, payload.offset, payload.length)
                                            } else {
                                                scratch.add(0)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val payloads = if (hasPayloads) ros.toArrayCopy() else null
                        val postings = scratch.get()

                        ent = LowFreqTerm(postings, payloads, docFreq, totalTermFreq.toInt())
                    } else {
                        val docs = IntArray(docFreq)
                        val freqs: IntArray?
                        val positions: Array<IntArray?>?
                        val payloads: Array<Array<ByteArray?>?>?
                        if (hasFreq) {
                            freqs = IntArray(docFreq)
                            if (hasPos) {
                                positions = arrayOfNulls(docFreq)
                                payloads = if (hasPayloads) arrayOfNulls(docFreq) else null
                            } else {
                                positions = null
                                payloads = null
                            }
                        } else {
                            freqs = null
                            positions = null
                            payloads = null
                        }

                        var upto = 0
                        while (requireNotNull(postingsEnum2).nextDoc().also { docID = it } != DocIdSetIterator.NO_MORE_DOCS) {
                            docs[upto] = docID
                            if (hasFreq) {
                                val freq = postingsEnum2.freq()
                                requireNotNull(freqs)[upto] = freq
                                if (hasPos) {
                                    val mult = if (hasOffsets) 3 else 1
                                    if (hasPayloads) {
                                        requireNotNull(payloads)[upto] = arrayOfNulls(freq)
                                    }
                                    requireNotNull(positions)[upto] = IntArray(mult * freq)
                                    var posUpto = 0
                                    for (pos in 0..<freq) {
                                        requireNotNull(positions[upto])[posUpto] =
                                            requireNotNull(docsAndPositionsEnum).nextPosition()
                                        if (hasPayloads) {
                                            val payload = docsAndPositionsEnum.payload
                                            if (payload != null) {
                                                val payloadBytes = ByteArray(payload.length)
                                                System.arraycopy(
                                                    payload.bytes,
                                                    payload.offset,
                                                    payloadBytes,
                                                    0,
                                                    payload.length
                                                )
                                                requireNotNull(requireNotNull(payloads)[upto])[pos] = payloadBytes
                                            }
                                        }
                                        posUpto++
                                        if (hasOffsets) {
                                            requireNotNull(positions[upto])[posUpto++] = docsAndPositionsEnum.startOffset()
                                            requireNotNull(positions[upto])[posUpto++] = docsAndPositionsEnum.endOffset()
                                        }
                                    }
                                }
                            }
                            upto++
                        }
                        ent = HighFreqTerm(docs, freqs, positions, payloads, totalTermFreq)
                    }

                    terms[count] = ent
                    setSkips(count, termBytes)
                    count++
                }

                termOffsets[count] = termOffset

                finishSkips()

                this.termBytes = ByteArray(termOffset)
                System.arraycopy(termBytes, 0, this.termBytes, 0, termOffset)

                this.skips = IntArray(skipCount)
                this.skipOffsets = IntArray(1 + numTerms)

                var skipOffset = 0
                for (i in 0..<numTerms) {
                    val termSkips = terms[i].skips
                    skipOffsets[i] = skipOffset
                    if (termSkips != null) {
                        System.arraycopy(termSkips, 0, skips, skipOffset, termSkips.size)
                        skipOffset += termSkips.size
                        terms[i].skips = null
                    }
                }
                this.skipOffsets[numTerms] = skipOffset
                assert(skipOffset == skipCount)
            }

            override fun ramBytesUsed(): Long {
                var sizeInBytes = BASE_RAM_BYTES_USED
                sizeInBytes += RamUsageEstimator.sizeOf(termBytes)
                sizeInBytes += RamUsageEstimator.sizeOf(termOffsets)
                sizeInBytes += RamUsageEstimator.sizeOf(skips)
                sizeInBytes += RamUsageEstimator.sizeOf(skipOffsets)
                sizeInBytes += RamUsageEstimator.sizeOf(sameCounts)

                sizeInBytes += RamUsageEstimator.shallowSizeOf(terms)
                for (termAndSkip in terms) {
                    sizeInBytes += termAndSkip.ramBytesUsed()
                }

                return sizeInBytes
            }

            override fun toString(): String {
                return "DirectTerms(terms=${terms.size},postings=$sumDocFreq,positions=$sumTotalTermFreq,docs=$docCount)"
            }

            // Compares in unicode (UTF8) order:
            fun compare(ord: Int, other: BytesRef): Int {
                val otherBytes = other.bytes

                var upto = termOffsets[ord]
                val termLen = termOffsets[1 + ord] - upto
                var otherUpto = other.offset

                val stop = upto + minOf(termLen, other.length)
                while (upto < stop) {
                    val diff =
                        (termBytes[upto++].toInt() and 0xFF) - (otherBytes[otherUpto++].toInt() and 0xFF)
                    if (diff != 0) {
                        return diff
                    }
                }

                // One is a prefix of the other, or, they are equal:
                return termLen - other.length
            }

            private fun setSkips(termOrd: Int, termBytes: ByteArray) {
                val termLength = termOffsets[termOrd + 1] - termOffsets[termOrd]

                if (sameCounts.size < termLength) {
                    sameCounts = ArrayUtil.grow(sameCounts, termLength)
                }

                // Update skip pointers:
                if (termOrd > 0) {
                    val lastTermLength = termOffsets[termOrd] - termOffsets[termOrd - 1]
                    val limit = minOf(termLength, lastTermLength)

                    var lastTermOffset = termOffsets[termOrd - 1]
                    var termOffset = termOffsets[termOrd]

                    var i = 0
                    while (i < limit) {
                        if (termBytes[lastTermOffset++] == termBytes[termOffset++]) {
                            sameCounts[i]++
                        } else {
                            while (i < limit) {
                                if (sameCounts[i] >= minSkipCount) {
                                    // Go back and add a skip pointer:
                                    saveSkip(termOrd, sameCounts[i])
                                }
                                sameCounts[i] = 1
                                i++
                            }
                            break
                        }
                        i++
                    }

                    while (i < lastTermLength) {
                        if (sameCounts[i] >= minSkipCount) {
                            // Go back and add a skip pointer:
                            saveSkip(termOrd, sameCounts[i])
                        }
                        sameCounts[i] = 0
                        i++
                    }
                    for (j in limit..<termLength) {
                        sameCounts[j] = 1
                    }
                } else {
                    for (i in 0..<termLength) {
                        sameCounts[i]++
                    }
                }
            }

            private fun finishSkips() {
                assert(count == terms.size)
                val lastTermOffset = termOffsets[count - 1]
                val lastTermLength = termOffsets[count] - lastTermOffset

                for (i in 0..<lastTermLength) {
                    if (sameCounts[i] >= minSkipCount) {
                        // Go back and add a skip pointer:
                        saveSkip(count, sameCounts[i])
                    }
                }

                // Reverse the skip pointers so they are "nested":
                for (termID in terms.indices) {
                    val term = terms[termID]
                    if (term.skips != null && requireNotNull(term.skips).size > 1) {
                        for (pos in 0..<requireNotNull(term.skips).size / 2) {
                            val otherPos = requireNotNull(term.skips).size - pos - 1

                            val temp = requireNotNull(term.skips)[pos]
                            requireNotNull(term.skips)[pos] = requireNotNull(term.skips)[otherPos]
                            requireNotNull(term.skips)[otherPos] = temp
                        }
                    }
                }
            }

            private fun saveSkip(ord: Int, backCount: Int) {
                val term = terms[ord - backCount]
                skipCount++
                if (term.skips == null) {
                    term.skips = intArrayOf(ord)
                } else {
                    // Normally we'd grow at a slight exponential... but
                    // given that the skips themselves are already log(N)
                    // we can grow by only 1 and still have amortized
                    // linear time:
                    val newSkips = IntArray(requireNotNull(term.skips).size + 1)
                    System.arraycopy(requireNotNull(term.skips), 0, newSkips, 0, requireNotNull(term.skips).size)
                    term.skips = newSkips
                    requireNotNull(term.skips)[requireNotNull(term.skips).size - 1] = ord
                }
            }

            override fun iterator(): TermsEnum {
                return DirectTermsEnum()
            }

            override fun intersect(compiled: CompiledAutomaton, startTerm: BytesRef?): TermsEnum {
                require(compiled.type == CompiledAutomaton.AUTOMATON_TYPE.NORMAL) {
                    "please use CompiledAutomaton.getTermsEnum instead"
                }
                return DirectIntersectTermsEnum(compiled, startTerm)
            }

            override fun size(): Long {
                return terms.size.toLong()
            }

            override fun hasFreqs(): Boolean {
                return hasFreq
            }

            override fun hasOffsets(): Boolean {
                return hasOffsets
            }

            override fun hasPositions(): Boolean {
                return hasPos
            }

            override fun hasPayloads(): Boolean {
                return hasPayloads
            }

            private inner class DirectTermsEnum : BaseTermsEnum() {
                private val scratch = BytesRef()
                private var termOrd: Int = -1

                private fun setTerm(): BytesRef {
                    scratch.bytes = termBytes
                    scratch.offset = termOffsets[termOrd]
                    scratch.length = termOffsets[termOrd + 1] - termOffsets[termOrd]
                    return scratch
                }

                override fun next(): BytesRef? {
                    termOrd++
                    return if (termOrd < terms.size) {
                        setTerm()
                    } else {
                        null
                    }
                }

                override fun termState(): TermState {
                    val state = OrdTermState()
                    state.ord = termOrd.toLong()
                    return state
                }

                // If non-negative, exact match; else, -ord-1, where ord
                // is where you would insert the term.
                private fun findTerm(term: BytesRef): Int {
                    // Just do binary search: should be (constant factor)
                    // faster than using the skip list:
                    var low = 0
                    var high = terms.size - 1

                    while (low <= high) {
                        val mid = (low + high) ushr 1
                        val cmp = compare(mid, term)
                        if (cmp < 0) {
                            low = mid + 1
                        } else if (cmp > 0) {
                            high = mid - 1
                        } else {
                            return mid // key found
                        }
                    }

                    return -(low + 1) // key not found.
                }

                override fun seekCeil(term: BytesRef): SeekStatus {
                    // TODO: we should use the skip pointers; should be
                    // faster than bin search; we should also hold
                    // & reuse current state so seeking forwards is
                    // faster
                    val ord = findTerm(term)
                    return if (ord >= 0) {
                        termOrd = ord
                        setTerm()
                        TermsEnum.SeekStatus.FOUND
                    } else if (ord == -terms.size - 1) {
                        TermsEnum.SeekStatus.END
                    } else {
                        termOrd = -ord - 1
                        setTerm()
                        TermsEnum.SeekStatus.NOT_FOUND
                    }
                }

                override fun seekExact(term: BytesRef): Boolean {
                    // TODO: we should use the skip pointers; should be
                    // faster than bin search; we should also hold
                    // & reuse current state so seeking forwards is
                    // faster
                    val ord = findTerm(term)
                    return if (ord >= 0) {
                        termOrd = ord
                        setTerm()
                        true
                    } else {
                        false
                    }
                }

                override fun seekExact(ord: Long) {
                    termOrd = ord.toInt()
                    setTerm()
                }

                override fun seekExact(term: BytesRef, state: TermState) {
                    termOrd = (state as OrdTermState).ord.toInt()
                    setTerm()
                    assert(term == scratch)
                }

                override fun term(): BytesRef {
                    return scratch
                }

                override fun ord(): Long {
                    return termOrd.toLong()
                }

                override fun docFreq(): Int {
                    return when (val term = terms[termOrd]) {
                        is LowFreqTerm -> term.docFreq
                        is HighFreqTerm -> requireNotNull(term.docIDs).size
                        else -> throw IllegalStateException()
                    }
                }

                override fun totalTermFreq(): Long {
                    return when (val term = terms[termOrd]) {
                        is LowFreqTerm -> term.totalTermFreq.toLong()
                        is HighFreqTerm -> term.totalTermFreq
                        else -> throw IllegalStateException()
                    }
                }

                override fun postings(reuse: PostingsEnum?, flags: Int): PostingsEnum {
                    // TODO: implement reuse
                    // it's hairy!

                    // TODO: the logic of which enum impl to choose should be refactored to be simpler...
                    if (PostingsEnum.featureRequested(flags, PostingsEnum.POSITIONS)) {
                        if (terms[termOrd] is LowFreqTerm) {
                            val term = terms[termOrd] as LowFreqTerm
                            val postings = term.postings
                            if (!hasFreq) {
                                val docsEnum =
                                    if (reuse is LowFreqDocsEnumNoTF) {
                                        reuse
                                    } else {
                                        LowFreqDocsEnumNoTF()
                                    }

                                return docsEnum.reset(postings)
                            } else if (!hasPos) {
                                val docsEnum =
                                    if (reuse is LowFreqDocsEnumNoPos) {
                                        reuse
                                    } else {
                                        LowFreqDocsEnumNoPos()
                                    }

                                return docsEnum.reset(postings)
                            }
                            val payloads = term.payloads
                            return LowFreqPostingsEnum(hasOffsets, hasPayloads).reset(postings, payloads)
                        } else {
                            val term = terms[termOrd] as HighFreqTerm
                            return if (!hasPos) {
                                HighFreqDocsEnum().reset(term.docIDs, term.freqs)
                            } else {
                                HighFreqPostingsEnum(hasOffsets)
                                    .reset(term.docIDs, term.freqs, term.positions, term.payloads)
                            }
                        }
                    }

                    if (terms[termOrd] is LowFreqTerm) {
                        val postings = (terms[termOrd] as LowFreqTerm).postings
                        if (hasFreq) {
                            if (hasPos) {
                                var posLen = if (hasOffsets) {
                                    3
                                } else {
                                    1
                                }
                                if (hasPayloads) {
                                    posLen++
                                }
                                var docsEnum =
                                    if (reuse is LowFreqDocsEnum) {
                                        reuse
                                    } else {
                                        LowFreqDocsEnum(posLen)
                                    }
                                if (!docsEnum.canReuse(posLen)) {
                                    docsEnum = LowFreqDocsEnum(posLen)
                                }

                                return docsEnum.reset(postings)
                            } else {
                                val docsEnum =
                                    if (reuse is LowFreqDocsEnumNoPos) {
                                        reuse
                                    } else {
                                        LowFreqDocsEnumNoPos()
                                    }

                                return docsEnum.reset(postings)
                            }
                        } else {
                            val docsEnum =
                                if (reuse is LowFreqDocsEnumNoTF) {
                                    reuse
                                } else {
                                    LowFreqDocsEnumNoTF()
                                }

                            return docsEnum.reset(postings)
                        }
                    } else {
                        val term = terms[termOrd] as HighFreqTerm

                        val docsEnum =
                            if (reuse is HighFreqDocsEnum) {
                                reuse
                            } else {
                                HighFreqDocsEnum()
                            }

                        return docsEnum.reset(term.docIDs, term.freqs)
                    }
                }

                override fun impacts(flags: Int): ImpactsEnum {
                    return SlowImpactsEnum(postings(null, flags))
                }
            }

            private inner class DirectIntersectTermsEnum(compiled: CompiledAutomaton, startTerm: BytesRef?) :
                BaseTermsEnum() {
                private val byteRunnable: ByteRunnable = requireNotNull(compiled.getByteRunnable())
                protected val transitionAccessor: TransitionAccessor = requireNotNull(compiled.getTransitionAccessor())
                private val commonSuffixRef: BytesRef? = compiled.commonSuffixRef
                private var termOrd: Int = -1
                private val scratch = BytesRef()

                private inner class State {
                    var changeOrd: Int = 0
                    var state: Int = 0
                    var transitionUpto: Int = 0
                    var transitionCount: Int = 0
                    var transitionMax: Int = 0
                    var transitionMin: Int = 0
                    val transition = Transition()
                }

                private var states: Array<State?> = arrayOfNulls(1)
                private var stateUpto: Int = 0

                init {
                    run initSearch@ {
                        states[0] = State()
                        requireNotNull(states[0]).changeOrd = terms.size
                        requireNotNull(states[0]).state = 0
                        requireNotNull(states[0]).transitionCount =
                            transitionAccessor.getNumTransitions(requireNotNull(states[0]).state)
                        transitionAccessor.initTransition(requireNotNull(states[0]).state, requireNotNull(states[0]).transition)
                        requireNotNull(states[0]).transitionUpto = -1
                        requireNotNull(states[0]).transitionMax = -1

                        if (startTerm != null) {
                            var skipUpto = 0
                            if (startTerm.length == 0) {
                                if (terms.isNotEmpty() && termOffsets[1] == 0) {
                                    termOrd = 0
                                }
                            } else {
                                termOrd++

                                nextLabel@
                                for (i in 0..<startTerm.length) {
                                    val label = startTerm.bytes[startTerm.offset + i].toInt() and 0xFF

                                    while (label > requireNotNull(states[i]).transitionMax) {
                                        requireNotNull(states[i]).transitionUpto++
                                        if (requireNotNull(states[i]).transitionUpto >= requireNotNull(states[i]).transitionCount) {
                                            break
                                        }
                                        transitionAccessor.getNextTransition(requireNotNull(states[i]).transition)
                                        requireNotNull(states[i]).transitionMin = requireNotNull(states[i]).transition.min
                                        requireNotNull(states[i]).transitionMax = requireNotNull(states[i]).transition.max
                                        assert(requireNotNull(states[i]).transitionMin >= 0)
                                        assert(requireNotNull(states[i]).transitionMin <= 255)
                                        assert(requireNotNull(states[i]).transitionMax >= 0)
                                        assert(requireNotNull(states[i]).transitionMax <= 255)
                                    }

                                    // Skip forwards until we find a term matching
                                    // the label at this position:
                                    while (termOrd < terms.size) {
                                        val skipOffset = skipOffsets[termOrd]
                                        val numSkips = skipOffsets[termOrd + 1] - skipOffset
                                        val termOffset = termOffsets[termOrd]
                                        val termLength = termOffsets[1 + termOrd] - termOffset

                                        if (termOrd == requireNotNull(states[stateUpto]).changeOrd) {
                                            stateUpto--
                                            termOrd--
                                            return@initSearch
                                        }

                                        if (termLength == i) {
                                            termOrd++
                                            skipUpto = 0
                                        } else if (label < (termBytes[termOffset + i].toInt() and 0xFF)) {
                                            termOrd--
                                            stateUpto -= skipUpto
                                            assert(stateUpto >= 0)
                                            return@initSearch
                                        } else if (label == (termBytes[termOffset + i].toInt() and 0xFF)) {
                                            if (skipUpto < numSkips) {
                                                grow()

                                                val nextState = byteRunnable.step(requireNotNull(states[stateUpto]).state, label)
                                                assert(nextState != -1)

                                                stateUpto++
                                                requireNotNull(states[stateUpto]).changeOrd = skips[skipOffset + skipUpto++]
                                                requireNotNull(states[stateUpto]).state = nextState
                                                requireNotNull(states[stateUpto]).transitionCount =
                                                    transitionAccessor.getNumTransitions(nextState)
                                                transitionAccessor.initTransition(
                                                    requireNotNull(states[stateUpto]).state,
                                                    requireNotNull(states[stateUpto]).transition
                                                )
                                                requireNotNull(states[stateUpto]).transitionUpto = -1
                                                requireNotNull(states[stateUpto]).transitionMax = -1

                                                continue@nextLabel
                                            } else {
                                                val startTermOrd = termOrd
                                                while (termOrd < terms.size && compare(termOrd, startTerm) <= 0) {
                                                    assert(termOrd == startTermOrd || skipOffsets[termOrd] == skipOffsets[termOrd + 1])
                                                    termOrd++
                                                }
                                                assert(termOrd - startTermOrd < minSkipCount)
                                                termOrd--
                                                stateUpto -= skipUpto
                                                return@initSearch
                                            }
                                        } else {
                                            if (skipUpto < numSkips) {
                                                termOrd = skips[skipOffset + skipUpto]
                                            } else {
                                                termOrd++
                                            }
                                            skipUpto = 0
                                        }
                                    }

                                    termOrd--
                                    return@initSearch
                                }
                            }

                            if (termOrd >= 0) {
                                val termOffset = termOffsets[termOrd]
                                val termLen = termOffsets[1 + termOrd] - termOffset

                                if (startTerm != BytesRef(termBytes, termOffset, termLen)) {
                                    stateUpto -= skipUpto
                                    termOrd--
                                }
                            }
                        }
                    }
                }

                private fun grow() {
                    if (states.size == 1 + stateUpto) {
                        val newStates = arrayOfNulls<State>(states.size + 1)
                        System.arraycopy(states, 0, newStates, 0, states.size)
                        newStates[states.size] = State()
                        states = newStates
                    }
                }

                override fun next(): BytesRef? {
                    termOrd++
                    var skipUpto = 0

                    if (termOrd == 0 && termOffsets[1] == 0) {
                        assert(stateUpto == 0)
                        if (byteRunnable.isAccept(requireNotNull(states[0]).state)) {
                            scratch.bytes = termBytes
                            scratch.offset = 0
                            scratch.length = 0
                            return scratch
                        }
                        termOrd++
                    }

                    nextTerm@
                    while (true) {
                        if (termOrd == terms.size) {
                            return null
                        }

                        val state = requireNotNull(states[stateUpto])
                        if (termOrd == state.changeOrd) {
                            stateUpto--
                            continue
                        }

                        val termOffset = termOffsets[termOrd]
                        val termLength = termOffsets[termOrd + 1] - termOffset
                        val skipOffset = skipOffsets[termOrd]
                        val numSkips = skipOffsets[termOrd + 1] - skipOffset

                        assert(termOrd < state.changeOrd)
                        assert(stateUpto <= termLength)
                        val label = termBytes[termOffset + stateUpto].toInt() and 0xFF

                        while (label > state.transitionMax) {
                            state.transitionUpto++
                            if (state.transitionUpto == state.transitionCount) {
                                if (stateUpto == 0) {
                                    termOrd = terms.size
                                    return null
                                } else {
                                    assert(state.changeOrd > termOrd)
                                    termOrd = requireNotNull(states[stateUpto]).changeOrd
                                    skipUpto = 0
                                    stateUpto--
                                }
                                continue@nextTerm
                            }
                            transitionAccessor.getNextTransition(state.transition)
                            assert(state.transitionUpto < state.transitionCount)
                            state.transitionMin = state.transition.min
                            state.transitionMax = state.transition.max
                            assert(state.transitionMin >= 0)
                            assert(state.transitionMin <= 255)
                            assert(state.transitionMax >= 0)
                            assert(state.transitionMax <= 255)
                        }

                        val targetLabel = state.transitionMin

                        if ((termBytes[termOffset + stateUpto].toInt() and 0xFF) < targetLabel) {
                            var low = termOrd + 1
                            var high = state.changeOrd - 1
                            while (true) {
                                if (low > high) {
                                    termOrd = low
                                    skipUpto = 0
                                    continue@nextTerm
                                }
                                var mid = (low + high) ushr 1
                                val cmp = (termBytes[termOffsets[mid] + stateUpto].toInt() and 0xFF) - targetLabel
                                if (cmp < 0) {
                                    low = mid + 1
                                } else if (cmp > 0) {
                                    high = mid - 1
                                } else {
                                    while (mid > termOrd &&
                                        (termBytes[termOffsets[mid - 1] + stateUpto].toInt() and 0xFF) == targetLabel
                                    ) {
                                        mid--
                                    }
                                    termOrd = mid
                                    skipUpto = 0
                                    continue@nextTerm
                                }
                            }
                        }

                        var nextState = byteRunnable.step(requireNotNull(states[stateUpto]).state, label)

                        if (nextState == -1) {
                            if (skipUpto < numSkips) {
                                termOrd = skips[skipOffset + skipUpto]
                            } else {
                                termOrd++
                            }
                            skipUpto = 0
                        } else if (skipUpto < numSkips) {
                            grow()
                            stateUpto++
                            requireNotNull(states[stateUpto]).state = nextState
                            requireNotNull(states[stateUpto]).changeOrd = skips[skipOffset + skipUpto++]
                            requireNotNull(states[stateUpto]).transitionCount =
                                transitionAccessor.getNumTransitions(nextState)
                            transitionAccessor.initTransition(nextState, requireNotNull(states[stateUpto]).transition)
                            requireNotNull(states[stateUpto]).transitionUpto = -1
                            requireNotNull(states[stateUpto]).transitionMax = -1

                            if (stateUpto == termLength) {
                                if (byteRunnable.isAccept(nextState)) {
                                    scratch.bytes = termBytes
                                    scratch.offset = termOffsets[termOrd]
                                    scratch.length = termOffsets[1 + termOrd] - scratch.offset
                                    return scratch
                                } else {
                                    termOrd++
                                    skipUpto = 0
                                }
                            }
                        } else {
                            if (commonSuffixRef != null) {
                                assert(commonSuffixRef.offset == 0)
                                if (termLength < commonSuffixRef.length) {
                                    termOrd++
                                    skipUpto = 0
                                    continue@nextTerm
                                }
                                val offset = termOffset + termLength - commonSuffixRef.length
                                for (suffix in 0..<commonSuffixRef.length) {
                                    if (termBytes[offset + suffix] != commonSuffixRef.bytes[suffix]) {
                                        termOrd++
                                        skipUpto = 0
                                        continue@nextTerm
                                    }
                                }
                            }

                            var upto = stateUpto + 1
                            while (upto < termLength) {
                                nextState = byteRunnable.step(nextState, termBytes[termOffset + upto].toInt() and 0xFF)
                                if (nextState == -1) {
                                    termOrd++
                                    skipUpto = 0
                                    continue@nextTerm
                                }
                                upto++
                            }

                            if (byteRunnable.isAccept(nextState)) {
                                scratch.bytes = termBytes
                                scratch.offset = termOffsets[termOrd]
                                scratch.length = termOffsets[1 + termOrd] - scratch.offset
                                return scratch
                            } else {
                                termOrd++
                                skipUpto = 0
                            }
                        }
                    }
                }

                override fun termState(): TermState {
                    val state = OrdTermState()
                    state.ord = termOrd.toLong()
                    return state
                }

                override fun term(): BytesRef {
                    return scratch
                }

                override fun ord(): Long {
                    return termOrd.toLong()
                }

                override fun docFreq(): Int {
                    return if (terms[termOrd] is LowFreqTerm) {
                        (terms[termOrd] as LowFreqTerm).docFreq
                    } else {
                        requireNotNull((terms[termOrd] as HighFreqTerm).docIDs).size
                    }
                }

                override fun totalTermFreq(): Long {
                    return if (terms[termOrd] is LowFreqTerm) {
                        (terms[termOrd] as LowFreqTerm).totalTermFreq.toLong()
                    } else {
                        (terms[termOrd] as HighFreqTerm).totalTermFreq
                    }
                }

                override fun postings(reuse: PostingsEnum?, flags: Int): PostingsEnum {
                    // TODO: implement reuse
                    // it's hairy!

                    // TODO: the logic of which enum impl to choose should be refactored to be simpler...
                    if (hasPos && PostingsEnum.featureRequested(flags, PostingsEnum.POSITIONS)) {
                        return if (terms[termOrd] is LowFreqTerm) {
                            val term = terms[termOrd] as LowFreqTerm
                            val postings = term.postings
                            val payloads = term.payloads
                            LowFreqPostingsEnum(hasOffsets, hasPayloads).reset(postings, payloads)
                        } else {
                            val term = terms[termOrd] as HighFreqTerm
                            HighFreqPostingsEnum(hasOffsets)
                                .reset(term.docIDs, term.freqs, term.positions, term.payloads)
                        }
                    }

                    return if (terms[termOrd] is LowFreqTerm) {
                        val postings = (terms[termOrd] as LowFreqTerm).postings
                        if (hasFreq) {
                            if (hasPos) {
                                var posLen = if (hasOffsets) 3 else 1
                                if (hasPayloads) {
                                    posLen++
                                }
                                LowFreqDocsEnum(posLen).reset(postings)
                            } else {
                                LowFreqDocsEnumNoPos().reset(postings)
                            }
                        } else {
                            LowFreqDocsEnumNoTF().reset(postings)
                        }
                    } else {
                        val term = terms[termOrd] as HighFreqTerm
                        HighFreqDocsEnum().reset(term.docIDs, term.freqs)
                    }
                }

                override fun impacts(flags: Int): ImpactsEnum {
                    return SlowImpactsEnum(postings(null, flags))
                }

                override fun seekCeil(text: BytesRef): SeekStatus {
                    throw UnsupportedOperationException()
                }

                override fun seekExact(ord: Long) {
                    throw UnsupportedOperationException()
                }
            }
        }

        // Docs only:
        private class LowFreqDocsEnumNoTF : PostingsEnum() {
            private var postings: IntArray? = null
            private var upto: Int = 0

            fun reset(postings: IntArray?): PostingsEnum {
                this.postings = postings
                upto = -1
                return this
            }

            // TODO: can do this w/o setting members?
            override fun nextDoc(): Int {
                upto++
                if (upto < requireNotNull(postings).size) {
                    return requireNotNull(postings)[upto]
                }
                return NO_MORE_DOCS
            }

            override fun docID(): Int {
                return if (upto < 0) {
                    -1
                } else if (upto < requireNotNull(postings).size) {
                    requireNotNull(postings)[upto]
                } else {
                    NO_MORE_DOCS
                }
            }

            override fun freq(): Int {
                return 1
            }

            override fun nextPosition(): Int {
                return -1
            }

            override fun startOffset(): Int {
                return -1
            }

            override fun endOffset(): Int {
                return -1
            }

            override val payload: BytesRef?
                get() = null

            override fun advance(target: Int): Int {
                // Linear scan, but this is low-freq term so it won't
                // be costly:
                return slowAdvance(target)
            }

            override fun cost(): Long {
                return requireNotNull(postings).size.toLong()
            }
        }

        // Docs + freqs:
        private class LowFreqDocsEnumNoPos : PostingsEnum() {
            private var postings: IntArray? = null
            private var upto: Int = 0

            fun reset(postings: IntArray?): PostingsEnum {
                this.postings = postings
                upto = -2
                return this
            }

            // TODO: can do this w/o setting members?
            override fun nextDoc(): Int {
                upto += 2
                if (upto < requireNotNull(postings).size) {
                    return requireNotNull(postings)[upto]
                }
                return NO_MORE_DOCS
            }

            override fun docID(): Int {
                return if (upto < 0) {
                    -1
                } else if (upto < requireNotNull(postings).size) {
                    requireNotNull(postings)[upto]
                } else {
                    NO_MORE_DOCS
                }
            }

            override fun freq(): Int {
                return requireNotNull(postings)[upto + 1]
            }

            override fun nextPosition(): Int {
                return -1
            }

            override fun startOffset(): Int {
                return -1
            }

            override fun endOffset(): Int {
                return -1
            }

            override val payload: BytesRef?
                get() = null

            override fun advance(target: Int): Int {
                // Linear scan, but this is low-freq term so it won't
                // be costly:
                return slowAdvance(target)
            }

            override fun cost(): Long {
                return requireNotNull(postings).size.toLong() / 2
            }
        }

        // Docs + freqs + positions/offsets:
        private class LowFreqDocsEnum(private val posMult: Int) : PostingsEnum() {
            private var postings: IntArray? = null
            private var upto: Int = 0
            private var freq: Int = 0

            fun canReuse(posMult: Int): Boolean {
                return this.posMult == posMult
            }

            fun reset(postings: IntArray?): PostingsEnum {
                this.postings = postings
                upto = -2
                freq = 0
                return this
            }

            // TODO: can do this w/o setting members?
            override fun nextDoc(): Int {
                upto += 2 + freq * posMult
                if (upto < requireNotNull(postings).size) {
                    freq = requireNotNull(postings)[upto + 1]
                    assert(freq > 0)
                    return requireNotNull(postings)[upto]
                }
                return NO_MORE_DOCS
            }

            override fun docID(): Int {
                // TODO: store docID member?
                return if (upto < 0) {
                    -1
                } else if (upto < requireNotNull(postings).size) {
                    requireNotNull(postings)[upto]
                } else {
                    NO_MORE_DOCS
                }
            }

            override fun freq(): Int {
                // TODO: can I do postings[upto+1]?
                return freq
            }

            override fun nextPosition(): Int {
                return -1
            }

            override fun startOffset(): Int {
                return -1
            }

            override fun endOffset(): Int {
                return -1
            }

            override val payload: BytesRef?
                get() = null

            override fun advance(target: Int): Int {
                // Linear scan, but this is low-freq term so it won't
                // be costly:
                return slowAdvance(target)
            }

            override fun cost(): Long {
                // TODO: could do a better estimate
                return requireNotNull(postings).size.toLong() / 2
            }
        }

        private class LowFreqPostingsEnum(
            private val hasOffsets: Boolean,
            private val hasPayloads: Boolean
        ) : PostingsEnum() {
            private var postings: IntArray? = null
            private val posMult: Int =
                if (hasOffsets) {
                    if (hasPayloads) {
                        4
                    } else {
                        3
                    }
                } else if (hasPayloads) {
                    2
                } else {
                    1
                }
            private val payloadValue = BytesRef()
            private var upto: Int = 0
            private var docIDValue: Int = -1
            private var freqValue: Int = 0
            private var skipPositions: Int = 0
            private var pos: Int = -1
            private var startOffsetValue: Int = -1
            private var endOffsetValue: Int = -1
            private var lastPayloadOffset: Int = 0
            private var payloadOffset: Int = 0
            private var payloadLength: Int = 0
            private var payloadBytes: ByteArray? = null

            fun reset(postings: IntArray?, payloadBytes: ByteArray?): PostingsEnum {
                this.postings = postings
                upto = 0
                skipPositions = 0
                pos = -1
                startOffsetValue = -1
                endOffsetValue = -1
                docIDValue = -1
                payloadLength = 0
                payloadOffset = 0
                lastPayloadOffset = 0
                this.payloadBytes = payloadBytes
                return this
            }

            override fun nextDoc(): Int {
                pos = -1
                if (hasPayloads) {
                    for (i in 0..<skipPositions) {
                        upto++
                        if (hasOffsets) {
                            upto += 2
                        }
                        payloadOffset += requireNotNull(postings)[upto++]
                    }
                } else {
                    upto += posMult * skipPositions
                }

                if (upto < requireNotNull(postings).size) {
                    docIDValue = requireNotNull(postings)[upto++]
                    freqValue = requireNotNull(postings)[upto++]
                    skipPositions = freqValue
                    return docIDValue
                }

                docIDValue = NO_MORE_DOCS
                return docIDValue
            }

            override fun docID(): Int {
                return docIDValue
            }

            override fun freq(): Int {
                return freqValue
            }

            override fun nextPosition(): Int {
                assert(skipPositions > 0)
                skipPositions--
                pos = requireNotNull(postings)[upto++]
                if (hasOffsets) {
                    startOffsetValue = requireNotNull(postings)[upto++]
                    endOffsetValue = requireNotNull(postings)[upto++]
                }
                if (hasPayloads) {
                    payloadLength = requireNotNull(postings)[upto++]
                    lastPayloadOffset = payloadOffset
                    payloadOffset += payloadLength
                }
                return pos
            }

            override fun startOffset(): Int {
                return startOffsetValue
            }

            override fun endOffset(): Int {
                return endOffsetValue
            }

            override fun advance(target: Int): Int {
                return slowAdvance(target)
            }

            override val payload: BytesRef?
                get() {
                    return if (payloadLength > 0) {
                        payloadValue.bytes = requireNotNull(payloadBytes)
                        payloadValue.offset = lastPayloadOffset
                        payloadValue.length = payloadLength
                        payloadValue
                    } else {
                        null
                    }
                }

            override fun cost(): Long {
                // TODO: could do a better estimate
                return requireNotNull(postings).size.toLong() / 2
            }
        }

        // Docs + freqs:
        private class HighFreqDocsEnum : PostingsEnum() {
            private var docIDs: IntArray? = null
            private var freqs: IntArray? = null
            private var upto: Int = 0
            private var docIDValue: Int = -1

            fun reset(docIDs: IntArray?, freqs: IntArray?): PostingsEnum {
                this.docIDs = docIDs
                this.freqs = freqs
                docIDValue = -1
                upto = -1
                return this
            }

            override fun nextDoc(): Int {
                upto++
                return try {
                    docIDValue = requireNotNull(docIDs)[upto]
                    docIDValue
                } catch (_: IndexOutOfBoundsException) {
                    docIDValue = NO_MORE_DOCS
                    docIDValue
                }
            }

            override fun docID(): Int {
                return docIDValue
            }

            override fun freq(): Int {
                return if (freqs == null) {
                    1
                } else {
                    requireNotNull(freqs)[upto]
                }
            }

            override fun advance(target: Int): Int {
                upto++
                if (upto == requireNotNull(docIDs).size) {
                    docIDValue = NO_MORE_DOCS
                    return docIDValue
                }

                var inc = 10
                var nextUpto = upto + 10
                val low: Int
                val high: Int
                while (true) {
                    if (nextUpto >= requireNotNull(docIDs).size) {
                        low = nextUpto - inc
                        high = requireNotNull(docIDs).size - 1
                        break
                    }

                    if (target <= requireNotNull(docIDs)[nextUpto]) {
                        low = nextUpto - inc
                        high = nextUpto
                        break
                    }
                    inc *= 2
                    nextUpto += inc
                }

                var lowVar = low
                var highVar = high
                while (true) {
                    if (lowVar > highVar) {
                        upto = lowVar
                        break
                    }

                    val mid = (lowVar + highVar) ushr 1
                    val cmp = requireNotNull(docIDs)[mid] - target

                    if (cmp < 0) {
                        lowVar = mid + 1
                    } else if (cmp > 0) {
                        highVar = mid - 1
                    } else {
                        upto = mid
                        break
                    }
                }

                return if (upto == requireNotNull(docIDs).size) {
                    docIDValue = NO_MORE_DOCS
                    docIDValue
                } else {
                    docIDValue = requireNotNull(docIDs)[upto]
                    docIDValue
                }
            }

            override fun cost(): Long {
                return requireNotNull(docIDs).size.toLong()
            }

            override fun nextPosition(): Int {
                return -1
            }

            override fun startOffset(): Int {
                return -1
            }

            override fun endOffset(): Int {
                return -1
            }

            override val payload: BytesRef?
                get() = null
        }

        // TODO: specialize offsets and not
        private class HighFreqPostingsEnum(private val hasOffsets: Boolean) : PostingsEnum() {
            private var docIDs: IntArray? = null
            private var freqs: IntArray? = null
            private var positions: Array<IntArray?>? = null
            private var payloads: Array<Array<ByteArray?>?>? = null
            private val posJump: Int = if (hasOffsets) 3 else 1
            private var upto: Int = 0
            private var docIDValue: Int = -1
            private var posUpto: Int = 0
            private var curPositions: IntArray? = null
            private val payloadValue = BytesRef()

            fun reset(
                docIDs: IntArray?,
                freqs: IntArray?,
                positions: Array<IntArray?>?,
                payloads: Array<Array<ByteArray?>?>?
            ): PostingsEnum {
                this.docIDs = docIDs
                this.freqs = freqs
                this.positions = positions
                this.payloads = payloads
                upto = -1
                docIDValue = -1
                return this
            }

            override fun nextDoc(): Int {
                upto++
                return if (upto < requireNotNull(docIDs).size) {
                    posUpto = -posJump
                    curPositions = requireNotNull(positions)[upto]
                    docIDValue = requireNotNull(docIDs)[upto]
                    docIDValue
                } else {
                    docIDValue = NO_MORE_DOCS
                    docIDValue
                }
            }

            override fun freq(): Int {
                return requireNotNull(freqs)[upto]
            }

            override fun docID(): Int {
                return docIDValue
            }

            override fun nextPosition(): Int {
                posUpto += posJump
                assert(posUpto < requireNotNull(curPositions).size)
                return requireNotNull(curPositions)[posUpto]
            }

            override fun startOffset(): Int {
                return if (hasOffsets) {
                    requireNotNull(curPositions)[posUpto + 1]
                } else {
                    -1
                }
            }

            override fun endOffset(): Int {
                return if (hasOffsets) {
                    requireNotNull(curPositions)[posUpto + 2]
                } else {
                    -1
                }
            }

            override fun advance(target: Int): Int {
                upto++
                if (upto == requireNotNull(docIDs).size) {
                    docIDValue = NO_MORE_DOCS
                    return docIDValue
                }

                var inc = 10
                var nextUpto = upto + 10
                val low: Int
                val high: Int
                while (true) {
                    if (nextUpto >= requireNotNull(docIDs).size) {
                        low = nextUpto - inc
                        high = requireNotNull(docIDs).size - 1
                        break
                    }

                    if (target <= requireNotNull(docIDs)[nextUpto]) {
                        low = nextUpto - inc
                        high = nextUpto
                        break
                    }
                    inc *= 2
                    nextUpto += inc
                }

                var lowVar = low
                var highVar = high
                while (true) {
                    if (lowVar > highVar) {
                        upto = lowVar
                        break
                    }

                    val mid = (lowVar + highVar) ushr 1
                    val cmp = requireNotNull(docIDs)[mid] - target

                    if (cmp < 0) {
                        lowVar = mid + 1
                    } else if (cmp > 0) {
                        highVar = mid - 1
                    } else {
                        upto = mid
                        break
                    }
                }

                return if (upto == requireNotNull(docIDs).size) {
                    docIDValue = NO_MORE_DOCS
                    docIDValue
                } else {
                    posUpto = -posJump
                    curPositions = requireNotNull(positions)[upto]
                    docIDValue = requireNotNull(docIDs)[upto]
                    docIDValue
                }
            }

            override val payload: BytesRef?
                get() {
                    if (payloads == null) {
                        return null
                    }
                    val payloadBytes =
                        requireNotNull(requireNotNull(payloads)[upto])[
                            posUpto / if (hasOffsets) 3 else 1
                        ]
                    return if (payloadBytes == null) {
                        null
                    } else {
                        payloadValue.bytes = payloadBytes
                        payloadValue.length = payloadBytes.size
                        payloadValue.offset = 0
                        payloadValue
                    }
                }

            override fun cost(): Long {
                return requireNotNull(docIDs).size.toLong()
            }
        }
    }

    // private static final boolean DEBUG = true;

    // TODO: allow passing/wrapping arbitrary postings format?

    override fun fieldsConsumer(state: SegmentWriteState): FieldsConsumer {
        return forName("Lucene101").fieldsConsumer(state)
    }

    override fun fieldsProducer(state: SegmentReadState): FieldsProducer {
        val postings = forName("Lucene101").fieldsProducer(state)
        return if (state.context.context != IOContext.Context.MERGE) {
            try {
                postings.checkIntegrity()
                DirectFields(state, postings, minSkipCount, lowFreqCutoff)
            } finally {
                postings.close()
            }
        } else {
            // Don't load postings for merge:
            postings
        }
    }
}
