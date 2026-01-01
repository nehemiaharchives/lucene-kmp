package org.gnit.lucenekmp.util

import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.jdkport.OkioSourceInputStream
import kotlin.reflect.KClass

/**
 * Simple ResourceLoader that resolves resources from the filesystem.
 *
 * Resource paths are treated as absolute if they start with '/'. Otherwise, they are resolved
 * relative to commonTest/commonMain resources and (if provided) the class' package path.
 */
class ClasspathResourceLoader(private val clazz: KClass<*>? = null) : ResourceLoader {
    @Throws(IOException::class)
    override fun openResource(resource: String): InputStream {
        val normalized = if (resource.startsWith("/")) resource.drop(1) else resource
        val candidates = mutableListOf<String>()
        if (resource.startsWith("/")) {
            candidates.add(resource)
        } else {
            candidates.add(normalized)
            val pkgPath = clazz?.qualifiedName?.substringBeforeLast('.', "")?.replace('.', '/')
            if (!pkgPath.isNullOrEmpty()) {
                candidates.add("$pkgPath/$normalized")
            }
        }

        val roots = listOf(
            "",
            "src/commonTest/resources",
            "src/commonMain/resources",
            "src/jvmTest/resources",
            "analysis/common/src/commonTest/resources",
            "analysis/common/src/commonMain/resources",
            "analysis/common/src/jvmTest/resources",
            "lucene-kmp/analysis/common/src/commonTest/resources",
            "lucene-kmp/analysis/common/src/commonMain/resources",
            "lucene-kmp/analysis/common/src/jvmTest/resources"
        )

        for (root in roots) {
            val rootPath: Path? = if (root.isEmpty()) null else root.toPath()
            for (candidate in candidates) {
                val path = rootPath?.resolve(candidate) ?: candidate.toPath()
                if (FileSystem.SYSTEM.exists(path)) {
                    val source = FileSystem.SYSTEM.source(path).buffer()
                    return OkioSourceInputStream(source)
                }
            }
        }

        throw IOException("Resource not found: $resource")
    }

    override fun <T> findClass(cname: String, expectedType: KClass<*>): KClass<*> {
        throw RuntimeException("Cannot load class: $cname")
    }
}
