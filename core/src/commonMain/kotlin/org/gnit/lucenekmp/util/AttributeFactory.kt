package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttributeImpl
import org.gnit.lucenekmp.analysis.tokenattributes.FlagsAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.FlagsAttributeImpl
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttributeImpl
import org.gnit.lucenekmp.analysis.tokenattributes.PackedTokenAttributeImpl
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttributeImpl
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermFrequencyAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.search.BoostAttribute
import org.gnit.lucenekmp.search.BoostAttributeImpl
import org.gnit.lucenekmp.search.MaxNonCompetitiveBoostAttribute
import org.gnit.lucenekmp.search.MaxNonCompetitiveBoostAttributeImpl
import kotlin.reflect.KClass

// KMP-friendly factory that creates AttributeImpl instances for Attribute interfaces.
// Reflection-free; supports combined implementations via getStaticImplementation.
abstract class AttributeFactory {
    @Throws(Exception::class)
    abstract fun createAttributeInstance(attClass: KClass<out Attribute>): AttributeImpl

    private class DefaultAttributeFactory : AttributeFactory() {
        override fun createAttributeInstance(attClass: KClass<out Attribute>): AttributeImpl {
            return when (attClass) {
                // Minimal direct mappings for cases that are not covered by static combined impls
                CharTermAttribute::class -> CharTermAttributeImpl()
                OffsetAttribute::class -> OffsetAttributeImpl()
                PositionIncrementAttribute::class -> PackedTokenAttributeImpl()
                PositionLengthAttribute::class -> PackedTokenAttributeImpl()
                TypeAttribute::class -> PackedTokenAttributeImpl()
                TermFrequencyAttribute::class -> PackedTokenAttributeImpl()
                PayloadAttribute::class -> PayloadAttributeImpl()
                FlagsAttribute::class -> FlagsAttributeImpl()
                BoostAttribute::class -> BoostAttributeImpl()
                MaxNonCompetitiveBoostAttribute::class -> MaxNonCompetitiveBoostAttributeImpl()
                else -> throw IllegalArgumentException(
                    "Cannot find implementing class for: ${attClass.qualifiedName}"
                )
            }
        }
    }

    abstract class StaticImplementationAttributeFactory<A : AttributeImpl>
    protected constructor(private val delegate: AttributeFactory, clazz: KClass<out A>) : AttributeFactory() {
        @Suppress("UNCHECKED_CAST")
        private val clazz: KClass<A> = clazz as KClass<A>

        override fun createAttributeInstance(attClass: KClass<out Attribute>): AttributeImpl {
            return if (implImplementsAttribute(clazz, attClass)) createInstance() else delegate.createAttributeInstance(attClass)
        }

        protected abstract fun createInstance(): A

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || other::class != this::class) return false
            val af = other as StaticImplementationAttributeFactory<*>
            return this.delegate == af.delegate && this.clazz == af.clazz
        }

        override fun hashCode(): Int {
            return 31 * delegate.hashCode() + clazz.hashCode()
        }
    }

    companion object {
        val DEFAULT_ATTRIBUTE_FACTORY: AttributeFactory = DefaultAttributeFactory()

        fun <A : AttributeImpl> getStaticImplementation(
            delegate: AttributeFactory, clazz: KClass<out A>
        ): AttributeFactory {
            val ctor = ctorFor(clazz)
            return object : StaticImplementationAttributeFactory<A>(delegate, clazz) {
                override fun createInstance(): A {
                    val impl = ctor.invoke()
                    @Suppress("UNCHECKED_CAST")
                    return impl as A
                }
            }
        }

        private fun implImplementsAttribute(implClazz: KClass<*>, attClass: KClass<out Attribute>): Boolean {
            val interfaces = AttributeSource.getInterfaces(implClazz)
            return interfaces.any { it == attClass }
        }

        private fun ctorFor(clazz: KClass<out AttributeImpl>): () -> AttributeImpl {
            return when (clazz) {
                PackedTokenAttributeImpl::class -> { { PackedTokenAttributeImpl() } }
                CharTermAttributeImpl::class -> { { CharTermAttributeImpl() } }
                OffsetAttributeImpl::class -> { { OffsetAttributeImpl() } }
                PayloadAttributeImpl::class -> { { PayloadAttributeImpl() } }
                FlagsAttributeImpl::class -> { { FlagsAttributeImpl() } }
                BoostAttributeImpl::class -> { { BoostAttributeImpl() } }
                MaxNonCompetitiveBoostAttributeImpl::class -> { { MaxNonCompetitiveBoostAttributeImpl() } }
                else -> throw IllegalArgumentException("No known no-arg constructor for ${clazz.qualifiedName}")
            }
        }
    }
}
