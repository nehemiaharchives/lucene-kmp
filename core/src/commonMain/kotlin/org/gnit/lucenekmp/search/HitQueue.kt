package org.gnit.lucenekmp.search


import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.util.PriorityQueue

/**
 * Expert: Priority queue containing hit docs
 *
 * @lucene.internal
 */
class HitQueue
/**
 * Creates a new instance with `size` elements. If `prePopulate` is set to
 * true, the queue will pre-populate itself with sentinel objects and set its [.size] to
 * `size`. In that case, you should not rely on [.size] to get the number of
 * actual elements that were added to the queue, but keep track yourself.<br></br>
 * **NOTE:** in case `prePopulate` is true, you should pop elements from the queue
 * using the following code example:
 *
 * <pre class="prettyprint">
 * PriorityQueue&lt;ScoreDoc&gt; pq = new HitQueue(10, true); // pre-populate.
 * ScoreDoc top = pq.top();
 *
 * // Add/Update one element.
 * top.score = 1.0f;
 * top.doc = 0;
 * top = (ScoreDoc) pq.updateTop();
 * int totalHits = 1;
 *
 * // Now pop only the elements that were *truly* inserted.
 * // First, pop all the sentinel elements (there are pq.size() - totalHits).
 * for (int i = pq.size() - totalHits; i &gt; 0; i--) pq.pop();
 *
 * // Now pop the truly added elements.
 * ScoreDoc[] results = new ScoreDoc[totalHits];
 * for (int i = totalHits - 1; i &gt;= 0; i--) {
 * results[i] = (ScoreDoc) pq.pop();
 * }
</pre> *
 *
 *
 * **NOTE**: This class pre-allocate a full array of length `size`.
 *
 * @param size the requested size of this queue.
 * @param prePopulate specifies whether to pre-populate the queue with sentinel values.
 */
    (size: Int, prePopulate: Boolean) : PriorityQueue<ScoreDoc?>(
    size,
    if (prePopulate) {
        { ScoreDoc(Int.Companion.MAX_VALUE, Float.Companion.NEGATIVE_INFINITY) }
    } else {
        { null }
    }
) {
    override fun lessThan(hitA: ScoreDoc?, hitB: ScoreDoc?): Boolean {
        val cmp: Int = Float.compare(hitA!!.score, hitB!!.score)
        if (cmp == 0) {
            return hitA.doc > hitB.doc
        }
        return cmp < 0
    }
}
