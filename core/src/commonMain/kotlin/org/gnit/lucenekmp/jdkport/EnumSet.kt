package org.gnit.lucenekmp.jdkport

/*import jdk.internal.access.SharedSecrets
import java.util.JumboEnumSet
import java.util.RegularEnumSet*/
import kotlin.reflect.KClass


/**
 * A specialized [Set] implementation for use with enum types.  All of
 * the elements in an enum set must come from a single enum type that is
 * specified, explicitly or implicitly, when the set is created.  Enum sets
 * are represented internally as bit vectors.  This representation is
 * extremely compact and efficient. The space and time performance of this
 * class should be good enough to allow its use as a high-quality, typesafe
 * alternative to traditional `int`-based "bit flags."  Even bulk
 * operations (such as `containsAll` and `retainAll`) should
 * run very quickly if their argument is also an enum set.
 *
 *
 * The iterator returned by the `iterator` method traverses the
 * elements in their *natural order* (the order in which the enum
 * constants are declared).  The returned iterator is *weakly
 * consistent*: it will never throw [ConcurrentModificationException]
 * and it may or may not show the effects of any modifications to the set that
 * occur while the iteration is in progress.
 *
 *
 * Null elements are not permitted.  Attempts to insert a null element
 * will throw [NullPointerException].  Attempts to test for the
 * presence of a null element or to remove one will, however, function
 * properly.
 *
 * <P>Like most collection implementations, `EnumSet` is not
 * synchronized.  If multiple threads access an enum set concurrently, and at
 * least one of the threads modifies the set, it should be synchronized
 * externally.  This is typically accomplished by synchronizing on some
 * object that naturally encapsulates the enum set.  If no such object exists,
 * the set should be "wrapped" using the [Collections.synchronizedSet]
 * method.  This is best done at creation time, to prevent accidental
 * unsynchronized access:
 *
</P> * <pre>
 * Set&lt;MyEnum&gt; s = Collections.synchronizedSet(EnumSet.noneOf(MyEnum.class));
</pre> *
 *
 *
 * Implementation note: All basic operations execute in constant time.
 * They are likely (though not guaranteed) to be much faster than their
 * [HashSet] counterparts.  Even bulk operations execute in
 * constant time if their argument is also an enum set.
 *
 *
 * This class is a member of the
 * [
 * Java Collections Framework]({@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework).
 *
 * @param <E> the enum type of elements maintained by this set
 *
 * @author Josh Bloch
 * @since 1.5
 * @see EnumMap
</E> */

