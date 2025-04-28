package org.gnit.lucenekmp.jdkport


/**
 * A base type for primitive specializations of `Iterator`.  Specialized
 * subtypes are provided for [int][OfInt], [long][OfLong], and
 * [double][OfDouble] values.
 *
 *
 * The specialized subtype default implementations of [Iterator.next]
 * and [Iterator.forEachRemaining] box
 * primitive values to instances of their corresponding wrapper class.  Such
 * boxing may offset any advantages gained when using the primitive
 * specializations.  To avoid boxing, the corresponding primitive-based methods
 * should be used.  For example, [PrimitiveIterator.OfInt.nextInt] and
 * [PrimitiveIterator.OfInt.forEachRemaining]
 * should be used in preference to [PrimitiveIterator.OfInt.next] and
 * [PrimitiveIterator.OfInt.forEachRemaining].
 *
 *
 * Iteration of primitive values using boxing-based methods
 * [next()][Iterator.next] and
 * [forEachRemaining()][Iterator.forEachRemaining],
 * does not affect the order in which the values, transformed to boxed values,
 * are encountered.
 *
 * @implNote
 * If the boolean system property `org.openjdk.java.util.stream.tripwire`
 * is set to `true` then diagnostic warnings are reported if boxing of
 * primitive values occur when operating on primitive subtype specializations.
 *
 * @param <T> the type of elements returned by this PrimitiveIterator.  The
 * type must be a wrapper type for a primitive type, such as
 * `Integer` for the primitive `int` type.
 * @param <T_CONS> the type of primitive consumer.  The type must be a
 * primitive specialization of [java.util.function.Consumer] for
 * `T`, such as [java.util.function.IntConsumer] for
 * `Integer`.
 *
 * @since 1.8
</T_CONS></T> */
interface PrimitiveIterator<T, T_CONS> : MutableIterator<T> {
    /**
     * Performs the given action for each remaining element until all elements
     * have been processed or the action throws an exception.  Actions are
     * performed in the order of iteration, if that order is specified.
     * Exceptions thrown by the action are relayed to the caller.
     *
     *
     * The behavior of an iterator is unspecified if the action modifies the
     * source of elements in any way (even by calling the [remove][.remove]
     * method or other mutator methods of `Iterator` subtypes),
     * unless an overriding class has specified a concurrent modification policy.
     *
     *
     * Subsequent behavior of an iterator is unspecified if the action throws an
     * exception.
     *
     * @param action The action to be performed for each element
     * @throws NullPointerException if the specified action is null
     */
    fun forEachRemaining(action: T_CONS)

    /**
     * An Iterator specialized for `int` values.
     * @since 1.8
     */
    interface OfInt : PrimitiveIterator<Int, (Int) -> Unit> {
        /**
         * Returns the next `int` element in the iteration.
         *
         * @return the next `int` element in the iteration
         * @throws NoSuchElementException if the iteration has no more elements
         */
        fun nextInt(): Int

        /**
         * {@inheritDoc}
         * @implSpec
         *
         * The default implementation behaves as if:
         * <pre>`while (hasNext())
         * action(nextInt());
        `</pre> *
         */
        override fun forEachRemaining(action: (Int) -> Unit) {
            while (hasNext()) action(nextInt())
        }

        /**
         * {@inheritDoc}
         * @implSpec
         * The default implementation boxes the result of calling
         * [.nextInt], and returns that boxed result.
         */
        override fun next(): Int {
            return nextInt()
        }

        /**
         * {@inheritDoc}
         * @implSpec
         * The action is adapted to an instance of the function type,
         * and then passed to [.forEachRemaining].
         */
        /*fun forEachRemaining(action: (Any?) -> Unit) {
            forEachRemaining { value: Int -> action(value) }
        }*/
    }

    /**
     * An Iterator specialized for `long` values.
     * @since 1.8
     */
    interface OfLong : PrimitiveIterator<Long, (Long) -> Unit> {
        /**
         * Returns the next `long` element in the iteration.
         *
         * @return the next `long` element in the iteration
         * @throws NoSuchElementException if the iteration has no more elements
         */
        fun nextLong(): Long

        /**
         * {@inheritDoc}
         * @implSpec
         *
         * The default implementation behaves as if:
         * <pre>`while (hasNext())
         * action(nextLong());
        `</pre> *
         */
        /*override fun forEachRemaining(action: (Long) -> Unit) {
            while (hasNext()) action(nextLong())
        }*/

        /**
         * {@inheritDoc}
         * @implSpec
         * The default implementation boxes the result of calling
         * [.nextLong], and returns that boxed result.
         */
        override fun next(): Long {
            return nextLong()
        }

        /**
         * {@inheritDoc}
         * @implSpec
         * The action is adapted to an instance of the function type,
         * and then passed to [.forEachRemaining].
         */
        fun forEachRemaining(action: (Any?) -> Unit) {
            forEachRemaining { value: Long -> action(value) }
        }
    }

    /**
     * An Iterator specialized for `double` values.
     * @since 1.8
     */
    interface OfDouble : PrimitiveIterator<Double, (Double) -> Unit> {
        /**
         * Returns the next `double` element in the iteration.
         *
         * @return the next `double` element in the iteration
         * @throws NoSuchElementException if the iteration has no more elements
         */
        fun nextDouble(): Double

        /**
         * {@inheritDoc}
         * @implSpec
         *
         * The default implementation behaves as if:
         * <pre>`while (hasNext())
         * action(nextDouble());
        `</pre> *
         */
        /*override fun forEachRemaining(action: (Double) -> Unit) {
            while (hasNext()) action(nextDouble())
        }*/

        /**
         * {@inheritDoc}
         * @implSpec
         * The default implementation boxes the result of calling
         * [.nextDouble], and returns that boxed result.
         */
        override fun next(): Double {
            return nextDouble()
        }

        /**
         * {@inheritDoc}
         * @implSpec
         * The action is adapted to an instance of the function type,
         * and then passed to [.forEachRemaining].
         */
        /*fun forEachRemaining(action: (Any?) -> Unit) {
            forEachRemaining { value: Double -> action(value) }
        }*/
    }
}
