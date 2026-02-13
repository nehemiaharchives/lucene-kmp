package org.gnit.lucenekmp.tests.index

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.simpletext.SimpleTextCodec
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StoredValue
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.EmptyDocValuesProducer
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexReaderContext
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.MergePolicy
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SegmentCommitInfo
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.index.SegmentInfos
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.SerialMergeScheduler
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.internal.tests.IndexWriterAccess
import org.gnit.lucenekmp.internal.tests.TestSecrets
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.PrintStream
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.TreeSet
import org.gnit.lucenekmp.jdkport.computeIfAbsent
import org.gnit.lucenekmp.jdkport.printStackTrace
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FilterDirectory
import org.gnit.lucenekmp.store.FilterIndexInput
import org.gnit.lucenekmp.store.FlushInfo
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RamUsageTester
import org.gnit.lucenekmp.tests.util.Rethrow
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.CloseableThreadLocal
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlinx.coroutines.Job
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest


/** Common tests to all index formats.  */
abstract class BaseIndexFileFormatTestCase : LuceneTestCase() {
    internal class Accumulator(private val root: Any) :
        RamUsageTester.Accumulator() {
        override fun accumulateObject(
            o: Any,
            shallowSize: Long,
            fieldValues: Collection<Any>,
            queue: MutableCollection<Any>
        ): Long {
            var clazz: KClass<*>? = o::class
            while (clazz != null) {
                if (EXCLUDED_CLASSES.contains(clazz) && o !== root) {
                    return 0
                }
                // KMP lacks general superclass reflection; only check the runtime class for now.
                clazz = null
            }
            // we have no way to estimate the size of these things in codecs although
            // something like a Collections.newSetFromMap(new HashMap<>()) uses quite
            // some memory... So for now the test ignores the overhead of such
            // collections but can we do better
            val v: Long
            if (o is MutableCollection<*>) {
                val coll = o
                for (item in coll) {
                    if (item != null) {
                        queue.add(item)
                    }
                }
                v =
                    coll.size.toLong() * RamUsageEstimator.NUM_BYTES_OBJECT_REF
            } else if (o is MutableMap<*, *>) {
                val map = o
                for (key in map.keys) {
                    if (key != null) {
                        queue.add(key)
                    }
                }
                for (value in map.values) {
                    if (value != null) {
                        queue.add(value)
                    }
                }
                v = 2L * map.size * RamUsageEstimator.NUM_BYTES_OBJECT_REF
            } else {
                val references: MutableList<Any> = mutableListOf()
                v = super.accumulateObject(o, shallowSize, fieldValues, references)
                for (r in references) {
                    // AssertingCodec adds Thread references to make sure objects are consumed in the right
                    // thread
                    if (r is Job == false) {
                        queue.add(r)
                    }
                }
            }
            return v
        }

        override fun accumulateArray(
            array: Any,
            shallowSize: Long,
            values: MutableList<Any>,
            queue: MutableCollection<Any>
        ): Long {
            val v: Long = super.accumulateArray(array, shallowSize, values, queue)
            // System.out.println(array.getClass() + "=" + v);
            return v
        }
    }

    /** Returns the codec to run tests against  */
    protected abstract val codec: Codec

    protected val createdVersionMajor: Int
        /** Returns the major version that this codec is compatible with.  */
        get() = Version.LATEST.major

    /** Set the created version of the given [Directory] and return it.  */
    @Throws(IOException::class)
    protected fun <D : Directory> applyCreatedVersionMajor(d: D): D {
        require(SegmentInfos.getLastCommitGeneration(d) == -1L) { "Cannot set the created version on a Directory that already has segments" }
        if (this.createdVersionMajor != Version.LATEST.major || random()
                .nextBoolean()
        ) {
            SegmentInfos(this.createdVersionMajor).commit(d)
        }
        return d
    }

    private var savedCodec: Codec? = null

