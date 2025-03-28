package org.gnit.lucenekmp.search

import kotlin.jvm.JvmOverloads


/** Holds one hit in [TopDocs].  */
class ScoreDoc
/** Constructs a ScoreDoc.  */ @JvmOverloads constructor(
    /**
     * A hit document's number.
     *
     * @see StoredFields.document
     */
    var doc: Int,
    /** The score of this document for the query.  */
    var score: Float,
    /** Only set by [TopDocs.merge]  */
    var shardIndex: Int = -1
) {
    /** Constructs a ScoreDoc.  */

    // A convenience method for debugging.
    override fun toString(): String {
        return "doc=$doc score=$score shardIndex=$shardIndex"
    }
}
