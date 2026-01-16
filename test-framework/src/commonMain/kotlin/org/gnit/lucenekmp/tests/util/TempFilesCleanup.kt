package org.gnit.lucenekmp.tests.util

import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.System

internal class TempFilesCleanup {
    private val cleanupQueue: MutableList<Path> = mutableListOf()
    private var tempDirBase: Path? = null
    private var javaTempDir: Path? = null

    fun createTempDir(prefix: String): Path {
        val base = getPerTestClassTempDir()
        val path = createUniquePath(base, prefix, isDirectory = true)
        registerToRemoveAfterSuite(path)
        return path
    }

    fun createTempFile(prefix: String, suffix: String): Path {
        val base = getPerTestClassTempDir()
        val path = createUniquePath(base, prefix, suffix, isDirectory = false)
        registerToRemoveAfterSuite(path)
        return path
    }

    fun cleanup() {
        if (LuceneTestCase.LEAVE_TEMPORARY) {
            for (path in cleanupQueue) {
                println("INFO: Will leave temporary file: ${path.toString()}")
            }
            cleanupQueue.clear()
            return
        }

        val toDelete = cleanupQueue.toList()
        cleanupQueue.clear()
        for (path in toDelete) {
            deleteRecursively(path)
        }
        tempDirBase = null
    }

    private fun registerToRemoveAfterSuite(path: Path) {
        if (LuceneTestCase.LEAVE_TEMPORARY) {
            println("INFO: Will leave temporary file: ${path.toString()}")
            return
        }
        cleanupQueue.add(path)
    }

    private fun getPerTestClassTempDir(): Path {
        if (tempDirBase == null) {
            val base = javaTempDir()
            val prefix = "lucene-kmp"
            val seed = RandomizedTest.randomLong().toString()
            val namePrefix = "${prefix}_${seed}"
            tempDirBase = createUniquePath(base, namePrefix, isDirectory = true)
            registerToRemoveAfterSuite(tempDirBase!!)
        }
        return tempDirBase!!
    }

    private fun javaTempDir(): Path {
        if (javaTempDir != null) {
            return javaTempDir!!
        }
        val base = System.getProperty("tempDir", System.getProperty("java.io.tmpdir")) ?: "/tmp"
        val basePath = base.toPath()
        Files.createDirectories(basePath)
        javaTempDir = basePath
        return javaTempDir!!
    }

    private fun createUniquePath(
        base: Path,
        prefix: String,
        suffix: String = "",
        isDirectory: Boolean
    ): Path {
        var attempt = 0
        while (true) {
            if (attempt++ >= LuceneTestCase.TEMP_NAME_RETRY_THRESHOLD) {
                throw RuntimeException(
                    "Failed to get a temporary name too many times, check your temp directory and consider manually cleaning it: ${base}"
                )
            }
            val attemptStr = attempt.toString().padStart(3, '0')
            val path = base.resolve("${prefix}-${attemptStr}${suffix}")
            try {
                if (isDirectory) {
                    Files.createDirectories(path)
                } else {
                    Files.createFile(path)
                }
                return path
            } catch (_: IOException) {
                // try another
            }
        }
    }

    private fun deleteRecursively(path: Path) {
        val fs = Files.getFileSystem()
        try {
            if (!fs.exists(path)) {
                return
            }
        } catch (_: Throwable) {
            return
        }

        val toDelete = try {
            fs.listRecursively(path).toList()
        } catch (_: Throwable) {
            emptyList()
        }

        val sorted = toDelete.sortedByDescending { it.toString().count { ch -> ch == '/' } }
        for (p in sorted) {
            try {
                fs.delete(p, mustExist = false)
            } catch (_: Throwable) {
                // best-effort
            }
        }
        try {
            fs.delete(path, mustExist = false)
        } catch (_: Throwable) {
            // best-effort
        }
    }
}
