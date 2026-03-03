package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.jdkport.ReentrantLock
import okio.IOException


/** A delegating Directory that records which files were written to and deleted.  */
class TrackingDirectoryWrapper(`in`: Directory) : FilterDirectory(`in`) {
    private val createdFileNames: MutableSet<String> = mutableSetOf()
    private val createdFileNamesLock = ReentrantLock()

    @Throws(IOException::class)
    override fun deleteFile(name: String) {
        `in`.deleteFile(name)
        try {
            createdFileNamesLock.lock()
            createdFileNames.remove(name)
        } finally {
            createdFileNamesLock.unlock()
        }
    }

    @Throws(IOException::class)
    override fun createOutput(name: String, context: IOContext): IndexOutput {
        val output: IndexOutput = `in`.createOutput(name, context)
        try {
            createdFileNamesLock.lock()
            createdFileNames.add(name)
        } finally {
            createdFileNamesLock.unlock()
        }
        return output
    }

    @Throws(IOException::class)
    override fun createTempOutput(prefix: String, suffix: String, context: IOContext): IndexOutput {
        val tempOutput: IndexOutput = `in`.createTempOutput(prefix, suffix, context)
        try {
            createdFileNamesLock.lock()
            createdFileNames.add(tempOutput.name!!)
        } finally {
            createdFileNamesLock.unlock()
        }
        return tempOutput
    }

    @Throws(IOException::class)
    override fun copyFrom(from: Directory, src: String, dest: String, context: IOContext) {
        `in`.copyFrom(from, src, dest, context)
        try {
            createdFileNamesLock.lock()
            createdFileNames.add(dest)
        } finally {
            createdFileNamesLock.unlock()
        }
    }

    @Throws(IOException::class)
    override fun rename(source: String, dest: String) {
        `in`.rename(source, dest)
        try {
            createdFileNamesLock.lock()
            createdFileNames.add(dest)
            createdFileNames.remove(source)
        } finally {
            createdFileNamesLock.unlock()
        }
    }

    val createdFiles: MutableSet<String>
        /** NOTE: returns a copy of the created files.  */
        get() = try {
            createdFileNamesLock.lock()
            HashSet(createdFileNames)
        } finally {
            createdFileNamesLock.unlock()
        }

    fun clearCreatedFiles() {
        try {
            createdFileNamesLock.lock()
            createdFileNames.clear()
        } finally {
            createdFileNamesLock.unlock()
        }
    }
}
