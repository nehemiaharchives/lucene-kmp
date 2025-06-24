package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.KClassValue
import org.gnit.lucenekmp.jdkport.MethodHandle
import org.gnit.lucenekmp.jdkport.MethodHandles
import org.gnit.lucenekmp.jdkport.MethodType
import org.gnit.lucenekmp.jdkport.Void
import org.gnit.lucenekmp.jdkport.asSubclass
import org.gnit.lucenekmp.jdkport.classForName
import org.gnit.lucenekmp.jdkport.getClassLoader
import org.gnit.lucenekmp.jdkport.isAssignableFrom
import kotlin.reflect.KClass


/** An AttributeFactory creates instances of [AttributeImpl]s.  */
abstract class AttributeFactory {
    /**
     * Returns an [AttributeImpl] for the supplied [Attribute] interface class.
     *
     * @throws UndeclaredThrowableException A wrapper runtime exception thrown if the constructor of
     * the attribute class throws a checked exception. Note that attributes should not throw or
     * declare checked exceptions; this may be verified and fail early in the future.
     */
    @Throws(/*java.lang.reflect.UndeclaredThrowableException::class*/ Exception::class)
    abstract fun createAttributeInstance(attClass: KClass<out Attribute>): AttributeImpl

    private class DefaultAttributeFactory : AttributeFactory() {
        override fun createAttributeInstance(attClass: KClass<out Attribute>): AttributeImpl {
            val ctor = findAttributeImplCtor(
                findImplClass(attClass.asSubclass(Attribute::class as KClass<*>) as KClass<Attribute>)
            )
            try {
                return ctor.invokeExact()
            } catch (e: Error) {
                throw e
            } catch (e: RuntimeException) {
                throw e
            } catch (e: Throwable) {
                throw /*java.lang.reflect.UndeclaredThrowable*/Exception(e)
            }
        }

        fun findImplClass(attClass: KClass<Attribute>): KClass<AttributeImpl> {
            try {
                return /*KClass*/classForName(attClass.qualifiedName + "Impl", true, attClass.getClassLoader())
                    .asSubclass(AttributeImpl::class) as KClass<AttributeImpl>
            } catch (cnfe: /*KClassNotFound*/Exception) {
                throw IllegalArgumentException(
                    "Cannot find implementing class for: " + attClass.qualifiedName, cnfe
                )
            }
        }
    }



    /**
     * **Expert**: AttributeFactory returning an instance of the given `clazz` for the
     * attributes it implements. For all other attributes it calls the given delegate factory as
     * fallback. This class can be used to prefer a specific `AttributeImpl` which combines
     * multiple attributes over separate classes.
     *
     * @lucene.internal
     */
    abstract class StaticImplementationAttributeFactory<A : AttributeImpl>
    protected constructor(private val delegate: AttributeFactory, clazz: KClass<out A>) : AttributeFactory() {
        private val clazz: KClass<A> = clazz as KClass<A>

        override fun createAttributeInstance(attClass: KClass<out Attribute>): AttributeImpl {
            return if (attClass.isAssignableFrom(clazz))
                createInstance()
            else
                delegate.createAttributeInstance(attClass)
        }

        /** Creates an instance of `A`.  */
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
        /** Returns a correctly typed [MethodHandle] for the no-arg ctor of the given class.  */
        fun findAttributeImplCtor(clazz: KClass<*>): MethodHandle {
            try {
                return lookup.findConstructor(clazz, NO_ARG_CTOR).asType(NO_ARG_RETURNING_ATTRIBUTEIMPL)
            } catch (e: /*NoSuchMethod*/Exception) {
                throw IllegalArgumentException(
                    "Cannot lookup accessible no-arg constructor for: " + clazz.qualifiedName, e
                )
            } catch (e: /*IllegalAccess*/Exception) {
                throw IllegalArgumentException(
                    "Cannot lookup accessible no-arg constructor for: " + clazz.qualifiedName, e
                )
            }
        }

        private val lookup: MethodHandles.Lookup = MethodHandles.publicLookup()
        private val NO_ARG_CTOR: MethodType = MethodType.methodType(Void.TYPE)
        private val NO_ARG_RETURNING_ATTRIBUTEIMPL: MethodType =
            MethodType.methodType(
                AttributeImpl::class
            )

        /**
         * This is the default factory that creates [AttributeImpl]s using the class name of the
         * supplied [Attribute] interface class by appending `Impl` to it.
         */
        val DEFAULT_ATTRIBUTE_FACTORY: AttributeFactory = DefaultAttributeFactory()

        /**
         * Returns an AttributeFactory returning an instance of the given `clazz` for the attributes
         * it implements. The given `clazz` must have a public no-arg constructor. For all other
         * attributes it calls the given delegate factory as fallback. This method can be used to prefer a
         * specific `AttributeImpl` which combines multiple attributes over separate classes.
         *
         *
         * Please save instances created by this method in a static final field, because on each call,
         * this does reflection for creating a [MethodHandle].
         */
        fun <A : AttributeImpl> getStaticImplementation(
            delegate: AttributeFactory, clazz: KClass<out A>
        ): AttributeFactory {
            val constr: MethodHandle = findAttributeImplCtor(clazz)
            return object : StaticImplementationAttributeFactory<A>(delegate, clazz) {
                override fun createInstance(): A {
                    try {
                        // be explicit with casting, so javac compiles correct call to polymorphic signature:
                        val impl = constr.invokeExact() as AttributeImpl
                        // now cast to generic type:
                        return impl as A
                    } catch (e: Error) {
                        throw e
                    } catch (e: RuntimeException) {
                        throw e
                    } catch (e: Throwable) {
                        throw /*java.lang.reflect.UndeclaredThrowable*/Exception(e)
                    }
                }
            }
        }
    }
}
