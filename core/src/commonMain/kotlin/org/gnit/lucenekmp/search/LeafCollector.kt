package org.gnit.lucenekmp.search

import okio.IOException

/**
 * Collector decouples the score from the collected doc: the score computation is skipped entirely
 * if it's not needed. Collectors that do need the score should implement the {@link #setScorer}
 * method, to hold onto the passed {@link Scorer} instance, and call {@link Scorer#score()} within
 * the collect method to compute the current hit's score. If your collector may request the score
 * for a single hit multiple times, you should use {@link ScoreCachingWrappingScorer}.
 *
 * <p><b>NOTE:</b> The doc that is passed to the collect method is relative to the current reader.
 * If your collector needs to resolve this to the docID space of the Multi*Reader, you must re-base
 * it by recording the docBase from the most recent setNextReader call. Here's a simple example
 * showing how to collect docIDs into a BitSet:
 *
 * <pre class="prettyprint">
 * IndexSearcher searcher = new IndexSearcher(indexReader);
 * final BitSet bits = new BitSet(indexReader.maxDoc());
 * searcher.search(query, new Collector() {
 *
 *   public LeafCollector getLeafCollector(LeafReaderContext context)
 *       throws IOException {
 *     final int docBase = context.docBase;
 *     return new LeafCollector() {
 *
 *       <em>// ignore scorer</em>
 *       public void setScorer(Scorer scorer) throws IOException {
 *       }
 *
 *       public void collect(int doc) throws IOException {
 *         bits.set(docBase + doc);
 *       }
 *
 *     };
 *   }
 *
 * });
 * </pre>
 *
 * <p>Not all collectors will need to rebase the docID. For example, a collector that simply counts
 * the total number of hits would skip it.
 *
 * @lucene.experimental
 */
interface LeafCollector {
    /**
     * Called before successive calls to [.collect]. Implementations that need the score of
     * the current document (passed-in to [.collect]), should save the passed-in Scorer and
     * call scorer.score() when needed.
     */
    @Throws(IOException::class)
    fun setScorer(scorer: Scorable)

    /**
     * Called once for every document matching a query, with the unbased document number.
     *
     *
     * Note: The collection of the current segment can be terminated by throwing a [ ]. In this case, the last docs of the current [ ] will be skipped and [IndexSearcher] will
     * swallow the exception and continue collection with the next leaf.
     *
     *
     * Note: This is called in an inner search loop. For good search performance, implementations
     * of this method should not call [StoredFields.document] on every hit. Doing so can slow
     * searches by an order of magnitude or more.
     */
    @Throws(IOException::class)
    fun collect(doc: Int)

    /**
     * Bulk-collect doc IDs.
     *
     *
     * Note: The provided [DocIdStream] may be reused across calls and should be consumed
     * immediately.
     *
     *
     * Note: The provided [DocIdStream] typically only holds a small subset of query matches.
     * This method may be called multiple times per segment.
     *
     *
     * Like [.collect], it is guaranteed that doc IDs get collected in order, ie. doc
     * IDs are collected in order within a [DocIdStream], and if called twice, all doc IDs from
     * the second [DocIdStream] will be greater than all doc IDs from the first [ ].
     *
     *
     * It is legal for callers to mix calls to [.collect] and [ ][.collect].
     *
     *
     * The default implementation calls `stream.forEach(this::collect)`.
     */
    @Throws(IOException::class)
    fun collect(stream: DocIdStream) {
        stream.forEach(this::collect)
    }

    /**
     * Optionally returns an iterator over competitive documents.
     *
     *
     * Collectors should delegate this method to their comparators if their comparators provide the
     * skipping functionality over non-competitive docs.
     *
     *
     * The default is to return `null` which is interpreted as the collector provide any
     * competitive iterator.
     */
    @Throws(IOException::class)
    fun competitiveIterator(): DocIdSetIterator? {
        return null
    }

    /**
     * Hook that gets called once the leaf that is associated with this collector has finished
     * collecting successfully, including when a [CollectionTerminatedException] is thrown. This
     * is typically useful to compile data that has been collected on this leaf, e.g. to convert facet
     * counts on leaf ordinals to facet counts on global ordinals. The default implementation does
     * nothing.
     *
     *
     * Note: It can be assumed that this method will only be called once per LeafCollector
     * instance.
     */
    @Throws(IOException::class)
    fun finish() {
    }
}
