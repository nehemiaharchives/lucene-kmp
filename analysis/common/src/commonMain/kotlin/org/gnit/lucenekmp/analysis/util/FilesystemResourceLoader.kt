package org.gnit.lucenekmp.analysis.util

import okio.FileSystem
import okio.IOException
import okio.Path
import okio.SYSTEM
import okio.buffer
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.jdkport.OkioSourceInputStream
import org.gnit.lucenekmp.util.ResourceLoader
import kotlin.reflect.KClass

/**
 * Simple [ResourceLoader] that opens resource files from the local file system, optionally
 * resolving against a base directory.
 *
 * <p>This loader wraps a delegate [ResourceLoader] that is used to resolve all files the
 * current base directory does not contain.
 *
 * <p>You can chain several `FilesystemResourceLoader`s to allow lookup of files in more than
 * one base directory.
 */
class FilesystemResourceLoader(private val baseDirectory: Path, private val delegate: ResourceLoader) : ResourceLoader {
    init {
        require(FileSystem.SYSTEM.exists(baseDirectory)) { "$baseDirectory is not a directory" }
    }

    @Throws(IOException::class)
    override fun openResource(resource: String): InputStream {
        val path = baseDirectory.resolve(resource)
        return if (FileSystem.SYSTEM.exists(path)) {
            OkioSourceInputStream(FileSystem.SYSTEM.source(path).buffer())
        } else {
            delegate.openResource(resource)
        }
    }

    override fun <T> findClass(cname: String, expectedType: KClass<*>): KClass<*> {
        return delegate.findClass<T>(cname, expectedType)
    }
}
