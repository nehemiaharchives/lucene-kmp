package org.gnit.lucenekmp.internal.vectorization

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.configureTestLogging
import kotlin.time.TimeSource
import kotlin.test.Test
import kotlin.test.assertEquals

class TestVectorizationProvider : LuceneTestCase() {
    init {
        configureTestLogging()
    }

    private val logger = KotlinLogging.logger {}

    @Test
    fun testCallerOfGetter() {
        expectThrows(UnsupportedOperationException::class) { illegalCaller() }
    }

    @Test
    fun testPerfCallerValidationProbe() {
        val validCallers = setOf(
            "org.gnit.lucenekmp.codecs.hnsw.FlatVectorScorerUtil",
            "org.gnit.lucenekmp.util.VectorUtil",
            "org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsReader",
            "org.gnit.lucenekmp.codecs.lucene101.PostingIndexInput",
            "org.gnit.lucenekmp.internal.vectorization.BaseVectorizationTestCase",
            "org.gnit.lucenekmp.internal.vectorization.TestPostingDecodingUtil",
            "org.gnit.lucenekmp.internal.vectorization.TestVectorScorer",
            "org.gnit.lucenekmp.internal.vectorization.TestVectorizationProvider"
        )
        val iterations = 2_000

        var baselineCount = 0
        val baselineMark = TimeSource.Monotonic.markNow()
        repeat(iterations) {
            if (baselineHasValidCaller(validCallers)) {
                baselineCount++
            }
        }
        val baselineMs = baselineMark.elapsedNow().inWholeMilliseconds

        var optimizedCount = 0
        val optimizedMark = TimeSource.Monotonic.markNow()
        repeat(iterations) {
            if (hasValidVectorizationCallerPlatform(validCallers)) {
                optimizedCount++
            }
        }
        val optimizedMs = optimizedMark.elapsedNow().inWholeMilliseconds

        assertEquals(baselineCount, optimizedCount)
        logger.debug {
            "perf:VectorizationProvider callerValidation iterations=$iterations baselineMs=$baselineMs optimizedMs=$optimizedMs count=$optimizedCount"
        }
    }

    companion object {
        private fun illegalCaller() {
            VectorizationProvider.getInstance()
        }

        private fun baselineHasValidCaller(validCallers: Set<String>): Boolean {
            val trace = Throwable().stackTraceToString()
            return validCallers.any { trace.contains(it) }
        }
    }
}
