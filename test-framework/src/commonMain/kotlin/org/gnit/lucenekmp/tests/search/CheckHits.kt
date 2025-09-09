package org.gnit.lucenekmp.tests.search

import kotlin.math.abs
import kotlin.test.fail
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreDoc

/** Utility class for asserting expected hits in tests. */
object CheckHits {
    /**
     * Tests that two queries have the same hits and scores.
     */
    fun checkEqual(query: Query, hits1: Array<ScoreDoc>, hits2: Array<ScoreDoc>) {
        val scoreTolerance = 1.0e-6f
        if (hits1.size != hits2.size) {
            fail("Unequal lengths: hits1=" + hits1.size + ",hits2=" + hits2.size)
        }
        for (i in hits1.indices) {
            if (hits1[i].doc != hits2[i].doc) {
                fail(
                    "Hit " + i + " docnumbers don't match\n" + hits2str(hits1, hits2, 0, 0) + "for query:" + query
                )
            }
            if (hits1[i].doc != hits2[i].doc || abs(hits1[i].score - hits2[i].score) > scoreTolerance) {
                fail(
                    "Hit " + i + ", doc nrs " + hits1[i].doc + " and " + hits2[i].doc +
                        "\nunequal       : " + hits1[i].score +
                        "\n           and: " + hits2[i].score +
                        "\nfor query:" + query
                )
            }
        }
    }

    fun hits2str(hits1: Array<ScoreDoc>?, hits2: Array<ScoreDoc>?, start: Int, endIn: Int): String {
        val sb = StringBuilder()
        val len1 = hits1?.size ?: 0
        val len2 = hits2?.size ?: 0
        var end = endIn
        if (end <= 0) {
            end = maxOf(len1, len2)
        }
        sb.append("Hits length1=").append(len1).append("\tlength2=").append(len2)
        sb.append('\n')
        for (i in start until end) {
            sb.append("hit=").append(i).append(':')
            if (i < len1) {
                val h1 = hits1!![i]
                sb.append(" doc").append(h1.doc).append('=').append(h1.score).append(" shardIndex=").append(h1.shardIndex)
            } else {
                sb.append("               ")
            }
            sb.append(",\t")
            if (i < len2) {
                val h2 = hits2!![i]
                sb.append(" doc").append(h2.doc).append('=').append(h2.score).append(" shardIndex=").append(h2.shardIndex)
            }
            sb.append('\n')
        }
        return sb.toString()
    }
}

