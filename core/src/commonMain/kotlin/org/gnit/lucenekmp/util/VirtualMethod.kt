package org.gnit.lucenekmp.util

import kotlin.reflect.KClass

/**
 * Minimal common-code port of Lucene's VirtualMethod.
 *
 * Reflection is not generally available in Kotlin common, so this implementation
 * provides best-effort behavior for test-framework usage.
 */
class VirtualMethod<C : Any>(
    private val baseClass: KClass<C>,
    private val method: String,
    vararg parameters: KClass<*>
) {
    @Suppress("UNUSED_PARAMETER")
    private val parameters: Array<out KClass<*>> = parameters

    fun getImplementationDistance(subclazz: KClass<out C>): Int {
        return if (subclazz == baseClass) 0 else 1
    }

    fun isOverriddenAsOf(subclazz: KClass<out C>): Boolean {
        return getImplementationDistance(subclazz) > 0
    }

    companion object {
        fun <C : Any> compareImplementationDistance(
            clazz: KClass<out C>,
            m1: VirtualMethod<C>,
            m2: VirtualMethod<C>
        ): Int {
            return m1.getImplementationDistance(clazz).compareTo(m2.getImplementationDistance(clazz))
        }
    }
}