    @Throws(Exception::class)
    @BeforeTest
    open fun setUp() {
        // set the default codec, so adding test cases to this isn't fragile
        savedCodec = Codec.default
        Codec.default = this.codec
    }

    @Throws(Exception::class)
    @AfterTest
    fun tearDown() {
        Codec.default = savedCodec!! // restore
    }

    /** Add random fields to the provided document.  */
    protected abstract fun addRandomFields(doc: Document)

    @Throws(IOException::class)
    private fun bytesUsedByExtension(d: Directory): MutableMap<String, Long> {
        val bytesUsedByExtension: MutableMap<String, Long> = mutableMapOf()
        for (file in d.listAll()) {
            if (IndexFileNames.CODEC_FILE_PATTERN.matches(file)) {
                val ext: String = IndexFileNames.getExtension(file)!!
                val previousLength: Long =
                    (if (bytesUsedByExtension.containsKey(ext)) bytesUsedByExtension[ext] else 0)!!
                bytesUsedByExtension[ext] = previousLength + d.fileLength(file)
            }
        }
        bytesUsedByExtension.keys.removeAll(excludedExtensionsFromByteCounts().toSet())

        return bytesUsedByExtension
    }

    /**
     * Return the list of extensions that should be excluded from byte counts when comparing indices
     * that store the same content.
     */
    protected fun excludedExtensionsFromByteCounts(): MutableCollection<String> {
        return mutableSetOf( // segment infos store various pieces of information that don't solely depend
                // on the content of the index in the diagnostics (such as a timestamp) so we
                // exclude this file from the bytes counts
                "si",  // lock files are 0 bytes (one directory in the test could be RAMDir, the other FSDir)
                "lock"
            )
    }

    /**
     * The purpose of this test is to make sure that bulk merge doesn't accumulate useless data over
     * runs.
     */
    @Throws(Exception::class)
    open fun testMergeStability() {
        assumeTrue(
            "merge is not stable",
            mergeIsStable()
        )
        val dir: Directory =
            applyCreatedVersionMajor(newDirectory())

        // do not use newMergePolicy that might return a MockMergePolicy that ignores the no-CFS ratio
        // do not use RIW which will change things up!
        var mp: MergePolicy = newTieredMergePolicy()
        mp.noCFSRatio = 0.0
        var cfg: IndexWriterConfig = IndexWriterConfig(MockAnalyzer(random()))
                .setUseCompoundFile(false)
                .setMergePolicy(mp)
        if (VERBOSE) {
            cfg.setInfoStream(PrintStream(autoFlush = false, out = ByteArrayOutputStream()))
        }
        var w = IndexWriter(dir, cfg)
        val numDocs: Int = atLeast(500)
        for (i in 0..<numDocs) {
            val d = Document()
            addRandomFields(d)
            w.addDocument(d)
        }
        w.forceMerge(1)
        w.commit()
        w.close()
        val reader: DirectoryReader =
            DirectoryReader.open(dir)

        val dir2: Directory =
            applyCreatedVersionMajor(newDirectory())
        mp = newTieredMergePolicy()
        mp.noCFSRatio = 0.0
        cfg =
            IndexWriterConfig(
                MockAnalyzer(
                    random()
                )
            )
                .setUseCompoundFile(false)
                .setMergePolicy(mp)
        w = IndexWriter(dir2, cfg)
        // TODO: TestUtil.addIndexesSlowly is not yet ported; no-op for now.

        w.commit()
        w.close()

        assertEquals(bytesUsedByExtension(dir), bytesUsedByExtension(dir2))

        reader.close()
        dir.close()
        dir2.close()
    }

    protected open fun mergeIsStable(): Boolean {
        return true
    }

