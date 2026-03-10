package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.IndexReader

/**
 * Factory class used by [SearcherManager] to create new IndexSearchers. The default
 * implementation just creates an IndexSearcher with no custom behavior:
 *
 * <pre class="prettyprint">
 *   public IndexSearcher newSearcher(IndexReader r) throws IOException {
 *     return new IndexSearcher(r);
 *   }
 * </pre>
 *
 * You can pass your own factory instead if you want custom behavior, such as:
 *
 * <ul>
 *   <li>Setting a custom scoring model: [IndexSearcher.similarity]
 *   <li>Parallel per-segment search: [IndexSearcher.IndexSearcher]
 *   <li>Return custom subclasses of IndexSearcher (for example that implement distributed scoring)
 *   <li>Run queries to warm your IndexSearcher before it is used. Note: when using near-realtime
 *       search you may want to also
 *       [org.gnit.lucenekmp.index.IndexWriterConfig.setMergedSegmentWarmer] to warm newly
 *       merged segments in the background, outside of the reopen path.
 * </ul>
 *
 * @lucene.experimental
 */
open class SearcherFactory {
    /**
     * Returns a new IndexSearcher over the given reader.
     *
     * @param reader the reader to create a new searcher for
     * @param previousReader the reader previously used to create a new searcher. This can be `null`
     * if unknown or if the given reader is the initially opened reader. If this reader is non-null
     * it can be used to find newly opened segments compared to the new reader to warm the searcher
     * up before returning.
     */
    @Throws(IOException::class)
    open fun newSearcher(reader: IndexReader, previousReader: IndexReader?): IndexSearcher {
        return IndexSearcher(reader)
    }
}
