package org.gnit.lucenekmp.util

import okio.IOException


/** A simple iterator interface for [BytesRef] iteration.  */
interface BytesRefIterator {
    /**
     * Increments the iteration to the next [BytesRef] in the iterator. Returns the resulting
     * [BytesRef] or `null` if the end of the iterator is reached. The returned
     * BytesRef may be re-used across calls to next. After this method returns null, do not call it
     * again: the results are undefined.
     *
     * @return the next [BytesRef] in the iterator or `null` if the end of the
     * iterator is reached.
     * @throws IOException If there is a low-level I/O error.
     */
    @Throws(IOException::class)
    fun next(): BytesRef?

    companion object {
        /** Singleton BytesRefIterator that iterates over 0 BytesRefs.  */
        val EMPTY: BytesRefIterator = object : BytesRefIterator {
            override fun next(): BytesRef? = null
        }
    }
}
