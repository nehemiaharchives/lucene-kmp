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
package org.gnit.lucenekmp.store

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileNotFoundException
import okio.IOException
import org.gnit.lucenekmp.jdkport.NoSuchFileException
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.getAndSet
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// TODO
//   - let subclass dictate policy...?
//   - rename to MergeCachingDir?  NRTCachingDir

/**
 * Wraps a RAM-resident directory around any provided delegate directory, to be used during NRT
 * search.
 *
 * <p>This class is likely only useful in a near-real-time context, where indexing rate is lowish
 * but reopen rate is highish, resulting in many tiny files being written. This directory keeps such
 * segments (as well as the segments produced by merging them, as long as they are small enough), in
 * RAM.
 *
 * <p>This is safe to use: when your app calls {IndexWriter#commit}, all cached files will be
 * flushed from the cached and sync'd.
 *
 * <p>Here's a simple example usage:
 *
 * <pre class="prettyprint">
 *   Directory fsDir = FSDirectory.open(new File("/path/to/index").toPath());
 *   NRTCachingDirectory cachedFSDir = new NRTCachingDirectory(fsDir, 5.0, 60.0);
 *   IndexWriterConfig conf = new IndexWriterConfig(analyzer);
 *   IndexWriter writer = new IndexWriter(cachedFSDir, conf);
 * </pre>
 *
 * <p>This will cache all newly flushed segments, all merges whose expected segment size is {@code
 * <= 5 MB}, unless the net cached bytes exceed 60 MB at which point all writes will not be cached
 * (until the net bytes fall below 60 MB).
 *
 * @lucene.experimental
 */
