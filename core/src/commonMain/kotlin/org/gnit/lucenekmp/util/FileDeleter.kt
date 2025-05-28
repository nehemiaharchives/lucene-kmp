package org.gnit.lucenekmp.util

import okio.FileNotFoundException
import okio.IOException
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.jdkport.NoSuchFileException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.computeIfAbsent
import org.gnit.lucenekmp.store.Directory

/**
 * This class provides ability to track the reference counts of a set of index files and delete them
 * when their counts decreased to 0.
 *
 *
 * This class is NOT thread-safe, the user should make sure the thread-safety themselves
 *
 * @lucene.internal
 */
class FileDeleter(directory: Directory, messenger: (MsgType, String) -> Unit) {
    private val refCounts: MutableMap<String, RefCount> = HashMap<String, RefCount>()

    private val directory: Directory

    /*
   * user specified message consumer, first argument will be message type
   * second argument will be the actual message
   */
    private val messenger: (MsgType, String) -> Unit

    /**
     * Create a new FileDeleter with a messenger consumes various verbose messages
     *
     * @param directory the index directory
     * @param messenger two arguments will be passed in, [MsgType] and the actual message in
     * String. Can be null if the user do not want debug infos
     */
    init {
        this.directory = directory
        this.messenger = messenger
    }

    /**
     * Types of messages this file deleter will broadcast REF: messages about reference FILE: messages
     * about file
     */
    enum class MsgType {
        REF,
        FILE
    }

    fun incRef(fileNames: MutableCollection<String>) {
        for (file in fileNames) {
            incRef(file)
        }
    }

    fun incRef(fileName: String) {
        val rc = getRefCountInternal(fileName)!!
        if (messenger != null) {
            messenger(MsgType.REF, "IncRef \"" + fileName + "\": pre-incr count is " + rc.count)
        }
        rc.incRef()
    }

    /**
     * Decrease ref counts for all provided files, delete them if ref counts down to 0, even on
     * exception. Throw first exception hit, if any
     */
    @Throws(IOException::class)
    fun decRef(fileNames: MutableCollection<String>) {
        val toDelete: MutableSet<String> = HashSet<String>()
        var firstThrowable: Throwable? = null
        for (fileName in fileNames) {
            try {
                if (decRef(fileName)) {
                    toDelete.add(fileName!!)
                }
            } catch (t: Throwable) {
                firstThrowable = IOUtils.useOrSuppress<Throwable>(firstThrowable, t)
            }
        }

        try {
            delete(toDelete)
        } catch (t: Throwable) {
            firstThrowable = IOUtils.useOrSuppress<Throwable>(firstThrowable, t)
        }

        if (firstThrowable != null) {
            throw IOUtils.rethrowAlways(firstThrowable)
        }
    }

    /** Returns true if the file should be deleted  */
    private fun decRef(fileName: String): Boolean {
        val rc = getRefCountInternal(fileName)!!
        if (messenger != null) {
            messenger(MsgType.REF, "DecRef \"" + fileName + "\": pre-decr count is " + rc.count)
        }
        if (rc.decRef() == 0) {
            refCounts.remove(fileName)
            return true
        }
        return false
    }

    private fun getRefCountInternal(fileName: String): RefCount? {
        return refCounts.computeIfAbsent(fileName) { fileName: String -> RefCount(fileName) }
    }

    /** if the file is not yet recorded, this method will create a new RefCount object with count 0  */
    fun initRefCount(fileName: String) {
        refCounts.computeIfAbsent(fileName) { fileName: String -> RefCount(fileName) }
    }

    /**
     * get ref count for a provided file, if the file is not yet recorded, this method will return 0
     */
    fun getRefCount(fileName: String): Int {
        return refCounts.getOrElse(fileName) { ZERO_REF }.count
    }

    val allFiles: MutableSet<String>
        /** get all files, some of them may have ref count 0  */
        get() = refCounts.keys

    /** return true only if file is touched and also has larger than 0 ref count  */
    fun exists(fileName: String): Boolean {
        return refCounts.containsKey(fileName) && refCounts.get(fileName)!!.count > 0
    }

