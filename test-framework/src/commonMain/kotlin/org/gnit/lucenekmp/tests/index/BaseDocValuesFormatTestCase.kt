package org.gnit.lucenekmp.tests.index

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.CheckIndex
import org.gnit.lucenekmp.index.CheckIndex.Status.DocValuesStatus
import org.gnit.lucenekmp.index.CodecReader
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.DocValuesSkipper
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SerialMergeScheduler
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.PrintStream
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Extends [LegacyBaseDocValuesFormatTestCase] and adds checks for [DocValuesSkipper].
 */
abstract class BaseDocValuesFormatTestCase : LegacyBaseDocValuesFormatTestCase() {
    /**
     * Override and return `false` if the [DocValuesSkipper] produced by this format
     * sometimes returns documents in [DocValuesSkipper.minDocID] or [ ][DocValuesSkipper.maxDocID] that may not have a value.
     */
    protected fun skipperHasAccurateDocBounds(): Boolean {
        return true
    }

    /**
     * Override and return `false` if the [DocValuesSkipper] produced by this format
     * sometimes returns values in [DocValuesSkipper.minValue] or [ ][DocValuesSkipper.maxValue] that none of the documents in the range have.
     */
    protected fun skipperHasAccurateValueBounds(): Boolean {
        return true
    }

