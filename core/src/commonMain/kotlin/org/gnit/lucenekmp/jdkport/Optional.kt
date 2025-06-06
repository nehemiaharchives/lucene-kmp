package org.gnit.lucenekmp.jdkport


/**
 * porterd from java.util.Optional
 *
 * A container object which may or may not contain a non-`null` value.
 * If a value is present, `isPresent()` returns `true`. If no
 * value is present, the object is considered *empty* and
 * `isPresent()` returns `false`.
 *
 *
 * Additional methods that depend on the presence or absence of a contained
 * value are provided, such as [orElse()][.orElse]
 * (returns a default value if no value is present) and
 * [ifPresent()][.ifPresent] (performs an
 * action if a value is present).
 *
 *
 * This is a [value-based]({@docRoot}/java.base/java/lang/doc-files/ValueBased.html)
 * class; programmers should treat instances that are
 * [equal][.equals] as interchangeable and should not
 * use instances for synchronization, or unpredictable behavior may
 * occur. For example, in a future release, synchronization may fail.
 *
 * @apiNote
 * `Optional` is primarily intended for use as a method return type where
 * there is a clear need to represent "no result," and where using `null`
 * is likely to cause errors. A variable whose type is `Optional` should
 * never itself be `null`; it should always point to an `Optional`
 * instance.
 *
 * @param <T> the type of value
 * @since 1.8
</T> */

class Optional<T> private constructor(value: T?) {
    /**
     * If non-null, the value; if null, indicates no value is present
     */
    private val value: T?

    /**
     * Constructs an instance with the described value.
     *
     * @param value the value to describe; it's the caller's responsibility to
     * ensure the value is non-`null` unless creating the singleton
     * instance returned by `empty()`.
     */
    init {
        this.value = value
    }

    /**
     * If a value is present, returns the value, otherwise throws
     * `NoSuchElementException`.
     *
     * @apiNote
     * The preferred alternative to this method is [.orElseThrow].
     *
     * @return the non-`null` value described by this `Optional`
     * @throws NoSuchElementException if no value is present
     */
    fun get(): T {
        if (value == null) {
            throw NoSuchElementException("No value present")
        }
        return value
    }

    val isPresent: Boolean
        /**
         * If a value is present, returns `true`, otherwise `false`.
         *
         * @return `true` if a value is present, otherwise `false`
         */
        get() = value != null

    val isEmpty: Boolean
        /**
         * If a value is  not present, returns `true`, otherwise
         * `false`.
         *
         * @return  `true` if a value is not present, otherwise `false`
         * @since   11
         */
        get() = value == null

    /**
     * If a value is present, performs the given action with the value,
     * otherwise does nothing.
     *
     * @param action the action to be performed if a value is present.
     *               (Note: in Kotlin, the function type (T) -> Unit cannot be null.)
     */
    fun ifPresent(action: (T) -> Unit) {
        value?.let(action)
    }

    /**
     * If a value is present, performs the given action with the value,
     * otherwise performs the given empty-based action.
     *
     * @param action the action to be performed if a value is present.
     * @param emptyAction the action to be performed if no value is present.
     */
    fun ifPresentOrElse(action: (T) -> Unit, emptyAction: () -> Unit) {
        if (value != null) {
            action(value)
        } else {
            emptyAction()
        }
    }


    /**
     * If a value is present, and the value matches the given predicate,
     * returns an Optional describing the value, otherwise returns an empty Optional.
     *
     * @param predicate the predicate to apply to a value, if present
     * @return an Optional describing the value if present and matching the predicate, or an empty Optional otherwise
     */
    fun filter(predicate: (T?) -> Boolean): Optional<T?> {
        if (isEmpty) return this as Optional<T?>
        return if (predicate(value)) this as Optional<T?> else empty()
    }


    /**
     * If a value is present, returns an Optional describing (as if by ofNullable)
     * the result of applying the given mapping function to the value, otherwise returns an empty Optional.
     *
     * If the mapping function returns a null result then this method returns an empty Optional.
     *
     * @param mapper the mapping function to apply to a value, if present
     * @param <U> the type of the value returned from the mapping function
     * @return an Optional describing the result of applying a mapping function to the value of this Optional,
     * if a value is present, otherwise an empty Optional
     */
    fun <U> map(mapper: (T?) -> U?): Optional<U?> {
        return if (isEmpty) {
            empty()
        } else {
            ofNullable(mapper(value))
        }
    }

    /**
     * If a value is present, returns the result of applying the given Optional-bearing
     * mapping function to the value, otherwise returns an empty Optional.
     *
     * This method is similar to [map], but the mapping function is one whose result is already
     * an Optional, and if invoked, flatMap does not wrap it within an additional Optional.
     *
     * @param mapper the mapping function to apply to a value, if present
     * @param <U> the type of value of the Optional returned by the mapping function
     * @return the result of applying an Optional-bearing mapping function to the value of this Optional,
     * if a value is present, otherwise an empty Optional
     * @throws NullPointerException if the mapping function returns a null result
     */
    fun <U> flatMap(mapper: (T?) -> Optional<U?>?): Optional<U?> {
        if (isEmpty) {
            return empty()
        }
        val r = mapper(value) ?: throw NullPointerException("Mapper returned null")
        return r
    }

