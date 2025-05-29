package org.gnit.lucenekmp.jdkport

// Note: This is a port of java.util.Spliterator for Kotlin common.
// It aims to maintain API compatibility where possible, replacing Java functional
// interfaces with Kotlin function types.
// The Tripwire diagnostic logic from the original JDK code has been omitted.

/**
 * An object for traversing and partitioning elements of a source. The source
 * could be an array, a Collection, an IO channel, or a generator function.
 *
 * Spliterators support sequential traversal (tryAdvance, forEachRemaining)
 * and parallel decomposition (trySplit). They report characteristics
 * about their structure and elements.
 *
 * @param T the type of elements returned by this Spliterator
 */
interface Spliterator<T> {

    /**
     * If a remaining element exists: performs the given action on it,
     * returning `true`; else returns `false`. If this Spliterator is ORDERED,
     * the action is performed on the next element in encounter order.
     * Exceptions thrown by the action are relayed to the caller.
     *
     * @param action The action whose operation is performed at-most once.
     * @return `false` if no remaining elements existed upon entry, else `true`.
     * @throws NullPointerException if the specified action is null (implicit in Kotlin non-null type).
     */
    fun tryAdvance(action: (T) -> Unit): Boolean

    /**
     * Performs the given action for each remaining element, sequentially,
     * until all elements have been processed or the action throws an exception.
     * If this Spliterator is ORDERED, actions are performed in encounter order.
     * Exceptions thrown by the action are relayed to the caller.
     *
     * Default implementation repeatedly invokes [tryAdvance] until it returns `false`.
     *
     * @param action The action.
     * @throws NullPointerException if the specified action is null (implicit in Kotlin non-null type).
     */
    fun forEachRemaining(action: (T) -> Unit) {
        do { } while (tryAdvance(action))
    }

    /**
     * If this spliterator can be partitioned, returns a Spliterator
     * covering elements that will, upon return, not be covered by this Spliterator.
     *
     * If ORDERED, the returned Spliterator must cover a strict prefix.
     * Repeated calls must eventually return `null` unless the Spliterator is infinite.
     * See Java documentation for size estimate constraints after splitting.
     *
     * May return `null` for any reason (emptiness, inability to split, etc.).
     *
     * @return A `Spliterator` covering some portion of the elements, or `null`.
     */
    fun trySplit(): Spliterator<T>?

    /**
     * Returns an estimate of the number of elements that would be
     * encountered by a [forEachRemaining] traversal, or [Long.MAX_VALUE]
     * if infinite, unknown, or too expensive to compute.
     *
     * See Java documentation for accuracy requirements based on characteristics
     * like SIZED and SUBSIZED.
     *
     * @return The estimated size, or [Long.MAX_VALUE].
     */
    fun estimateSize(): Long

    /**
     * Convenience method returning [estimateSize] if SIZED, else -1L.
     *
     * Default implementation checks the SIZED characteristic.
     *
     * @return The exact size if known (SIZED), else -1L.
     */
    fun getExactSizeIfKnown(): Long {
        return if ((characteristics() and SIZED) == 0) -1L else estimateSize()
    }

    /**
     * Returns a set of characteristics of this Spliterator and its elements.
     * The result is represented as ORed values from the constants defined
     * in the companion object (ORDERED, DISTINCT, etc.).
     *
     * Repeated calls should return the same result prior to/between splits.
     *
     * @return A representation of characteristics.
     */
    fun characteristics(): Int

    /**
     * Returns `true` if this Spliterator's [characteristics] contain all of
     * the given characteristics.
     *
     * Default implementation checks if the corresponding bits are set.
     *
     * @param characteristics The characteristics to check for.
     * @return `true` if all specified characteristics are present, else `false`.
     */
    fun hasCharacteristics(characteristics: Int): Boolean {
        return (characteristics() and characteristics) == characteristics
    }

    /**
     * If SORTED by a Comparator, returns that Comparator.
     * If SORTED in natural order, returns `null`.
     * Otherwise (not SORTED), throws [IllegalStateException].
     *
     * Default implementation always throws [IllegalStateException].
     *
     * @return A Comparator, or `null` if sorted naturally.
     * @throws IllegalStateException if not SORTED.
     */
    fun getComparator(): Comparator<in T>? {
        throw IllegalStateException("Spliterator does not report SORTED characteristic")
    }

