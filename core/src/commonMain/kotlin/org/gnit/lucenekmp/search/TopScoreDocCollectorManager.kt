package org.gnit.lucenekmp.search

import okio.IOException
import kotlin.math.max

/**
 * Create a TopScoreDocCollectorManager which uses a shared hit counter to maintain number of hits
 * and a shared [MaxScoreAccumulator] to propagate the minimum score across segments
 *
 *
 * Note that a new collectorManager should be created for each search due to its internal states.
 */
class TopScoreDocCollectorManager(numHits: Int, after: ScoreDoc?, totalHitsThreshold: Int) :
    CollectorManager<TopScoreDocCollector, TopDocs> {
    private val numHits: Int
    private val after: ScoreDoc?
    private val totalHitsThreshold: Int
    private val minScoreAcc: MaxScoreAccumulator?

    /**
     * Creates a new [TopScoreDocCollectorManager] given the number of hits to collect and the
     * number of hits to count accurately.
     *
     *
     * **NOTE**: If the total hit count of the top docs is less than or exactly `totalHitsThreshold` then this value is accurate. On the other hand, if the [ ][TopDocs.totalHits] value is greater than `totalHitsThreshold` then its value is a lower
     * bound of the hit count. A value of [Integer.MAX_VALUE] will make the hit count accurate
     * but will also likely make query processing slower.
     *
     *
     * **NOTE**: The instances returned by this method pre-allocate a full array of length
     * `numHits`, and fill the array with sentinel objects.
     *
     * @param numHits the number of results to collect.
     * @param after the previous doc after which matching docs will be collected.
     * @param totalHitsThreshold the number of docs to count accurately. If the query matches more
     * than `totalHitsThreshold` hits then its hit count will be a lower bound. On the other
     * hand if the query matches less than or exactly `totalHitsThreshold` hits then the hit
     * count of the result will be accurate. [Integer.MAX_VALUE] may be used to make the hit
     * count accurate, but this will also make query processing slower.
     */
    @Deprecated(
        """Use {@link #TopScoreDocCollectorManager(int, ScoreDoc, int)}, the
        supportsConcurrency parameter is now a no-op."""
    )
    constructor(
        numHits: Int,
        after: ScoreDoc?,
        totalHitsThreshold: Int,
        supportsConcurrency: Boolean
    ) : this(numHits, after, totalHitsThreshold)

    /**
     * Creates a new [TopScoreDocCollectorManager] given the number of hits to collect and the
     * number of hits to count accurately, with thread-safe internal states.
     *
     *
     * **NOTE**: If the total hit count of the top docs is less than or exactly `totalHitsThreshold` then this value is accurate. On the other hand, if the [ ][TopDocs.totalHits] value is greater than `totalHitsThreshold` then its value is a lower
     * bound of the hit count. A value of [Integer.MAX_VALUE] will make the hit count accurate
     * but will also likely make query processing slower.
     *
     *
     * **NOTE**: The instances returned by this method pre-allocate a full array of length
     * `numHits`, and fill the array with sentinel objects.
     *
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

        require(numHits > 0) { "numHits must be > 0; please use TotalHitCountCollectorManager if you just need the total hit count" }

        this.numHits = numHits
        this.after = after
        this.totalHitsThreshold = max(totalHitsThreshold, numHits)
        this.minScoreAcc =
            if (totalHitsThreshold != Int.MAX_VALUE) MaxScoreAccumulator() else null
    }

    /**
     * Creates a new [TopScoreDocCollectorManager] given the number of hits to collect and the
     * number of hits to count accurately, with thread-safe internal states.
     *
     *
     * **NOTE**: If the total hit count of the top docs is less than or exactly `totalHitsThreshold` then this value is accurate. On the other hand, if the [ ][TopDocs.totalHits] value is greater than `totalHitsThreshold` then its value is a lower
     * bound of the hit count. A value of [Integer.MAX_VALUE] will make the hit count accurate
     * but will also likely make query processing slower.
     *
     *
     * **NOTE**: The instances returned by this method pre-allocate a full array of length
     * `numHits`, and fill the array with sentinel objects.
     *
     * @param numHits the number of results to collect.
     * @param totalHitsThreshold the number of docs to count accurately. If the query matches more
     * than `totalHitsThreshold` hits then its hit count will be a lower bound. On the other
     * hand if the query matches less than or exactly `totalHitsThreshold` hits then the hit
     * count of the result will be accurate. [Integer.MAX_VALUE] may be used to make the hit
     * count accurate, but this will also make query processing slower.
     */
    constructor(numHits: Int, totalHitsThreshold: Int) : this(numHits = numHits, after = null, totalHitsThreshold, true)

    override fun newCollector(): TopScoreDocCollector {
        return TopScoreDocCollector(numHits, after, totalHitsThreshold, minScoreAcc)
    }

    @Throws(IOException::class)
    override fun reduce(collectors: MutableCollection<TopScoreDocCollector>): TopDocs {
        val topDocs: Array<TopDocs> =
            kotlin.arrayOfNulls<TopDocs>(collectors.size) as Array<TopDocs>
        var i = 0
        for (collector in collectors) {
            topDocs[i++] = collector.topDocs()
        }
        return TopDocs.merge(0, numHits, topDocs)
    }
}
