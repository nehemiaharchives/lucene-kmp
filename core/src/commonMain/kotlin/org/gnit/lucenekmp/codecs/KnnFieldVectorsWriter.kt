package org.gnit.lucenekmp.codecs


import okio.IOException
import org.gnit.lucenekmp.util.Accountable

/**
 * Vectors' writer for a field
 *
 * @param <T> an array type; the type of vectors to be written
</T> */
abstract class KnnFieldVectorsWriter<T>
/** Sole constructor  */
protected constructor() : Accountable {
    /**
     * Add new docID with its vector value to the given field for indexing. Doc IDs must be added in
     * increasing order.
     */
    @Throws(IOException::class)
    abstract fun addValue(docID: Int, vectorValue: T)

    /**
     * Used to copy values being indexed to internal storage.
     *
     * @param vectorValue an array containing the vector value to add
     * @return a copy of the value; a new array
     */
    abstract fun copyValue(vectorValue: T): T
}