    companion object {
        /** ORDERED: Encounter order is defined and maintained. */
        const val ORDERED    = 0x00000010

        /** DISTINCT: No two elements are equal (`x.equals(y)` is false). */
        const val DISTINCT   = 0x00000001

        /** SORTED: Encounter order follows a defined sort order (implies ORDERED). */
        const val SORTED     = 0x00000004

        /** SIZED: estimateSize() is an exact count before traversal/split. */
        const val SIZED      = 0x00000040

        /** NONNULL: Encountered elements will not be null. */
        const val NONNULL    = 0x00000100

        /** IMMUTABLE: Source cannot be structurally modified during traversal. */
        const val IMMUTABLE  = 0x00000400

        /** CONCURRENT: Source can be safely modified concurrently. */
        const val CONCURRENT = 0x00001000

        /** SUBSIZED: All spliterators resulting from trySplit() are SIZED and SUBSIZED. */
        const val SUBSIZED = 0x00004000


        /**
         * Internal implementation class for creating a Spliterator from an Iterator.
         * This mimics the logic from java.util.Spliterators.IteratorSpliterator.
         */
        open class IteratorSpliterator<T> : Spliterator<T> {
            private val it: Iterator<T> // The underlying iterator
            private var est: Long // Estimated size, Long.MAX_VALUE if unknown, or the known size
            private val characteristics: Int // Characteristics of this spliterator

            // Batching fields for trySplit
            private var batch: Int = 0 // Size of the current batch for trySplit

            companion object {
                // Constants for trySplit batching logic (from Java's Spliterators)
                private const val BATCH_UNIT = 1024 // Minimum batch size increment
                private const val MAX_BATCH = 1 shl 25 // Max batch size (approx 33.5M)
            }

            /** Constructor for unknown size. */
            constructor(iterator: Iterator<T>, characteristics: Int) {
                this.it = iterator
                this.est = Long.MAX_VALUE
                // Remove SIZED and SUBSIZED characteristics if present, as size is unknown
                this.characteristics = characteristics and (Spliterator.SIZED or Spliterator.SUBSIZED).inv()
            }

            /** Constructor for known size. */
            constructor(iterator: Iterator<T>, size: Long, characteristics: Int) {
                this.it = iterator
                this.est = size
                // Ensure SIZED is reported if size >= 0
                this.characteristics = if (size >= 0 && (characteristics and Spliterator.SIZED) != 0) {
                    characteristics
                } else {
                    // If size is unknown (<0) or SIZED wasn't requested, remove SIZED and SUBSIZED
                    characteristics and (Spliterator.SIZED or Spliterator.SUBSIZED).inv()
                }
            }


            override fun trySplit(): Spliterator<T>? {
                /*
                 * Split into arrays using arithmetically increasing batch sizes.
                 * See Java Spliterators.IteratorSpliterator documentation for rationale.
                 */
                val i = it // Use the instance iterator
                val s = est
                if (s > 1 && i.hasNext()) {
                    var n = batch + BATCH_UNIT
                    if (n > s) n = s.toInt() // Cast to Int safely as s <= Long.MAX_VALUE here
                    if (n > MAX_BATCH) n = MAX_BATCH

                    // Allocate array - using MutableList as intermediate for dynamic size
                    val buffer = ArrayList<T>(n) // Initial capacity
                    var j = 0
                    while (j < n && i.hasNext()) {
                        buffer.add(i.next())
                        j++
                    }
                    // Update batch size for next split attempt
                    batch = j

                    // Update estimate for this spliterator
                    if (est != Long.MAX_VALUE) {
                        est -= j.toLong()
                    }

                    val array: Array<Any?> = Array(buffer.size) { buffer[it] as Any?} // Convert to Array<T>

                    // Create ArraySpliterator for the split-off portion
                    // **Requires ArraySpliterator implementation in this package**
                    // Assuming ArraySpliterator constructor: ArraySpliterator(array, start, end, characteristics)
                    // Or potentially ArraySpliterator(list, characteristics)
                    return Spliterators.ArraySpliterator(
                        array as Array<T?>, // Cast to Array<T> for type safety
                        0,
                        j,
                        characteristics()
                    ) // Need ArraySpliterator!
                    // If ArraySpliterator can take a List directly:
                    // return ArraySpliterator(buffer, characteristics()) // Need ArraySpliterator!
                }
                return null // Cannot split further
            }

            override fun forEachRemaining(action: (T) -> Unit) {
                // Kotlin's Iterator do not have forEachRemaining
                /*it.forEachRemaining(action)*/
                throw UnsupportedOperationException("forEachRemaining not implemented for IteratorSpliterator")
            }

            override fun tryAdvance(action: (T) -> Unit): Boolean {
                if (it.hasNext()) {
                    action(it.next())
                    return true
                }
                return false
            }

            override fun estimateSize(): Long {
                // Note: Unlike Java's version which might re-fetch from a collection,
                // this implementation relies solely on the initial estimate and decrements
                // during trySplit.
                return est
            }

            override fun characteristics(): Int {
                return characteristics
            }

            override fun getComparator(): Comparator<in T>? {
                if (hasCharacteristics(Spliterator.SORTED)) {
                    // If SORTED is reported, but we only have an iterator,
                    // we cannot guarantee the comparator. Java's returns null here.
                    return null
                }
                throw IllegalStateException("Spliterator does not report SORTED characteristic")
            }
        }
    }

