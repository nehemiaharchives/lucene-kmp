package org.gnit.lucenekmp.jdkport

import kotlin.reflect.KClass

/**
 * No-op ServiceLoader for Kotlin Multiplatform. Mimics java.util.ServiceLoader
 */
class ServiceLoader<S> {

    companion object{
        fun <S : Any> load(clazz: KClass<S>, classLoader: ClassLoader): List<S> {
            // This is a no-op implementation for Kotlin Multiplatform
            return emptyList()
        }
    }
}