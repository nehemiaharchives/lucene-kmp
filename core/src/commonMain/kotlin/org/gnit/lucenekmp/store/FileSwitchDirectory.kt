package org.gnit.lucenekmp.store

import okio.IOException
import org.gnit.lucenekmp.jdkport.AtomicMoveNotSupportedException
import org.gnit.lucenekmp.jdkport.NoSuchFileException
import org.gnit.lucenekmp.util.IOUtils

/**
 * Expert: A Directory instance that switches files between two other Directory instances.
 *
 * Files with the specified extensions are placed in the primary directory; others are placed in
 * the secondary directory.
 */
class FileSwitchDirectory(
    private val primaryExtensions: Set<String>,
    private val primaryDir: Directory,
    private val secondaryDir: Directory,
    private var doClose: Boolean
) : Directory() {

    init {
        require(!primaryExtensions.contains("tmp")) { "tmp is a reserved extension" }
    }

    /** Return the primary directory */
    fun getPrimaryDir(): Directory {
        return primaryDir
    }

    /** Return the secondary directory */
    fun getSecondaryDir(): Directory {
        return secondaryDir
    }

    @Throws(IOException::class)
    override fun obtainLock(name: String): Lock {
        return getDirectory(name).obtainLock(name)
    }

    override fun close() {
        if (doClose) {
            IOUtils.close(primaryDir, secondaryDir)
            doClose = false
        }
    }

    @Throws(IOException::class)
    override fun listAll(): Array<String> {
        val files = mutableListOf<String>()
        var exc: NoSuchFileException? = null
        try {
            for (f in primaryDir.listAll()) {
                val ext = getExtension(f)
                if (primaryExtensions.contains(ext)) {
                    files.add(f)
                }
            }
        } catch (e: NoSuchFileException) {
            exc = e
        }
        try {
            for (f in secondaryDir.listAll()) {
                val ext = getExtension(f)
                if (!primaryExtensions.contains(ext)) {
                    files.add(f)
                }
            }
        } catch (e: NoSuchFileException) {
            if (exc != null) {
                throw exc
            }
            if (files.isEmpty()) {
                throw e
            }
        }
        if (exc != null && files.isEmpty()) {
            throw exc
        }
        return files.sorted().toTypedArray()
    }

    @Throws(IOException::class)
    override fun deleteFile(name: String) {
        if (getDirectory(name) == primaryDir) {
            primaryDir.deleteFile(name)
        } else {
            secondaryDir.deleteFile(name)
        }
    }

    @Throws(IOException::class)
    override fun fileLength(name: String): Long {
        return getDirectory(name).fileLength(name)
    }

    @Throws(IOException::class)
    override fun createOutput(name: String, context: IOContext): IndexOutput {
        return getDirectory(name).createOutput(name, context)
    }

    @Throws(IOException::class)
    override fun createTempOutput(prefix: String, suffix: String, context: IOContext): IndexOutput {
        val tmpFileName = Directory.getTempFileName(prefix, suffix, 0)
        return getDirectory(tmpFileName).createTempOutput(prefix, suffix, context)
    }

    @Throws(IOException::class)
    override fun sync(names: MutableCollection<String>) {
        val primaryNames = mutableListOf<String>()
        val secondaryNames = mutableListOf<String>()
        for (name in names) {
            if (primaryExtensions.contains(getExtension(name))) {
                primaryNames.add(name)
            } else {
                secondaryNames.add(name)
            }
        }
        primaryDir.sync(primaryNames)
        secondaryDir.sync(secondaryNames)
    }

    @Throws(IOException::class)
    override fun rename(source: String, dest: String) {
        val sourceDir = getDirectory(source)
        if (sourceDir != getDirectory(dest)) {
            throw AtomicMoveNotSupportedException(
                source,
                dest,
                "source and dest are in different directories"
            )
        }
        sourceDir.rename(source, dest)
    }

    @Throws(IOException::class)
    override fun syncMetaData() {
        primaryDir.syncMetaData()
        secondaryDir.syncMetaData()
    }

    @Throws(IOException::class)
    override fun openInput(name: String, context: IOContext): IndexInput {
        return getDirectory(name).openInput(name, context)
    }

    override val pendingDeletions: MutableSet<String>
        get() {
            if (primaryDir.pendingDeletions.isEmpty() && secondaryDir.pendingDeletions.isEmpty()) {
                return mutableSetOf()
            }
            val combined = mutableSetOf<String>()
            combined.addAll(primaryDir.pendingDeletions)
            combined.addAll(secondaryDir.pendingDeletions)
            return combined
        }

    private fun getDirectory(name: String): Directory {
        val ext = getExtension(name)
        return if (primaryExtensions.contains(ext)) primaryDir else secondaryDir
    }

    companion object {
        private val EXT_PATTERN = Regex("\\.([a-zA-Z]+)")

        /** Utility method to return a file's extension. */
        fun getExtension(name: String): String {
            val i = name.lastIndexOf('.')
            if (i == -1) {
                return ""
            }
            val ext = name.substring(i + 1)
            if (ext == "tmp") {
                val match = EXT_PATTERN.find(name.substring(0, i + 1))
                if (match != null && match.groupValues.size > 1) {
                    return match.groupValues[1]
                }
            }
            return ext
        }
    }
}

