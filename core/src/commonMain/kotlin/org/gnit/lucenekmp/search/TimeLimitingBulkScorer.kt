package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.QueryTimeout
import org.gnit.lucenekmp.util.Bits
import kotlin.math.min


/**
 * The [TimeLimitingBulkScorer] is used to timeout search requests that take longer than the
 * maximum allowed search time limit. After this time is exceeded, the search thread is stopped by
 * throwing a [TimeLimitingBulkScorer.TimeExceededException].
 *
 * @see org.apache.lucene.index.ExitableDirectoryReader
 */
internal class TimeLimitingBulkScorer(
    bulkScorer: BulkScorer,
    private val queryTimeout: QueryTimeout
) : BulkScorer() {
    /** Thrown when elapsed search time exceeds allowed search time.  */
    internal class TimeExceededException() : RuntimeException("TimeLimit Exceeded") {
        fun fillInStackTrace(): Throwable {
            // never re-thrown so we can save the expensive stacktrace
            return this
        }
    }

    private val `in`: BulkScorer = bulkScorer

    @Throws(IOException::class)
    override fun score(
        collector: LeafCollector,
        acceptDocs: Bits?,
        min: Int,
        max: Int
    ): Int {
        var min = min
        var interval = INTERVAL
        while (min < max) {
            val newMax = min(min.toLong() + interval, max.toLong()).toInt()
            val newInterval =
                interval + (interval shr 1) // increase the interval by 50% on each iteration
            // overflow check
            if (interval < newInterval) {
                interval = newInterval
            }
            if (queryTimeout.shouldExit()) {
                throw TimeExceededException()
            }
            min = `in`.score(collector, acceptDocs, min, newMax) // in is the wrapped bulk scorer
        }
        return min
    }

    override fun cost(): Long {
        return `in`.cost()
    }

    companion object {
        // We score chunks of documents at a time so as to avoid the cost of checking the timeout for
        // every document we score.
        const val INTERVAL: Int = 100
    }
}
