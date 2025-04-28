package org.gnit.lucenekmp.codecs.hnsw


import org.gnit.lucenekmp.codecs.KnnFieldVectorsWriter
import org.gnit.lucenekmp.index.DocsWithFieldSet
import kotlinx.io.IOException

/**
 * Vectors' writer for a field
 *
 * @param <T> an array type; the type of vectors to be written
 * @lucene.experimental
</T> */
abstract class FlatFieldVectorsWriter<T> : KnnFieldVectorsWriter<T>() {
    /**
     * @return a list of vectors to be written
     */
    abstract val vectors: MutableList<T>

    /**
     * @return the docsWithFieldSet for the field writer
     */
    abstract val docsWithFieldSet: DocsWithFieldSet

    /**
     * indicates that this writer is done and no new vectors are allowed to be added
     *
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    abstract fun finish()

    /**
     * @return true if the writer is done and no new vectors are allowed to be added
     */
    abstract val isFinished: Boolean
}
