package org.gnit.lucenekmp.search


import org.gnit.lucenekmp.util.PriorityQueue
import kotlin.math.min

/**
 * A base class for all collectors that return a [TopDocs] output. This collector allows easy
 * extension by providing a single constructor which accepts a [PriorityQueue] as well as
 * protected members for that priority queue and a counter of the number of total hits.<br></br>
 * Extending classes can override any of the methods to provide their own implementation, as well as
 * avoid the use of the priority queue entirely by passing null to [ ][.TopDocsCollector]. In that case however, you might want to consider overriding
 * all methods, in order to avoid a NullPointerException.
 */
abstract class TopDocsCollector<T : ScoreDoc> protected constructor(
    /**
     * The priority queue which holds the top documents. Note that different implementations of
     * PriorityQueue give different meaning to 'top documents'. HitQueue for example aggregates the
     * top scoring documents, while other PQ implementations may hold documents sorted by other
     * criteria.
     */
    protected val pq: PriorityQueue<T>
) : Collector {

    /** The total number of documents that matched this query.  */
    /** The total number of documents that the collector encountered.  */
    var totalHits: Int = 0
        protected set

    /** Whether [.totalHits] is exact or a lower bound.  */
    protected var totalHitsRelation: TotalHits.Relation = TotalHits.Relation.EQUAL_TO

    /**
     * Populates the results array with the ScoreDoc instances. This can be overridden in case a
     * different ScoreDoc type should be returned.
     */
    protected open fun populateResults(results: Array<ScoreDoc>, howMany: Int) {
        for (i in howMany - 1 downTo 0) {
            results[i] = pq.pop()!!
        }
    }

    /**
     * Returns a [TopDocs] instance containing the given results. If `results` is
     * null it means there are no results to return, either because there were 0 calls to collect() or
     * because the arguments to topDocs were invalid.
     */
    protected open fun newTopDocs(results: Array<ScoreDoc>?, start: Int): TopDocs {
        return if (results == null)
            EMPTY_TOPDOCS
        else
            TopDocs(TotalHits(totalHits.toLong(), totalHitsRelation), results)
    }

    /** The number of valid PQ entries  */
    protected fun topDocsSize(): Int {
        // In case pq was populated with sentinel values, there might be less
        // results than pq.size(). Therefore return all results until either
        // pq.size() or totalHits.
        return min(totalHits, pq.size())
    }

    /** Returns the top docs that were collected by this collector.  */
    open fun topDocs(): TopDocs {
        // In case pq was populated with sentinel values, there might be less
        // results than pq.size(). Therefore return all results until either
        // pq.size() or totalHits.
        return topDocs(0, topDocsSize())
    }

    /**
     * Returns the documents in the range [start .. pq.size()) that were collected by this collector.
     * Note that if `start >= pq.size()`, an empty TopDocs is returned.<br></br>
     * This method is convenient to call if the application always asks for the last results, starting
     * from the last 'page'.<br></br>
     * **NOTE:** you cannot call this method more than once for each search execution. If you need
     * to call it more than once, passing each time a different `start`, you should call
     * [.topDocs] and work with the returned [TopDocs] object, which will contain all
     * the results this search execution collected.
     */
    fun topDocs(start: Int): TopDocs {
        // In case pq was populated with sentinel values, there might be less
        // results than pq.size(). Therefore return all results until either
        // pq.size() or totalHits.
        return topDocs(start, topDocsSize())
    }

    /**
     * Returns the documents in the range [start .. start+howMany) that were collected by this
     * collector. Note that if `start >= pq.size()`, an empty TopDocs is returned, and if
     * pq.size() - start &lt; howMany, then only the available documents in [start .. pq.size()) are
     * returned.<br></br>
     * This method is useful to call in case pagination of search results is allowed by the search
     * application, as well as it attempts to optimize the memory used by allocating only as much as
     * requested by howMany.<br></br>
     * **NOTE:** you cannot call this method more than once for each search execution. If you need
     * to call it more than once, passing each time a different range, you should call [ ][.topDocs] and work with the returned [TopDocs] object, which will contain all the
     * results this search execution collected.
     */
    fun topDocs(start: Int, howMany: Int): TopDocs {
        // In case pq was populated with sentinel values, there might be less
        // results than pq.size(). Therefore return all results until either
        // pq.size() or totalHits.

        var howMany = howMany
        val size = topDocsSize()

        require(howMany >= 0) { "Number of hits requested must be greater than 0 but value was $howMany" }

        require(start >= 0) { "Expected value of starting position is between 0 and $size, got $start" }

        if (start >= size || howMany == 0) {
            return newTopDocs(null, start)
        }

        // We know that start < pqsize, so just fix howMany.
        howMany = min(size - start, howMany)
        val results = kotlin.arrayOfNulls<ScoreDoc>(howMany) as Array<ScoreDoc>

        // pq's pop() returns the 'least' element in the queue, therefore need
        // to discard the first ones, until we reach the requested range.
        // Note that this loop will usually not be executed, since the common usage
        // should be that the caller asks for the last howMany results. However it's
        // needed here for completeness.
        for (i in pq.size() - start - howMany downTo 1) {
            pq.pop()
        }

        // Get the requested results from pq.
        populateResults(results, howMany)

        return newTopDocs(results, start)
    }

    companion object {
        /**
         * This is used in case topDocs() is called with illegal parameters, or there simply aren't
         * (enough) results.
         *
         * @lucene.internal
         */
        val EMPTY_TOPDOCS: TopDocs =
            TopDocs(TotalHits(0, TotalHits.Relation.EQUAL_TO), emptyArray<ScoreDoc>())
    }
}
