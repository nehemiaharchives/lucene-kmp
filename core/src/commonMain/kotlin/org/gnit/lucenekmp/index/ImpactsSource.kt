package org.gnit.lucenekmp.index

import kotlinx.io.IOException

/**
 * Source of [Impacts].
 *
 * @lucene.internal
 */
interface ImpactsSource {
    /**
     * Shallow-advance to `target`. This is cheaper than calling [ ][DocIdSetIterator.advance] and allows further calls to [.getImpacts] to ignore doc
     * IDs that are less than `target` in order to get more precise information about impacts.
     * This method may not be called on targets that are less than the current [ ][DocIdSetIterator.docID]. After this method has been called, [ ][DocIdSetIterator.nextDoc] may not be called if the current doc ID is less than `target
     * - 1` and [DocIdSetIterator.advance] may not be called on targets that are less than
     * `target`.
     */
    @Throws(IOException::class)
    fun advanceShallow(target: Int)

    val impacts: Impacts
}
