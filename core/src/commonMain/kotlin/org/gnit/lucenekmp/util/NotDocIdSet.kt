package org.gnit.lucenekmp.util

import okio.IOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSet
import org.gnit.lucenekmp.search.DocIdSetIterator

/**
 * This [DocIdSet] encodes the negation of another [DocIdSet]. It is cacheable and
 * supports random-access if the underlying set is cacheable and supports random-access.
 *
 * @lucene.internal
 */
class NotDocIdSet(private val maxDoc: Int, private val `in`: DocIdSet) :
    DocIdSet() {

    override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES_USED + `in`.ramBytesUsed()
    }

    override fun iterator(): DocIdSetIterator {
        val inIterator: DocIdSetIterator = `in`.iterator()
        return object : DocIdSetIterator() {
            var doc: Int = -1
            var nextSkippedDoc: Int = -1

            @Throws(IOException::class)
            override fun nextDoc(): Int {
                return advance(doc + 1)
            }

            @Throws(IOException::class)
            override fun advance(target: Int): Int {
                doc = target
                if (doc > nextSkippedDoc) {
                    nextSkippedDoc = inIterator.advance(doc)
                }
                while (true) {
                    if (doc >= maxDoc) {
                        return NO_MORE_DOCS.also { doc = it }
                    }
                    assert(doc <= nextSkippedDoc)
                    if (doc != nextSkippedDoc) {
                        return doc
                    }
                    doc += 1
                    nextSkippedDoc = inIterator.nextDoc()
                }
            }

            override fun docID(): Int {
                return doc
            }

            override fun cost(): Long {
                // even if there are few docs in this set, iterating over all documents
                // costs O(maxDoc) in all cases
                return maxDoc.toLong()
            }
        }
    }

    companion object {
        private val BASE_RAM_BYTES_USED: Long =
            RamUsageEstimator.shallowSizeOfInstance(NotDocIdSet::class)
    }
}
