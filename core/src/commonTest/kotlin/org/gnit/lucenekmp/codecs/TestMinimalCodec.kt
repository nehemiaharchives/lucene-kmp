package org.gnit.lucenekmp.codecs

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.randomBoolean
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests to ensure that [Codec]s won't need to implement all formats in case where only a
 * small subset of Lucene's functionality is used.
 */
class TestMinimalCodec : LuceneTestCase() {
    @Test
    @Throws(IOException::class)
    fun testMinimalCodec() {
        runMinimalCodecTest(false)
    }

    @Test
    @Throws(IOException::class)
    fun testMinimalCompoundCodec() {
        runMinimalCodecTest(true)
    }

    @Throws(IOException::class)
    private fun runMinimalCodecTest(useCompoundFile: Boolean) {
        newDirectory().use { dir: BaseDirectoryWrapper ->
            val writerConfig: IndexWriterConfig = newIndexWriterConfig(MockAnalyzer(random()))
                .setCodec(if (useCompoundFile) MinimalCompoundCodec() else MinimalCodec())
                .setUseCompoundFile(useCompoundFile)
            if (!useCompoundFile) {
                // Avoid using MockMP as it randomly enables compound file creation
                writerConfig.setMergePolicy(newMergePolicy(random(), false))
                writerConfig.mergePolicy.noCFSRatio = 0.0
                writerConfig.mergePolicy.maxCFSSegmentSizeMB = Double.POSITIVE_INFINITY
            }

            IndexWriter(dir, writerConfig).use { writer ->
                writer.addDocument(basicDocument())
                writer.flush()
                // create second segment
                writer.addDocument(basicDocument())
                writer.forceMerge(1) // test merges
                if (randomBoolean()) {
                    writer.commit()
                }

                DirectoryReader.open(writer).use { reader ->
                    assertEquals(2, reader.numDocs())
                }
            }
        }
    }

    /** returns a basic document with no indexed fields */
    private fun basicDocument(): Document {
        return Document()
    }

    /** Minimal codec implementation for working with the most basic documents */
    open class MinimalCodec : Codec {
        protected val wrappedCodec: Codec = TestUtil.getDefaultCodec()

        constructor() : this("MinimalCodec")

        protected constructor(name: String) : super(name)

        override fun fieldInfosFormat(): FieldInfosFormat {
            return wrappedCodec.fieldInfosFormat()
        }

        override fun segmentInfoFormat(): SegmentInfoFormat {
            return wrappedCodec.segmentInfoFormat()
        }

        override fun compoundFormat(): CompoundFormat {
            throw UnsupportedOperationException()
        }

        override fun liveDocsFormat(): LiveDocsFormat {
            throw UnsupportedOperationException()
        }

        override fun storedFieldsFormat(): StoredFieldsFormat {
            // TODO: avoid calling this when no stored fields are written or read
            return wrappedCodec.storedFieldsFormat()
        }

        override fun postingsFormat(): PostingsFormat {
            throw UnsupportedOperationException()
        }

        override fun docValuesFormat(): DocValuesFormat {
            throw UnsupportedOperationException()
        }

        override fun termVectorsFormat(): TermVectorsFormat {
            throw UnsupportedOperationException()
        }

        override fun normsFormat(): NormsFormat {
            throw UnsupportedOperationException()
        }

        override fun pointsFormat(): PointsFormat {
            throw UnsupportedOperationException()
        }

        override fun knnVectorsFormat(): KnnVectorsFormat {
            throw UnsupportedOperationException()
        }
    }

    /**
     * Minimal codec implementation for working with the most basic documents, supporting compound
     * formats
     */
    class MinimalCompoundCodec : MinimalCodec("MinimalCompoundCodec") {
        override fun compoundFormat(): CompoundFormat {
            return wrappedCodec.compoundFormat()
        }
    }
}