    /** Calls close multiple times on closeable codec apis  */
    @Throws(IOException::class)
    fun testMultiClose() {
        // first make a one doc index
        val oneDocIndex: Directory =
            applyCreatedVersionMajor(newDirectory())
        val iw =
            IndexWriter(
                oneDocIndex,
                IndexWriterConfig(
                    MockAnalyzer(random())
                )
            )
        val oneDoc = Document()
        val customType =
            FieldType(TextField.TYPE_STORED)
        customType.setStoreTermVectors(true)
        val customField =
            Field("field", "contents", customType)
        oneDoc.add(customField)
        oneDoc.add(NumericDocValuesField("field", 5))
        iw.addDocument(oneDoc)
        val oneDocReader: LeafReader =
            getOnlyLeafReader(
                DirectoryReader.open(iw)
            )
        iw.close()

        // now feed to codec apis manually
        // we use FSDir, things like ramdir are not guaranteed to cause fails if you write to them after
        // close(), etc
        val dir: Directory =
            newFSDirectory(
                createTempDir("justSoYouGetSomeChannelErrors")
            )
        val codec: Codec = this.codec

        val segmentInfo =
            SegmentInfo(
                dir,
                Version.LATEST,
                Version.LATEST,
                "_0",
                1,
                false,
                false,
                codec,
                mutableMapOf(),
                StringHelper.randomId(),
                mutableMapOf(),
                null
            )
        val proto: FieldInfo = oneDocReader.fieldInfos.fieldInfo("field")!!
        val field =
            FieldInfo(
                proto.name,
                proto.number,
                proto.hasTermVectors(),
                proto.omitsNorms(),
                proto.hasPayloads(),
                proto.indexOptions,
                proto.docValuesType,
                proto.docValuesSkipIndexType(),
                proto.docValuesGen,
                HashMap(),
                proto.pointDimensionCount,
                proto.pointIndexDimensionCount,
                proto.pointNumBytes,
                proto.vectorDimension,
                proto.vectorEncoding,
                proto.vectorSimilarityFunction,
                proto.isSoftDeletesField,
                proto.isParentField
            )

        val fieldInfos = FieldInfos(arrayOf(field))

        val writeState =
            SegmentWriteState(
                null,
                dir,
                segmentInfo,
                fieldInfos,
                null,
                IOContext(FlushInfo(1, 20))
            )

        val readState =
            SegmentReadState(
                dir,
                segmentInfo,
                fieldInfos,
                IOContext.DEFAULT
            )

        // PostingsFormat
        val fakeNorms: NormsProducer =
            object : NormsProducer() {
                @Throws(IOException::class)
                override fun close() {
                }

                @Throws(IOException::class)
                override fun getNorms(field: FieldInfo): NumericDocValues? {
                    if (field.hasNorms() == false) {
                        return null
                    }
                    return oneDocReader.getNormValues(field.name)
                }

                @Throws(IOException::class)
                override fun checkIntegrity() {
                }
            }
        codec.postingsFormat().fieldsConsumer(writeState).use { consumer ->
            val fields: Fields =
                object : Fields() {
                    val indexedFields: TreeSet<String> = TreeSet(
                        FieldInfos.getIndexedFields(oneDocReader)
                    )

                    override fun iterator(): MutableIterator<String> {
                        return indexedFields.iterator()
                    }

                    @Throws(IOException::class)
                    override fun terms(field: String?): Terms? {
                        return oneDocReader.terms(field)
                    }

                    override fun size(): Int {
                        return indexedFields.size
                    }
                }
            consumer.write(fields, fakeNorms)
            IOUtils.close(consumer)
            IOUtils.close(consumer)
        }
        codec.postingsFormat().fieldsProducer(readState).use { producer ->
            IOUtils.close(producer)
            IOUtils.close(producer)
        }
        codec.docValuesFormat().fieldsConsumer(writeState).use { consumer ->
            consumer.addNumericField(
                field,
                object : EmptyDocValuesProducer() {
                    override fun getNumeric(field: FieldInfo): NumericDocValues {
                        return object : NumericDocValues() {
                            var docID: Int = -1

                            override fun docID(): Int {
                                return docID
                            }

                            override fun nextDoc(): Int {
                                docID++
                                if (docID == 1) {
                                    docID = NO_MORE_DOCS
                                }
                                return docID
                            }

                            override fun advance(target: Int): Int {
                                if (docID <= 0 && target == 0) {
                                    docID = 0
                                } else {
                                    docID = NO_MORE_DOCS
                                }
                                return docID
                            }

                            @Throws(IOException::class)
                            override fun advanceExact(target: Int): Boolean {
                                docID = target
                                return target == 0
                            }

                            override fun cost(): Long {
                                return 1
                            }

                            override fun longValue(): Long {
                                return 5
                            }
                        }
                    }
                })
            IOUtils.close(consumer)
            IOUtils.close(consumer)
        }
        codec.docValuesFormat().fieldsProducer(readState).use { producer ->
            IOUtils.close(producer)
            IOUtils.close(producer)
        }
        codec.normsFormat().normsConsumer(writeState).use { consumer ->
            consumer.addNormsField(
                field,
                object : NormsProducer() {
                    override fun getNorms(field: FieldInfo): NumericDocValues {
                        return object : NumericDocValues() {
                            var docID: Int = -1

                            override fun docID(): Int {
                                return docID
                            }

                            override fun nextDoc(): Int {
                                docID++
                                if (docID == 1) {
                                    docID = NO_MORE_DOCS
                                }
                                return docID
                            }

                            override fun advance(target: Int): Int {
                                if (docID <= 0 && target == 0) {
                                    docID = 0
                                } else {
                                    docID = NO_MORE_DOCS
                                }
                                return docID
                            }

                            @Throws(IOException::class)
                            override fun advanceExact(target: Int): Boolean {
                                docID = target
                                return target == 0
                            }

                            override fun cost(): Long {
                                return 1
                            }

                            override fun longValue(): Long {
                                return 5
                            }
                        }
                    }

                    override fun checkIntegrity() {}

                    override fun close() {}
                })
            IOUtils.close(consumer)
            IOUtils.close(consumer)
        }
        codec.normsFormat().normsProducer(readState).use { producer ->
            IOUtils.close(producer)
            IOUtils.close(producer)
        }
        codec.termVectorsFormat().vectorsWriter(dir, segmentInfo, writeState.context)
            .use { consumer ->
                consumer.startDocument(1)
                consumer.startField(field, 1, false, false, false)
                consumer.startTerm(BytesRef("testing"), 2)
                consumer.finishTerm()
                consumer.finishField()
                consumer.finishDocument()
                consumer.finish(1)
                IOUtils.close(consumer)
                IOUtils.close(consumer)
            }
        codec.termVectorsFormat().vectorsReader(dir, segmentInfo, fieldInfos, readState.context)
            .use { producer ->
                IOUtils.close(producer)
                IOUtils.close(producer)
            }
        codec.storedFieldsFormat().fieldsWriter(dir, segmentInfo, writeState.context)
            .use { consumer ->
                consumer.startDocument()
                val value: StoredValue = customField.storedValue()!!
                when (value.type) {
                    StoredValue.Type.INTEGER -> consumer.writeField(
                        field,
                        value.getIntValue()
                    )

                    StoredValue.Type.LONG -> consumer.writeField(
                        field,
                        value.getLongValue()
                    )

                    StoredValue.Type.FLOAT -> consumer.writeField(
                        field,
                        value.getFloatValue()
                    )

                    StoredValue.Type.DOUBLE -> consumer.writeField(
                        field,
                        value.getDoubleValue()
                    )

                    StoredValue.Type.BINARY -> consumer.writeField(
                        field,
                        value.binaryValue
                    )

                    StoredValue.Type.DATA_INPUT -> consumer.writeField(
                        field,
                        value.dataInputValue!!
                    )

                    StoredValue.Type.STRING -> consumer.writeField(
                        field,
                        value.stringValue
                    )

                    else -> throw AssertionError()
                }
                consumer.finishDocument()
                consumer.finish(1)
                IOUtils.close(consumer)
                IOUtils.close(consumer)
            }
        codec.storedFieldsFormat().fieldsReader(dir, segmentInfo, fieldInfos, readState.context)
            .use { producer ->
                IOUtils.close(producer)
                IOUtils.close(producer)
            }
        IOUtils.close(oneDocReader, oneDocIndex, dir)
    }