@OptIn(ExperimentalAtomicApi::class)
open class NRTCachingDirectory(delegate: Directory, maxMergeSizeMB: Double, maxCachedMB: Double) :
    FilterDirectory(delegate), Accountable {
    private val closed = AtomicBoolean(false)

    /** Current total size of files in the cache is maintained separately for faster access. */
    private val cacheSize = AtomicLong(0)

    /** RAM-resident directory that updates [cacheSize] when files are successfully closed. */
    private val cacheDirectory = ByteBuffersDirectory(
        SingleInstanceLockFactory(),
        { ByteBuffersDataOutput() },
        { fileName, content ->
            // Defensive check to handle the case the file has been deleted before this lambda
            // is called when the IndexOutput is closed. Unsafe in the unlikely case the deletion
            // happens concurrently on another thread.
            if (isCachedFile(fileName)) {
                cacheSize.addAndFetch(content.size())
            }
            ByteBuffersDirectory.OUTPUT_AS_MANY_BUFFERS(fileName, content)
        }
    )

    private val maxMergeSizeBytes: Long = (maxMergeSizeMB * 1024 * 1024).toLong()
    private val maxCachedBytes: Long = (maxCachedMB * 1024 * 1024).toLong()

    private val mutex = Mutex()

    private inline fun <T> withLock(crossinline action: () -> T): T = runBlocking {
        mutex.withLock { action() }
    }

    override fun toString(): String {
        return "NRTCachingDirectory(${`in`}; maxCacheMB=${maxCachedBytes / 1024.0 / 1024.0} maxMergeSizeMB=${maxMergeSizeBytes / 1024.0 / 1024.0})"
    }

    @Throws(IOException::class)
    override fun listAll(): Array<String> = withLock {
        val files = mutableSetOf<String>()
        files.addAll(cacheDirectory.listAll().toList())
        files.addAll(`in`.listAll().toList())
        files.toList().sorted().toTypedArray()
    }

    @Throws(IOException::class)
    override fun deleteFile(name: String) = withLock {
        if (VERBOSE) {
            println("nrtdir.deleteFile name=$name")
        }
        if (cacheDirectory.fileExists(name)) {
            val size = cacheDirectory.fileLength(name)
            cacheDirectory.deleteFile(name)
            val newSize = cacheSize.addAndFetch(-size)
            assert(newSize >= 0)
        } else {
            `in`.deleteFile(name)
        }
    }

    @Throws(IOException::class)
    override fun fileLength(name: String): Long = withLock {
        if (cacheDirectory.fileExists(name)) {
            cacheDirectory.fileLength(name)
        } else {
            `in`.fileLength(name)
        }
    }

    fun listCachedFiles(): Array<String> {
        try {
            return cacheDirectory.listAll()
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    @Throws(IOException::class)
    override fun createOutput(name: String, context: IOContext): IndexOutput {
        if (VERBOSE) {
            println("nrtdir.createOutput name=$name")
        }
        return if (doCacheWrite(name, context)) {
            if (VERBOSE) {
                println("  to cache")
            }
            cacheDirectory.createOutput(name, context)
        } else {
            `in`.createOutput(name, context)
        }
    }

    @Throws(IOException::class)
    override fun sync(names: MutableCollection<String>) {
        if (VERBOSE) {
            println("nrtdir.sync files=$names")
        }
        for (fileName in names) {
            unCache(fileName)
        }
        `in`.sync(names)
    }

    @Throws(IOException::class)
    override fun rename(source: String, dest: String) {
        unCache(source)
        if (cacheDirectory.fileExists(dest)) {
            throw IllegalArgumentException("target file $dest already exists")
        }
        `in`.rename(source, dest)
    }

    @Throws(IOException::class)
    override fun openInput(name: String, context: IOContext): IndexInput = withLock {
        if (VERBOSE) {
            println("nrtdir.openInput name=$name")
        }
        if (cacheDirectory.fileExists(name)) {
            if (VERBOSE) {
                println("  from cache")
            }
            cacheDirectory.openInput(name, context)
        } else {
            `in`.openInput(name, context)
        }
    }

    /**
     * Close this directory, which flushes any cached files to the delegate and then closes the
     * delegate.
     */
    override fun close() {
        // NOTE: technically we shouldn't have to do this, ie,
        // IndexWriter should have sync'd all files, but we do
        // it for defensive reasons... or in case the app is
        // doing something custom (creating outputs directly w/o
        // using IndexWriter):
        IOUtils.close(
            object : AutoCloseable {
                override fun close() {
                    if (!closed.getAndSet(true)) {
                        for (fileName in cacheDirectory.listAll()) {
                            unCache(fileName)
                        }
                    }
                }
            },
            cacheDirectory,
            `in`
        )
    }

    /**
     * Subclass can override this to customize logic; return true if this file should be written to
     * the RAM-based cache first.
     */
    protected open fun doCacheWrite(name: String, context: IOContext): Boolean {
        // System.out.println(Thread.currentThread().getName() + ": CACHE check merge=" + merge + "
        // size=" + (merge==null ? 0 : merge.estimatedMergeBytes));

        var bytes: Long = 0
        if (context.mergeInfo != null) {
            bytes = context.mergeInfo.estimatedMergeBytes
        } else if (context.flushInfo != null) {
            bytes = context.flushInfo.estimatedSegmentSize
        } else {
            return false
        }

        return bytes <= maxMergeSizeBytes && (bytes + cacheSize.load()) <= maxCachedBytes
    }

    @Throws(IOException::class)
    override fun createTempOutput(prefix: String, suffix: String, context: IOContext): IndexOutput {
        if (VERBOSE) {
            println("nrtdir.createTempOutput prefix=$prefix suffix=$suffix")
        }
        val toDelete = mutableSetOf<String>()

        // This is very ugly/messy/dangerous (can in some disastrous case maybe create too many temp
        // files), but I don't know of a cleaner way:
        var success = false

        val first: Directory
        val second: Directory
        if (doCacheWrite(prefix, context)) {
            first = cacheDirectory
            second = `in`
        } else {
            first = `in`
            second = cacheDirectory
        }

        var out: IndexOutput? = null
        try {
            while (true) {
                out = first.createTempOutput(prefix, suffix, context)
                val name = out.name!!
                toDelete.add(name)
                if (slowFileExists(second, name)) {
                    out.close()
                } else {
                    toDelete.remove(name)
                    success = true
                    break
                }
            }
        } finally {
            if (success) {
                IOUtils.deleteFiles(first, toDelete)
            } else {
                IOUtils.closeWhileHandlingException(out)
                IOUtils.deleteFilesIgnoringExceptions(first, toDelete)
            }
        }

        return out
    }

    /**
     * Returns true if the file exists (can be opened), false if it cannot be opened, and (unlike
     * Java's File.exists) throws IOException if there's some unexpected error.
     */
    @Throws(IOException::class)
    internal fun slowFileExists(dir: Directory, fileName: String): Boolean {
        return try {
            dir.fileLength(fileName)
            true
        } catch (e: NoSuchFileException) {
            false
        } catch (e: FileNotFoundException) {
            false
        }
    }

    private fun isCachedFile(fileName: String): Boolean = withLock {
        cacheDirectory.fileExists(fileName)
    }

    @Throws(IOException::class)
    private fun unCache(fileName: String) {
        // Must sync here because other sync methods have
        // if (cache.fileNameExists(name)) { ... } else { ... }:
        withLock {
            if (VERBOSE) {
                println("nrtdir.unCache name=$fileName")
            }
            if (!cacheDirectory.fileExists(fileName)) {
                // Another thread beat us...
                return@withLock
            }
            assert(slowFileExists(`in`, fileName) == false) {
                "fileName=$fileName exists both in cache and in delegate"
            }

            `in`.copyFrom(cacheDirectory, fileName, fileName, IOContext.DEFAULT)
            cacheSize.addAndFetch(-cacheDirectory.fileLength(fileName))
            cacheDirectory.deleteFile(fileName)
        }
    }

    override fun ramBytesUsed(): Long {
        return cacheSize.load()
    }

    companion object {
        private const val VERBOSE = false
    }
}