    val unrefedFiles: MutableSet<String>
        /** get files that are touched but not incref'ed  */
        get() {
            val unrefed: MutableSet<String> = HashSet<String>()
            for (entry in refCounts.entries) {
                val rc = entry.value
                val fileName = entry.key
                if (rc.count == 0) {
                    messenger(MsgType.FILE, "removing unreferenced file \"$fileName\"")
                    unrefed.add(fileName)
                }
            }
            return unrefed
        }

    /** delete only files that are unref'ed  */
    @Throws(IOException::class)
    fun deleteFilesIfNoRef(files: MutableCollection<String>) {
        val toDelete: MutableSet<String> = HashSet<String>()
        for (fileName in files) {
            // NOTE: it's very unusual yet possible for the
            // refCount to be present and 0: it can happen if you
            // open IW on a crashed index, and it removes a bunch
            // of unref'd files, and then you add new docs / do
            // merging, and it reuses that segment name.
            // TestCrash.testCrashAfterReopen can hit this:
            if (exists(fileName) == false) {
                if (messenger != null) {
                    messenger(MsgType.FILE, "will delete new file \"$fileName\"")
                }
                toDelete.add(fileName!!)
            }
        }

        delete(toDelete)
    }

    @Throws(IOException::class)
    fun forceDelete(fileName: String) {
        refCounts.remove(fileName)
        delete(fileName)
    }

    @Throws(IOException::class)
    fun deleteFileIfNoRef(fileName: String) {
        if (exists(fileName) == false) {
            if (messenger != null) {
                messenger(MsgType.FILE, "will delete new file \"$fileName\"")
            }
            delete(fileName)
        }
    }

    @Throws(IOException::class)
    private fun delete(toDelete: MutableCollection<String>) {
        if (messenger != null) {
            messenger(MsgType.FILE, "now delete " + toDelete.size + " files: " + toDelete)
        }

        // First pass: delete any segments_N files.  We do these first to be certain stale commit points
        // are removed
        // before we remove any files they reference, in case we crash right now:
        for (fileName in toDelete) {
            assert(exists(fileName) == false)
            if (fileName.startsWith(IndexFileNames.SEGMENTS)) {
                delete(fileName)
            }
        }

        // Only delete other files if we were able to remove the segments_N files; this way we never
        // leave a corrupt commit in the index even in the presense of virus checkers:
        for (fileName in toDelete) {
            assert(exists(fileName) == false)
            if (fileName.startsWith(IndexFileNames.SEGMENTS) == false) {
                delete(fileName)
            }
        }
    }

    @Throws(IOException::class)
    private fun delete(fileName: String) {
        try {
            directory.deleteFile(fileName)
        } catch (e: NoSuchFileException) {
            if (Constants.WINDOWS) {
                // TODO: can we remove this OS-specific hacky logic?  If windows deleteFile is buggy, we
                // should instead contain this workaround in
                // a WindowsFSDirectory ...
                // LUCENE-6684: we suppress this assert for Windows, since a file could be in a confusing
                // "pending delete" state, where we already
                // deleted it once, yet it still shows up in directory listings, and if you try to delete it
                // again you'll hit NSFE/FNFE:
            } else {
                throw e
            }
        } catch (e: FileNotFoundException) {
            if (Constants.WINDOWS) {
            } else {
                throw e
            }
        }
    }

    /** Tracks the reference count for a single index file:  */
    class RefCount internal constructor(// fileName used only for better assert error messages
        val fileName: String
    ) {
        var initDone: Boolean = false

        var count: Int = 0

        fun incRef(): Int {
            if (initDone == false) {
                initDone = true
            } else {
                assert(
                    count > 0
                ) {
                    (/*java.lang.Thread.currentThread().getName()*/
                            ": RefCount is 0 pre-increment for file \""
                                    + fileName
                                    + "\"")
                }
            }
            return ++count
        }

        fun decRef(): Int {
            assert(
                count > 0
            ) {
                (/*java.lang.Thread.currentThread().getName()*/
                        ": RefCount is 0 pre-decrement for file \""
                                + fileName
                                + "\"")
            }
            return --count
        }
    }

    companion object {
        /*
   * used to return 0 ref count
   */
        private val ZERO_REF = RefCount("")
    }
}