    /**
     * Base interface for Spliterators specialized for primitive values.
     *
     * @param T The wrapper type (e.g., Int, Long, Double).
     * @param T_CONS The primitive consumer function type (e.g., `(Int) -> Unit`).
     * @param T_SPLITR The concrete primitive Spliterator type (e.g., `OfInt`).
     */
    interface OfPrimitive<T, T_CONS, T_SPLITR : OfPrimitive<T, T_CONS, T_SPLITR>>
        : Spliterator<T> {

        override fun trySplit(): T_SPLITR? // Refined return type

        /**
         * Primitive-specific version of tryAdvance.
         *
         * @param action The primitive action.
         * @return `false` if no remaining elements existed, else `true`.
         */
        fun tryAdvance(action: T_CONS): Boolean

        /**
         * Primitive-specific version of forEachRemaining.
         *
         * Default implementation repeatedly invokes the primitive `tryAdvance`.
         *
         * @param action The primitive action.
         */
        fun forEachRemaining(action: T_CONS) {
            do { } while (tryAdvance(action))
        }

        // Note: The default implementations of the generic tryAdvance/forEachRemaining
        // in Java handle boxing. In Kotlin, this might be handled via overloads
        // or potentially separate extension functions if needed, depending on use case.
        // The direct port below mimics the Java structure.

        /**
         * Generic tryAdvance implementation for primitive spliterators.
         * Default behavior adapts the generic consumer to the primitive one,
         * potentially causing boxing.
         */
        // Suppress is not needed in Kotlin as overload resolution usually works
        override fun tryAdvance(action: (T) -> Unit): Boolean {
            // This default implementation requires knowing how to convert
            // the generic (T) -> Unit to the specific T_CONS. This is tricky
            // without concrete types. Subclasses should override this.
            // The Java version relies on runtime checks and specific lambda forms.
            // For a direct port structure, we might need abstract or open fun here,
            // forcing subclasses to implement the bridging if they want to support
            // the generic consumer efficiently or correctly.
            // Let's provide a basic boxing implementation, similar to Java's intent,
            // but acknowledge its limitations in pure common code.
            // This requires a way to invoke the primitive tryAdvance with a conversion.
            // This is difficult abstractly. We'll mimic the structure, but concrete
            // implementations (OfInt, OfLong, OfDouble) will provide the real logic.
            throw UnsupportedOperationException("Generic tryAdvance needs implementation in primitive subclass")

        }

        /**
         * Generic forEachRemaining implementation for primitive spliterators.
         * Default behavior adapts the generic consumer to the primitive one,
         * potentially causing boxing.
         */
        // Suppress is not needed in Kotlin
        override fun forEachRemaining(action: (T) -> Unit) {
            // Similar limitation as generic tryAdvance. Relies on the (potentially boxing)
            // default tryAdvance implementation above or expects override.
            // Let's provide a basic loop calling the generic tryAdvance.
            do { } while (tryAdvance(action))
        }
    }

    /**
     * A Spliterator specialized for `Int` values.
     */
    interface OfInt : OfPrimitive<Int, (Int) -> Unit, OfInt> {

        override fun trySplit(): OfInt?

        override fun tryAdvance(action: (Int) -> Unit): Boolean

        override fun forEachRemaining(action: (Int) -> Unit) {
            do { } while (tryAdvance(action))
        }
    }

    /**
     * A Spliterator specialized for `Long` values.
     */
    interface OfLong : OfPrimitive<Long, (Long) -> Unit, OfLong> {

        override fun trySplit(): OfLong?

        override fun tryAdvance(action: (Long) -> Unit): Boolean

        override fun forEachRemaining(action: (Long) -> Unit) {
            do { } while (tryAdvance(action))
        }
    }

    /**
     * A Spliterator specialized for `Double` values.
     */
    interface OfDouble : OfPrimitive<Double, (Double) -> Unit, OfDouble> {

        override fun trySplit(): OfDouble?

        override fun tryAdvance(action: (Double) -> Unit): Boolean

        override fun forEachRemaining(action: (Double) -> Unit) {
            do { } while (tryAdvance(action))
        }
    }
}