    /** Tests exception handling on write and openInput/createOutput  */ // TODO: this is really not ideal. each BaseXXXTestCase should have unit tests doing this.
    // but we use this shotgun approach to prevent bugs in the meantime: it just ensures the
    // codec does not corrupt the index or leak file handles.
    @Throws(Exception::class)
    fun testRandomExceptions() {
        // disable slow things: we don't rely upon sleeps here.
        val dir: MockDirectoryWrapper =
            applyCreatedVersionMajor(newMockDirectory())
        dir.setThrottling(MockDirectoryWrapper.Throttling.NEVER)
        dir.useSlowOpenClosers = false
        dir.randomIOExceptionRate = 0.001 // more rare

        // log all exceptions we hit, in case we fail (for debugging)
        val exceptionLog = ByteArrayOutputStream()
        val exceptionStream = PrintStream(exceptionLog, true, StandardCharsets.UTF_8)

        // PrintStream exceptionStream = System.out;
        val analyzer: Analyzer = MockAnalyzer(random())

        var conf: IndexWriterConfig =
            newIndexWriterConfig(analyzer)
        // just for now, try to keep this test reproducible
        conf.setMergeScheduler(SerialMergeScheduler())
        conf.setCodec(this.codec)

        val numDocs: Int = atLeast(500)

        var iw = IndexWriter(dir, conf)
        try {
            var allowAlreadyClosed = false
            for (i in 0..<numDocs) {
                dir.randomIOExceptionRateOnOpen = 0.02 // turn on exceptions for openInput/createOutput

                val doc = Document()
                doc.add(
                    newStringField(
                        "id",
                        i.toString(),
                        Field.Store.NO
                    )
                )
                addRandomFields(doc)

                // single doc
                try {
                    iw.addDocument(doc)
                    // we made it, sometimes delete our doc
                    iw.deleteDocuments(Term("id", i.toString()))
                } catch (ace: AlreadyClosedException) {
                    // OK: writer was closed by abort; we just reopen now:
                    dir.randomIOExceptionRateOnOpen =
                        0.0
                    // disable exceptions on openInput until next iteration
                    assertTrue(INDEX_WRITER_ACCESS.isDeleterClosed(iw))
                    assertTrue(allowAlreadyClosed)
                    allowAlreadyClosed = false
                    conf =
                        newIndexWriterConfig(analyzer)
                    // just for now, try to keep this test reproducible
                    conf.setMergeScheduler(SerialMergeScheduler())
                    conf.setCodec(this.codec)
                    iw = IndexWriter(dir, conf)
                } catch (e: IOException) {
                    handleFakeIOException(e, exceptionStream)
                    allowAlreadyClosed = true
                }

                if (random().nextInt(10) == 0) {
                    // trigger flush:
                    try {
                        if (random().nextBoolean()) {
                            var ir: DirectoryReader? = null
                            try {
                                ir = DirectoryReader.open(
                                    iw,
                                    random()
                                        .nextBoolean(),
                                    false
                                )
                                dir.randomIOExceptionRateOnOpen = 0.0
                                // disable exceptions on openInput until next iteration
                                TestUtil.checkReader(ir)
                            } finally {
                                IOUtils.closeWhileHandlingException(ir)
                            }
                        } else {
                            dir.randomIOExceptionRateOnOpen = 0.0
                            // disable exceptions on openInput until next iteration:
                            // or we make slowExists angry and trip a scarier assert!
                            iw.commit()
                        }
                        if (DirectoryReader.indexExists(dir)) {
                            TestUtil.checkIndex(dir)
                        }
                    } catch (ace: AlreadyClosedException) {
                        // OK: writer was closed by abort; we just reopen now:
                        dir.randomIOExceptionRateOnOpen = 0.0
                        // disable exceptions on openInput until next iteration
                        assertTrue(INDEX_WRITER_ACCESS.isDeleterClosed(iw))
                        assertTrue(allowAlreadyClosed)
                        allowAlreadyClosed = false
                        conf = newIndexWriterConfig(
                            analyzer
                        )
                        // just for now, try to keep this test reproducible
                        conf.setMergeScheduler(SerialMergeScheduler())
                        conf.setCodec(this.codec)
                        iw = IndexWriter(dir, conf)
                    } catch (e: IOException) {
                        handleFakeIOException(e, exceptionStream)
                        allowAlreadyClosed = true
                    }
                }
            }

            try {
                dir.randomIOExceptionRateOnOpen = 0.0
                // disable exceptions on openInput until next iteration:
                // or we make slowExists angry and trip a scarier assert!
                iw.close()
            } catch (e: IOException) {
                handleFakeIOException(e, exceptionStream)
                try {
                    iw.rollback()
                } catch (t: Throwable) {
                }
            }
            dir.close()
        } catch (t: Throwable) {
            println("Unexpected exception: dumping fake-exception-log:...")
            exceptionStream.flush()
            println(exceptionLog.toString(/*StandardCharsets.UTF_8*/))
            /*java.lang.System.out.flush()*/ // TODO not sure if we need to port this on kmp/kotlin common
            Rethrow.rethrow(t)
        }

        if (VERBOSE) {
            println("TEST PASSED: dumping fake-exception-log:...")
            println(exceptionLog.toString(/*StandardCharsets.UTF_8*/))
        }
    }

