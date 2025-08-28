package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.jdkport.assert
import okio.IOException
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermFrequencyAttribute
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.util.BytesRef
import kotlin.math.max


// TODO: break into separate freq and prox writers as
// codecs; make separate container (tii/tis/skip/*) that can
// be configured as any number of files 1..N
internal class FreqProxTermsWriterPerField(
    invertState: FieldInvertState,
    termsHash: TermsHash,
    private val fieldInfo: FieldInfo,
    nextPerField: TermsHashPerField
) : TermsHashPerField(
    if (fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
        2
    else
        1,
    termsHash.intPool,
    termsHash.bytePool,
    termsHash.termBytePool!!,
    termsHash.bytesUsed,
    nextPerField,
    fieldInfo.name,
    fieldInfo.indexOptions
) {
    private var freqProxPostingsArray: FreqProxPostingsArray? = null
    private val fieldState: FieldInvertState = invertState

    val hasFreq: Boolean = indexOptions >= IndexOptions.DOCS_AND_FREQS
    val hasProx: Boolean = indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
    val hasOffsets: Boolean = indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
    var payloadAttribute: PayloadAttribute? = null
    var offsetAttribute: OffsetAttribute? = null
    var termFreqAtt: TermFrequencyAttribute? = null

    /** Set to true if any token had a payload in the current segment.  */
    var sawPayloads: Boolean = false

    @Throws(IOException::class)
    override fun finish() {
        super.finish()
        if (sawPayloads) {
            fieldInfo.setStorePayloads()
        }
    }

    override fun start(field: IndexableField, first: Boolean): Boolean {
        super.start(field, first)
        termFreqAtt = fieldState.termFreqAttribute
        payloadAttribute = fieldState.payloadAttribute
        offsetAttribute = fieldState.offsetAttribute
        return true
    }

    fun writeProx(termID: Int, proxCode: Int) {
        if (payloadAttribute == null) {
            writeVInt(1, proxCode shl 1)
        } else {
            val payload: BytesRef? = payloadAttribute!!.payload
            if (payload != null && payload.length > 0) {
                writeVInt(1, (proxCode shl 1) or 1)
                writeVInt(1, payload.length)
                writeBytes(1, payload.bytes, payload.offset, payload.length)
                sawPayloads = true
            } else {
                writeVInt(1, proxCode shl 1)
            }
        }

        assert(postingsArray === freqProxPostingsArray)
        freqProxPostingsArray!!.lastPositions!![termID] = fieldState.position
    }

    fun writeOffsets(termID: Int, offsetAccum: Int) {
        val startOffset: Int = offsetAccum + offsetAttribute!!.startOffset()
        val endOffset: Int = offsetAccum + offsetAttribute!!.endOffset()
        assert(startOffset - freqProxPostingsArray!!.lastOffsets!![termID] >= 0)
        writeVInt(1, startOffset - freqProxPostingsArray!!.lastOffsets!![termID])
        writeVInt(1, endOffset - startOffset)
        freqProxPostingsArray!!.lastOffsets!![termID] = startOffset
    }

    override fun newTerm(termID: Int, docID: Int) {
        val postings: FreqProxPostingsArray = freqProxPostingsArray!!

        postings.lastDocIDs[termID] = docID
        if (!hasFreq) {
            assert(postings.termFreqs == null)
            postings.lastDocCodes[termID] = docID
            fieldState.maxTermFrequency = max(1, fieldState.maxTermFrequency)
        } else {
            postings.lastDocCodes[termID] = docID shl 1
            postings.termFreqs!![termID] = this.termFreq
            if (hasProx) {
                writeProx(termID, fieldState.position)
                if (hasOffsets) {
                    writeOffsets(termID, fieldState.offset)
                }
            } else {
                assert(!hasOffsets)
            }
            fieldState.maxTermFrequency = max(postings.termFreqs!![termID], fieldState.maxTermFrequency)
        }
        fieldState.uniqueTermCount++
    }

    override fun addTerm(termID: Int, docID: Int) {
        val postings: FreqProxPostingsArray = freqProxPostingsArray!!
        assert(!hasFreq || postings.termFreqs!![termID] > 0)

        if (!hasFreq) {
            assert(postings.termFreqs == null)
            check(!(termFreqAtt != null && termFreqAtt!!.termFrequency != 1)) {
                ("field \""
                        + fieldName
                        + "\": must index term freq while using custom TermFrequencyAttribute")
            }
            if (docID != postings.lastDocIDs[termID]) {
                assert(docID > postings.lastDocIDs[termID])
                writeVInt(0, postings.lastDocCodes[termID])
                postings.lastDocCodes[termID] = docID - postings.lastDocIDs[termID]
                postings.lastDocIDs[termID] = docID
                fieldState.uniqueTermCount++
            }
        } else if (docID != postings.lastDocIDs[termID]) {
            assert(
                docID > postings.lastDocIDs[termID]
            ) { "id: " + docID + " postings ID: " + postings.lastDocIDs[termID] + " termID: " + termID }

            // Term not yet seen in the current doc but previously
            // seen in other doc(s) since the last flush

            // Now that we know doc freq for previous doc,
            // write it & lastDocCode
            if (1 == postings.termFreqs!![termID]) {
                writeVInt(0, postings.lastDocCodes[termID] or 1)
            } else {
                writeVInt(0, postings.lastDocCodes[termID])
                writeVInt(0, postings.termFreqs!![termID])
            }

            // Init freq for the current document
            postings.termFreqs!![termID] = this.termFreq
            fieldState.maxTermFrequency = max(postings.termFreqs!![termID], fieldState.maxTermFrequency)
            postings.lastDocCodes[termID] = (docID - postings.lastDocIDs[termID]) shl 1
            postings.lastDocIDs[termID] = docID
            if (hasProx) {
                writeProx(termID, fieldState.position)
                if (hasOffsets) {
                    postings.lastOffsets!![termID] = 0
                    writeOffsets(termID, fieldState.offset)
                }
            } else {
                assert(!hasOffsets)
            }
            fieldState.uniqueTermCount++
        } else {
            postings.termFreqs!![termID] = Math.addExact(postings.termFreqs!![termID], this.termFreq)
            fieldState.maxTermFrequency = max(fieldState.maxTermFrequency, postings.termFreqs!![termID])
            if (hasProx) {
                writeProx(termID, fieldState.position - postings.lastPositions!![termID])
                if (hasOffsets) {
                    writeOffsets(termID, fieldState.offset)
                }
            }
        }
    }

    private val termFreq: Int
        get() {
            val freq = if (termFreqAtt == null) 1 else termFreqAtt!!.termFrequency
            if (freq != 1) {
                check(!hasProx) {
                    ("field \""
                            + fieldName
                            + "\": cannot index positions while using custom TermFrequencyAttribute")
                }
            }

            return freq
        }

    override fun newPostingsArray() {
        freqProxPostingsArray = postingsArray as FreqProxPostingsArray
    }

    override fun createPostingsArray(size: Int): ParallelPostingsArray {
        val hasFreq = indexOptions >= IndexOptions.DOCS_AND_FREQS
        val hasProx = indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
        val hasOffsets = indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
        return FreqProxPostingsArray(size, hasFreq, hasProx, hasOffsets)
    }

    internal class FreqProxPostingsArray(size: Int, writeFreqs: Boolean, writeProx: Boolean, writeOffsets: Boolean) :
        ParallelPostingsArray(size) {
        var termFreqs: IntArray? = null // # times this term occurs in the current doc
        var lastDocIDs: IntArray // Last docID where this term occurred
        var lastDocCodes: IntArray // Code for prior doc
        var lastPositions: IntArray? = null // Last position where this term occurred
        var lastOffsets: IntArray? = null // Last endOffset where this term occurred

        init {
            if (writeFreqs) {
                termFreqs = IntArray(size)
            }
            lastDocIDs = IntArray(size)
            lastDocCodes = IntArray(size)
            if (writeProx) {
                lastPositions = IntArray(size)
                if (writeOffsets) {
                    lastOffsets = IntArray(size)
                }
            } else {
                assert(!writeOffsets)
            }
        }

        override fun newInstance(size: Int): ParallelPostingsArray {
            return FreqProxPostingsArray(
                size, termFreqs != null, lastPositions != null, lastOffsets != null
            )
        }

        override fun copyTo(toArray: ParallelPostingsArray, numToCopy: Int) {
            assert(toArray is FreqProxPostingsArray)
            val to = toArray as FreqProxPostingsArray

            super.copyTo(toArray, numToCopy)

            System.arraycopy(lastDocIDs, 0, to.lastDocIDs, 0, numToCopy)
            System.arraycopy(lastDocCodes, 0, to.lastDocCodes, 0, numToCopy)
            if (lastPositions != null) {
                checkNotNull(to.lastPositions)
                System.arraycopy(lastPositions!!, 0, to.lastPositions!!, 0, numToCopy)
            }
            if (lastOffsets != null) {
                checkNotNull(to.lastOffsets)
                System.arraycopy(lastOffsets!!, 0, to.lastOffsets!!, 0, numToCopy)
            }
            if (termFreqs != null) {
                checkNotNull(to.termFreqs)
                System.arraycopy(termFreqs!!, 0, to.termFreqs!!, 0, numToCopy)
            }
        }

        override fun bytesPerPosting(): Int {
            var bytes: Int = BYTES_PER_POSTING + 2 * Int.SIZE_BYTES
            if (lastPositions != null) {
                bytes += Int.SIZE_BYTES
            }
            if (lastOffsets != null) {
                bytes += Int.SIZE_BYTES
            }
            if (termFreqs != null) {
                bytes += Int.SIZE_BYTES
            }

            return bytes
        }
    }
}
