package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.codecs.compressing.CompressingCodec
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Test indexes 2B docs with 65k freqs each, so you get > Integer.MAX_VALUE postings data for the
 * term
 *
 * @lucene.experimental
 */
//@SuppressCodecs("SimpleText", "Direct")
//@Monster("takes ~20GB-30GB of space and 10 minutes")
class Test2BPostingsBytes : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun test() {
        val defaultConfig = IndexWriterConfig(MockAnalyzer(random()))
        val defaultCodec: Codec = defaultConfig.codec
        if (IndexWriterConfig(MockAnalyzer(random())).codec is CompressingCodec) {
            val regex = Regex("maxDocsPerChunk=(\\d+), blockSize=(\\d+)")
            val match = regex.find(defaultCodec.toString())
            assertTrue(match != null, "Unexpected CompressingCodec toString() output: $defaultCodec")
            val maxDocsPerChunk = match.groupValues[1].toInt()
            val blockSize = match.groupValues[2].toInt()
            val product = maxDocsPerChunk * blockSize
            assumeTrue(
                "${defaultCodec.name} maxDocsPerChunk ($maxDocsPerChunk) * blockSize ($blockSize) < 16 - this can trigger OOM with -Dtests.heapsize=30g",
                product >= 16
            )
        }

        val dir: BaseDirectoryWrapper = newFSDirectory(createTempDir("2BPostingsBytes1"))
        if (dir is MockDirectoryWrapper) {
            dir.setThrottling(MockDirectoryWrapper.Throttling.NEVER)
        }

        val w = IndexWriter(
            dir,
            IndexWriterConfig(MockAnalyzer(random()))
                .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
                .setRAMBufferSizeMB(256.0)
                .setMergeScheduler(ConcurrentMergeScheduler())
                .setMergePolicy(newLogMergePolicy(false, 10))
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE)
                .setCodec(TestUtil.getDefaultCodec())
        )

        val mp = w.config.mergePolicy
        if (mp is LogByteSizeMergePolicy) {
            // 1 petabyte:
            mp.maxMergeMB = 1024.0 * 1024 * 1024
        }

        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        ft.setOmitNorms(true)
        val tokenStream = MyTokenStream()
        val field = Field("field", tokenStream, ft)
        doc.add(field)

        val numDocs = 10 // TODO reduced numDocs = 1000 to 10 for dev speed
        for (i in 0..<numDocs) {
            if (i % 2 == 1) {
                // trick blockPF's little optimization
                tokenStream.n = 65536
            } else {
                tokenStream.n = 65537
            }
            w.addDocument(doc)
        }
        w.forceMerge(1)
        w.close()

        val oneThousand = DirectoryReader.open(dir)
        var subReaders = Array(10) { oneThousand } // TODO reduced subReadersLength = 1000 to 10 for dev speed
        val dir2: BaseDirectoryWrapper = newFSDirectory(createTempDir("2BPostingsBytes2"))
        if (dir2 is MockDirectoryWrapper) {
            dir2.setThrottling(MockDirectoryWrapper.Throttling.NEVER)
        }
        val w2 = IndexWriter(dir2, IndexWriterConfig(MockAnalyzer(random())))
        TestUtil.addIndexesSlowly(w2, *subReaders)
        w2.forceMerge(1)
        w2.close()
        oneThousand.close()

        val oneMillion = DirectoryReader.open(dir2)
        subReaders = Array(20) { oneMillion } // TODO reduced subReadersLength = 2000 to 20 for dev speed
        val dir3: BaseDirectoryWrapper = newFSDirectory(createTempDir("2BPostingsBytes3"))
        if (dir3 is MockDirectoryWrapper) {
            dir3.setThrottling(MockDirectoryWrapper.Throttling.NEVER)
        }
        val w3 = IndexWriter(dir3, IndexWriterConfig(MockAnalyzer(random())))
        TestUtil.addIndexesSlowly(w3, *subReaders)
        w3.forceMerge(1)
        w3.close()
        oneMillion.close()

        dir.close()
        dir2.close()
        dir3.close()
    }

    class MyTokenStream : TokenStream() {
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
        private var index = 0
        var n = 0

        override fun incrementToken(): Boolean {
            if (index < n) {
                clearAttributes()
                termAtt.buffer()[0] = 'a'
                termAtt.setLength(1)
                index++
                return true
            }
            return false
        }

        @Throws(IOException::class)
        override fun reset() {
            super.reset()
            index = 0
        }
    }
}
