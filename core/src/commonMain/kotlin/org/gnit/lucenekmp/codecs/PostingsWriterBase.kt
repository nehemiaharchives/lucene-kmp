package org.gnit.lucenekmp.codecs

import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.FixedBitSet
import kotlinx.io.IOException

/**
 * Class that plugs into term dictionaries, such as [Lucene90BlockTreeTermsWriter], and
 * handles writing postings.
 *
 * @see PostingsReaderBase
 *
 * @lucene.experimental
 */
// TODO: find a better name; this defines the API that the
// terms dict impls use to talk to a postings impl.
// TermsDict + PostingsReader/WriterBase == FieldsProducer/Consumer
abstract class PostingsWriterBase
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : AutoCloseable {
    /**
     * Called once after startup, before any terms have been added. Implementations typically write a
     * header to the provided `termsOut`.
     */
    @Throws(IOException::class)
    abstract fun init(termsOut: IndexOutput, state: SegmentWriteState)

    /**
     * Write all postings for one term; use the provided [TermsEnum] to pull a [ ]. This method should not re-position the `TermsEnum`! It is already positioned on the term that should be written. This method must set
     * the bit in the provided [FixedBitSet] for every docID written. If no docs were written,
     * this method should return null, and the terms dict will skip the term.
     */
    @Throws(IOException::class)
    abstract fun writeTerm(
        term: BytesRef, termsEnum: TermsEnum, docsSeen: FixedBitSet, norms: NormsProducer
    ): BlockTermState

    /**
     * Encode metadata as long[] and byte[]. `absolute` controls whether current term is delta
     * encoded according to latest term. Usually elements in `longs` are file pointers, so each
     * one always increases when a new term is consumed. `out` is used to write generic bytes,
     * which are not monotonic.
     */
    @Throws(IOException::class)
    abstract fun encodeTerm(
        out: DataOutput, fieldInfo: FieldInfo, state: BlockTermState, absolute: Boolean
    )

    /** Sets the current field for writing.  */
    abstract fun setField(fieldInfo: FieldInfo)

    @Throws(IOException::class)
    abstract override fun close()
}
