package org.gnit.lucenekmp.store

import kotlinx.io.IOException


/**
 * Directory implementation that delegates calls to another directory. This class can be used to add
 * limitations on top of an existing [Directory] implementation such as [ ] or to add additional sanity checks for tests. However, if you plan to write
 * your own [Directory] implementation, you should consider extending directly [ ] or [BaseDirectory] rather than try to reuse functionality of existing [ ]s by extending this class.
 *
 * @lucene.internal
 */
abstract class FilterDirectory
/** Sole constructor, typically called from sub-classes.  */ protected constructor(
    /** Return the wrapped [Directory].  */
    val `in`: Directory
) : Directory() {
    @Throws(IOException::class)
    override fun listAll(): Array<String> {
        return `in`.listAll()
    }

    @Throws(IOException::class)
    override fun deleteFile(name: String) {
        `in`.deleteFile(name)
    }

    @Throws(IOException::class)
    override fun fileLength(name: String): Long {
        return `in`.fileLength(name)
    }

    @Throws(IOException::class)
    override fun createOutput(name: String, context: IOContext): IndexOutput {
        return `in`.createOutput(name, context)
    }

    @Throws(IOException::class)
    override fun createTempOutput(prefix: String, suffix: String, context: IOContext): IndexOutput {
        return `in`.createTempOutput(prefix, suffix, context)
    }

    @Throws(IOException::class)
    override fun sync(names: MutableCollection<String>) {
        `in`.sync(names)
    }

    @Throws(IOException::class)
    override fun rename(source: String, dest: String) {
        `in`.rename(source, dest)
    }

    @Throws(IOException::class)
    override fun syncMetaData() {
        `in`.syncMetaData()
    }

    @Throws(IOException::class)
    override fun openInput(name: String, context: IOContext): IndexInput {
        return `in`.openInput(name, context)
    }

    @Throws(IOException::class)
    override fun obtainLock(name: String): Lock {
        return `in`.obtainLock(name)
    }

    @Throws(IOException::class)
    override fun close() {
        `in`.close()
    }

    override fun toString(): String {
        return this::class.simpleName + "(" + `in`.toString() + ")"
    }

    @get:Throws(IOException::class)
    override val pendingDeletions: MutableSet<String>
        get() = `in`.pendingDeletions

    @Throws(AlreadyClosedException::class)
    override fun ensureOpen() {
        `in`.ensureOpen()
    }

    companion object {
        /**
         * Get the wrapped instance by `dir` as long as this reader is an instance of [ ].
         */
        fun unwrap(dir: Directory): Directory {
            var dir = dir
            while (dir is FilterDirectory) {
                dir = dir.`in`
            }
            return dir
        }
    }
}