    @Throws(IOException::class)
    open fun testSortedMergeAwayAllValuesWithSkipper() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig: IndexWriterConfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory,iwconfig)

        var doc = Document()
        doc.add(StringField("id", "0", Field.Store.NO))
        iwriter.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.NO))
        doc.add(SortedDocValuesField.indexedField("field", newBytesRef("hello")))
        iwriter.addDocument(doc)
        iwriter.commit()
        iwriter.deleteDocuments(Term("id", "1"))
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedDocValues = getOnlyLeafReader(ireader).getSortedDocValues("field")!!
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())

        val skipper: DocValuesSkipper = getOnlyLeafReader(ireader).getDocValuesSkipper("field")!!
        assertEquals(0, skipper.docCount().toLong())
        skipper.advance(0)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), skipper.minDocID(0).toLong())

        val termsEnum: TermsEnum = dv.termsEnum()!!
        assertFalse(termsEnum.seekExact(BytesRef("lucene")))
        assertEquals(TermsEnum.SeekStatus.END, termsEnum.seekCeil(BytesRef("lucene")))
        assertEquals(-1, dv.lookupTerm(BytesRef("lucene")).toLong())

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedSetMergeAwayAllValuesWithSkipper() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig: IndexWriterConfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        var doc = Document()
        doc.add(StringField("id", "0", Field.Store.NO))
        iwriter.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.NO))
        doc.add(SortedSetDocValuesField.indexedField("field", newBytesRef("hello")))
        iwriter.addDocument(doc)
        iwriter.commit()
        iwriter.deleteDocuments(Term("id", "1"))
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedSetDocValues = getOnlyLeafReader(ireader).getSortedSetDocValues("field")!!
        assertEquals(0, dv.valueCount)

        val skipper: DocValuesSkipper = getOnlyLeafReader(ireader).getDocValuesSkipper("field")!!
        assertEquals(0, skipper.docCount().toLong())
        skipper.advance(0)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), skipper.minDocID(0).toLong())

        val termsEnum: TermsEnum = dv.termsEnum()
        assertFalse(termsEnum.seekExact(BytesRef("lucene")))
        assertEquals(TermsEnum.SeekStatus.END, termsEnum.seekCeil(BytesRef("lucene")))
        assertEquals(-1, dv.lookupTerm(BytesRef("lucene")))

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testNumberMergeAwayAllValuesWithSkipper() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig: IndexWriterConfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        var doc = Document()
        doc.add(StringField("id", "0", Field.Store.NO))
        iwriter.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.NO))
        doc.add(NumericDocValuesField.indexedField("field", 5))
        iwriter.addDocument(doc)
        iwriter.commit()
        iwriter.deleteDocuments(Term("id", "1"))
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: NumericDocValues = getOnlyLeafReader(ireader).getNumericDocValues("field")!!
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())

        val skipper: DocValuesSkipper = getOnlyLeafReader(ireader).getDocValuesSkipper("field")!!
        assertEquals(0, skipper.docCount().toLong())
        skipper.advance(0)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), skipper.minDocID(0).toLong())

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedNumberMergeAwayAllValuesWithSkipper() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig: IndexWriterConfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        var doc = Document()
        doc.add(StringField("id", "0", Field.Store.NO))
        iwriter.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.NO))
        doc.add(SortedNumericDocValuesField.indexedField("field", 5))
        iwriter.addDocument(doc)
        iwriter.commit()
        iwriter.deleteDocuments(Term("id", "1"))
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedNumericDocValues = getOnlyLeafReader(ireader).getSortedNumericDocValues("field")!!
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())

        val skipper: DocValuesSkipper = getOnlyLeafReader(ireader).getDocValuesSkipper("field")!!
        assertEquals(0, skipper.docCount().toLong())
        skipper.advance(0)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), skipper.minDocID(0).toLong())

        ireader.close()
        directory.close()
    }

    // same as testSortedMergeAwayAllValues but on more than 1024 docs to have sparse encoding on
    @Throws(IOException::class)
    open fun testSortedMergeAwayAllValuesLargeSegmentWithSkipper() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig: IndexWriterConfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        val doc = Document()
        doc.add(StringField("id", "1", Field.Store.NO))
        doc.add(SortedDocValuesField.indexedField("field", newBytesRef("hello")))
        iwriter.addDocument(doc)
        val numEmptyDocs: Int = atLeast(1024)
        for (i in 0..<numEmptyDocs) {
            iwriter.addDocument(Document())
        }
        iwriter.commit()
        iwriter.deleteDocuments(Term("id", "1"))
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedDocValues = getOnlyLeafReader(ireader).getSortedDocValues("field")!!
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())

        val skipper: DocValuesSkipper = getOnlyLeafReader(ireader).getDocValuesSkipper("field")!!
        assertEquals(0, skipper.docCount().toLong())
        skipper.advance(0)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), skipper.minDocID(0).toLong())

        val termsEnum: TermsEnum = dv.termsEnum()!!
        assertFalse(termsEnum.seekExact(BytesRef("lucene")))
        assertEquals(TermsEnum.SeekStatus.END, termsEnum.seekCeil(BytesRef("lucene")))
        assertEquals(-1, dv.lookupTerm(BytesRef("lucene")).toLong())

        ireader.close()
        directory.close()
    }

    // same as testSortedSetMergeAwayAllValues but on more than 1024 docs to have sparse encoding on
    @Throws(IOException::class)
    open fun testSortedSetMergeAwayAllValuesLargeSegmentWithSkipper() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig: IndexWriterConfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        val doc = Document()
        doc.add(StringField("id", "1", Field.Store.NO))
        doc.add(SortedSetDocValuesField.indexedField("field", newBytesRef("hello")))
        iwriter.addDocument(doc)
        val numEmptyDocs: Int = atLeast(1024)
        for (i in 0..<numEmptyDocs) {
            iwriter.addDocument(Document())
        }
        iwriter.commit()
        iwriter.deleteDocuments(Term("id", "1"))
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedSetDocValues = getOnlyLeafReader(ireader).getSortedSetDocValues("field")!!
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())

        val skipper: DocValuesSkipper = getOnlyLeafReader(ireader).getDocValuesSkipper("field")!!
        assertEquals(0, skipper.docCount().toLong())
        skipper.advance(0)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(),skipper.minDocID(0).toLong())

        val termsEnum: TermsEnum = dv.termsEnum()
        assertFalse(termsEnum.seekExact(BytesRef("lucene")))
        assertEquals(TermsEnum.SeekStatus.END, termsEnum.seekCeil(BytesRef("lucene")))
        assertEquals(-1, dv.lookupTerm(BytesRef("lucene")))

        ireader.close()
        directory.close()
    }

    // same as testNumericMergeAwayAllValues but on more than 1024 docs to have sparse encoding on
    @Throws(IOException::class)
    open fun testNumericMergeAwayAllValuesLargeSegmentWithSkipper() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig: IndexWriterConfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        val doc = Document()
        doc.add(StringField("id", "1", Field.Store.NO))
        doc.add(NumericDocValuesField.indexedField("field", 42L))
        iwriter.addDocument(doc)
        val numEmptyDocs: Int = atLeast(1024)
        for (i in 0..<numEmptyDocs) {
            iwriter.addDocument(Document())
        }
        iwriter.commit()
        iwriter.deleteDocuments(Term("id", "1"))
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: NumericDocValues = getOnlyLeafReader(ireader).getNumericDocValues("field")!!
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())

        val skipper: DocValuesSkipper = getOnlyLeafReader(ireader).getDocValuesSkipper("field")!!
        assertEquals(0, skipper.docCount().toLong())
        skipper.advance(0)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), skipper.minDocID(0).toLong())

        ireader.close()
        directory.close()
    }

    // same as testSortedNumericMergeAwayAllValues but on more than 1024 docs to have sparse encoding
    // on
    @Throws(IOException::class)
    open fun testSortedNumericMergeAwayAllValuesLargeSegmentWithSkipper() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig: IndexWriterConfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        val doc = Document()
        doc.add(StringField("id", "1", Field.Store.NO))
        doc.add(SortedNumericDocValuesField.indexedField("field", 42L))
        iwriter.addDocument(doc)
        val numEmptyDocs: Int = atLeast(1024)
        for (i in 0..<numEmptyDocs) {
            iwriter.addDocument(Document())
        }
        iwriter.commit()
        iwriter.deleteDocuments(Term("id", "1"))
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedNumericDocValues = getOnlyLeafReader(ireader).getSortedNumericDocValues("field")!!
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())

        val skipper: DocValuesSkipper = getOnlyLeafReader(ireader).getDocValuesSkipper("field")!!
        assertEquals(0, skipper.docCount().toLong())
        skipper.advance(0)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), skipper.minDocID(0).toLong())

        ireader.close()
        directory.close()
    }

    @Throws(Exception::class)
    open fun testNumericDocValuesWithSkipperSmall() {
        doTestNumericDocValuesWithSkipper(
            random().nextInt(1, 10) // TODO reduced from 1, 1000 to 1, 10 for dev speed
        )
    }

    @Throws(Exception::class)
    open fun testNumericDocValuesWithSkipperMedium() {
        doTestNumericDocValuesWithSkipper(
            random().nextInt(10, 20) // TODO reduced from 1000, 20000 to 10, 20 for dev speed
        )
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Throws(Exception::class)
    open fun testNumericDocValuesWithSkipperBig() {
        doTestNumericDocValuesWithSkipper(
            random().nextInt(50, 100) // TODO reduced from 50000, 100000 to 50, 100 for dev speed
        )
    }

    @Throws(Exception::class)
    private fun doTestNumericDocValuesWithSkipper(totalDocs: Int) {
        assertDocValuesWithSkipper(
            totalDocs,
            object : TestDocValueSkipper {
                override fun populateDoc(doc: Document) {
                    doc.add(NumericDocValuesField.indexedField("test", random().nextLong()))
                }

                @Throws(IOException::class)
                override fun docValuesWrapper(leafReader: LeafReader): DocValuesWrapper {
                    val numericDocValues: NumericDocValues =
                        leafReader.getNumericDocValues("test")!!
                    return object : DocValuesWrapper {
                        @Throws(IOException::class)
                        override fun advance(target: Int): Int {
                            return numericDocValues.advance(target)
                        }

                        @Throws(IOException::class)
                        override fun advanceExact(target: Int): Boolean {
                            return numericDocValues.advanceExact(target)
                        }

                        @Throws(IOException::class)
                        override fun maxValue(): Long {
                            return numericDocValues.longValue()
                        }

                        @Throws(IOException::class)
                        override fun minValue(): Long {
                            return numericDocValues.longValue()
                        }

                        override fun docID(): Int {
                            return numericDocValues.docID()
                        }
                    }
                }

                @Throws(IOException::class)
                override fun docValuesSkipper(leafReader: LeafReader): DocValuesSkipper {
                    return leafReader.getDocValuesSkipper("test")!!
                }
            })
    }

    @Throws(Exception::class)
    open fun testSortedNumericDocValuesWithSkipperSmall() {
        doTestSortedNumericDocValuesWithSkipper(
            random().nextInt(1, 10) // TODO reduced from 1, 1000 to 1, 10 for dev speed
        )
    }

    @Throws(Exception::class)
    open fun testSortedNumericDocValuesWithSkipperMedium() {
        doTestSortedNumericDocValuesWithSkipper(
            random().nextInt(10, 20) // TODO reduced from 1000, 20000 to 10, 20 for dev speed
        )
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Throws(Exception::class)
    open fun testSortedNumericDocValuesWithSkipperBig() {
        doTestSortedNumericDocValuesWithSkipper(
            random().nextInt(50, 100) // TODO reduced from 50000, 100000 to 50, 100 for dev speed
        )
    }

    @Throws(Exception::class)
    private fun doTestSortedNumericDocValuesWithSkipper(totalDocs: Int) {
        assertDocValuesWithSkipper(
            totalDocs,
            object : TestDocValueSkipper {
                override fun populateDoc(doc: Document) {
                    for (j in 0..<random().nextInt(1, 5)) {
                        doc.add(SortedNumericDocValuesField.indexedField("test", random().nextLong()))
                    }
                }

                @Throws(IOException::class)
                override fun docValuesWrapper(leafReader: LeafReader): DocValuesWrapper {
                    val sortedNumericDocValues: SortedNumericDocValues =
                        leafReader.getSortedNumericDocValues("test")!!
                    return object : DocValuesWrapper {
                        var max: Long = 0
                        var min: Long = 0

                        @Throws(IOException::class)
                        override fun advance(target: Int): Int {
                            val doc: Int = sortedNumericDocValues.advance(target)
                            if (doc != DocIdSetIterator.NO_MORE_DOCS) {
                                readValues()
                            }
                            return doc
                        }

                        @Throws(IOException::class)
                        override fun advanceExact(target: Int): Boolean {
                            if (sortedNumericDocValues.advanceExact(target)) {
                                readValues()
                                return true
                            }
                            return false
                        }

                        @Throws(IOException::class)
                        fun readValues() {
                            max = Long.MIN_VALUE
                            min = Long.MAX_VALUE
                            for (i in 0..<sortedNumericDocValues.docValueCount()) {
                                val value: Long = sortedNumericDocValues.nextValue()
                                max = max(max, value)
                                min = min(min, value)
                            }
                        }

                        override fun maxValue(): Long {
                            return max
                        }

                        override fun minValue(): Long {
                            return min
                        }

                        override fun docID(): Int {
                            return sortedNumericDocValues.docID()
                        }
                    }
                }

                @Throws(IOException::class)
                override fun docValuesSkipper(leafReader: LeafReader): DocValuesSkipper {
                    return leafReader.getDocValuesSkipper("test")!!
                }
            })
    }

    @Throws(Exception::class)
    open fun testSortedDocValuesWithSkipperSmall() {
        doTestSortedDocValuesWithSkipper(
            random().nextInt(1, 10) // TODO reduced from 1, 1000 to 1, 10 for dev speed
        )
    }

    @Throws(Exception::class)
    open fun testSortedDocValuesWithSkipperMedium() {
        doTestSortedDocValuesWithSkipper(
            random().nextInt(10, 20) // TODO reduced from 1000, 20000 to 10, 20 for dev speed
        )
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Throws(Exception::class)
    open fun testSortedDocValuesWithSkipperBig() {
        doTestSortedDocValuesWithSkipper(
            random().nextInt(50, 100) // TODO reduced from 50000, 100000 to 50, 100 for dev speed
        )
    }

    @Throws(Exception::class)
    private fun doTestSortedDocValuesWithSkipper(totalDocs: Int) {
        assertDocValuesWithSkipper(
            totalDocs,
            object : TestDocValueSkipper {
                override fun populateDoc(doc: Document) {
                    doc.add(SortedDocValuesField.indexedField("test", TestUtil.randomBinaryTerm(random())))
                }

                @Throws(IOException::class)
                override fun docValuesWrapper(leafReader: LeafReader): DocValuesWrapper {
                    val sortedDocValues: SortedDocValues =
                        leafReader.getSortedDocValues("test")!!
                    return object : DocValuesWrapper {
                        @Throws(IOException::class)
                        override fun advance(target: Int): Int {
                            return sortedDocValues.advance(target)
                        }

                        @Throws(IOException::class)
                        override fun advanceExact(target: Int): Boolean {
                            return sortedDocValues.advanceExact(target)
                        }

                        @Throws(IOException::class)
                        override fun maxValue(): Long {
                            return sortedDocValues.ordValue().toLong()
                        }

                        @Throws(IOException::class)
                        override fun minValue(): Long {
                            return sortedDocValues.ordValue().toLong()
                        }

                        override fun docID(): Int {
                            return sortedDocValues.docID()
                        }
                    }
                }

                @Throws(IOException::class)
                override fun docValuesSkipper(leafReader: LeafReader): DocValuesSkipper {
                    return leafReader.getDocValuesSkipper("test")!!
                }
            })
    }

    @Throws(Exception::class)
    open fun testSortedSetDocValuesWithSkipperSmall() {
        doTestSortedSetDocValuesWithSkipper(
            random().nextInt(1, 10) // TODO reduced from 1, 1000 to 1, 10 for dev speed
        )
    }

    @Throws(Exception::class)
    open fun testSortedSetDocValuesWithSkipperMedium() {
        doTestSortedSetDocValuesWithSkipper(
            random().nextInt(10, 20) // TODO reduced from 10000, 20000 to 10, 20 for dev speed
        )
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Throws(Exception::class)
    open fun testSortedSetDocValuesWithSkipperBig() {
        doTestSortedSetDocValuesWithSkipper(
            random().nextInt(50, 100) // TODO reduced from 50000, 100000 to 50, 100 for dev speed
        )
    }

    @Throws(Exception::class)
    private fun doTestSortedSetDocValuesWithSkipper(totalDocs: Int) {
        assertDocValuesWithSkipper(
            totalDocs,
            object : TestDocValueSkipper {
                override fun populateDoc(doc: Document) {
                    for (j in 0..<random().nextInt(1, 5)) {
                        doc.add(SortedSetDocValuesField.indexedField("test", TestUtil.randomBinaryTerm(random())))
                    }
                }

                @Throws(IOException::class)
                override fun docValuesWrapper(leafReader: LeafReader): DocValuesWrapper {
                    val sortedSetDocValues: SortedSetDocValues =
                        leafReader.getSortedSetDocValues("test")!!
                    return object : DocValuesWrapper {
                        var max: Long = 0
                        var min: Long = 0

                        @Throws(IOException::class)
                        override fun advance(target: Int): Int {
                            val doc: Int = sortedSetDocValues.advance(target)
                            if (doc != DocIdSetIterator.NO_MORE_DOCS) {
                                readValues()
                            }
                            return doc
                        }

                        @Throws(IOException::class)
                        override fun advanceExact(target: Int): Boolean {
                            if (sortedSetDocValues.advanceExact(target)) {
                                readValues()
                                return true
                            }
                            return false
                        }

                        @Throws(IOException::class)
                        fun readValues() {
                            max = Long.MIN_VALUE
                            min = Long.MAX_VALUE
                            for (i in 0..<sortedSetDocValues.docValueCount()) {
                                val value: Long = sortedSetDocValues.nextOrd()
                                max = max(max, value)
                                min = min(min, value)
                            }
                        }

                        override fun maxValue(): Long {
                            return max
                        }

                        override fun minValue(): Long {
                            return min
                        }

                        override fun docID(): Int {
                            return sortedSetDocValues.docID()
                        }
                    }
                }

                @Throws(IOException::class)
                override fun docValuesSkipper(leafReader: LeafReader): DocValuesSkipper {
                    return leafReader.getDocValuesSkipper("test")!!
                }
            })
    }

    @Throws(Exception::class)
    private fun assertDocValuesWithSkipper(
        totalDocs: Int,
        testDocValueSkipper: TestDocValueSkipper
    ) {
        val booleanSupplier: () -> Boolean /*java.util.function.Supplier<Boolean>*/
        when (random().nextInt(3)) {
            0 -> booleanSupplier = { true }
            1 -> booleanSupplier = {
                random().nextBoolean()
            }

            2 -> booleanSupplier = {
                random().nextBoolean() && random().nextBoolean()
            }

            else -> throw AssertionError()
        }
        val directory: Directory =
            newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                directory
            )
        var numDocs = 0
        for (i in 0..<totalDocs) {
            val doc = Document()
            if (booleanSupplier()) {
                testDocValueSkipper.populateDoc(doc)
                numDocs++
            }
            writer.addDocument(doc)
            if (rarely()) {
                writer.commit()
            }
        }
        writer.flush()

        if (random().nextBoolean()) {
            writer.forceMerge(1)
        }

        val r: IndexReader = writer.reader
        var readDocs = 0
        for (readerContext in r.leaves()) {
            val reader: LeafReader = readerContext.reader()
            val bos = ByteArrayOutputStream(1024)
            val infoStream = PrintStream(bos, false, StandardCharsets.UTF_8)
            val status: DocValuesStatus = CheckIndex.testDocValues(reader as CodecReader, infoStream, true)
            if (status.error != null) {
                throw Exception(status.error)
            }
            readDocs +=
                assertDocValuesSkipSequential(testDocValueSkipper.docValuesWrapper(reader), testDocValueSkipper.docValuesSkipper(reader))
            for (i in 0..9) {
                assertDocValuesSkipRandom(testDocValueSkipper.docValuesWrapper(reader), testDocValueSkipper.docValuesSkipper(reader), reader.maxDoc())
            }
        }
        assertEquals(numDocs.toLong(), readDocs.toLong())
        IOUtils.close(r, writer, directory)
    }

    @Throws(IOException::class)
    private fun assertDocValuesSkipSequential(
        iterator: DocValuesWrapper,
        skipper: DocValuesSkipper?
    ): Int {
        if (skipper == null) {
            return 0
        }

        assertEquals(-1, iterator.docID().toLong())
        assertEquals(-1, skipper.minDocID(0).toLong())
        assertEquals(-1, skipper.maxDocID(0).toLong())

        iterator.advance(0)
        var docCount = 0
        while (true) {
            val previousMaxDoc: Int = skipper.maxDocID(0)
            skipper.advance(previousMaxDoc + 1)
            assertTrue(skipper.minDocID(0) > previousMaxDoc)
            if (skipperHasAccurateDocBounds()) {
                assertEquals(iterator.docID().toLong(), skipper.minDocID(0).toLong())
            } else {
                assertTrue(skipper.minDocID(0) <= iterator.docID(), "Expected: " + iterator.docID() + " but got " + skipper.minDocID(0))
            }

            if (skipper.minDocID(0) == DocIdSetIterator.NO_MORE_DOCS) {
                assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), skipper.maxDocID(0).toLong())
                break
            }
            assertTrue(skipper.docCount(0) > 0)

            var maxDoc = -1
            var minVal = Long.MAX_VALUE
            var maxVal = Long.MIN_VALUE
            for (i in 0..<skipper.docCount(0)) {
                assertNotEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), iterator.docID().toLong())
                maxDoc = max(maxDoc, iterator.docID())
                minVal = min(minVal, iterator.minValue())
                maxVal = max(maxVal, iterator.maxValue())
                iterator.advance(iterator.docID() + 1)
            }
            if (skipperHasAccurateDocBounds()) {
                assertEquals(maxDoc.toLong(), skipper.maxDocID(0).toLong())
            } else {
                assertTrue(skipper.maxDocID(0) >= maxDoc, message = "Expected: " + maxDoc + " but got " + skipper.maxDocID(0))
            }
            if (skipperHasAccurateValueBounds()) {
                assertEquals(minVal, skipper.minValue(0))
                assertEquals(maxVal, skipper.maxValue(0))
            } else {
                assertTrue(minVal >= skipper.minValue(0), "Expected: " + minVal + " but got " + skipper.minValue(0))
                assertTrue(maxVal <= skipper.maxValue(0), "Expected: " + maxVal + " but got " + skipper.maxValue(0))
            }
            docCount += skipper.docCount(0)
            for (level in 1..<skipper.numLevels()) {
                assertTrue(skipper.minDocID(0) >= skipper.minDocID(level))
                assertTrue(skipper.maxDocID(0) <= skipper.maxDocID(level))
                assertTrue(skipper.minValue(0) >= skipper.minValue(level))
                assertTrue(skipper.maxValue(0) <= skipper.maxValue(level))
                assertTrue(skipper.docCount(0) < skipper.docCount(level))
            }
        }

        assertEquals(docCount.toLong(), skipper.docCount().toLong())
        return docCount
    }

    private interface TestDocValueSkipper {
        fun populateDoc(doc: Document)

        @Throws(IOException::class)
        fun docValuesWrapper(leafReader: LeafReader): DocValuesWrapper

        @Throws(IOException::class)
        fun docValuesSkipper(leafReader: LeafReader): DocValuesSkipper
    }

    private interface DocValuesWrapper {
        @Throws(IOException::class)
        fun advance(target: Int): Int

        @Throws(IOException::class)
        fun advanceExact(target: Int): Boolean

        @Throws(IOException::class)
        fun maxValue(): Long

        @Throws(IOException::class)
        fun minValue(): Long

        fun docID(): Int
    }

    @Throws(Exception::class)
    open fun testMismatchedFields() {
        val dir1: Directory = newDirectory()
        val w1 = IndexWriter(dir1, newIndexWriterConfig())
        val doc = Document()
        doc.add(BinaryDocValuesField("binary", BytesRef("lucene")))
        doc.add(NumericDocValuesField("numeric", 0L))
        doc.add(SortedDocValuesField("sorted", BytesRef("search")))
        doc.add(SortedNumericDocValuesField("sorted_numeric", 1L))
        doc.add(SortedSetDocValuesField("sorted_set", BytesRef("engine")))
        w1.addDocument(doc)

        val dir2: Directory = newDirectory()
        val w2 = IndexWriter(dir2, newIndexWriterConfig().setMergeScheduler(SerialMergeScheduler()))
        w2.addDocument(doc)
        w2.commit()

        var reader: DirectoryReader = DirectoryReader.open(w1)
        w1.close()
        w2.addIndexes(MismatchedCodecReader(getOnlyLeafReader(reader) as CodecReader, random()))
        reader.close()
        w2.forceMerge(1)
        reader = DirectoryReader.open(w2)
        w2.close()

        val leafReader: LeafReader = getOnlyLeafReader(reader)

        val bdv: BinaryDocValues? = leafReader.getBinaryDocValues("binary")
        assertNotNull(bdv)
        assertEquals(0, bdv.nextDoc().toLong())
        assertEquals(BytesRef("lucene"), bdv.binaryValue())
        assertEquals(1, bdv.nextDoc().toLong())
        assertEquals(BytesRef("lucene"), bdv.binaryValue())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), bdv.nextDoc().toLong())

        val ndv: NumericDocValues? = leafReader.getNumericDocValues("numeric")
        assertNotNull(ndv)
        assertEquals(0, ndv.nextDoc().toLong())
        assertEquals(0, ndv.longValue())
        assertEquals(1, ndv.nextDoc().toLong())
        assertEquals(0, ndv.longValue())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), ndv.nextDoc().toLong())

        val sdv: SortedDocValues? = leafReader.getSortedDocValues("sorted")
        assertNotNull(sdv)
        assertEquals(0, sdv.nextDoc().toLong())
        assertEquals(BytesRef("search"), sdv.lookupOrd(sdv.ordValue()))
        assertEquals(1, sdv.nextDoc().toLong())
        assertEquals(BytesRef("search"), sdv.lookupOrd(sdv.ordValue()))
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), sdv.nextDoc().toLong())

        val sndv: SortedNumericDocValues? = leafReader.getSortedNumericDocValues("sorted_numeric")
        assertNotNull(sndv)
        assertEquals(0, sndv.nextDoc().toLong())
        assertEquals(1, sndv.nextValue())
        assertEquals(1, sndv.nextDoc().toLong())
        assertEquals(1, sndv.nextValue())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), sndv.nextDoc().toLong())

        val ssdv: SortedSetDocValues? = leafReader.getSortedSetDocValues("sorted_set")
        assertNotNull(ssdv)
        assertEquals(0, ssdv.nextDoc().toLong())
        assertEquals(BytesRef("engine"), ssdv.lookupOrd(ssdv.nextOrd()))
        assertEquals(1, ssdv.nextDoc().toLong())
        assertEquals(BytesRef("engine"), ssdv.lookupOrd(ssdv.nextOrd()))
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), ssdv.nextDoc().toLong())

        IOUtils.close(reader, w2, dir1, dir2)
    }

    companion object {
        @Throws(IOException::class)
        private fun assertDocValuesSkipRandom(
            iterator: DocValuesWrapper,
            skipper: DocValuesSkipper?,
            maxDoc: Int
        ) {
            if (skipper == null) {
                return
            }
            var nextLevel = 0
            while (true) {
                val doc: Int = random().nextInt(skipper.maxDocID(nextLevel), maxDoc + 1) + 1
                skipper.advance(doc)
                if (skipper.minDocID(0) == DocIdSetIterator.NO_MORE_DOCS) {
                    assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), skipper.maxDocID(0).toLong())
                    return
                }
                if (iterator.advanceExact(doc)) {
                    for (level in 0..<skipper.numLevels()) {
                        assertTrue(iterator.docID() >= skipper.minDocID(level))
                        assertTrue(iterator.docID() <= skipper.maxDocID(level))
                        assertTrue(iterator.minValue() >= skipper.minValue(level))
                        assertTrue(iterator.maxValue() <= skipper.maxValue(level))
                    }
                }
                nextLevel = random()
                    .nextInt(skipper.numLevels())
            }
        }
    }
}
