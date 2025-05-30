package org.gnit.lucenekmp.store

import okio.IOException


/**
 * This class makes a best-effort check that a provided [Lock] is valid before any destructive
 * filesystem operation.
 */
class LockValidatingDirectoryWrapper(
    `in`: Directory,
    private val writeLock: Lock
) : FilterDirectory(`in`) {

    @Throws(IOException::class)
    override fun deleteFile(name: String) {
        writeLock.ensureValid()
        `in`.deleteFile(name)
    }

    @Throws(IOException::class)
    override fun createOutput(
        name: String,
        context: IOContext
    ): IndexOutput {
        writeLock.ensureValid()
        return `in`.createOutput(name, context)
    }

    @Throws(IOException::class)
    override fun copyFrom(
        from: Directory,
        src: String,
        dest: String,
        context: IOContext
    ) {
        writeLock.ensureValid()
        `in`.copyFrom(from, src, dest, context)
    }

    @Throws(IOException::class)
    override fun rename(source: String, dest: String) {
        writeLock.ensureValid()
        `in`.rename(source, dest)
    }

    @Throws(IOException::class)
    override fun syncMetaData() {
        writeLock.ensureValid()
        `in`.syncMetaData()
    }

    @Throws(IOException::class)
    override fun sync(names: MutableCollection<String>) {
        writeLock.ensureValid()
        `in`.sync(names)
    }
}
