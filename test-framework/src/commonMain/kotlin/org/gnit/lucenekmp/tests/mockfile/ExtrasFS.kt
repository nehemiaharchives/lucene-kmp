package org.gnit.lucenekmp.tests.mockfile

import okio.FileSystem
import okio.Path
import org.gnit.lucenekmp.jdkport.Files

/**
 * Adds extra files/subdirectories when directories are created.
 *
 * Lucene shouldn't care about these, but sometimes operating systems create special files
 * themselves (.DS_Store, thumbs.db, .nfsXXX, ...), so we add them and see what breaks.
 *
 * When a directory is created, sometimes an "extra" file or directory will be included with it
 * (use [isExtra] to check if it's one of those files).
 *
 * All other filesystem operations are delegated as normal.
 */
class ExtrasFS(
    delegate: FileSystem,
    val active: Boolean,
    val createDirectory: Boolean
) : FilterFileSystemProvider(delegate, "extras://") {
    override fun createDirectory(dir: Path, mustCreate: Boolean) {
        super.createDirectory(dir, mustCreate)
        // ok, we created the directory successfully.

        if (active) {
            // lets add a bogus file... if this fails, we don't care, its best effort.
            try {
                val target = dir / EXTRA_FILE_NAME
                if (createDirectory) {
                    super.createDirectory(target, mustCreate = true)
                } else {
                    Files.setFileSystem(getFileSystem())
                    try {
                        Files.createFile(target)
                    } finally {
                        Files.resetFileSystem()
                    }
                }
            } catch (_: Exception) {
                // best effort
            }
        }
    }

    // TODO: would be great if we overrode attributes, so file size was always zero for
    // our fake files. But this is tricky because its hooked into several places.
    // Currently MDW has a hack so we don't break disk full tests.

    companion object {
        private const val EXTRA_FILE_NAME = "extra0"

        /**
         * @return Return true if `fileName` is one of the extra files added by this class.
         */
        fun isExtra(fileName: String): Boolean {
            return fileName == EXTRA_FILE_NAME
        }
    }
}