    /**
     * If a value is present, returns an Optional describing the value,
     * otherwise returns an Optional produced by the supplying function.
     *
     * @param supplier the supplying function that produces an Optional to be returned.
     * @return an Optional describing the value if present, or the Optional produced by the supplier if not.
     * @throws NullPointerException if the supplier produces a null result.
     */
    fun or(supplier: () -> Optional<T?>): Optional<T?> {
        if (isPresent) return this as Optional<T?>
        return requireNotNull(supplier()) { "Supplier returned null" }
    }


    /**
     * If a value is present, returns a sequential [Sequence] containing
     * only that value, otherwise returns an empty [Sequence].
     *
     * This method can be used to transform a sequence of optional elements
     * into a sequence of present values.
     *
     * @return the optional value as a [Sequence]
     */
    fun stream(): Sequence<T?> =
        if (isEmpty) emptySequence() else sequenceOf(value)

    /**
     * If a value is present, returns the value, otherwise returns
     * `other`.
     *
     * @param other the value to be returned, if no value is present.
     * May be `null`.
     * @return the value, if present, otherwise `other`
     */
    fun orElse(other: T?): T? {
        return if (value != null) value else other
    }

    /**
     * If a value is present, returns the value, otherwise returns the result
     * produced by the supplying function.
     *
     * @param supplier the supplying function that produces a value to be returned
     * @return the value, if present, otherwise the result produced by the supplying function
     */
    fun orElseGet(supplier: () -> T?): T? = value ?: supplier()

    /**
     * If a value is present, returns the value, otherwise throws
     * NoSuchElementException.
     *
     * @return the non-null value described by this Optional
     * @throws NoSuchElementException if no value is present
     * @since 10
     */
    fun orElseThrow(): T = value ?: throw NoSuchElementException("No value present")


    /**
     * If a value is present, returns the value, otherwise throws an exception
     * produced by the exception supplying function.
     *
     * A method reference to an exception constructor (with no arguments) can be used,
     * for example: `::IllegalStateException`.
     *
     * @param exceptionSupplier the supplying function that produces an exception to be thrown.
     * @return the value, if present.
     * @throws X if no value is present.
     */
    fun <X : Throwable> orElseThrow(exceptionSupplier: () -> X): T =
        value ?: throw exceptionSupplier()

    /**
     * Indicates whether some other object is "equal to" this `Optional`.
     * The other object is considered equal if:
     *
     *  * it is also an `Optional` and;
     *  * both instances have no value present or;
     *  * the present values are "equal to" each other via `equals()`.
     *
     *
     * @param obj an object to be tested for equality
     * @return `true` if the other object is "equal to" this object
     * otherwise `false`
     */
    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }

        return obj is Optional<*>
                && value == obj.value
    }

    /**
     * Returns the hash code of the value, if present, otherwise `0`
     * (zero) if no value is present.
     *
     * @return hash code value of the present value or `0` if no value is
     * present
     */
    override fun hashCode(): Int {
        return Objects.hashCode(value)
    }

    /**
     * Returns a non-empty string representation of this `Optional`
     * suitable for debugging.  The exact presentation format is unspecified and
     * may vary between implementations and versions.
     *
     * @implSpec
     * If a value is present the result must include its string representation
     * in the result.  Empty and present `Optional`s must be unambiguously
     * differentiable.
     *
     * @return the string representation of this instance
     */
    override fun toString(): String {
        return if (value != null)
            ("Optional[$value]")
        else
            "Optional.empty"
    }

    companion object {
        /**
         * Common instance for `empty()`.
         */
        private val EMPTY: Optional<*> = Optional<Any?>(null)

        /**
         * Returns an empty `Optional` instance.  No value is present for this
         * `Optional`.
         *
         * @apiNote
         * Though it may be tempting to do so, avoid testing if an object is empty
         * by comparing with `==` or `!=` against instances returned by
         * `Optional.empty()`.  There is no guarantee that it is a singleton.
         * Instead, use [.isEmpty] or [.isPresent].
         *
         * @param <T> The type of the non-existent value
         * @return an empty `Optional`
        </T> */
        fun <T> empty(): Optional<T> {
            val t = EMPTY as Optional<T>
            return t
        }

        /**
         * Returns an Optional describing the given non-null value.
         *
         * @param value the value to describe, which must be non-null
         * @param T the type of the value
         * @return an Optional with the value present
         * @throws NullPointerException if value is null
         */
        fun <T> of(value: T): Optional<T> {
            return Optional(value ?: throw NullPointerException())
        }

        /**
         * Returns an `Optional` describing the given value, if
         * non-`null`, otherwise returns an empty `Optional`.
         *
         * @param value the possibly-`null` value to describe
         * @param <T> the type of the value
         * @return an `Optional` with a present value if the specified value
         * is non-`null`, otherwise an empty `Optional`
        </T> */
        fun <T> ofNullable(value: T?): Optional<T?> {
            return if (value == null)
                EMPTY as Optional<T?>
            else
                Optional<T?>(value)
        }
    }
}
