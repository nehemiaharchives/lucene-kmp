package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.StandardDirectoryReader.Companion.open
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.IOFunction
import org.gnit.lucenekmp.util.IOUtils


/** Default implementation of [DirectoryReader].  */
class StandardDirectoryReader internal constructor(
    directory: Directory?,
    readers: Array<LeafReader?>?,
    writer: IndexWriter?,
    sis: SegmentInfos?,
    leafSorter: java.util.Comparator<LeafReader?>?,
    applyAllDeletes: Boolean,
    writeAllDeletes: Boolean
) : DirectoryReader(directory, readers, leafSorter) {
    val writer: IndexWriter?
    val segmentInfos: SegmentInfos?
    private val applyAllDeletes: Boolean
    private val writeAllDeletes: Boolean

    override fun toString(): String {
        val buffer = StringBuilder()
        buffer.append(this::class.simpleName)
        buffer.append('(')
        val segmentsFile: String? = segmentInfos.getSegmentsFileName()
        if (segmentsFile != null) {
            buffer.append(segmentsFile).append(":").append(segmentInfos.getVersion())
        }
        if (writer != null) {
            buffer.append(":nrt")
        }
        for (r in getSequentialSubReaders()) {
            buffer.append(' ')
            buffer.append(r)
        }
        buffer.append(')')
        return buffer.toString()
    }

    @Throws(IOException::class)
    protected override fun doOpenIfChanged(): DirectoryReader? {
        return doOpenIfChanged(null as IndexCommit?)
    }

    @Throws(IOException::class)
    protected override fun doOpenIfChanged(commit: IndexCommit?): DirectoryReader? {
        ensureOpen()

        // If we were obtained by writer.getReader(), re-ask the
        // writer to get a new reader.
        if (writer != null) {
            return doOpenFromWriter(commit)
        } else {
            return doOpenNoWriter(commit)
        }
    }

    @Throws(IOException::class)
    protected override fun doOpenIfChanged(writer: IndexWriter, applyAllDeletes: Boolean): DirectoryReader? {
        ensureOpen()
        if (writer === this.writer && applyAllDeletes == this.applyAllDeletes) {
            return doOpenFromWriter(null)
        } else {
            return writer.getReader(applyAllDeletes, writeAllDeletes)
        }
    }

    @Throws(IOException::class)
    private fun doOpenFromWriter(commit: IndexCommit?): DirectoryReader? {
        if (commit != null) {
            return doOpenFromCommit(commit)
        }

        if (writer.nrtIsCurrent(segmentInfos)) {
            return null
        }

        val reader: DirectoryReader = writer.getReader(applyAllDeletes, writeAllDeletes)

        // If in fact no changes took place, return null:
        if (reader.getVersion() === segmentInfos.getVersion()) {
            reader.decRef()
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
            if (directory !== commit.getDirectory()) {
                throw java.io.IOException("the specified commit does not match the specified Directory")
            }
            if (segmentInfos != null
                && commit.getSegmentsFileName().equals(segmentInfos.getSegmentsFileName())
            ) {
                return null
            }
        }

        return doOpenFromCommit(commit)
    }

    @Throws(IOException::class)
    private fun doOpenFromCommit(commit: IndexCommit?): DirectoryReader {
        return object : FindSegmentsFile<DirectoryReader?>(directory) {
            @Throws(IOException::class)
            protected override fun doBody(segmentFileName: String?): DirectoryReader? {
                val infos: SegmentInfos? = SegmentInfos.readCommit(directory, segmentFileName)
                return doOpenIfChanged(infos)
            }
        }.run(commit)
    }

    @Throws(IOException::class)
    fun doOpenIfChanged(infos: SegmentInfos?): DirectoryReader? {
        return open(
            directory, infos, getSequentialSubReaders(), subReadersSorter
        )
    }

    val version: Long
        get() {
            ensureOpen()
            return segmentInfos.getVersion()
        }

    /**
     * Return the [SegmentInfos] for this reader.
     *
     * @lucene.internal
     */
    fun getSegmentInfos(): SegmentInfos? {
        return segmentInfos
    }

    @get:Throws(IOException::class)
    val isCurrent: Boolean
        get() {
            ensureOpen()
            if (writer == null || writer.isClosed()) {
                // Fully read the segments file: this ensures that it's
                // completely written so that if
                // IndexWriter.prepareCommit has been called (but not
                // yet commit), then the reader will still see itself as
                // current:
                val sis: SegmentInfos = SegmentInfos.readLatestCommit(directory)

                // we loaded SegmentInfos from the directory
                return sis.getVersion() === segmentInfos.getVersion()
            } else {
                return writer.nrtIsCurrent(segmentInfos)
            }
        }

    @Throws(IOException::class)
    protected override fun doClose() {
        val decRefDeleter: java.io.Closeable =
            java.io.Closeable {
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
            val sequentialSubReaders: MutableList<out LeafReader?>? = getSequentialSubReaders()
            IOUtils.applyToAll(sequentialSubReaders, LeafReader::decRef)
        }
    }

    @get:Throws(IOException::class)
    val indexCommit: IndexCommit?
        get() {
            ensureOpen()
            return ReaderCommit(this, segmentInfos, directory)
        }

    internal class ReaderCommit(reader: StandardDirectoryReader?, infos: SegmentInfos, dir: Directory?) :
        IndexCommit() {
        val segmentsFileName: String
        var fileNames: MutableCollection<String?>?
        var dir: Directory?
        var generation: Long
        val userData: MutableMap<String?, String?>?
        val segmentCount: Int
        val reader: StandardDirectoryReader?

        init {
            segmentsFileName = infos.getSegmentsFileName()
            this.dir = dir
            userData = infos.getUserData()
            this.fileNames = java.util.Collections.unmodifiableCollection<T?>(infos.files(true))
            generation = infos.getGeneration()
            segmentCount = infos.size()

            // NOTE: we intentionally do not incRef this!  Else we'd need to make IndexCommit Closeable...
            this.reader = reader
        }

        override fun toString(): String {
            return "StandardDirectoryReader.ReaderCommit(" + segmentsFileName + " files=" + this.fileNames + ")"
        }

        val directory: Directory?
            get() = dir

        val isDeleted: Boolean
            get() = false

        public override fun delete() {
            throw UnsupportedOperationException("This IndexCommit does not support deletions")
        }
    }

    private val readerClosedListeners: MutableSet<ClosedListener?> =
        java.util.concurrent.CopyOnWriteArraySet<ClosedListener?>()

    private val cacheHelper: CacheHelper = object : CacheHelper() {
        private val cacheKey: CacheKey = CacheKey()

        val key: CacheKey
            get() = cacheKey

        public override fun addClosedListener(listener: ClosedListener?) {
            ensureOpen()
            readerClosedListeners.add(listener)
        }
    }

    /** package private constructor, called only from static open() methods.  */
    init {
        this.writer = writer
        this.segmentInfos = sis
        this.applyAllDeletes = applyAllDeletes
        this.writeAllDeletes = writeAllDeletes
    }

    @Throws(IOException::class)
    protected override fun notifyReaderClosedListeners() {
        synchronized(readerClosedListeners) {
            IOUtils.applyToAll(readerClosedListeners, { l -> l.onClose(cacheHelper.getKey()) })
        }
    }

    val readerCacheHelper: CacheHelper
        get() = cacheHelper

    companion object {
        @Throws(IOException::class)
        fun open(
            directory: Directory?, commit: IndexCommit?, leafSorter: java.util.Comparator<LeafReader?>?
        ): DirectoryReader? {
            return open(directory, Version.MIN_SUPPORTED_MAJOR, commit, leafSorter)
        }

        /** called from DirectoryReader.open(...) methods  */
        @Throws(IOException::class)
        fun open(
            directory: Directory?,
            minSupportedMajorVersion: Int,
            commit: IndexCommit?,
            leafSorter: java.util.Comparator<LeafReader?>?
        ): DirectoryReader {
            return object : FindSegmentsFile<DirectoryReader?>(directory) {
                @Throws(IOException::class)
                protected override fun doBody(segmentFileName: String?): DirectoryReader {
                    require(!(minSupportedMajorVersion > Version.LATEST.major || minSupportedMajorVersion < 0)) {
                        ("minSupportedMajorVersion must be positive and <= "
                                + Version.LATEST.major
                                + " but was: "
                                + minSupportedMajorVersion)
                    }
                    val sis: SegmentInfos =
                        SegmentInfos.readCommit(directory, segmentFileName, minSupportedMajorVersion)
                    val readers: Array<SegmentReader?> = kotlin.arrayOfNulls<SegmentReader>(sis.size())
                    var success = false
                    try {
                        for (i in sis.size() - 1 downTo 0) {
                            readers[i] =
                                SegmentReader(
                                    sis.info(i), sis.getIndexCreatedVersionMajor(), IOContext.DEFAULT
                                )
                        }
                        // This may throw CorruptIndexException if there are too many docs, so
                        // it must be inside try clause so we close readers in that case:
                        val reader: DirectoryReader =
                            StandardDirectoryReader(directory, readers, null, sis, leafSorter, false, false)
                        success = true

                        return reader
                    } finally {
                        if (success == false) {
                            IOUtils.closeWhileHandlingException(readers)
                        }
                    }
                }
            }.run(commit)
        }

        /** Used by near real-time search  */
        @Throws(IOException::class)
        fun open(
            writer: IndexWriter,
            readerFunction: IOFunction<SegmentCommitInfo?, SegmentReader?>,
            infos: SegmentInfos,
            applyAllDeletes: Boolean,
            writeAllDeletes: Boolean
        ): StandardDirectoryReader {
            // IndexWriter synchronizes externally before calling
            // us, which ensures infos will not change; so there's
            // no need to process segments in reverse order
            val numSegments: Int = infos.size()

            val readers: MutableList<SegmentReader?> = java.util.ArrayList<SegmentReader?>(numSegments)
            val dir: Directory? = writer.getDirectory()

            val segmentInfos: SegmentInfos = infos.clone()
            var infosUpto = 0
            try {
                for (i in 0..<numSegments) {
                    // NOTE: important that we use infos not
                    // segmentInfos here, so that we are passing the
                    // actual instance of SegmentInfoPerCommit in
                    // IndexWriter's segmentInfos:
                    val info: SegmentCommitInfo = infos.info(i)
                    require(info.info.dir === dir)
                    val reader: SegmentReader = readerFunction.apply(info)
                    if (reader.numDocs() > 0
                        || writer.getConfig().mergePolicy.keepFullyDeletedSegment({ reader })
                    ) {
                        // Steal the ref:
                        readers.add(reader)
                        infosUpto++
                    } else {
                        reader.decRef()
                        segmentInfos.remove(infosUpto)
                    }
                }

                writer.incRefDeleter(segmentInfos)

                val result =
                    StandardDirectoryReader(
                        dir,
                        readers.toTypedArray<SegmentReader?>(),
                        writer,
                        segmentInfos,
                        writer.getConfig().getLeafSorter(),
                        applyAllDeletes,
                        writeAllDeletes
                    )
                return result
            } catch (t: Throwable) {
                try {
                    IOUtils.applyToAll(readers, SegmentReader::decRef)
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
            directory: Directory?,
            infos: SegmentInfos,
            oldReaders: MutableList<out LeafReader?>?,
            leafSorter: java.util.Comparator<LeafReader?>?
        ): DirectoryReader? {
            // we put the old SegmentReaders in a map, that allows us
            // to lookup a reader using its segment name

            var segmentReaders = mutableMapOf<String?, Int?>()

            if (oldReaders != null) {
                segmentReaders = CollectionUtil.newHashMap(oldReaders.size)
                // create a Map SegmentName->SegmentReader
                var i = 0
                val c = oldReaders.size
                while (i < c) {
                    val sr: SegmentReader = oldReaders.get(i) as SegmentReader
                    segmentReaders.put(sr.getSegmentName(), i)
                    i++
                }
            }

            val newReaders: Array<SegmentReader?> = kotlin.arrayOfNulls<SegmentReader>(infos.size())
            for (i in infos.size() - 1 downTo 0) {
                val commitInfo: SegmentCommitInfo = infos.info(i)

                // find SegmentReader for this segment
                val oldReaderIndex = segmentReaders.get(commitInfo.info.name)
                val oldReader: SegmentReader?
                if (oldReaderIndex == null) {
                    // this is a new segment, no old SegmentReader can be reused
                    oldReader = null
                } else {
                    // there is an old reader for this segment - we'll try to reopen it
                    oldReader = oldReaders!!.get(oldReaderIndex) as SegmentReader?
                }

                // Make a best effort to detect when the app illegally "rm -rf" their
                // index while a reader was open, and then called openIfChanged:
                check(
                    !(oldReader != null
                            && (java.util.Arrays.equals(
                        commitInfo.info.getId(),
                        oldReader.getSegmentInfo().info.getId()
                    )
                            == false))
                ) {
                    ("same segment "
                            + commitInfo.info.name
                            + " has invalid doc count change; likely you are re-opening a reader after illegally removing index files yourself and building a new index in their place.  Use IndexWriter.deleteAll or open a new IndexWriter using OpenMode.CREATE instead")
                }

                var success = false
                try {
                    val newReader: SegmentReader?
                    if (oldReader == null
                        || (commitInfo.info.getUseCompoundFile()
                                !== oldReader.getSegmentInfo().info.getUseCompoundFile())
                    ) {
                        // this is a new reader; in case we hit an exception we can decRef it safely
                        newReader =
                            SegmentReader(commitInfo, infos.getIndexCreatedVersionMajor(), IOContext.DEFAULT)
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
                                        .readLiveDocs(commitInfo.info.dir, commitInfo, IOContext.READONCE)
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
                            if (oldReader.getSegmentInfo().getDelGen() === commitInfo.getDelGen()
                                && oldReader.getSegmentInfo().getFieldInfosGen() === commitInfo.getFieldInfosGen()
                            ) {
                                // No change; this reader will be shared between
                                // the old and the new one, so we must incRef
                                // it:
                                oldReader.incRef()
                                newReaders[i] = oldReader
                            } else {
                                // Steal the ref returned by SegmentReader ctor:
                                assert(commitInfo.info.dir === oldReader.getSegmentInfo().info.dir)

                                if (oldReader.getSegmentInfo().getDelGen() === commitInfo.getDelGen()) {
                                    // only DV updates
                                    newReaders[i] =
                                        SegmentReader(
                                            commitInfo,
                                            oldReader,
                                            oldReader.getLiveDocs(),
                                            oldReader.getHardLiveDocs(),
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
                                                .readLiveDocs(commitInfo.info.dir, commitInfo, IOContext.READONCE)
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
                        decRefWhileHandlingException(newReaders)
                    }
                }
            }
            return StandardDirectoryReader(
                directory, newReaders, null, infos, leafSorter, false, false
            )
        }

        // TODO: move somewhere shared if it's useful elsewhere
        private fun decRefWhileHandlingException(readers: Array<SegmentReader?>) {
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
