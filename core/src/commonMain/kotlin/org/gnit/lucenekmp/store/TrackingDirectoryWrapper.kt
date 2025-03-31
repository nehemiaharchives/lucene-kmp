package org.gnit.lucenekmp.store

import kotlinx.io.IOException


/** A delegating Directory that records which files were written to and deleted.  */
class TrackingDirectoryWrapper(`in`: Directory) : FilterDirectory(`in`) {
    private val createdFileNames: MutableSet<String> = mutableSetOf()

    @Throws(IOException::class)
    override fun deleteFile(name: String) {
        `in`.deleteFile(name)
        createdFileNames.remove(name)
    }

    @Throws(IOException::class)
    override fun createOutput(name: String, context: IOContext): IndexOutput {
        val output: IndexOutput = `in`.createOutput(name, context)
        createdFileNames.add(name)
        return output
    }

    @Throws(IOException::class)
    override fun createTempOutput(prefix: String, suffix: String, context: IOContext): IndexOutput {
        val tempOutput: IndexOutput = `in`.createTempOutput(prefix, suffix, context)
        createdFileNames.add(tempOutput.name)
        return tempOutput
    }

    @Throws(IOException::class)
    override fun copyFrom(from: Directory, src: String, dest: String, context: IOContext) {
        `in`.copyFrom(from, src, dest, context)
        createdFileNames.add(dest)
    }

    @Throws(IOException::class)
    override fun rename(source: String, dest: String) {
        `in`.rename(source, dest)

        // in kotlin common, synchronized is not available
        // synchronized(createdFileNames) {
            createdFileNames.add(dest)
            createdFileNames.remove(source)
        //}
    }

    val createdFiles: MutableSet<String>
        /** NOTE: returns a copy of the created files.  */
        get() = HashSet(createdFileNames)

    fun clearCreatedFiles() {
        createdFileNames.clear()
    }
}