    private fun handleFakeIOException(
        e: IOException,
        exceptionStream: PrintStream
    ) {
        var ex: Throwable? = e
        while (ex != null) {
            if (ex.message != null && ex.message!!.startsWith("a random IOException")) {
                exceptionStream.println("\nTEST: got expected fake exc:" + ex.message)
                ex.printStackTrace(exceptionStream)
                return
            }
            ex = ex.cause
        }

        Rethrow.rethrow(e)
    }

    /**
     * Returns `false` if only the regular fields reader should be tested, and `true` if
     * only the merge instance should be tested.
     */
    protected fun shouldTestMergeInstance(): Boolean {
        return false
    }

    @Throws(IOException::class)
    protected fun maybeWrapWithMergingReader(r: DirectoryReader): DirectoryReader {
        var r: DirectoryReader = r
        if (shouldTestMergeInstance()) {
            r = MergingDirectoryReaderWrapper(r)
        }
        return r
    }

    /** A directory that tracks created files that haven't been deleted.  */
    protected class FileTrackingDirectoryWrapper
    /** Sole constructor.  */
    internal constructor(`in`: Directory) :
        FilterDirectory(`in`) {
        private val files: MutableSet<String> = mutableSetOf()
            /*java.util.concurrent.ConcurrentHashMap.newKeySet<String>()*/

        /** Get the set of created files.  */
        fun getFiles(): MutableSet<String> {
            return /*java.util.Set.copyOf<String>(files)*/ files.toMutableSet()
        }

        @Throws(IOException::class)
        override fun createOutput(
            name: String,
            context: IOContext
        ): IndexOutput {
            files.add(name)
            return super.createOutput(name, context)
        }

        @Throws(IOException::class)
        override fun rename(source: String, dest: String) {
            files.remove(source)
            files.add(dest)
            super.rename(source, dest)
        }

        @Throws(IOException::class)
        override fun deleteFile(name: String) {
            files.remove(name)
            super.deleteFile(name)
        }
    }

