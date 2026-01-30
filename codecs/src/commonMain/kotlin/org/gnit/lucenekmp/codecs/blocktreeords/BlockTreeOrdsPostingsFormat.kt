package org.gnit.lucenekmp.codecs.blocktreeords

import okio.IOException
import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.codecs.PostingsReaderBase
import org.gnit.lucenekmp.codecs.PostingsWriterBase
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsReader
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsWriter
import org.gnit.lucenekmp.codecs.lucene90.blocktree.Lucene90BlockTreeTermsWriter
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.util.IOUtils

/** Uses [OrdsBlockTreeTermsWriter] with [Lucene101PostingsWriter].  */
class BlockTreeOrdsPostingsFormat(
    private val minTermBlockSize: Int = OrdsBlockTreeTermsWriter.DEFAULT_MIN_BLOCK_SIZE,
    private val maxTermBlockSize: Int = OrdsBlockTreeTermsWriter.DEFAULT_MAX_BLOCK_SIZE
) : PostingsFormat("BlockTreeOrds") {
    /**
     * Creates `Lucene41PostingsFormat` with custom values for `minBlockSize` and `maxBlockSize` passed to block terms dictionary.
     *
     * @see      OrdsBlockTreeTermsWriter.OrdsBlockTreeTermsWriter
     */
    /** Creates `Lucene41PostingsFormat` with default settings.  */
    init {
        Lucene90BlockTreeTermsWriter.validateSettings(
            minTermBlockSize,
            maxTermBlockSize
        )
    }

    override fun toString(): String {
        return "$name(blocksize=$BLOCK_SIZE)"
    }

    @Throws(IOException::class)
    override fun fieldsConsumer(state: SegmentWriteState): FieldsConsumer {
        val postingsWriter: PostingsWriterBase =
            Lucene101PostingsWriter(state)

        var success = false
        try {
            val ret: FieldsConsumer =
                OrdsBlockTreeTermsWriter(
                    state,
                    postingsWriter,
                    minTermBlockSize,
                    maxTermBlockSize
                )
            success = true
            return ret
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(postingsWriter)
            }
        }
    }

    @Throws(IOException::class)
    override fun fieldsProducer(state: SegmentReadState): FieldsProducer {
        val postingsReader: PostingsReaderBase =
            Lucene101PostingsReader(state)
        var success = false
        try {
            val ret: FieldsProducer =
                OrdsBlockTreeTermsReader(
                    postingsReader,
                    state
                )
            success = true
            return ret
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(postingsReader)
            }
        }
    }

    companion object {
        /** Fixed packed block size, number of integers encoded in a single packed block.  */ // NOTE: must be multiple of 64 because of PackedInts long-aligned encoding/decoding
        const val BLOCK_SIZE: Int = 128
    }
}
