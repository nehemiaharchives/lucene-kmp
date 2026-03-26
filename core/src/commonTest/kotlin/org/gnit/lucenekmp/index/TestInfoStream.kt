package org.gnit.lucenekmp.index

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.InfoStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests indexwriter's infostream */
@OptIn(ExperimentalAtomicApi::class)
class TestInfoStream : LuceneTestCase() {
    /** we shouldn't have test points unless we ask */
    @Test
    fun testTestPointsOff() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig()
        iwc.setInfoStream(
            object : InfoStream() {
                override fun close() {}

                override fun message(component: String, message: String) {
                    assertFalse("TP" == component)
                }

                override fun isEnabled(component: String): Boolean {
                    assertFalse("TP" == component)
                    return true
                }
            }
        )
        val iw = IndexWriter(dir, iwc)
        iw.addDocument(Document())
        iw.close()
        dir.close()
    }

    /** but they should work when we need */
    @Test
    fun testTestPointsOn() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig()
        val seenTestPoint = AtomicBoolean(false)
        iwc.setInfoStream(
            object : InfoStream() {
                override fun close() {}

                override fun message(component: String, message: String) {
                    if ("TP" == component) {
                        seenTestPoint.store(true)
                    }
                }

                override fun isEnabled(component: String): Boolean {
                    return true
                }
            }
        )
        val iw =
            object : IndexWriter(dir, iwc) {
                override fun isEnableTestPoints(): Boolean {
                    return true
                }
            }
        iw.addDocument(Document())
        iw.close()
        dir.close()
        assertTrue(seenTestPoint.load())
    }
}