@Ported(from = "java.util.EnumSet")
abstract class EnumSet<E : Enum<E>>(
    elementType: KClass<E>,
    universe: Array<Enum<E>>
) : AbstractMutableSet<E>(), Cloneable<EnumSet<E>>/*, java.io.Serializable*/ {
    /**
     * The class of all the elements of this set.
     */
    /*@Transient*/
    val elementType: KClass<E>

    /**
     * All of the values comprising E.  (Cached for performance.)
     */
    /*@Transient*/
    val universe: Array<Enum<E>>

    init {
        this.elementType = elementType
        this.universe = universe
    }

    /**
     * Adds all of the elements from the appropriate enum type to this enum
     * set, which is empty prior to the call.
     */
    abstract fun addAll()

    /**
     * Adds the specified range to this enum set, which is empty prior
     * to the call.
     */
    abstract fun addRange(from: E, to: E)

    /**
     * Returns a copy of this set.
     *
     * @return a copy of this set
     */
    override fun clone(): EnumSet<E> {
        /*try {
            return super.clone() as EnumSet<E>
        } catch (e: *//*CloneNotSupported*//*Exception) {
            throw AssertionError(e)
        }*/

         val result = RegularEnumSet(elementType, universe)
         for (e in this) result.add(e)
         return result
    }

    /**
     * Complements the contents of this enum set.
     */
    abstract fun complement()

    /**
     * Throws an exception if e is not of the correct type for this enum set.
     */
    /*fun typeCheck(e: E) {
        val eClass: KClass<*> = e.javaClass
        if (eClass != elementType && eClass.getSuperclass() != elementType) throw KClassCastException(
            eClass.toString() + " != " + elementType
        )
    }*/

    /**
     * This class is used to serialize all EnumSet instances, regardless of
     * implementation type.  It captures their "logical contents" and they
     * are reconstructed using public static factories.  This is necessary
     * to ensure that the existence of a particular implementation type is
     * an implementation detail.
     *
     * @serial include
     */
    /*private class SerializationProxy<E : Enum<E>>
        (set: EnumSet<E>) : java.io.Serializable {
        *//**
         * The element type of this enum set.
         *
         * @serial
         *//*
        private val elementType: KClass<E>

        *//**
         * The elements contained in this enum set.
         *
         * @serial
         *//*
        private val elements: Array<Enum<*>>

        *//**
         * Returns an `EnumSet` object with initial state
         * held by this proxy.
         *
         * @return a `EnumSet` object with initial state
         * held by this proxy
         *//*
        *//*@java.io.Serial*//*
        fun readResolve(): Any {
            // instead of cast to E, we should perhaps use elementType.cast()
            // to avoid injection of forged stream, but it will slow the
            // implementation
            val result = noneOf<E>(elementType)
            for (e in elements) result.add(e as E)
            return result
        }

        init {
            elementType = set.elementType
            elements = set.toArray<Enum<*>>(ZERO_LENGTH_ENUM_ARRAY)
        }

        companion object {
            private val ZERO_LENGTH_ENUM_ARRAY = kotlin.arrayOfNulls<Enum<*>>(0)

            *//*@java.io.Serial*//*
            private const val serialVersionUID = 362491234563181265L
        }
    }*/

    /**
     * Returns a
     * [
     * SerializationProxy]({@docRoot}/serialized-form.html#java.util.EnumSet.SerializationProxy)
     * representing the state of this instance.
     *
     * @return a [SerializationProxy]
     * representing the state of this instance
     */
    /*@java.io.Serial*/
    /*fun writeReplace(): Any {
        return EnumSet.SerializationProxy<E>(this)
    }*/

    /**
     * Throws `InvalidObjectException`.
     * @param s the stream
     * @throws InvalidObjectException always
     */
    /*@java.io.Serial*/
    /*@Throws(InvalidObjectException::class)
    private fun readObject(s: java.io.ObjectInputStream) {
        throw InvalidObjectException("Proxy required")
    }*/

    /**
     * Throws `InvalidObjectException`.
     * @throws InvalidObjectException always
     */
    /*@java.io.Serial*/
    /*@Throws(InvalidObjectException::class)
    private fun readObjectNoData() {
        throw InvalidObjectException("Proxy required")
    }*/

    companion object {
        // declare EnumSet.class serialization compatibility with JDK 8
        /*@java.io.Serial*/
        //private const val serialVersionUID = 1009687484059888093L

        /**
         * Creates an empty enum set with the specified element type.
         *
         * @param <E> The class of the elements in the set
         * @param elementType the class object of the element type for this enum
         * set
         * @return An empty enum set of the specified type.
         * @throws NullPointerException if `elementType` is null
        </E> */
        /*fun <E : Enum<E>> noneOf(elementType: KClass<out E>): EnumSet<E> {
            val universe: Array<Enum<E>>? = getUniverse<E>(elementType)
            if (universe == null) throw ClassCastException("$elementType not an enum")

            *//*if (universe.size <= 64)*//* return RegularEnumSet<E>(elementType, universe)
            //else return JumboEnumSet<E>(elementType, universe)
        }*/

         inline fun <reified E : Enum<E>> noneOf(elementType: KClass<out E>): EnumSet<E> {
             val universe = getUniverse<E>(elementType)
             ?: throw ClassCastException("$elementType not an enum")
             @Suppress("UNCHECKED_CAST")
             val et = elementType as KClass<E>
             /*if (universe.size <=64)*/ return RegularEnumSet<E>(et, universe as Array<Enum<*>>)
             //else return JumboEnumSet<E>(et, universe)
         }




        /**
         * Creates an enum set containing all of the elements in the specified
         * element type.
         *
         * @param <E> The class of the elements in the set
         * @param elementType the class object of the element type for this enum
         * set
         * @return An enum set containing all the elements in the specified type.
         * @throws NullPointerException if `elementType` is null
        </E> */
        inline fun <reified E : Enum<E>> allOf(elementType: KClass<E>): EnumSet<E> {
            val result = noneOf<E>(elementType)
            result.addAll()
            return result
        }

        /**
         * Creates an enum set with the same element type as the specified enum
         * set, initially containing the same elements (if any).
         *
         * @param <E> The class of the elements in the set
         * @param s the enum set from which to initialize this enum set
         * @return A copy of the specified enum set.
         * @throws NullPointerException if `s` is null
        </E> */
        fun <E : Enum<E>> copyOf(s: EnumSet<E>): EnumSet<E> {
            return s.clone()
        }

        /**
         * Creates an enum set initialized from the specified collection.  If
         * the specified collection is an `EnumSet` instance, this static
         * factory method behaves identically to [.copyOf].
         * Otherwise, the specified collection must contain at least one element
         * (in order to determine the new enum set's element type).
         *
         * @param <E> The class of the elements in the collection
         * @param c the collection from which to initialize this enum set
         * @return An enum set initialized from the given collection.
         * @throws IllegalArgumentException if `c` is not an
         * `EnumSet` instance and contains no elements
         * @throws NullPointerException if `c` is null
        </E> */
        inline fun <reified E : Enum<E>> copyOf(c: MutableCollection<E>): EnumSet<E> {
            if (c is EnumSet<*>) {
                return (c as EnumSet<E>).clone()
            } else {
                require(!c.isEmpty()) { "Collection is empty" }
                val i = c.iterator()
                val first = i.next()
                val result = of<E>(first)
                while (i.hasNext()) result.add(i.next())
                return result
            }
        }

        /**
         * Creates an enum set with the same element type as the specified enum
         * set, initially containing all the elements of this type that are
         * *not* contained in the specified set.
         *
         * @param <E> The class of the elements in the enum set
         * @param s the enum set from whose complement to initialize this enum set
         * @return The complement of the specified set in this set
         * @throws NullPointerException if `s` is null
        </E> */
        fun <E : Enum<E>> complementOf(s: EnumSet<E>): EnumSet<E> {
            val result = copyOf<E>(s)
            result.complement()
            return result
        }

        /**
         * Creates an enum set initially containing the specified element.
         *
         * Overloadings of this method exist to initialize an enum set with
         * one through five elements.  A sixth overloading is provided that
         * uses the varargs feature.  This overloading may be used to create
         * an enum set initially containing an arbitrary number of elements, but
         * is likely to run slower than the overloadings that do not use varargs.
         *
         * @param <E> The class of the specified element and of the set
         * @param e the element that this set is to contain initially
         * @throws NullPointerException if `e` is null
         * @return an enum set initially containing the specified element
        </E> */
        inline fun <reified E : Enum<E>> of(e: E): EnumSet<E> {
            val result = noneOf<E>(e::class)
            result.add(e)
            return result
        }

        /**
         * Creates an enum set initially containing the specified elements.
         *
         * Overloadings of this method exist to initialize an enum set with
         * one through five elements.  A sixth overloading is provided that
         * uses the varargs feature.  This overloading may be used to create
         * an enum set initially containing an arbitrary number of elements, but
         * is likely to run slower than the overloadings that do not use varargs.
         *
         * @param <E> The class of the parameter elements and of the set
         * @param e1 an element that this set is to contain initially
         * @param e2 another element that this set is to contain initially
         * @throws NullPointerException if any parameters are null
         * @return an enum set initially containing the specified elements
        </E> */
        inline fun <reified E : Enum<E>> of(e1: E, e2: E): EnumSet<E> {
            val result = noneOf<E>(e1::class)
            result.add(e1)
            result.add(e2)
            return result
        }

        /**
         * Creates an enum set initially containing the specified elements.
         *
         * Overloadings of this method exist to initialize an enum set with
         * one through five elements.  A sixth overloading is provided that
         * uses the varargs feature.  This overloading may be used to create
         * an enum set initially containing an arbitrary number of elements, but
         * is likely to run slower than the overloadings that do not use varargs.
         *
         * @param <E> The class of the parameter elements and of the set
         * @param e1 an element that this set is to contain initially
         * @param e2 another element that this set is to contain initially
         * @param e3 another element that this set is to contain initially
         * @throws NullPointerException if any parameters are null
         * @return an enum set initially containing the specified elements
        </E> */
        inline fun <reified E : Enum<E>> of(e1: E, e2: E, e3: E): EnumSet<E> {
            val result = noneOf<E>(e1::class)
            result.add(e1)
            result.add(e2)
            result.add(e3)
            return result
        }

        /**
         * Creates an enum set initially containing the specified elements.
         *
         * Overloadings of this method exist to initialize an enum set with
         * one through five elements.  A sixth overloading is provided that
         * uses the varargs feature.  This overloading may be used to create
         * an enum set initially containing an arbitrary number of elements, but
         * is likely to run slower than the overloadings that do not use varargs.
         *
         * @param <E> The class of the parameter elements and of the set
         * @param e1 an element that this set is to contain initially
         * @param e2 another element that this set is to contain initially
         * @param e3 another element that this set is to contain initially
         * @param e4 another element that this set is to contain initially
         * @throws NullPointerException if any parameters are null
         * @return an enum set initially containing the specified elements
        </E> */
        inline fun <reified E : Enum<E>> of(e1: E, e2: E, e3: E, e4: E): EnumSet<E> {
            val result = noneOf<E>(e1::class)
            result.add(e1)
            result.add(e2)
            result.add(e3)
            result.add(e4)
            return result
        }

        /**
         * Creates an enum set initially containing the specified elements.
         *
         * Overloadings of this method exist to initialize an enum set with
         * one through five elements.  A sixth overloading is provided that
         * uses the varargs feature.  This overloading may be used to create
         * an enum set initially containing an arbitrary number of elements, but
         * is likely to run slower than the overloadings that do not use varargs.
         *
         * @param <E> The class of the parameter elements and of the set
         * @param e1 an element that this set is to contain initially
         * @param e2 another element that this set is to contain initially
         * @param e3 another element that this set is to contain initially
         * @param e4 another element that this set is to contain initially
         * @param e5 another element that this set is to contain initially
         * @throws NullPointerException if any parameters are null
         * @return an enum set initially containing the specified elements
        </E> */
        inline fun <reified E : Enum<E>> of(
            e1: E, e2: E, e3: E, e4: E,
            e5: E
        ): EnumSet<E> {
            val result = noneOf<E>(e1::class)
            result.add(e1)
            result.add(e2)
            result.add(e3)
            result.add(e4)
            result.add(e5)
            return result
        }

        /**
         * Creates an enum set initially containing the specified elements.
         * This factory, whose parameter list uses the varargs feature, may
         * be used to create an enum set initially containing an arbitrary
         * number of elements, but it is likely to run slower than the overloadings
         * that do not use varargs.
         *
         * @param <E> The class of the parameter elements and of the set
         * @param first an element that the set is to contain initially
         * @param rest the remaining elements the set is to contain initially
         * @throws NullPointerException if any of the specified elements are null,
         * or if `rest` is null
         * @return an enum set initially containing the specified elements
        </E> */
        /*@java.lang.SafeVarargs*/
        inline fun <reified E : Enum<E>> of(first: E, vararg rest: E): EnumSet<E> {
            val result = noneOf<E>(first::class)
            result.add(first)
            for (e in rest) result.add(e)
            return result
        }

        /**
         * Creates an enum set initially containing all of the elements in the
         * range defined by the two specified endpoints.  The returned set will
         * contain the endpoints themselves, which may be identical but must not
         * be out of order.
         *
         * @param <E> The class of the parameter elements and of the set
         * @param from the first element in the range
         * @param to the last element in the range
         * @throws NullPointerException if `from` or `to` are null
         * @throws IllegalArgumentException if `from.compareTo(to) > 0`
         * @return an enum set initially containing all of the elements in the
         * range defined by the two specified endpoints
        </E> */
        inline fun <reified E : Enum<E>> range(from: E, to: E): EnumSet<E> {
            require(from!!.compareTo(to) <= 0) { "$from > $to" }
            val result = noneOf<E>(from::class)
            result.addRange(from, to)
            return result
        }

        /**
         * Returns all of the values comprising E.
         * The result is uncloned, cached, and shared by all callers.
         */
        inline fun <reified E : Enum<E>> getUniverse(elementType: KClass<out E>): Array<E>? {
            return enumValues()
        }
    }
}
