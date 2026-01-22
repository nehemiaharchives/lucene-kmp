package org.gnit.lucenekmp.codecs.blockterms

import okio.IOException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.util.BytesRef


// TODO
//   - allow for non-regular index intervals?  eg with a
//     long string of rare terms, you don't need such
//     frequent indexing
/**
 * [BlockTermsReader] interacts with an instance of this class to manage its terms index. The
 * writer must accept indexed terms (many pairs of BytesRef text + long fileOffset), and then this
 * reader must be able to retrieve the nearest index term to a provided term text.
 *
 * @lucene.experimental
 */
abstract class TermsIndexReaderBase : AutoCloseable {
    abstract fun getFieldEnum(fieldInfo: FieldInfo): FieldIndexEnum?

    abstract override fun close()

    abstract fun supportsOrd(): Boolean

    /**
     * Similar to TermsEnum, except, the only "metadata" it reports for a given indexed term is the
     * long fileOffset into the main terms dictionary file.
     */
    abstract class FieldIndexEnum {
        /**
         * Seeks to "largest" indexed term that's &lt;= term; returns file pointer index (into the main
         * terms index file) for that term
         */
        @Throws(IOException::class)
        abstract fun seek(term: BytesRef): Long

        /** Returns -1 at end  */
        @Throws(IOException::class)
        abstract fun next(): Long

        abstract fun term(): BytesRef?

        /** Only implemented if [TermsIndexReaderBase.supportsOrd] returns true.  */
        @Throws(IOException::class)
        abstract fun seek(ord: Long): Long

        /** Only implemented if [TermsIndexReaderBase.supportsOrd] returns true.  */
        abstract fun ord(): Long
    }
}
