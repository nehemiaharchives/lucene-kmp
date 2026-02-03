package org.gnit.lucenekmp.tests.codecs.blockterms

import okio.IOException
import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.codecs.PostingsReaderBase
import org.gnit.lucenekmp.codecs.PostingsWriterBase
import org.gnit.lucenekmp.codecs.blockterms.BlockTermsReader
import org.gnit.lucenekmp.codecs.blockterms.BlockTermsWriter
import org.gnit.lucenekmp.codecs.blockterms.FixedGapTermsIndexReader
import org.gnit.lucenekmp.codecs.blockterms.FixedGapTermsIndexWriter
import org.gnit.lucenekmp.codecs.blockterms.TermsIndexReaderBase
import org.gnit.lucenekmp.codecs.blockterms.TermsIndexWriterBase
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsReader
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsWriter
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState

// TODO: we could make separate base class that can wrap
// any PostingsFormat and make it ord-able...
/**
 * Customized version of [Lucene101PostingsFormat] that uses [FixedGapTermsIndexWriter].
 */
class LuceneFixedGap(val termIndexInterval: Int = FixedGapTermsIndexWriter.DEFAULT_TERM_INDEX_INTERVAL) : PostingsFormat("LuceneFixedGap") {

    @Throws(IOException::class)
    override fun fieldsConsumer(state: SegmentWriteState): FieldsConsumer {
        val docs: PostingsWriterBase = Lucene101PostingsWriter(state)

        // TODO: should we make the terms index more easily
        // pluggable?  Ie so that this codec would record which
        // index impl was used, and switch on loading?
        // Or... you must make a new Codec for this?
        var indexWriter: TermsIndexWriterBase
        var success = false
        try {
            indexWriter = FixedGapTermsIndexWriter(
                state,
                termIndexInterval
            )
            success = true
        } finally {
            if (!success) {
                docs.close()
            }
        }

        success = false
        try {
            // Must use BlockTermsWriter (not BlockTree) because
            // BlockTree doens't support ords (yet)...
            val ret: FieldsConsumer = BlockTermsWriter(indexWriter, state, docs)
            success = true
            return ret
        } finally {
            if (!success) {
                try {
                    docs.close()
                } finally {
                    indexWriter.close()
                }
            }
        }
    }


    @Throws(IOException::class)
    override fun fieldsProducer(state: SegmentReadState): FieldsProducer {
        val postings: PostingsReaderBase = Lucene101PostingsReader(state)
        var indexReader: TermsIndexReaderBase

        var success = false
        try {
            indexReader = FixedGapTermsIndexReader(state)
            success = true
        } finally {
            if (!success) {
                postings.close()
            }
        }

        success = false
        try {
            val ret: FieldsProducer = BlockTermsReader(indexReader, postings, state)
            success = true
            return ret
        } finally {
            if (!success) {
                try {
                    postings.close()
                } finally {
                    indexReader.close()
                }
            }
        }
    }

    companion object {
        /** Extension of freq postings file  */
        const val FREQ_EXTENSION: String = "frq"

        /** Extension of prox postings file  */
        const val PROX_EXTENSION: String = "prx"
    }
}
