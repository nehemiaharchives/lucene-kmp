package org.gnit.lucenekmp.jdkport

import kotlin.reflect.KClass

/**
 * No-op classloader for Kotlin Multiplatform. Mimics java.lang.ClassLoader
 */
class ClassLoader {

    fun getParent() = ClassLoader()
}

fun KClass<*>.getClassLoader(): ClassLoader {
    // This is a no-op implementation for Kotlin Multiplatform
    return ClassLoader()
}
