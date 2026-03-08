package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.InfoStream

/** Hackidy-Hack-Hack to cause a test to fail on non-bulk merges */
// TODO: we should probably be a wrapper so verbose still works...
class FailOnNonBulkMergesInfoStream : InfoStream() {
    override fun close() {}

    override fun isEnabled(component: String): Boolean {
        return true
    }

    override fun message(component: String, message: String) {
        assert(!message.contains("non-bulk merges"))
    }
}
