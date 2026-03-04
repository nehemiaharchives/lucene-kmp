package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.Test2BConstants
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

/**
 * Test indexes ~82M docs with 26 terms each, so you get > Integer.MAX_VALUE terms/docs pairs
 *
 * @lucene.experimental
 */
//@SuppressCodecs("SimpleText", "Direct", "Compressing")
//@TimeoutSuite(millis = 4 * TimeUnits.HOUR)
class Test2BPostings : LuceneTestCase() {

    //@Nightly
    @Test
    @Throws(Exception::class)
    fun test() {
        val dir: BaseDirectoryWrapper = newFSDirectory(createTempDir("2BPostings"))
        if (dir is MockDirectoryWrapper) {
            dir.setThrottling(MockDirectoryWrapper.Throttling.NEVER)
        }

        val iwc = IndexWriterConfig(MockAnalyzer(random()))
            .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            .setRAMBufferSizeMB(256.0)
            .setMergeScheduler(ConcurrentMergeScheduler())
            .setMergePolicy(newLogMergePolicy(false, 10))
            .setOpenMode(IndexWriterConfig.OpenMode.CREATE)

        val w = IndexWriter(dir, iwc)

        val mp = w.config.mergePolicy
        if (mp is LogByteSizeMergePolicy) {
            // 1 petabyte:
            mp.maxMergeMB = 1024.0 * 1024 * 1024
        }

        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setOmitNorms(true)
        ft.setIndexOptions(IndexOptions.DOCS)
        val field = Field("field", MyTokenStream(), ft)
        doc.add(field)

        val numDocs = (Test2BConstants.MAX_DOCS / 26) + 1 // TODO reduced numDocs = (Int.MAX_VALUE / 26) + 1 to (Test2BConstants.MAX_DOCS / 26) + 1 for dev speed
        for (i in 0..<numDocs) {
            w.addDocument(doc)
            if (VERBOSE && i % 100000 == 0) {
                println("$i of $numDocs...")
            }
        }
        w.forceMerge(1)
        w.close()
        dir.close()
    }

    class MyTokenStream : TokenStream() {
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
        private var index = 0

        override fun incrementToken(): Boolean {
            if (index <= 'z'.code) {
                clearAttributes()
                termAtt.setLength(1)
                termAtt.buffer()[0] = index.toChar()
                index++
                return true
            }
            return false
        }

        @Throws(IOException::class)
        override fun reset() {
            super.reset()
            index = 'a'.code
        }
    }
}
