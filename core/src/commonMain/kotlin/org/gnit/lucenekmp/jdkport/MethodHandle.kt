package org.gnit.lucenekmp.jdkport

import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import kotlin.reflect.KClass

fun KClass<*>.getClassLoader(): ClassLoader {
    // No real classloader semantics in KMP common; return a placeholder.
    return ClassLoader()
}

fun <U : Any> KClass<U>.asSubclass(clazz: KClass<*>): KClass<U> {
    // Simplified: assume caller provides correct type in KMP usage paths.
    @Suppress("UNCHECKED_CAST")
    return this as KClass<U>
}

fun KClass<*>.isAssignableFrom(other: KClass<*>): Boolean {
    // Minimal, conservative check: equality only.
    return this == other
}

fun classForName(name: String, initialize: Boolean, loader: ClassLoader): KClass<*> {
    throw UnsupportedOperationException("classForName is not supported in KMP common")
}

class MethodHandle {
    fun invokeExact(): AttributeImpl {
        throw UnsupportedOperationException("MethodHandle.invokeExact is not supported in KMP common")
    }

    fun asType(@Suppress("UNUSED_PARAMETER") NO_ARG_RETURNING_ATTRIBUTEIMPL: MethodType): MethodHandle {
        // No-op; return self.
        return this
    }
}

class MethodHandles {
    class Lookup {
        fun findConstructor(@Suppress("UNUSED_PARAMETER") clazz: KClass<*>, @Suppress("UNUSED_PARAMETER") NO_ARG_CTOR: MethodType): MethodHandle {
            throw UnsupportedOperationException("MethodHandles.Lookup.findConstructor is not supported in KMP common")
        }
        // Placeholder to mirror potential API surface; not used.
        fun findClass(@Suppress("UNUSED_PARAMETER") name: String): KClass<*> {
            throw UnsupportedOperationException("MethodHandles.Lookup.findClass is not supported in KMP common")
        }
    }

    companion object {
        fun publicLookup(): Lookup {
            // Return a benign Lookup that throws if used for unsupported operations.
            return Lookup()
        }
    }
}

class MethodType {
    companion object {
        fun methodType(@Suppress("UNUSED_PARAMETER") type: KClass<*>): MethodType {
            // Return a dummy MethodType instance.
            return MethodType()
        }
    }
}

object Void {
    val TYPE: KClass<*> = Any::class
}