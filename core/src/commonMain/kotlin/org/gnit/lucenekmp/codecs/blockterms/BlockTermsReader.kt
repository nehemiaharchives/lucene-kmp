package org.gnit.lucenekmp.codecs.blockterms

import okio.IOException
import org.gnit.lucenekmp.codecs.blockterms.TermsIndexReaderBase.FieldIndexEnum
import org.gnit.lucenekmp.codecs.BlockTermState
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.PostingsReaderBase
import org.gnit.lucenekmp.index.BaseTermsEnum
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.Cloneable
import org.gnit.lucenekmp.jdkport.TreeMap
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.RamUsageEstimator

/**
 * Handles a terms dict, but decouples all details of doc/freqs/positions reading to an instance of
 * [PostingsReaderBase]. This class is reusable for codecs that use a different format for
 * docs/freqs/positions (though codecs are also free to make their own terms dict impl).
 *
 *
 * This class also interacts with an instance of [TermsIndexReaderBase], to abstract away
 * the specific implementation of the terms dict index.
 *
 * @lucene.experimental
 */
class BlockTermsReader(
    indexReader: TermsIndexReaderBase,
    // Reads the terms dict entries, to gather state to
    // produce DocsEnum on demand
    private val postingsReader: PostingsReaderBase,
    state: SegmentReadState
) : FieldsProducer() {
    // Open input to the main terms dict file (_X.tis)
    private val `in`: IndexInput

    private val fields: TreeMap<String, FieldReader> = TreeMap<String, FieldReader>()

    // Reads the terms index
    private var indexReader: TermsIndexReaderBase?

    // Used as key for the terms cache
    private class FieldAndTerm : Cloneable<FieldAndTerm> {
        var field: String? = null
        var term: BytesRef? = null

        constructor()

        constructor(other: FieldAndTerm) {
            field = other.field
            term = BytesRef.deepCopyOf(other.term!!)
        }

        override fun equals(_other: Any?): Boolean {
            val other = _other as FieldAndTerm
            return other.field == field && term!!.bytesEquals(other.term!!)
        }

        override fun clone(): FieldAndTerm {
            return FieldAndTerm(this)
        }

        override fun hashCode(): Int {
            return field.hashCode() * 31 + term.hashCode()
        }
    }

    @Throws(IOException::class)
    private fun seekDir(input: IndexInput) {
        input.seek(input.length() - CodecUtil.footerLength() - 8)
        val dirOffset: Long = input.readLong()
        input.seek(dirOffset)
    }

    override fun close() {
        try {
            try {
                IOUtils.close(indexReader)
            } finally {
                // null so if an app hangs on to us (ie, we are not
                // GCable, despite being closed) we still free most
                // ram
                indexReader = null
                IOUtils.close(`in`)
            }
        } finally {
            IOUtils.close(postingsReader)
        }
    }

    override fun iterator(): MutableIterator<String> {
        return fields.keys.iterator()
    }

    @Throws(IOException::class)
    override fun terms(field: String?): Terms? {
        checkNotNull(field)
        return fields[field]
    }

    override fun size(): Int {
        return fields.size
    }

    init {

        val filename: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name, state.segmentSuffix, BlockTermsWriter.TERMS_EXTENSION
            )
        `in` = state.directory.openInput(filename, state.context)

        var success = false
        try {
            CodecUtil.checkIndexHeader(
                `in`,
                BlockTermsWriter.CODEC_NAME,
                BlockTermsWriter.VERSION_START,
                BlockTermsWriter.VERSION_CURRENT,
                state.segmentInfo.getId(),
                state.segmentSuffix
            )

            // Have PostingsReader init itself
            postingsReader.init(`in`, state)

            // NOTE: data file is too costly to verify checksum against all the bytes on open,
            // but for now we at least verify proper structure of the checksum footer: which looks
            // for FOOTER_MAGIC + algorithmID. This is cheap and can detect some forms of corruption
            // such as file truncation.
            CodecUtil.retrieveChecksum(`in`)

            // Read per-field details
            seekDir(`in`)

            val numFields: Int = `in`.readVInt()
            if (numFields < 0) {
                throw CorruptIndexException(
                    "invalid number of fields: $numFields",
                    `in`
                )
            }
            for (i in 0..<numFields) {
                val field: Int = `in`.readVInt()
                val numTerms: Long = `in`.readVLong()
                assert(numTerms >= 0)
                val termsStartPointer: Long = `in`.readVLong()
                val fieldInfo: FieldInfo = state.fieldInfos.fieldInfo(field)!!
                val sumTotalTermFreq: Long = `in`.readVLong()
                // when frequencies are omitted, sumDocFreq=totalTermFreq and we only write one value
                val sumDocFreq =
                    if (fieldInfo.indexOptions == IndexOptions.DOCS) sumTotalTermFreq else `in`.readVLong()
                val docCount: Int = `in`.readVInt()
                if (docCount < 0
                    || docCount > state.segmentInfo.maxDoc()
                ) { // #docs with field must be <= #docs
                    throw CorruptIndexException(
                        "invalid docCount: " + docCount + " maxDoc: " + state.segmentInfo.maxDoc(),
                        `in`
                    )
                }
                if (sumDocFreq < docCount) { // #postings must be >= #docs with field
                    throw CorruptIndexException(
                        "invalid sumDocFreq: $sumDocFreq docCount: $docCount", `in`
                    )
                }
                if (sumTotalTermFreq < sumDocFreq) { // #positions must be >= #postings
                    throw CorruptIndexException(
                        "invalid sumTotalTermFreq: $sumTotalTermFreq sumDocFreq: $sumDocFreq",
                        `in`
                    )
                }
                val previous: FieldReader? =
                    fields.put(
                        fieldInfo.name,
                        FieldReader(
                            fieldInfo,
                            numTerms,
                            termsStartPointer,
                            sumTotalTermFreq,
                            sumDocFreq,
                            docCount
                        )
                    )
                if (previous != null) {
                    throw CorruptIndexException(
                        "duplicate fields: " + fieldInfo.name,
                        `in`
                    )
                }
            }
            success = true
        } finally {
            if (!success) {
                `in`.close()
            }
        }

        this.indexReader = indexReader
    }

    private inner class FieldReader(
        fieldInfo: FieldInfo,
        numTerms: Long,
        termsStartPointer: Long,
        sumTotalTermFreq: Long,
        sumDocFreq: Long,
        docCount: Int
    ) : Terms(), Accountable {
        val numTerms: Long
        val fieldInfo: FieldInfo
        val termsStartPointer: Long
        override val sumTotalTermFreq: Long

        override val sumDocFreq: Long

        override val docCount: Int

        init {
            assert(numTerms > 0)
            this.fieldInfo = fieldInfo
            this.numTerms = numTerms
            this.termsStartPointer = termsStartPointer
            this.sumTotalTermFreq = sumTotalTermFreq
            this.sumDocFreq = sumDocFreq
            this.docCount = docCount
        }

        override fun ramBytesUsed(): Long {
            return FIELD_READER_RAM_BYTES_USED
        }

        @Throws(IOException::class)
        override fun iterator(): TermsEnum {
            return SegmentTermsEnum()
        }

        override fun hasFreqs(): Boolean {
            return fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS
        }

        override fun hasOffsets(): Boolean {
            return (fieldInfo
                .indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        }

        override fun hasPositions(): Boolean {
            return fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
        }

        override fun hasPayloads(): Boolean {
            return fieldInfo.hasPayloads()
        }

        override fun size(): Long {
            return numTerms
        }

        // Iterates through terms in this field
        private inner class SegmentTermsEnum : BaseTermsEnum() {
            private val `in`: IndexInput = this@BlockTermsReader.`in`.clone()
            private val state: BlockTermState
            private val doOrd: Boolean
            private val fieldTerm = FieldAndTerm()
            private val indexEnum: FieldIndexEnum
            private val term: BytesRefBuilder =
                BytesRefBuilder()

            /* This is true if indexEnum is "still" seek'd to the index term
      for the current term. We set it to true on seeking, and then it
      remains valid until next() is called enough times to load another
      terms block: */
            private var indexIsCurrent = false

            /* True if we've already called .next() on the indexEnum, to "bracket"
      the current block of terms: */
            private var didIndexNext = false

            /* Next index term, bracketing the current block of terms; this is
      only valid if didIndexNext is true: */
            private var nextIndexTerm: BytesRef? = null

            /* True after seekExact(TermState), do defer seeking.  If the app then
      calls next() (which is not "typical"), then we'll do the real seek */
            private var seekPending = false

            private var termSuffixes: ByteArray
            private val termSuffixesReader: ByteArrayDataInput =
                ByteArrayDataInput()

            /* Common prefix used for all terms in this block. */
            private var termBlockPrefix = 0

            /* How many terms in current block */
            private var blockTermCount = 0

            private var docFreqBytes: ByteArray
            private val freqReader: ByteArrayDataInput =
                ByteArrayDataInput()
            private var metaDataUpto = 0

            private var bytes: ByteArray? = null
            private var bytesReader: ByteArrayDataInput? = null

            init {
                `in`.seek(termsStartPointer)
                indexEnum = indexReader!!.getFieldEnum(fieldInfo)!!
                doOrd = indexReader!!.supportsOrd()
                fieldTerm.field = fieldInfo.name
                state = postingsReader.newTermState()
                state.totalTermFreq = -1
                state.ord = -1

                termSuffixes = ByteArray(128)
                docFreqBytes = ByteArray(64)
                // System.out.println("BTR.enum init this=" + this + " postingsReader=" + postingsReader);
            }

            // TODO: we may want an alternate mode here which is
            // "if you are about to return NOT_FOUND I won't use
            // the terms data from that"; eg FuzzyTermsEnum will
            // (usually) just immediately call seek again if we
            // return NOT_FOUND so it's a waste for us to fill in
            // the term that was actually NOT_FOUND
            @Throws(IOException::class)
            override fun seekCeil(target: BytesRef): SeekStatus {
                checkNotNull(indexEnum) { "terms index was not loaded" }

                // System.out.println("BTR.seek seg=" + segment + " target=" + fieldInfo.name + ":" +
                // target.utf8ToString() + " " + target + " current=" + term().utf8ToString() + " " + term()
                // + " indexIsCurrent=" + indexIsCurrent + " didIndexNext=" + didIndexNext + " seekPending="
                // + seekPending + " divisor=" + indexReader.getDivisor() + " this="  + this);
                // if (didIndexNext) {
                //  if (nextIndexTerm == null) {
                //    // System.out.println("  nextIndexTerm=null");
                //  } else {
                //    // System.out.println("  nextIndexTerm=" + nextIndexTerm.utf8ToString());
                //  }
                // }
                var doSeek = true

                // See if we can avoid seeking, because target term
                // is after current term but before next index term:
                if (indexIsCurrent) {
                    val cmp = term.get().compareTo(target)

                    if (cmp == 0) {
                        // Already at the requested term
                        return SeekStatus.FOUND
                    } else if (cmp < 0) {
                        // Target term is after current term

                        if (!didIndexNext) {
                            if (indexEnum.next() == -1L) {
                                nextIndexTerm = null
                            } else {
                                nextIndexTerm = indexEnum.term()
                            }
                            // System.out.println("  now do index next() nextIndexTerm=" + (nextIndexTerm == null
                            //  "null" : nextIndexTerm.utf8ToString()));
                            didIndexNext = true
                        }

                        if (nextIndexTerm == null || target < nextIndexTerm!!) {
                            // Optimization: requested term is within the
                            // same term block we are now in; skip seeking
                            // (but do scanning):
                            doSeek = false
                            // System.out.println("  skip seek: nextIndexTerm=" + (nextIndexTerm == null  "null"
                            // : nextIndexTerm.utf8ToString()));
                        }
                    }
                }

                if (doSeek) {
                    // System.out.println("  seek");

                    // Ask terms index to find biggest indexed term (=
                    // first term in a block) that's <= our text:

                    `in`.seek(indexEnum.seek(target))
                    val result = nextBlock()

                    // Block must exist since, at least, the indexed term
                    // is in the block:
                    assert(result)

                    indexIsCurrent = true
                    didIndexNext = false

                    if (doOrd) {
                        state.ord = indexEnum.ord() - 1
                    }

                    term.copyBytes(indexEnum.term()!!)
                    // System.out.println("  seek: term=" + term.utf8ToString());
                } else {
                    // System.out.println("  skip seek");
                    if (state.termBlockOrd == blockTermCount && !nextBlock()) {
                        indexIsCurrent = false
                        return SeekStatus.END
                    }
                }

                seekPending = false

                var common = 0

                // Scan within block.  We could do this by calling
                // _next() and testing the resulting term, but this
                // is wasteful.  Instead, we first confirm the
                // target matches the common prefix of this block,
                // and then we scan the term bytes directly from the
                // termSuffixesreader's byte[], saving a copy into
                // the BytesRef term per term.  Only when we return
                // do we then copy the bytes into the term.
                while (true) {
                    // First, see if target term matches common prefix
                    // in this block:

                    if (common < termBlockPrefix) {
                        val cmp: Int =
                            (term.byteAt(common)
                                .toInt() and 0xFF) - (target.bytes[target.offset + common].toInt() and 0xFF)
                        if (cmp < 0) {
                            // TODO: maybe we should store common prefix
                            // in block header  (instead of relying on
                            // last term of previous block)

                            // Target's prefix is after the common block
                            // prefix, so term cannot be in this block
                            // but it could be in next block.  We
                            // must scan to end-of-block to set common
                            // prefix for next block:

                            if (state.termBlockOrd < blockTermCount) {
                                while (state.termBlockOrd < blockTermCount - 1) {
                                    state.termBlockOrd++
                                    state.ord++
                                    termSuffixesReader.skipBytes(
                                        termSuffixesReader.readVInt().toLong()
                                    )
                                }
                                val suffix: Int = termSuffixesReader.readVInt()
                                term.setLength(termBlockPrefix + suffix)
                                term.grow(term.length())
                                termSuffixesReader.readBytes(term.bytes(), termBlockPrefix, suffix)
                            }
                            state.ord++

                            if (!nextBlock()) {
                                indexIsCurrent = false
                                return SeekStatus.END
                            }
                            common = 0
                        } else if (cmp > 0) {
                            // Target's prefix is before the common prefix
                            // of this block, so we position to start of
                            // block and return NOT_FOUND:
                            assert(state.termBlockOrd == 0)

                            val suffix: Int = termSuffixesReader.readVInt()
                            term.setLength(termBlockPrefix + suffix)
                            term.grow(term.length())
                            termSuffixesReader.readBytes(term.bytes(), termBlockPrefix, suffix)
                            return SeekStatus.NOT_FOUND
                        } else {
                            common++
                        }

                        continue
                    }

                    // Test every term in this block
                    while (true) {
                        state.termBlockOrd++
                        state.ord++

                        val suffix: Int = termSuffixesReader.readVInt()

                        // We know the prefix matches, so just compare the new suffix:
                        val termLen = termBlockPrefix + suffix
                        var bytePos: Int = termSuffixesReader.position

                        var next = false
                        val limit: Int =
                            target.offset + (if (termLen < target.length) termLen else target.length)
                        var targetPos: Int = target.offset + termBlockPrefix
                        while (targetPos < limit) {
                            val cmp =
                                (termSuffixes[bytePos++].toInt() and 0xFF) - (target.bytes[targetPos++].toInt() and 0xFF)
                            if (cmp < 0) {
                                // Current term is still before the target;
                                // keep scanning
                                next = true
                                break
                            } else if (cmp > 0) {
                                // Done!  Current term is after target. Stop
                                // here, fill in real term, return NOT_FOUND.
                                term.setLength(termBlockPrefix + suffix)
                                term.grow(term.length())
                                termSuffixesReader.readBytes(term.bytes(), termBlockPrefix, suffix)
                                // System.out.println("  NOT_FOUND");
                                return SeekStatus.NOT_FOUND
                            }
                        }

                        if (!next && target.length <= termLen) {
                            term.setLength(termBlockPrefix + suffix)
                            term.grow(term.length())
                            termSuffixesReader.readBytes(term.bytes(), termBlockPrefix, suffix)

                            if (target.length == termLen) {
                                // Done!  Exact match.  Stop here, fill in
                                // real term, return FOUND.
                                // System.out.println("  FOUND");
                                return SeekStatus.FOUND
                            } else {
                                // System.out.println("  NOT_FOUND");
                                return SeekStatus.NOT_FOUND
                            }
                        }

                        if (state.termBlockOrd == blockTermCount) {
                            // Must pre-fill term for next block's common prefix
                            term.setLength(termBlockPrefix + suffix)
                            term.grow(term.length())
                            termSuffixesReader.readBytes(term.bytes(), termBlockPrefix, suffix)
                            break
                        } else {
                            termSuffixesReader.skipBytes(suffix.toLong())
                        }
                    }

                    // The purpose of the terms dict index is to seek
                    // the enum to the closest index term before the
                    // term we are looking for.  So, we should never
                    // cross another index term (besides the first
                    // one) while we are scanning:
                    assert(indexIsCurrent)

                    if (!nextBlock()) {
                        // System.out.println("  END");
                        indexIsCurrent = false
                        return SeekStatus.END
                    }
                    common = 0
                }
            }

            @Throws(IOException::class)
            override fun next(): BytesRef? {
                // System.out.println("BTR.next() seekPending=" + seekPending + " pendingSeekCount=" +
                // state.termBlockOrd);

                // If seek was previously called and the term was cached,
                // usually caller is just going to pull a D/&PEnum or get
                // docFreq, etc.  But, if they then call next(),
                // this method catches up all internal state so next()
                // works properly:

                if (seekPending) {
                    assert(!indexIsCurrent)
                    `in`.seek(state.blockFilePointer)
                    val pendingSeekCount: Int = state.termBlockOrd
                    val result = nextBlock()

                    val savOrd: Long = state.ord

                    // Block must exist since seek(TermState) was called w/ a
                    // TermState previously returned by this enum when positioned
                    // on a real term:
                    assert(result)

                    while (state.termBlockOrd < pendingSeekCount) {
                        val nextResult: BytesRef = checkNotNull(_next())
                    }
                    seekPending = false
                    state.ord = savOrd
                }
                return _next()
            }

            /* Decodes only the term bytes of the next term.  If caller then asks for
      metadata, ie docFreq, totalTermFreq or pulls a D/&PEnum, we then (lazily)
      decode all metadata up to the current term. */
            @Throws(IOException::class)
            fun _next(): BytesRef? {
                // System.out.println("BTR._next seg=" + segment + " this=" + this + " termCount=" +
                // state.termBlockOrd + " (vs " + blockTermCount + ")");
                if (state.termBlockOrd == blockTermCount && !nextBlock()) {
                    // System.out.println("  eof");
                    indexIsCurrent = false
                    return null
                }

                // TODO: cutover to something better for these ints!  simple64
                val suffix: Int = termSuffixesReader.readVInt()

                // System.out.println("  suffix=" + suffix);
                term.setLength(termBlockPrefix + suffix)
                term.grow(term.length())
                termSuffixesReader.readBytes(term.bytes(), termBlockPrefix, suffix)
                state.termBlockOrd++

                // NOTE: meaningless in the non-ord case
                state.ord++

                // System.out.println("  return term=" + fieldInfo.name + ":" + term.utf8ToString() + " " +
                // term + " tbOrd=" + state.termBlockOrd);
                return term.get()
            }

            override fun term(): BytesRef {
                return term.get()
            }

            @Throws(IOException::class)
            override fun docFreq(): Int {
                // System.out.println("BTR.docFreq");
                decodeMetaData()
                // System.out.println("  return " + state.docFreq);
                return state.docFreq
            }

            @Throws(IOException::class)
            override fun totalTermFreq(): Long {
                decodeMetaData()
                return state.totalTermFreq
            }

            @Throws(IOException::class)
            override fun postings(
                reuse: PostingsEnum?,
                flags: Int
            ): PostingsEnum {
                // System.out.println("BTR.docs this=" + this);
                decodeMetaData()
                // System.out.println("BTR.docs:  state.docFreq=" + state.docFreq);
                return postingsReader.postings(fieldInfo, state, reuse, flags)
            }

            @Throws(IOException::class)
            override fun impacts(flags: Int): ImpactsEnum {
                decodeMetaData()
                return postingsReader.impacts(fieldInfo, state, flags)
            }

            override fun seekExact(
                target: BytesRef,
                otherState: TermState
            ) {
                // System.out.println("BTR.seekExact termState target=" + target.utf8ToString() + " " +
                // target + " this=" + this);
                assert(otherState != null && otherState is BlockTermState)
                assert(!doOrd || (otherState as BlockTermState).ord < numTerms)
                state.copyFrom(otherState)
                seekPending = true
                indexIsCurrent = false
                term.copyBytes(target)
            }

            @Throws(IOException::class)
            override fun termState(): TermState {
                // System.out.println("BTR.termState this=" + this);
                decodeMetaData()
                val ts: TermState = state.clone()
                // System.out.println("  return ts=" + ts);
                return ts
            }

            @Throws(IOException::class)
            override fun seekExact(ord: Long) {
                // System.out.println("BTR.seek by ord ord=" + ord);
                //checkNotNull(indexEnum) { "terms index was not loaded" }

                assert(ord < numTerms)

                // TODO: if ord is in same terms block and
                // after current ord, we should avoid this seek just
                // like we do in the seek(BytesRef) case
                `in`.seek(indexEnum.seek(ord))
                val result = nextBlock()

                // Block must exist since ord < numTerms:
                assert(result)

                indexIsCurrent = true
                didIndexNext = false
                seekPending = false

                state.ord = indexEnum.ord() - 1
                assert(state.ord >= -1) { "ord=" + state.ord }
                term.copyBytes(indexEnum.term()!!)

                // Now, scan:
                var left = (ord - state.ord).toInt()
                while (left > 0) {
                    val term: BytesRef = checkNotNull(_next())
                    left--
                    assert(indexIsCurrent)
                }
            }

            override fun ord(): Long {
                if (!doOrd) {
                    throw UnsupportedOperationException()
                }
                return state.ord
            }

            /* Does initial decode of next block of terms; this
      doesn't actually decode the docFreq, totalTermFreq,
      postings details (frq/prx offset, etc.) metadata;
      it just loads them as byte[] blobs which are then
      decoded on-demand if the metadata is ever requested
      for any term in this block.  This enables terms-only
      intensive consumes (eg certain MTQs, respelling) to
      not pay the price of decoding metadata they won't
      use. */
            @Throws(IOException::class)
            fun nextBlock(): Boolean {
                // TODO: we still lazy-decode the byte[] for each
                // term (the suffix), but, if we decoded
                // all N terms up front then seeking could do a fast
                // bsearch w/in the block...

                // System.out.println("BTR.nextBlock() fp=" + in.filePointer + " this=" + this);

                state.blockFilePointer = `in`.filePointer
                blockTermCount = `in`.readVInt()
                // System.out.println("  blockTermCount=" + blockTermCount);
                if (blockTermCount == 0) {
                    return false
                }
                termBlockPrefix = `in`.readVInt()

                // term suffixes:
                var len: Int = `in`.readVInt()
                if (termSuffixes.size < len) {
                    termSuffixes = ByteArray(ArrayUtil.oversize(len, 1))
                }
                // System.out.println("  termSuffixes len=" + len);
                `in`.readBytes(termSuffixes, 0, len)
                termSuffixesReader.reset(termSuffixes, 0, len)

                // docFreq, totalTermFreq
                len = `in`.readVInt()
                if (docFreqBytes.size < len) {
                    docFreqBytes = ByteArray(ArrayUtil.oversize(len, 1))
                }
                // System.out.println("  freq bytes len=" + len);
                `in`.readBytes(docFreqBytes, 0, len)
                freqReader.reset(docFreqBytes, 0, len)

                // metadata
                len = `in`.readVInt()
                if (bytes == null) {
                    bytes = ByteArray(ArrayUtil.oversize(len, 1))
                    bytesReader = ByteArrayDataInput()
                } else if (bytes!!.size < len) {
                    bytes = ByteArray(ArrayUtil.oversize(len, 1))
                }
                `in`.readBytes(bytes!!, 0, len)
                bytesReader!!.reset(bytes!!, 0, len)

                metaDataUpto = 0
                state.termBlockOrd = 0

                indexIsCurrent = false

                // System.out.println("  indexIsCurrent=" + indexIsCurrent);
                return true
            }

            @Throws(IOException::class)
            fun decodeMetaData() {
                // System.out.println("BTR.decodeMetadata mdUpto=" + metaDataUpto + " vs termCount=" +
                // state.termBlockOrd + " state=" + state);
                if (!seekPending) {
                    // TODO: cutover to random-access API
                    // here.... really stupid that we have to decode N
                    // wasted term metadata just to get to the N+1th
                    // that we really need...

                    // lazily catch up on metadata decode:

                    val limit: Int = state.termBlockOrd
                    var absolute = metaDataUpto == 0
                    // TODO: better API would be "jump straight to term=N"
                    while (metaDataUpto < limit) {
                        // System.out.println("  decode mdUpto=" + metaDataUpto);
                        // TODO: we could make "tiers" of metadata, ie,
                        // decode docFreq/totalTF but don't decode postings
                        // metadata; this way caller could get
                        // docFreq/totalTF w/o paying decode cost for
                        // postings

                        // TODO: if docFreq were bulk decoded we could
                        // just skipN here:

                        // docFreq, totalTermFreq

                        state.docFreq = freqReader.readVInt()
                        // System.out.println("    dF=" + state.docFreq);
                        if (fieldInfo.indexOptions == IndexOptions.DOCS) {
                            state.totalTermFreq = state.docFreq.toLong() // all postings have tf=1
                        } else {
                            state.totalTermFreq = state.docFreq + freqReader.readVLong()
                            // System.out.println("    totTF=" + state.totalTermFreq);
                        }
                        // metadata
                        postingsReader.decodeTerm(bytesReader!!, fieldInfo, state, absolute)
                        metaDataUpto++
                        absolute = false
                    }
                } else {
                    // System.out.println("  skip! seekPending");
                }
            }
        }
    }

    override fun toString(): String {
        return (this::class.simpleName
                + "(index="
                + indexReader
                + ",delegate="
                + postingsReader
                + ")")
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        // verify terms
        CodecUtil.checksumEntireFile(`in`)

        // verify postings
        postingsReader.checkIntegrity()
    }

    companion object {
        private val FIELD_READER_RAM_BYTES_USED: Long =
            RamUsageEstimator.shallowSizeOfInstance(
                FieldReader::class
            )
    }
}
