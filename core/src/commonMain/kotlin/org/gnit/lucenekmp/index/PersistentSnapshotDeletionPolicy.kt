/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.withCurrentCallPathHint

/**
 * A [SnapshotDeletionPolicy] which adds a persistence layer so that snapshots can be
 * maintained across the life of an application. The snapshots are persisted in a [Directory]
 * and are committed as soon as [snapshot] or [release] is called.
 *
 * <p><b>NOTE:</b> Sharing [PersistentSnapshotDeletionPolicy]s that write to the same
 * directory across [IndexWriter]s will corrupt snapshots. You should make sure every [IndexWriter]
 * has its own [PersistentSnapshotDeletionPolicy] and that they all write to a
 * different [Directory]. It is OK to use the same Directory that holds the index.
 *
 * <p>This class adds a [release] method to release commits from a previous snapshot's
 * [IndexCommit.generation].
 *
 * @lucene.experimental
 */
class PersistentSnapshotDeletionPolicy : SnapshotDeletionPolicy {
    companion object {
        /** Prefix used for the save file.  */
        const val SNAPSHOTS_PREFIX = "snapshots_"

        private const val VERSION_START = 0
        private const val VERSION_CURRENT = VERSION_START
        private const val CODEC_NAME = "snapshots"
    }

    // The index writer which maintains the snapshots metadata
    private var nextWriteGen: Long = 0

    private val dir: Directory

    /**
     * [PersistentSnapshotDeletionPolicy] wraps another [IndexDeletionPolicy] to enable
     * flexible snapshotting, passing [OpenMode.CREATE_OR_APPEND] by default.
     *
     * @param primary the [IndexDeletionPolicy] that is used on non-snapshotted commits.
     * Snapshotted commits, by definition, are not deleted until explicitly released via [release].
     * @param dir the [Directory] which will be used to persist the snapshots information.
     */
    @Throws(IOException::class)
    constructor(primary: IndexDeletionPolicy, dir: Directory) : this(primary, dir, OpenMode.CREATE_OR_APPEND)

    /**
     * [PersistentSnapshotDeletionPolicy] wraps another [IndexDeletionPolicy] to enable
     * flexible snapshotting.
     *
     * @param primary the [IndexDeletionPolicy] that is used on non-snapshotted commits.
     * Snapshotted commits, by definition, are not deleted until explicitly released via [release].
     * @param dir the [Directory] which will be used to persist the snapshots information.
     * @param mode specifies whether a new index should be created, deleting all existing snapshots
     * information (immediately), or open an existing index, initializing the class with the
     * snapshots information.
     */
    @Throws(IOException::class)
    constructor(primary: IndexDeletionPolicy, dir: Directory, mode: OpenMode) : super(primary) {
        this.dir = dir

        if (mode == OpenMode.CREATE) {
            clearPriorSnapshots()
        }

        loadPriorSnapshots()

        if (mode == OpenMode.APPEND && nextWriteGen == 0L) {
            throw IllegalStateException("no snapshots stored in this directory")
        }
    }

    /**
     * Snapshots the last commit. Once this method returns, the snapshot information is persisted in
     * the directory.
     *
     * @see SnapshotDeletionPolicy.snapshot
     */
    @Throws(IOException::class)
    override fun snapshot(): IndexCommit {
        val ic = super.snapshot()
        var success = false
        try {
            persist()
            success = true
        } finally {
            if (!success) {
                try {
                    super.release(ic)
                } catch (_: Exception) {
                    // Suppress so we keep throwing original exception
                }
            }
        }
        return ic
    }

    /**
     * Deletes a snapshotted commit. Once this method returns, the snapshot information is persisted
     * in the directory.
     *
     * @see SnapshotDeletionPolicy.release
     */
    @Throws(IOException::class)
    override fun release(commit: IndexCommit) {
        super.release(commit)
        var success = false
        try {
            persist()
            success = true
        } finally {
            if (!success) {
                try {
                    incRef(commit)
                } catch (_: Exception) {
                    // Suppress so we keep throwing original exception
                }
            }
        }
    }

