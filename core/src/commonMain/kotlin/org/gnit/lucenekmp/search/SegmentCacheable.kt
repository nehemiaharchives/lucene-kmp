package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.LeafReaderContext


/**
 * Interface defining whether or not an object can be cached against a [LeafReader]
 *
 *
 * Objects that depend only on segment-immutable structures such as Points or postings lists can
 * just return `true` from [.isCacheable]
 *
 *
 * Objects that depend on doc values should return [ ][DocValues.isCacheable], which will check to see if the doc values
 * fields have been updated. Updated doc values fields are not suitable for cacheing.
 *
 *
 * Objects that are not segment-immutable, such as those that rely on global statistics or
 * scores, should return `false`
 */
interface SegmentCacheable {
    /**
     * @return `true` if the object can be cached against a given leaf
     */
    fun isCacheable(ctx: LeafReaderContext): Boolean
}
