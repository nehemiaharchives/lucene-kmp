package org.gnit.lucenekmp.tests.mockfile

import okio.FileSystem
import okio.Path
import org.gnit.lucenekmp.jdkport.AccessDeniedException
import org.gnit.lucenekmp.jdkport.ReentrantLock

/**
 * FileSystem that (imperfectly) acts like windows.
 *
 * Currently this filesystem only prevents deletion of open files.
 */
class WindowsFS(delegate: FileSystem) : HandleTrackingFS("windows://", delegate) {
    // This map also supports fileKey -> Path -> counts.
    private val openFiles: MutableMap<String, MutableMap<Path, Int>> = mutableMapOf()
    private val openFilesLock = ReentrantLock()

    /** Returns file "key" for the specified path */
    private fun getKey(existing: Path): String {
        return delegate.canonicalize(toDelegate(existing)).toString()
    }

    override fun onOpen(path: Path, stream: Any) {
        try {
            openFilesLock.lock()
            val key = getKey(path)
            val pathMap = openFiles.getOrPut(key) { mutableMapOf() }
            pathMap[path] = (pathMap[path] ?: 0) + 1
        } finally {
            openFilesLock.unlock()
        }
    }

    override fun onClose(path: Path, stream: Any) {
        val key = getKey(path)
        try {
            openFilesLock.lock()
            val pathMap = checkNotNull(openFiles[key])
            val count = checkNotNull(pathMap[path])
            if (count == 1) {
                pathMap.remove(path)
            } else {
                pathMap[path] = count - 1
            }
            if (pathMap.isEmpty()) {
                openFiles.remove(key)
            }
        } finally {
            openFilesLock.unlock()
        }
    }

    private fun getKeyOrNull(path: Path): String? {
        return try {
            getKey(path)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks that it's ok to delete `Path`. If the file is still open, it throws access denied.
     */
    private fun checkDeleteAccess(path: Path) {
        val key = getKeyOrNull(path)
        if (key != null) {
            try {
                openFilesLock.lock()
                if (openFiles.containsKey(key)) {
                    throw AccessDeniedException(path.toString(), "", "access denied")
                }
            } finally {
                openFilesLock.unlock()
            }
        }
    }

    override fun delete(path: Path, mustExist: Boolean) {
        try {
            openFilesLock.lock()
            checkDeleteAccess(path)
            super.delete(path, mustExist)
        } finally {
            openFilesLock.unlock()
        }
    }

    override fun atomicMove(source: Path, target: Path) {
        try {
            openFilesLock.lock()
            checkDeleteAccess(source)
            super.atomicMove(source, target)
        } finally {
            openFilesLock.unlock()
        }
    }
}
