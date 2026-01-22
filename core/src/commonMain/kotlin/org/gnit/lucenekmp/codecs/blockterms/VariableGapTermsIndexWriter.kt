package org.gnit.lucenekmp.codecs.blockterms

import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.TermStats
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.util.fst.FSTCompiler
import org.gnit.lucenekmp.util.fst.PositiveIntOutputs
import org.gnit.lucenekmp.util.fst.Util
import kotlin.math.min


/**
 * Selects index terms according to provided pluggable [IndexTermSelector], and stores them in
 * a prefix trie that's loaded entirely in RAM stored as an FST. This terms index only supports
 * unsigned byte term sort order (unicode codepoint order when the bytes are UTF8).
 *
 * @lucene.experimental
 */
class VariableGapTermsIndexWriter(
    state: SegmentWriteState,
    private val policy: IndexTermSelector
) : TermsIndexWriterBase() {
    protected var metaOut: IndexOutput? = null
    protected var out: IndexOutput? = null

    @Suppress("unused")
    private val fieldInfos: FieldInfos? = state.fieldInfos // unread

    /**
     * Hook for selecting which terms should be placed in the terms index.
     *
     *
     * [.newField] is called at the start of each new field, and [.isIndexTerm] for
     * each term in that field.
     *
     * @lucene.experimental
     */
    abstract class IndexTermSelector {
        /**
         * Called sequentially on every term being written, returning true if this term should be
         * indexed
         */
        abstract fun isIndexTerm(
            term: BytesRef,
            stats: TermStats
        ): Boolean

        /** Called when a new field is started.  */
        abstract fun newField(fieldInfo: FieldInfo)
    }

    /** Same policy as [FixedGapTermsIndexWriter]  */
    class EveryNTermSelector(private val interval: Int) : IndexTermSelector() {
        private var count: Int

        init {
            // First term is first indexed term:
            count = interval
        }

        override fun isIndexTerm(
            term: BytesRef,
            stats: TermStats
        ): Boolean {
            if (count >= interval) {
                count = 1
                return true
            } else {
                count++
                return false
            }
        }

        override fun newField(fieldInfo: FieldInfo) {
            count = interval
        }
    }

    /**
     * Sets an index term when docFreq &gt;= docFreqThresh, or every interval terms. This should
     * reduce seek time to high docFreq terms.
     */
    class EveryNOrDocFreqTermSelector(private val docFreqThresh: Int, private val interval: Int) :
        IndexTermSelector() {
        private var count: Int

        init {
            // First term is first indexed term:
            count = interval
        }

        override fun isIndexTerm(
            term: BytesRef,
            stats: TermStats
        ): Boolean {
            if (stats.docFreq >= docFreqThresh || count >= interval) {
                count = 1
                return true
            } else {
                count++
                return false
            }
        }

        override fun newField(fieldInfo: FieldInfo) {
            count = interval
        }
    }

    // TODO: it'd be nice to let the FST builder prune based
    // on term count of each node (the prune1/prune2 that it
    // accepts), and build the index based on that.  This
    // should result in a more compact terms index, more like
    // a prefix trie than the other selectors, because it
    // only stores enough leading bytes to get down to N
    // terms that may complete that prefix.  It becomes
    // "deeper" when terms are dense, and "shallow" when they
    // are less dense.
    //
    // However, it's not easy to make that work this this
    // API, because that pruning doesn't immediately know on
    // seeing each term whether that term will be a seek point
    // or not.  It requires some non-causality in the API, ie
    // only on seeing some number of future terms will the
    // builder decide which past terms are seek points.
    // Somehow the API'd need to be able to return a "I don't
    // know" value, eg like a Future, which only later on is
    // flipped (frozen) to true or false.
    //
    // We could solve this with a 2-pass approach, where the
    // first pass would build an FSA (no outputs) solely to
    // determine which prefixes are the 'leaves' in the
    // pruning. The 2nd pass would then look at this prefix
    // trie to mark the seek points and build the FST mapping
    // to the true output.
    //
    // But, one downside to this approach is that it'd result
    // in uneven index term selection.  EG with prune1=10, the
    // resulting index terms could be as frequent as every 10
    // terms or as rare as every <maxArcCount> * 10 (eg 2560),
    // in the extremes.
    init {

        val metaFileName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name, state.segmentSuffix, TERMS_META_EXTENSION
            )
        val indexFileName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name, state.segmentSuffix, TERMS_INDEX_EXTENSION
            )

        var success = false
        try {
            metaOut = state.directory.createOutput(metaFileName, state.context)
            out = state.directory.createOutput(indexFileName, state.context)
            CodecUtil.writeIndexHeader(
                metaOut!!,
                META_CODEC_NAME,
                VERSION_CURRENT,
                state.segmentInfo.getId(),
                state.segmentSuffix
            )
            CodecUtil.writeIndexHeader(
                out!!, CODEC_NAME, VERSION_CURRENT, state.segmentInfo.getId(), state.segmentSuffix
            )
            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(this)
            }
        }
    }

    @Throws(IOException::class)
    override fun addField(
        field: FieldInfo,
        termsFilePointer: Long
    ): FieldWriter { /* System.out.println(
        "VGW: field=" + field.name
    ); */
        policy.newField(field)
        return FSTFieldWriter(field, termsFilePointer)
    }

    /**
     * NOTE: if your codec does not sort in unicode code point order, you must override this method,
     * to simply return indexedTerm.length.
     */
    protected fun indexedTermPrefixLength(
        priorTerm: BytesRef,
        indexedTerm: BytesRef
    ): Int {
        // As long as codec sorts terms in unicode codepoint
        // order, we can safely strip off the non-distinguishing
        // suffix to save RAM in the loaded terms index.
        val idxTermOffset: Int = indexedTerm.offset
        val priorTermOffset: Int = priorTerm.offset
        val limit: Int = min(priorTerm.length, indexedTerm.length)
        for (byteIdx in 0..<limit) {
            if (priorTerm.bytes[priorTermOffset + byteIdx]
                != indexedTerm.bytes[idxTermOffset + byteIdx]
            ) {
                return byteIdx + 1
            }
        }
        return min(1 + priorTerm.length, indexedTerm.length)
    }

    private inner class FSTFieldWriter(
        val fieldInfo: FieldInfo,
        termsFilePointer: Long
    ) : FieldWriter() {
        private val fstCompiler: FSTCompiler<Long>
        private val fstOutputs: PositiveIntOutputs = PositiveIntOutputs.singleton
        private val startTermsFilePointer: Long

        var fst: FST<Long>? = null

        private val lastTerm: BytesRefBuilder =
            BytesRefBuilder()
        private var first = true

        @Throws(IOException::class)
        override fun checkIndexTerm(
            text: BytesRef,
            stats: TermStats
        ): Boolean {
            // System.out.println("VGW: index term=" + text.utf8ToString());
            // NOTE: we must force the first term per field to be
            // indexed, in case policy doesn't:
            if (policy.isIndexTerm(text, stats) || first) {
                first = false
                // System.out.println("  YES");
                return true
            } else {
                lastTerm.copyBytes(text)
                return false
            }
        }

        private val scratchIntsRef: IntsRefBuilder = IntsRefBuilder()

        init {
            fstCompiler = FSTCompiler.Builder<Long>(
                FST.INPUT_TYPE.BYTE1,
                fstOutputs
            ).build()

            /* println("VGW: field=" + fieldInfo.name); */

            // Always put empty string in
            fstCompiler.add(IntsRef(), termsFilePointer)
            startTermsFilePointer = termsFilePointer
        }

        @Throws(IOException::class)
        override fun add(
            text: BytesRef,
            stats: TermStats,
            termsFilePointer: Long
        ) {
            if (text.length == 0) {
                // We already added empty string in ctor
                assert(termsFilePointer == startTermsFilePointer)
                return
            }
            val lengthSave: Int = text.length
            text.length = indexedTermPrefixLength(lastTerm.get(), text)
            try {
                fstCompiler.add(
                    Util.toIntsRef(text, scratchIntsRef),
                    termsFilePointer
                )
            } finally {
                text.length = lengthSave
            }
            lastTerm.copyBytes(text)
        }

        @Throws(IOException::class)
        override fun finish(termsFilePointer: Long) {
            fst = FST.fromFSTReader(
                fstCompiler.compile(),
                fstCompiler.getFSTReader()
            )
            if (fst != null) {
                metaOut!!.writeInt(fieldInfo.number)
                metaOut!!.writeVLong(out!!.filePointer)
                fst!!.save(metaOut!!, out!!)
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        try {
            if (metaOut != null) {
                metaOut!!.writeInt(-1)
                CodecUtil.writeFooter(metaOut!!)
            }
            if (out != null) {
                CodecUtil.writeFooter(out!!)
            }
        } finally {
            try {
                IOUtils.close(out, metaOut)
            } finally {
                out = null
                metaOut = null
            }
        }
    }

    companion object {
        /** Extension of terms index file  */
        const val TERMS_INDEX_EXTENSION: String = "tiv"

        /** Extension of terms meta file  */
        const val TERMS_META_EXTENSION: String = "tmv"

        const val META_CODEC_NAME: String = "VariableGapTermsMeta"
        const val CODEC_NAME: String = "VariableGapTermsIndex"
        const val VERSION_START: Int = 4
        const val VERSION_CURRENT: Int = VERSION_START
    }
}
