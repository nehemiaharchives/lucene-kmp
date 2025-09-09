package org.gnit.lucenekmp.tests.store

import okio.IOException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import kotlin.random.Random

/**
 * Lightweight Kotlin port of Lucene's MockDirectoryWrapper.
 *
 * This implementation only contains a very small subset of the
 * original functionality which is sufficient for the current
 * test-framework. Features such as crash simulation, file handle
 * tracking and Windows specific behaviour are currently not implemented.
 */
class MockDirectoryWrapper(
    private val random: Random,
    delegate: Directory
) : BaseDirectoryWrapper(delegate) {

    /** Probability of throwing an IOException during write operations. */
    var randomIOExceptionRate: Double = 0.0

    /** Probability of throwing an IOException when opening files. */
    var randomIOExceptionRateOnOpen: Double = 0.0

    /** Throttling behavior, no-op in this simplified implementation. */
    enum class Throttling {
        /** Never throttles I/O operations. */
        NEVER,

        /** Occasionally throttles I/O operations. */
        SOMETIMES,

        /** Always throttles I/O operations. */
        ALWAYS
    }

    /** Current throttling mode. */
    private var throttling: Throttling = Throttling.NEVER

    /** Set the [Throttling] mode. */
    fun setThrottling(throttling: Throttling) {
        this.throttling = throttling
    }

    private fun maybeThrowIOException(reason: String?) {
        if (random.nextDouble() < randomIOExceptionRate) {
            throw IOException("Mock IO exception" + (reason?.let { ": $it" } ?: ""))
        }
    }

    override fun createOutput(name: String, context: IOContext): IndexOutput {
        maybeThrowIOException(name)
        return super.createOutput(name, context)
    }

    override fun openInput(name: String, context: IOContext): IndexInput {
        if (random.nextDouble() < randomIOExceptionRateOnOpen) {
            throw IOException("Mock IO exception opening $name")
        }
        return super.openInput(name, context)
    }
}