    private class ReadBytesIndexInputWrapper(
        `in`: IndexInput,
        /*java.util.function.IntConsumer*/private val readByte: (Int) -> Unit
    ) : FilterIndexInput(`in`.toString(), `in`) {

        override fun clone(): IndexInput {
            return ReadBytesIndexInputWrapper(`in`.clone(), readByte)
        }

        @Throws(IOException::class)
        override fun slice(
            sliceDescription: String,
            offset: Long,
            length: Long
        ): IndexInput {
            val slice: IndexInput =
                `in`.slice(sliceDescription, offset, length)
            return ReadBytesIndexInputWrapper(
                slice
            ) { o: Int ->
                readByte(Math.toIntExact(offset + o))
            }
        }

        @Throws(IOException::class)
        override fun readByte(): Byte {
            readByte(Math.toIntExact(filePointer))
            return `in`.readByte()
        }

        @Throws(IOException::class)
        override fun readBytes(b: ByteArray, offset: Int, len: Int) {
            val fp: Int = Math.toIntExact(filePointer)
            for (i in 0..<len) {
                readByte(Math.addExact(fp, i))
            }
            `in`.readBytes(b, offset, len)
        }
    }

    /** A directory that tracks read bytes.  */
    protected class ReadBytesDirectoryWrapper
    /** Sole constructor.  */
        (`in`: Directory) : FilterDirectory(`in`) {
        private val readBytes: MutableMap<String, FixedBitSet> = mutableMapOf()
            /*java.util.concurrent.ConcurrentHashMap<String, FixedBitSet>()*/

        /** Get information about which bytes have been read.  */
        fun getReadBytes(): MutableMap<String, FixedBitSet> {
            return readBytes.toMutableMap() /*java.util.Map.copyOf<String, FixedBitSet>(readBytes)*/
        }

        @Throws(IOException::class)
        override fun openInput(
            name: String,
            context: IOContext
        ): IndexInput {
            val `in`: IndexInput = super.openInput(name, context)
            val set: FixedBitSet =
                readBytes.computeIfAbsent(name) { `_`: String ->
                    FixedBitSet(
                        Math.toIntExact(`in`.length())
                    )
                }!!
            check(set.length().toLong() == `in`.length())
            return ReadBytesIndexInputWrapper(
                `in`
            ) { index: Int -> set.set(index) }
        }

        @Throws(IOException::class)
        override fun openChecksumInput(name: String): ChecksumIndexInput {
            val `in`: ChecksumIndexInput = super.openChecksumInput(name)
            val set: FixedBitSet =
                readBytes.computeIfAbsent(name) { `_`: String ->
                    FixedBitSet(
                        Math.toIntExact(`in`.length())
                    )
                }!!
            check(set.length().toLong() == `in`.length())
            return object : ChecksumIndexInput(`in`.toString()) {
                @Throws(IOException::class)
                override fun readBytes(b: ByteArray, offset: Int, len: Int) {
                    val fp: Int = Math.toIntExact(this.filePointer)
                    set.set(fp, Math.addExact(fp, len))
                    `in`.readBytes(b, offset, len)
                }

                @Throws(IOException::class)
                override fun readByte(): Byte {
                    set.set(Math.toIntExact(this.filePointer))
                    return `in`.readByte()
                }

                @Throws(IOException::class)
                override fun slice(
                    sliceDescription: String,
                    offset: Long,
                    length: Long
                ): IndexInput {
                    throw UnsupportedOperationException()
                }

                override fun length(): Long {
                    return `in`.length()
                }

                override val filePointer: Long
                    get() = `in`.filePointer

                @Throws(IOException::class)
                override fun close() {
                    `in`.close()
                }

                override val checksum: Long
                    get() = `in`.checksum
            }
        }

        @Throws(IOException::class)
        override fun createOutput(
            name: String,
            context: IOContext
        ): IndexOutput {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun createTempOutput(
            prefix: String,
            suffix: String,
            context: IOContext
        ): IndexOutput {
            throw UnsupportedOperationException()
        }
    }

    /**
     * This test is the best effort at verifying that checkIntegrity doesn't miss any files. It tests
     * that the combination of opening a reader and calling checkIntegrity on it reads all bytes of
     * all files.
     */
    @Throws(Exception::class)
    fun testCheckIntegrityReadsAllBytes() {
        assumeFalse(
            "SimpleText doesn't store checksums of its files",
            this.codec is SimpleTextCodec
        )
        val dir =
            FileTrackingDirectoryWrapper(newDirectory())
        applyCreatedVersionMajor(dir)

        val cfg = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, cfg)
        val numDocs: Int = atLeast(100)
        for (i in 0..<numDocs) {
            val d = Document()
            addRandomFields(d)
            w.addDocument(d)
        }
        w.forceMerge(1)
        w.commit()
        w.close()

        val readBytesWrapperDir = ReadBytesDirectoryWrapper(dir)
        val reader: IndexReader =
            DirectoryReader.open(readBytesWrapperDir)
        val leafReader: LeafReader = getOnlyLeafReader(reader)
        leafReader.checkIntegrity()

        val readBytesMap: MutableMap<String, FixedBitSet> =
            readBytesWrapperDir.getReadBytes()

        val unreadFiles: MutableSet<String> = HashSet(dir.getFiles())
        println(dir.listAll().contentToString())
        unreadFiles.removeAll(readBytesMap.keys)
        unreadFiles.remove(IndexWriter.WRITE_LOCK_NAME)
        assertTrue(unreadFiles.isEmpty(), message = "Some files have not been open: $unreadFiles")

        val messages: MutableList<String> = mutableListOf()
        for (entry in readBytesMap.entries) {
            val name = entry.key
            val unreadBytes: FixedBitSet = entry.value.clone()
            unreadBytes.flip(0, unreadBytes.length())
            val unread: Int = unreadBytes.nextSetBit(0)
            if (unread != Int.MAX_VALUE) {
                messages.add(
                    ("Offset "
                            + unread
                            + " of file "
                            + name
                            + " ("
                            + unreadBytes.length()
                            + " bytes) was not read.")
                )
            }
        }
        assertTrue(messages.isEmpty(), /*java.lang.String.join("\n", messages)*/ messages.joinToString("\n"))
        reader.close()
        dir.close()
    }

