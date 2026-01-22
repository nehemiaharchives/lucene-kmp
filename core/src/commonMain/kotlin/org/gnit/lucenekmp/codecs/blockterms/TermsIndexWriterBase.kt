package org.gnit.lucenekmp.codecs.blockterms

import okio.IOException
import org.gnit.lucenekmp.codecs.TermStats
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.util.BytesRef

/**
 * Base class for terms index implementations to plug into [BlockTermsWriter].
 *
 * @see TermsIndexReaderBase
 *
 * @lucene.experimental
 */
abstract class TermsIndexWriterBase : AutoCloseable {
    /** Terms index API for a single field.  */
    abstract inner class FieldWriter {
        @Throws(IOException::class)
        abstract fun checkIndexTerm(text: BytesRef, stats: TermStats): Boolean

        @Throws(IOException::class)
        abstract fun add(text: BytesRef, stats: TermStats, termsFilePointer: Long)

        @Throws(IOException::class)
        abstract fun finish(termsFilePointer: Long)
    }

    @Throws(IOException::class)
    abstract fun addField(fieldInfo: FieldInfo, termsFilePointer: Long): FieldWriter
}