    /**
     * Deletes a snapshotted commit by generation. Once this method returns, the snapshot information
     * is persisted in the directory.
     *
     * @see IndexCommit.generation
     * @see SnapshotDeletionPolicy.release
     */
    @Throws(IOException::class)
    fun release(gen: Long) {
        super.releaseGen(gen)
        persist()
    }

    @Throws(IOException::class)
    private fun persist() {
        withCurrentCallPathHint("org.gnit.lucenekmp.index.PersistentSnapshotDeletionPolicy", "persist") {
            val fileName = SNAPSHOTS_PREFIX + nextWriteGen
            var success = false
            try {
                dir.createOutput(fileName, IOContext.DEFAULT).use { out ->
                    CodecUtil.writeHeader(out, CODEC_NAME, VERSION_CURRENT)
                    out.writeVInt(refCounts.size)
                    for (ent in refCounts.entries) {
                        out.writeVLong(ent.key)
                        out.writeVInt(ent.value)
                    }
                    success = true
                }
            } finally {
                if (!success) {
                    IOUtils.deleteFilesIgnoringExceptions(dir, fileName)
                }
            }

            dir.sync(mutableListOf(fileName))

            if (nextWriteGen > 0) {
                val lastSaveFile = SNAPSHOTS_PREFIX + (nextWriteGen - 1)
                // exception OK: likely it didn't exist
                IOUtils.deleteFilesIgnoringExceptions(dir, lastSaveFile)
            }

            nextWriteGen++
        }
    }

    @Throws(IOException::class)
    private fun clearPriorSnapshots() {
        for (file in dir.listAll()) {
            if (file.startsWith(SNAPSHOTS_PREFIX)) {
                dir.deleteFile(file)
            }
        }
    }

    /**
     * Returns the file name the snapshots are currently saved to, or null if no snapshots have been
     * saved.
     */
    fun getLastSaveFile(): String? {
        return if (nextWriteGen == 0L) {
            null
        } else {
            SNAPSHOTS_PREFIX + (nextWriteGen - 1)
        }
    }

    /**
     * Reads the snapshots information from the given [Directory]. This method can be used if
     * the snapshots information is needed, however you cannot instantiate the deletion policy
     * (because e.g., some other process keeps a lock on the snapshots directory).
     */
    @Throws(IOException::class)
    private fun loadPriorSnapshots() {
        var genLoaded = -1L
        var ioe: IOException? = null
        val snapshotFiles: MutableList<String> = ArrayList()
        for (file in dir.listAll()) {
            if (file.startsWith(SNAPSHOTS_PREFIX)) {
                val gen = file.substring(SNAPSHOTS_PREFIX.length).toLong()
                if (genLoaded == -1L || gen > genLoaded) {
                    snapshotFiles.add(file)
                    val m: MutableMap<Long, Int> = HashMap()
                    val input: IndexInput = dir.openInput(file, IOContext.DEFAULT)
                    try {
                        CodecUtil.checkHeader(input, CODEC_NAME, VERSION_START, VERSION_START)
                        val count = input.readVInt()
                        for (i in 0..<count) {
                            val commitGen = input.readVLong()
                            val refCount = input.readVInt()
                            m[commitGen] = refCount
                        }
                    } catch (ioe2: IOException) {
                        // Save first exception & throw in the end
                        if (ioe == null) {
                            ioe = ioe2
                        }
                    } finally {
                        input.close()
                    }

                    genLoaded = gen
                    refCounts.clear()
                    refCounts.putAll(m)
                }
            }
        }

        if (genLoaded == -1L) {
            // Nothing was loaded...
            if (ioe != null) {
                // ... not for lack of trying:
                throw ioe
            }
        } else {
            if (snapshotFiles.size > 1) {
                // Remove any broken / old snapshot files:
                val curFileName = SNAPSHOTS_PREFIX + genLoaded
                for (file in snapshotFiles) {
                    if (curFileName != file) {
                        IOUtils.deleteFilesIgnoringExceptions(dir, file)
                    }
                }
            }
            nextWriteGen = 1 + genLoaded
        }
    }
}
