package org.gnit.lucenekmp.jdkport

import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import kotlin.reflect.KClass

fun KClass<*>.getClassLoader(): ClassLoader {
    // This is a no-op implementation for Kotlin Multiplatform
    return ClassLoader()
}

fun <U : Any> KClass<U>.asSubclass(clazz: KClass<*>): KClass<U> {
    // This is a no-op implementation for Kotlin Multiplatform
    return this
}

fun KClass<*>.isAssignableFrom(other: KClass<*>): Boolean {
    // This is a no-op implementation for Kotlin Multiplatform
    return this == other
}

fun classForName(name: String, initialize: Boolean, loader: ClassLoader): KClass<*> {
    return Any::class
}

class MethodHandle() {
    fun invokeExact(): AttributeImpl {
        return object : AttributeImpl(){

            override fun clear() {
                TODO("Not yet implemented")
            }

            override fun reflectWith(reflector: AttributeReflector) {
                TODO("Not yet implemented")
            }

            override fun copyTo(target: AttributeImpl) {
                TODO("Not yet implemented")
            }

            override fun newInstance(): AttributeImpl {
                TODO("Not yet implemented")
            }
        }
    }

    fun asType(NO_ARG_RETURNING_ATTRIBUTEIMPL: MethodType): MethodHandle {
        // This is a no-op implementation for Kotlin Multiplatform
        return MethodHandle()
    }
}

class MethodHandles {
    class Lookup {
        fun findConstructor(clazz: KClass<*>, NO_ARG_CTOR: MethodType): MethodHandle {
            // This is a no-op implementation for Kotlin Multiplatform
            return MethodHandle()
        }
    }

    companion object{
        fun publicLookup(): Lookup {
            // This is a no-op implementation for Kotlin Multiplatform
            return Lookup()
        }
    }
}

class MethodType {
    companion object {

        fun methodType(type: KClass<*>): MethodType {
            // This is a no-op implementation for Kotlin Multiplatform
            return MethodType()
        }
    }
}

object Void {
    val TYPE: KClass<*> = Any::class
}