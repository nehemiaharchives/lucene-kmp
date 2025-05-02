package org.gnit.lucenekmp.codecs

import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.IndexInput
import kotlinx.io.IOException

/**
 * The core terms dictionaries (BlockTermsReader, BlockTreeTermsReader) interact with a single
 * instance of this class to manage creation of [org.apache.lucene.index.PostingsEnum] and
 * [org.apache.lucene.index.ImpactsEnum] instances. It provides an IndexInput (termsIn) where
 * this class may read any previously stored data that it had written in its corresponding [ ] at indexing time.
 *
 * @lucene.experimental
 */
// TODO: maybe move under blocktree  but it's used by other terms dicts (e.g. Block)
// TODO: find a better name; this defines the API that the
// terms dict impls use to talk to a postings impl.
// TermsDict + PostingsReader/WriterBase == PostingsConsumer/Producer
abstract class PostingsReaderBase
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : AutoCloseable {
    /**
     * Performs any initialization, such as reading and verifying the header from the provided terms
     * dictionary [IndexInput].
     */
    @Throws(IOException::class)
    abstract fun init(termsIn: IndexInput, state: SegmentReadState)

    /** Return a newly created empty TermState  */
    @Throws(IOException::class)
    abstract fun newTermState(): BlockTermState

    /**
     * Actually decode metadata for next term
     *
     * @see PostingsWriterBase.encodeTerm
     */
    @Throws(IOException::class)
    abstract fun decodeTerm(
        `in`: DataInput, fieldInfo: FieldInfo, state: BlockTermState, absolute: Boolean
    )

    /** Must fully consume state, since after this call that TermState may be reused.  */
    @Throws(IOException::class)
    abstract fun postings(
        fieldInfo: FieldInfo, state: BlockTermState, reuse: PostingsEnum?, flags: Int
    ): PostingsEnum

    /**
     * Return a [ImpactsEnum] that computes impacts with `scorer`.
     *
     * @see .postings
     */
    @Throws(IOException::class)
    abstract fun impacts(fieldInfo: FieldInfo, state: BlockTermState, flags: Int): ImpactsEnum

    /**
     * Checks consistency of this reader.
     *
     *
     * Note that this may be costly in terms of I/O, e.g. may involve computing a checksum value
     * against large data files.
     *
     * @lucene.internal
     */
    @Throws(IOException::class)
    abstract fun checkIntegrity()

    abstract override fun close()
}
