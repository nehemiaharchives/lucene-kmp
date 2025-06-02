package org.gnit.lucenekmp.index


import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.IOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.CollectionUtil
import org.gnit.lucenekmp.util.IOFunction
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.Version

/*import java.util.concurrent.CopyOnWriteArraySet*/

/** Default implementation of [DirectoryReader].  */
class StandardDirectoryReader internal constructor(
    directory: Directory,
    readers: Array<out LeafReader>,
    val writer: IndexWriter?,
    sis: SegmentInfos,
    leafSorter: Comparator<out LeafReader>?,
    private val applyAllDeletes: Boolean,
    private val writeAllDeletes: Boolean
) : DirectoryReader(directory, readers, leafSorter) {

    /**
     * Return the [SegmentInfos] for this reader.
     *
     * @lucene.internal
     */
    val segmentInfos: SegmentInfos = sis

    override fun toString(): String {
        val buffer = StringBuilder()
        buffer.append(this::class.simpleName)
        buffer.append('(')
        val segmentsFile: String? = segmentInfos.segmentsFileName
        if (segmentsFile != null) {
            buffer.append(segmentsFile).append(":").append(segmentInfos.version)
        }
        if (writer != null) {
            buffer.append(":nrt")
        }
        for (r in sequentialSubReaders) {
            buffer.append(' ')
            buffer.append(r)
        }
        buffer.append(')')
        return buffer.toString()
    }

    @Throws(IOException::class)
    override fun doOpenIfChanged(): DirectoryReader? {
        return doOpenIfChanged(null)
    }

    @Throws(IOException::class)
    override fun doOpenIfChanged(commit: IndexCommit?): DirectoryReader? {
        ensureOpen()

        // If we were obtained by writer.getReader(), re-ask the
        // writer to get a new reader.
        return if (writer != null) {
            doOpenFromWriter(commit)
        } else {
            doOpenNoWriter(commit)
        }
    }

    @Throws(IOException::class)
    override fun doOpenIfChanged(
        writer: IndexWriter,
        applyAllDeletes: Boolean
    ): DirectoryReader? {
        ensureOpen()
        return if (writer === this.writer && applyAllDeletes == this.applyAllDeletes) {
            doOpenFromWriter(null)
        } else {
            writer.getReader(applyAllDeletes, writeAllDeletes)
        }
    }

    @Throws(IOException::class)
    private fun doOpenFromWriter(commit: IndexCommit?): DirectoryReader? {
        if (commit != null) {
            return doOpenFromCommit(commit)
        }

        if (writer!!.nrtIsCurrent(segmentInfos)) {
            return null
        }

        val reader: DirectoryReader = writer.getReader(applyAllDeletes, writeAllDeletes)

        // If in fact no changes took place, return null:
        if (reader.version == segmentInfos.version) {
            runBlocking {
                reader.decRef()
            }
            return null
        }

        return reader
    }

    @Throws(IOException::class)
    private fun doOpenNoWriter(commit: IndexCommit?): DirectoryReader? {
        if (commit == null) {
            if (this.isCurrent) {
                return null
            }
        } else {
            if (directory !== commit.directory) {
                throw IOException("the specified commit does not match the specified Directory")
            }
            if (segmentInfos != null
                && commit.segmentsFileName == segmentInfos.segmentsFileName
            ) {
                return null
            }
        }

        return doOpenFromCommit(commit!!)
    }

    @Throws(IOException::class)
    private fun doOpenFromCommit(commit: IndexCommit): DirectoryReader {
        return object :
            SegmentInfos.FindSegmentsFile<DirectoryReader>(directory) {
            @Throws(IOException::class)
            override fun doBody(segmentFileName: String): DirectoryReader {
                val infos: SegmentInfos =
                    SegmentInfos.readCommit(directory, segmentFileName)
                return doOpenIfChanged(infos)
            }
        }.run(commit)
    }

    @Throws(IOException::class)
    fun doOpenIfChanged(infos: SegmentInfos): DirectoryReader {
        return open(
            directory, infos, sequentialSubReaders, subReadersSorter
        )
    }

    override val version: Long
        get() {
            ensureOpen()
            return segmentInfos.version
        }

    override val isCurrent: Boolean
        get() {
            ensureOpen()
            if (writer == null || writer.isClosed()) {
                // Fully read the segments file: this ensures that it's
                // completely written so that if
                // IndexWriter.prepareCommit has been called (but not
                // yet commit), then the reader will still see itself as
                // current:
                val sis: SegmentInfos =
                    SegmentInfos.readLatestCommit(directory)

                // we loaded SegmentInfos from the directory
                return sis.version == segmentInfos.version
            } else {
                return writer.nrtIsCurrent(segmentInfos)
            }
        }

    @Throws(IOException::class)
    override fun doClose() {
        val decRefDeleter = AutoCloseable {
            if (writer != null) {
                try {
                    writer.decRefDeleter(segmentInfos)
                } catch (ex: AlreadyClosedException) {
                    // This is OK, it just means our original writer was
                    // closed before we were, and this may leave some
                    // un-referenced files in the index, which is
                    // harmless.  The next time IW is opened on the
                    // index, it will delete them.
                }
            }
        }
        decRefDeleter.use { `_` ->
            // try to close each reader, even if an exception is thrown
            val sequentialSubReaders: List<LeafReader> = sequentialSubReaders
            IOUtils.applyToAll(
                sequentialSubReaders
            ) { obj: LeafReader -> runBlocking { obj.decRef() } }
        }
    }

    override val indexCommit: IndexCommit
        get() {
            ensureOpen()
            return ReaderCommit(this, segmentInfos, directory)
        }

    internal class ReaderCommit(
        override val reader: StandardDirectoryReader?,
        infos: SegmentInfos,
        var dir: Directory
    ) : IndexCommit() {
        override val segmentsFileName: String? = infos.segmentsFileName
        override var fileNames: MutableCollection<String> = infos.files(true)
        override var generation: Long = infos.generation
        override val userData: MutableMap<String, String> = infos.userData
        override val segmentCount: Int = infos.size()

        // NOTE: we intentionally do not incRef this!  Else we'd need to make IndexCommit Closeable...

        override fun toString(): String {
            return "StandardDirectoryReader.ReaderCommit(" + segmentsFileName + " files=" + this.fileNames + ")"
        }

        override val directory: Directory
            get() = dir

        override val isDeleted: Boolean
            get() = false

        override fun delete() {
            throw UnsupportedOperationException("This IndexCommit does not support deletions")
        }
    }

    private val readerClosedListeners: MutableSet<ClosedListener> = mutableSetOf()

    /*java.util.concurrent.CopyOnWriteArraySet<ClosedListener>()*/
    private val readerClosedListenerLock = Mutex()

    private val cacheHelper: CacheHelper =
        object : CacheHelper {
            private val cacheKey: CacheKey =
                CacheKey()

            override val key: CacheKey
                get() = cacheKey

            override suspend fun addClosedListener(listener: ClosedListener) {
                ensureOpen()
                readerClosedListeners.add(listener)
            }
        }

    override suspend fun notifyReaderClosedListeners() {
        readerClosedListenerLock.withLock {
            IOUtils.applyToAll(
                readerClosedListeners
            ) { l: ClosedListener ->
                l.onClose(cacheHelper.key)
            }
        }
    }

    override val readerCacheHelper: CacheHelper
        get() = cacheHelper

    companion object {
        @Throws(IOException::class)
        fun open(
            directory: Directory,
            commit: IndexCommit?,
            leafSorter: Comparator<LeafReader>?
        ): DirectoryReader {
            return open(directory, Version.MIN_SUPPORTED_MAJOR, commit, leafSorter)
        }

        /** called from DirectoryReader.open(...) methods  */
        @Throws(IOException::class)
        fun open(
            directory: Directory,
            minSupportedMajorVersion: Int,
            commit: IndexCommit?,
            leafSorter: Comparator<out LeafReader>?
        ): DirectoryReader {
            return object :
                SegmentInfos.FindSegmentsFile<DirectoryReader>(
                    directory
                ) {
                @Throws(IOException::class)
                override fun doBody(segmentFileName: String): DirectoryReader {
                    require(!(minSupportedMajorVersion > Version.LATEST.major || minSupportedMajorVersion < 0)) {
                        ("minSupportedMajorVersion must be positive and <= "
                                + Version.LATEST.major
                                + " but was: "
                                + minSupportedMajorVersion)
                    }
                    val sis: SegmentInfos =
                        SegmentInfos.readCommit(
                            directory,
                            segmentFileName,
                            minSupportedMajorVersion
                        )
                    val readers: Array<SegmentReader> =
                        kotlin.arrayOfNulls<SegmentReader>(sis.size()) as Array<SegmentReader>
                    var success = false
                    try {
                        for (i in sis.size() - 1 downTo 0) {
                            readers[i] =
                                SegmentReader(
                                    sis.info(i),
                                    sis.indexCreatedVersionMajor,
                                    IOContext.DEFAULT
                                )
                        }
                        // This may throw CorruptIndexException if there are too many docs, so
                        // it must be inside try clause so we close readers in that case:
                        val reader: DirectoryReader =
                            StandardDirectoryReader(directory, readers, null, sis, leafSorter,
                                applyAllDeletes = false,
                                writeAllDeletes = false
                            )
                        success = true

                        return reader
                    } finally {
                        if (success == false) {
                            IOUtils.closeWhileHandlingException(*readers)
                        }
                    }
                }
            }.run(commit)
        }

        /** Used by near real-time search  */
        @Throws(IOException::class)
        fun open(
            writer: IndexWriter,
            readerFunction: IOFunction<SegmentCommitInfo, SegmentReader>,
            infos: SegmentInfos,
            applyAllDeletes: Boolean,
            writeAllDeletes: Boolean
        ): StandardDirectoryReader {
            // IndexWriter synchronizes externally before calling
            // us, which ensures infos will not change; so there's
            // no need to process segments in reverse order
            val numSegments: Int = infos.size()

            val readers: MutableList<SegmentReader> =
                ArrayList(numSegments)
            val dir: Directory = writer.getDirectory()

            val segmentInfos: SegmentInfos = infos.clone()
            var infosUpto = 0
            try {
                for (i in 0..<numSegments) {
                    // NOTE: important that we use infos not
                    // segmentInfos here, so that we are passing the
                    // actual instance of SegmentInfoPerCommit in
                    // IndexWriter's segmentInfos:
                    val info: SegmentCommitInfo = infos.info(i)
                    assert(info.info.dir === dir)
                    val reader: SegmentReader = readerFunction.apply(info)
                    if (reader.numDocs() > 0
                        || writer.config!!.mergePolicy!!.keepFullyDeletedSegment { reader }
                    ) {
                        // Steal the ref:
                        readers.add(reader)
                        infosUpto++
                    } else {
                        runBlocking { reader.decRef() }
                        segmentInfos.remove(infosUpto)
                    }
                }

                writer.incRefDeleter(segmentInfos)

                val result =
                    StandardDirectoryReader(
                        dir,
                        readers.toTypedArray<SegmentReader>(),
                        writer,
                        segmentInfos,
                        writer.config!!.leafSorter,
                        applyAllDeletes,
                        writeAllDeletes
                    )
                return result
            } catch (t: Throwable) {
                try {
                    IOUtils.applyToAll(
                        readers
                    ) { obj: SegmentReader -> runBlocking { obj.decRef() } }
                } catch (t1: Throwable) {
                    t.addSuppressed(t1)
                }
                throw t
            }
        }

        /**
         * This constructor is only used for [.doOpenIfChanged], as well as NRT
         * replication.
         *
         * @lucene.internal
         */
        @Throws(IOException::class)
        fun open(
            directory: Directory,
            infos: SegmentInfos,
            oldReaders: List<LeafReader>,
            leafSorter: Comparator<out LeafReader>?
        ): DirectoryReader {
            // we put the old SegmentReaders in a map, that allows us
            // to lookup a reader using its segment name

            var segmentReaders = mutableMapOf<String, Int>()

            if (oldReaders != null) {
                segmentReaders = CollectionUtil.newHashMap(oldReaders.size)
                // create a Map SegmentName->SegmentReader
                var i = 0
                val c = oldReaders.size
                while (i < c) {
                    val sr: SegmentReader =
                        oldReaders[i] as SegmentReader
                    segmentReaders.put(sr.segmentName, i)
                    i++
                }
            }

            val newReaders: Array<SegmentReader> =
                kotlin.arrayOfNulls<SegmentReader>(infos.size()) as Array<SegmentReader>
            for (i in infos.size() - 1 downTo 0) {
                val commitInfo: SegmentCommitInfo = infos.info(i)

                // find SegmentReader for this segment
                val oldReaderIndex = segmentReaders[commitInfo.info.name]
                val oldReader = if (oldReaderIndex == null) {
                    // this is a new segment, no old SegmentReader can be reused
                    null
                } else {
                    // there is an old reader for this segment - we'll try to reopen it
                    oldReaders[oldReaderIndex] as SegmentReader
                }

                // Make a best effort to detect when the app illegally "rm -rf" their
                // index while a reader was open, and then called openIfChanged:
                check(
                    !(oldReader != null && !commitInfo.info.getId().contentEquals(oldReader.segmentInfo.info.getId()))
                ) {
                    ("same segment "
                            + commitInfo.info.name
                            + " has invalid doc count change; likely you are re-opening a reader after illegally removing index files yourself and building a new index in their place.  Use IndexWriter.deleteAll or open a new IndexWriter using OpenMode.CREATE instead")
                }

                var success = false
                try {
                    val newReader: SegmentReader
                    if (oldReader == null
                        || (commitInfo.info.useCompoundFile
                                != oldReader.segmentInfo.info.useCompoundFile)
                    ) {
                        // this is a new reader; in case we hit an exception we can decRef it safely
                        newReader =
                            SegmentReader(
                                commitInfo,
                                infos.indexCreatedVersionMajor,
                                IOContext.DEFAULT
                            )
                        newReaders[i] = newReader
                    } else {
                        if (oldReader.isNRT) {
                            // We must load liveDocs/DV updates from disk:
                            val liveDocs: Bits? =
                                if (commitInfo.hasDeletions())
                                    commitInfo
                                        .info
                                        .getCodec()
                                        .liveDocsFormat()
                                        .readLiveDocs(
                                            commitInfo.info.dir,
                                            commitInfo,
                                            IOContext.READONCE
                                        )
                                else
                                    null
                            newReaders[i] =
                                SegmentReader(
                                    commitInfo,
                                    oldReader,
                                    liveDocs,
                                    liveDocs,
                                    commitInfo.info.maxDoc() - commitInfo.getDelCount(),
                                    false
                                )
                        } else {
                            if (oldReader.segmentInfo.delGen == commitInfo.delGen
                                && oldReader.segmentInfo.fieldInfosGen == commitInfo.fieldInfosGen
                            ) {
                                // No change; this reader will be shared between
                                // the old and the new one, so we must incRef
                                // it:
                                oldReader.incRef()
                                newReaders[i] = oldReader
                            } else {
                                // Steal the ref returned by SegmentReader ctor:
                                assert(commitInfo.info.dir === oldReader.segmentInfo.info.dir)

                                if (oldReader.segmentInfo.delGen == commitInfo.delGen) {
                                    // only DV updates
                                    newReaders[i] =
                                        SegmentReader(
                                            commitInfo,
                                            oldReader,
                                            oldReader.liveDocs,
                                            oldReader.hardLiveDocs,
                                            oldReader.numDocs(),
                                            false
                                        ) // this is not an NRT reader!
                                } else {
                                    // both DV and liveDocs have changed
                                    val liveDocs: Bits? =
                                        if (commitInfo.hasDeletions())
                                            commitInfo
                                                .info
                                                .getCodec()
                                                .liveDocsFormat()
                                                .readLiveDocs(
                                                    commitInfo.info.dir,
                                                    commitInfo,
                                                    IOContext.READONCE
                                                )
                                        else
                                            null
                                    newReaders[i] =
                                        SegmentReader(
                                            commitInfo,
                                            oldReader,
                                            liveDocs,
                                            liveDocs,
                                            commitInfo.info.maxDoc() - commitInfo.getDelCount(),
                                            false
                                        )
                                }
                            }
                        }
                    }
                    success = true
                } finally {
                    if (!success) {
                        runBlocking {
                            decRefWhileHandlingException(newReaders)
                        }
                    }
                }
            }
            return StandardDirectoryReader(
                directory, newReaders, null, infos, leafSorter, applyAllDeletes = false, writeAllDeletes = false
            )
        }

        // TODO: move somewhere shared if it's useful elsewhere
        private suspend fun decRefWhileHandlingException(readers: Array<SegmentReader>) {
            for (reader in readers) {
                if (reader != null) {
                    try {
                        reader.decRef()
                    } catch (t: Throwable) {
                        // Ignore so we keep throwing original exception
                    }
                }
            }
        }
    }
}
