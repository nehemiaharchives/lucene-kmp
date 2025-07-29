package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.util.Version


/**
 * Provides read-only metadata about a leaf.
 *
 * @param createdVersionMajor the Lucene version that created this index. This can be used to
 * implement backward compatibility on top of the codec API. A return value of `6`
 * indicates that the created version is unknown.
 * @param minVersion the minimum Lucene version that contributed documents to this index, or `null` if this information is not available.
 * @param sort the order in which documents from this index are sorted, or `null` if documents
 * are in no particular order.
 * @param hasBlocks Returns `true` iff this index contains blocks created with [     ][IndexWriter.addDocument] or it's corresponding update methods with at least 2 or
 * more documents per call. Note: This property was not recorded before [     LUCENE_9_9_0][Version] this method will return false for all leaves written before [     LUCENE_9_9_0][Version]
 * @see IndexWriter.updateDocuments
 * @see IndexWriter.updateDocuments
 * @see IndexWriter.softUpdateDocuments
 * @see IndexWriter.addDocuments
 * @lucene.experimental
 */
class LeafMetaData(val createdVersionMajor: Int, val minVersion: Version?, val sort: Sort?, val hasBlocks: Boolean) {

    /** Expert: Sole constructor. Public for use by custom [LeafReader] impls.  */
    init {
        require(!(createdVersionMajor > Version.LATEST.major)) { "createdVersionMajor is in the future: $createdVersionMajor" }
        require(createdVersionMajor >= 6) { "createdVersionMajor must be >= 6, got: $createdVersionMajor" }
        require(!(createdVersionMajor >= 7 && minVersion == null)) { "minVersion must be set when createdVersionMajor is >= 7" }
    }
}
