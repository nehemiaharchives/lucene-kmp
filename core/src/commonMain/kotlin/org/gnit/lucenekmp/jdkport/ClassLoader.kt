package org.gnit.lucenekmp.jdkport

/**
 * No-op classloader for Kotlin Multiplatform. Mimics java.lang.ClassLoader
 */
@Ported(from = "kotlin.reflect.KClassLoader") // strangely not java.lang.ClassLoader
class ClassLoader {
    fun getParent(): ClassLoader {
        TODO("Not yet implemented")
    }
}