    companion object {
        private val INDEX_WRITER_ACCESS: IndexWriterAccess = TestSecrets.getIndexWriterAccess()

        // metadata or Directory-level objects
        private val EXCLUDED_CLASSES: MutableSet<KClass<*>> = mutableSetOf()
            /*java.util.Collections.newSetFromMap<KClass<*>>(java.util.IdentityHashMap<KClass<*>, Boolean>())*/

        init {
            // Directory objects, don't take into account, e.g. the NIO buffers
            EXCLUDED_CLASSES.add(Directory::class)
            EXCLUDED_CLASSES.add(IndexInput::class)

            // used for thread management, not by the index
            EXCLUDED_CLASSES.add(CloseableThreadLocal::class)
            /*EXCLUDED_CLASSES.add(java.lang.ThreadLocal::class)*/

            // don't follow references to the top-level reader
            EXCLUDED_CLASSES.add(IndexReader::class)
            EXCLUDED_CLASSES.add(IndexReaderContext::class)

            // usually small but can bump memory usage for
            // memory-efficient things like stored fields
            EXCLUDED_CLASSES.add(FieldInfos::class)
            EXCLUDED_CLASSES.add(SegmentInfo::class)
            EXCLUDED_CLASSES.add(SegmentCommitInfo::class)
            EXCLUDED_CLASSES.add(FieldInfo::class)

            // constant overhead is typically due to strings
            // TODO: can we remove this and still pass the test consistently
            EXCLUDED_CLASSES.add(String::class)
        }
    }
}
