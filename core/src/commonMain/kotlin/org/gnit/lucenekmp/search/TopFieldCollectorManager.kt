package org.gnit.lucenekmp.search

import okio.IOException

/**
 * Create a TopFieldCollectorManager which uses a shared hit counter to maintain number of hits and
 * a shared [MaxScoreAccumulator] to propagate the minimum score across segments if the
 * primary sort is by relevancy.
 *
 *
 * Note that a new collectorManager should be created for each search due to its internal states.
 */
class TopFieldCollectorManager(
    sort: Sort,
    numHits: Int,
    after: FieldDoc?,
    totalHitsThreshold: Int
) : CollectorManager<TopFieldCollector, TopFieldDocs> {
    private val sort: Sort
    private val numHits: Int
    private val after: FieldDoc?
    private val totalHitsThreshold: Int
    private val minScoreAcc: MaxScoreAccumulator?
    val collectors: MutableList<TopFieldCollector>

    /**
     * Creates a new [TopFieldCollectorManager] from the given arguments.
     *
     *
     * **NOTE**: The instances returned by this method pre-allocate a full array of length
     * `numHits`.
     *
     * @param sort the sort criteria (SortFields).
     * @param numHits the number of results to collect.
     * @param after the previous doc after which matching docs will be collected.
     * @param totalHitsThreshold the number of docs to count accurately. If the query matches more
     * than `totalHitsThreshold` hits then its hit count will be a lower bound. On the other
     * hand if the query matches less than or exactly `totalHitsThreshold` hits then the hit
     * count of the result will be accurate. [Integer.MAX_VALUE] may be used to make the hit
     * count accurate, but this will also make query processing slower.
     * @param supportsConcurrency to use thread-safe and slower internal states for count tracking.
     */
    @Deprecated(
        """Use {@link #TopFieldCollectorManager(Sort, int, FieldDoc, int)}, the
        supportsConcurrency parameter is now a no-op."""
    )
    constructor(
        sort: Sort,
        numHits: Int,
        after: FieldDoc?,
        totalHitsThreshold: Int,
        supportsConcurrency: Boolean
    ) : this(sort, numHits, after, totalHitsThreshold)

    /**
     * Creates a new [TopFieldCollectorManager] from the given arguments, with thread-safe
     * internal states.
     *
     *
     * **NOTE**: The instances returned by this method pre-allocate a full array of length
     * `numHits`.
     *
     * @param sort the sort criteria (SortFields).
     * @param numHits the number of results to collect.
     * @param after the previous doc after which matching docs will be collected.
     * @param totalHitsThreshold the number of docs to count accurately. If the query matches more
     * than `totalHitsThreshold` hits then its hit count will be a lower bound. On the other
     * hand if the query matches less than or exactly `totalHitsThreshold` hits then the hit
     * count of the result will be accurate. [Integer.MAX_VALUE] may be used to make the hit
     * count accurate, but this will also make query processing slower.
     */
    init {
        require(totalHitsThreshold >= 0) { "totalHitsThreshold must be >= 0, got $totalHitsThreshold" }

        require(numHits > 0) { "numHits must be > 0; please use TotalHitCountCollector if you just need the total hit count" }

        require(sort.sort.isNotEmpty()) { "Sort must contain at least one field" }

        if (after != null) {
            requireNotNull(after.fields) { "after.fields wasn't set; you must pass fillFields=true for the previous search" }

            require(after.fields!!.size == sort.sort.size) {
                ("after.fields has "
                        + after.fields!!.size
                        + " values but sort has "
                        + sort.sort.size)
            }
        }

        this.sort = sort
        this.numHits = numHits
        this.after = after
        this.totalHitsThreshold = totalHitsThreshold
        this.minScoreAcc =
            if (totalHitsThreshold != Int.MAX_VALUE) MaxScoreAccumulator() else null
        this.collectors = mutableListOf()
    }

    /**
     * Creates a new [TopFieldCollectorManager] from the given arguments, with thread-safe
     * internal states.
     *
     *
     * **NOTE**: The instances returned by this method pre-allocate a full array of length
     * `numHits`.
     *
     * @param sort the sort criteria (SortFields).
     * @param numHits the number of results to collect.
     * @param totalHitsThreshold the number of docs to count accurately. If the query matches more
     * than `totalHitsThreshold` hits then its hit count will be a lower bound. On the other
     * hand if the query matches less than or exactly `totalHitsThreshold` hits then the hit
     * count of the result will be accurate. [Integer.MAX_VALUE] may be used to make the hit
     * count accurate, but this will also make query processing slower.
     */
    constructor(sort: Sort, numHits: Int, totalHitsThreshold: Int) : this(
        sort,
        numHits,
        null,
        totalHitsThreshold,
        true
    )

    override fun newCollector(): TopFieldCollector {
        val queue: FieldValueHitQueue<FieldValueHitQueue.Entry> = FieldValueHitQueue.create(sort.sort, numHits)

        val collector: TopFieldCollector
        if (after == null) {
            // inform a comparator that sort is based on this single field
            // to enable some optimizations for skipping over non-competitive documents
            // We can't set single sort when the `after` parameter is non-null as it's
            // an implicit sort over the document id.
            if (queue.comparators.size == 1) {
                queue.comparators[0].setSingleSort()
            }
            collector =
                TopFieldCollector.SimpleFieldCollector(
                    sort, queue, numHits, totalHitsThreshold, minScoreAcc
                )
        } else {
            requireNotNull(after.fields) { "after.fields wasn't set; you must pass fillFields=true for the previous search" }

            require(after.fields!!.size == sort.sort.size) {
                ("after.fields has "
                        + after.fields!!.size
                        + " values but sort has "
                        + sort.sort.size)
            }
            collector =
                TopFieldCollector.PagingFieldCollector(
                    sort, queue, after, numHits, totalHitsThreshold, minScoreAcc
                )
        }

        collectors.add(collector)
        return collector
    }

    @Throws(IOException::class)
    override fun reduce(collectors: MutableCollection<TopFieldCollector>): TopFieldDocs {
        val topDocs: Array<TopFieldDocs> =
            kotlin.arrayOfNulls<TopFieldDocs>(collectors.size) as Array<TopFieldDocs>
        var i = 0
        for (collector in collectors) {
            topDocs[i++] = collector.topDocs()
        }
        return TopDocs.merge(sort, 0, numHits, topDocs)
    }

    /*fun getCollectors(): MutableList<TopFieldCollector> {
        return collectors
    }*/
}
