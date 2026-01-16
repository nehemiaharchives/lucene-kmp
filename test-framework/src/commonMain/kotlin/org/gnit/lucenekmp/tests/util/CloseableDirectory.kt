package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.tests.util.TestRuleMarkFailure
import org.gnit.lucenekmp.jdkport.Closeable
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import kotlin.test.fail

/**
 * Attempts to close a [BaseDirectoryWrapper].
 *
 * @see LuceneTestCase.newDirectory
 */
internal class CloseableDirectory(
    dir: BaseDirectoryWrapper,
    failureMarker: TestRuleMarkFailure
) : Closeable {
    private val dir: BaseDirectoryWrapper
    private val failureMarker: TestRuleMarkFailure

    init {
        this.dir = dir
        this.failureMarker = failureMarker
    }

    override fun close() {
        // We only attempt to check open/closed state if there were no other test
        // failures.
        try {
            if (failureMarker.wasSuccessful() && dir.isOpen()) {
                fail("Directory not closed: $dir")
            }
        } finally {
            // TODO: perform real close of the delegate: LUCENE-4058
            // dir.close();
        }
    }
}
