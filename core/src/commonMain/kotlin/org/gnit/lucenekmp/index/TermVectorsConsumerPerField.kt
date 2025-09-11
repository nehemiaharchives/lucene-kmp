package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermFrequencyAttribute
import org.gnit.lucenekmp.codecs.TermVectorsWriter
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBlockPool
import okio.IOException
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert

class TermVectorsConsumerPerField(
    invertState: FieldInvertState,
    termsHash: TermVectorsConsumer,
    private val fieldInfo: FieldInfo
) : TermsHashPerField(
    2,
    termsHash.intPool,
    termsHash.bytePool,
    termsHash.termBytePool!!,
    termsHash.bytesUsed,
    null,
    fieldInfo.name,
    fieldInfo.indexOptions
) {
    private var termVectorsPostingsArray: TermVectorsPostingsArray? = null

    private val termsWriter: TermVectorsConsumer = termsHash
    private val fieldState: FieldInvertState = invertState

    private var doVectors = false
    private var doVectorPositions = false
    private var doVectorOffsets = false
    private var doVectorPayloads = false

    private var offsetAttribute: OffsetAttribute? = null
    private var payloadAttribute: PayloadAttribute? = null
    private var termFreqAtt: TermFrequencyAttribute? = null
    private val termBytePool: BytesRefBlockPool = BytesRefBlockPool(termsHash.termBytePool!!)

    private var hasPayloads = false // if enabled, and we actually saw any for this field

    /**
     * Called once per field per document if term vectors are enabled, to write the vectors to
     * RAMOutputStream, which is then quickly flushed to the real term vectors files in the Directory.
     */
    override fun finish() {
        if (!doVectors || numTerms == 0) {
            return
        }
        termsWriter.addFieldToFlush(this)
    }

    @Throws(IOException::class)
    fun finishDocument() {
        if (!doVectors) {
            return
        }

        doVectors = false

        val numPostings: Int = numTerms

        val flushTerm: BytesRef = termsWriter.flushTerm

        assert(numPostings >= 0)

        // This is called once, after inverting all occurrences
        // of a given field in the doc.  At this point we flush
        // our hash into the DocWriter.
        val postings: TermVectorsPostingsArray = termVectorsPostingsArray!!
        val tv: TermVectorsWriter = termsWriter.writer!!

        sortTerms()
        val termIDs: IntArray = getSortedTermIDs()

        tv.startField(fieldInfo, numPostings, doVectorPositions, doVectorOffsets, hasPayloads)

        val posReader: ByteSliceReader? =
            if (doVectorPositions) termsWriter.vectorSliceReaderPos else null
        val offReader: ByteSliceReader? =
            if (doVectorOffsets) termsWriter.vectorSliceReaderOff else null

        for (j in 0..<numPostings) {
            val termID = termIDs[j]
            val freq = postings.freqs[termID]

            // Get BytesRef
            termBytePool.fillBytesRef(flushTerm, postings.textStarts[termID])
            tv.startTerm(flushTerm, freq)

            if (doVectorPositions || doVectorOffsets) {
                if (posReader != null) {
                    initReader(posReader, termID, 0)
                }
                if (offReader != null) {
                    initReader(offReader, termID, 1)
                }
                tv.addProx(freq, posReader, offReader)
            }
            tv.finishTerm()
        }
        tv.finishField()

        reset()

        fieldInfo.setStoreTermVectors()
    }

    override fun start(field: IndexableField, first: Boolean): Boolean {
        super.start(field, first)
        termFreqAtt = fieldState.termFreqAttribute
        assert(field.fieldType().indexOptions() != IndexOptions.NONE)

        if (first) {
            if (numTerms != 0) {
                // Only necessary if previous doc hit a
                // non-aborting exception while writing vectors in
                // this field:
                reset()
            }

            reinitHash()

            hasPayloads = false

            doVectors = field.fieldType().storeTermVectors()

            if (doVectors) {
                doVectorPositions = field.fieldType().storeTermVectorPositions()

                // Somewhat confusingly, unlike postings, you are
                // allowed to index TV offsets without TV positions:
                doVectorOffsets = field.fieldType().storeTermVectorOffsets()

                if (doVectorPositions) {
                    doVectorPayloads = field.fieldType().storeTermVectorPayloads()
                } else {
                    doVectorPayloads = false
                    require(!field.fieldType().storeTermVectorPayloads()) {
                        ("cannot index term vector payloads without term vector positions (field=\""
                                + field.name()
                                + "\")")
                    }
                }
            } else {
                require(!field.fieldType().storeTermVectorOffsets()) {
                    ("cannot index term vector offsets when term vectors are not indexed (field=\""
                            + field.name()
                            + "\")")
                }
                require(!field.fieldType().storeTermVectorPositions()) {
                    ("cannot index term vector positions when term vectors are not indexed (field=\""
                            + field.name()
                            + "\")")
                }
                require(!field.fieldType().storeTermVectorPayloads()) {
                    ("cannot index term vector payloads when term vectors are not indexed (field=\""
                            + field.name()
                            + "\")")
                }
            }
        } else {
            require(doVectors == field.fieldType().storeTermVectors()) {
                ("all instances of a given field name must have the same term vectors settings (storeTermVectors changed for field=\""
                        + field.name()
                        + "\")")
            }
            require(doVectorPositions == field.fieldType().storeTermVectorPositions()) {
                ("all instances of a given field name must have the same term vectors settings (storeTermVectorPositions changed for field=\""
                        + field.name()
                        + "\")")
            }
            require(doVectorOffsets == field.fieldType().storeTermVectorOffsets()) {
                ("all instances of a given field name must have the same term vectors settings (storeTermVectorOffsets changed for field=\""
                        + field.name()
                        + "\")")
            }
            require(doVectorPayloads == field.fieldType().storeTermVectorPayloads()) {
                ("all instances of a given field name must have the same term vectors settings (storeTermVectorPayloads changed for field=\""
                        + field.name()
                        + "\")")
            }
        }

        if (doVectors) {
            if (doVectorOffsets) {
                offsetAttribute = fieldState.offsetAttribute
                checkNotNull(offsetAttribute)
            }

            if (doVectorPayloads) {
                // Can be null:
                payloadAttribute = fieldState.payloadAttribute
            } else {
                payloadAttribute = null
            }
        }

        return doVectors
    }

    fun writeProx(postings: TermVectorsPostingsArray, termID: Int) {
        if (doVectorOffsets) {
            val startOffset: Int = fieldState.offset + offsetAttribute!!.startOffset()
            val endOffset: Int = fieldState.offset + offsetAttribute!!.endOffset()

            writeVInt(1, startOffset - postings.lastOffsets[termID])
            writeVInt(1, endOffset - startOffset)
            postings.lastOffsets[termID] = endOffset
        }

        if (doVectorPositions) {
            val payload = if (payloadAttribute == null) {
                null
            } else {
                payloadAttribute!!.payload
            }

            val pos: Int = fieldState.position - postings.lastPositions[termID]
            if (payload != null && payload.length > 0) {
                writeVInt(0, (pos shl 1) or 1)
                writeVInt(0, payload.length)
                writeBytes(0, payload.bytes, payload.offset, payload.length)
                hasPayloads = true
            } else {
                writeVInt(0, pos shl 1)
            }
            postings.lastPositions[termID] = fieldState.position
        }
    }

    override fun newTerm(termID: Int, docID: Int) {
        val postings: TermVectorsPostingsArray = termVectorsPostingsArray!!

        postings.freqs[termID] = this.termFreq
        postings.lastOffsets[termID] = 0
        postings.lastPositions[termID] = 0

        writeProx(postings, termID)
    }

    override fun addTerm(termID: Int, docID: Int) {
        val postings: TermVectorsPostingsArray = termVectorsPostingsArray!!

        postings.freqs[termID] += this.termFreq

        writeProx(postings, termID)
    }

    private val termFreq: Int
        get() {
            if (termFreqAtt == null) {
                return 1
            }
            val freq: Int = termFreqAtt!!.termFrequency
            if (freq != 1) {
                require(!doVectorPositions) {
                    ("field \""
                            + fieldName
                            + "\": cannot index term vector positions while using custom TermFrequencyAttribute")
                }
                require(!doVectorOffsets) {
                    ("field \""
                            + fieldName
                            + "\": cannot index term vector offsets while using custom TermFrequencyAttribute")
                }
            }

            return freq
        }

    override fun newPostingsArray() {
        termVectorsPostingsArray = postingsArray as? TermVectorsPostingsArray
    }

    override fun createPostingsArray(size: Int): ParallelPostingsArray {
        return TermVectorsPostingsArray(size)
    }

    class TermVectorsPostingsArray(size: Int) : ParallelPostingsArray(size) {
        var freqs: IntArray = IntArray(size) // How many times this term occurred in the current doc
        var lastOffsets: IntArray = IntArray(size) // Last offset we saw
        var lastPositions: IntArray = IntArray(size) // Last position where this term occurred

        override fun newInstance(size: Int): ParallelPostingsArray {
            return TermVectorsPostingsArray(size)
        }

        override fun copyTo(toArray: ParallelPostingsArray, numToCopy: Int) {
            assert(toArray is TermVectorsPostingsArray)
            val to = toArray as TermVectorsPostingsArray

            super.copyTo(toArray, numToCopy)

            System.arraycopy(freqs, 0, to.freqs, 0, size)
            System.arraycopy(lastOffsets, 0, to.lastOffsets, 0, size)
            System.arraycopy(lastPositions, 0, to.lastPositions, 0, size)
        }

        override fun bytesPerPosting(): Int {
            return super.bytesPerPosting() + 3 * Int.SIZE_BYTES
        }
    }
}
