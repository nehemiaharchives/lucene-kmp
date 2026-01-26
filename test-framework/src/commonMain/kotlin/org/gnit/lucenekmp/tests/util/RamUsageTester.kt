package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.ByteOrder
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.CharsetDecoder
import org.gnit.lucenekmp.jdkport.CharsetEncoder
import org.gnit.lucenekmp.jdkport.StringBuffer
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.reflect.KClass


/** Crawls object graph to collect RAM usage for testing  */
object RamUsageTester {
    /**
     * Estimates the RAM usage by the given object. It will walk the object tree and sum up all
     * referenced objects.
     *
     *
     * **Resource Usage:** This method internally uses a set of every object seen during
     * traversals so it does allocate memory (it isn't side effect free). After the method exits, this
     * memory should be GCed.
     */
    /** Same as calling `sizeOf(obj, DEFAULT_FILTER)`.  */
    fun ramUsed(obj: Any, accumulator: Accumulator = Accumulator()): Long {
        return measureObjectSize(obj, accumulator)
    }

    /**
     * Return a human-readable size of a given object.
     *
     * @see .ramUsed
     * @see RamUsageEstimator.humanReadableUnits
     */
    fun humanSizeOf(`object`: Any): String {
        return RamUsageEstimator.humanReadableUnits(ramUsed(`object`))
    }

    /*
   * Non-recursive version of object descend. This consumes more memory than recursive in-depth
   * traversal but prevents stack overflows on long chains of objects
   * or complex graphs (a max. recursion depth on my machine was ~5000 objects linked in a chain
   * so not too much).
   */
    private fun measureObjectSize(root: Any, accumulator: Accumulator): Long {
        // Objects seen so far.
        val seen: MutableSet<Any> = mutableSetOf()
        // Class cache with reference Field and precalculated shallow size.
        val classCache: MutableMap<KClass<*>, ClassCache> = mutableMapOf()
        // Stack of objects pending traversal. Recursion caused stack overflows.
        val stack: MutableList<Any> = mutableListOf()
        stack.add(root)

        var totalSize: Long = 0
        while (!stack.isEmpty()) {
            val ob: Any = stack.removeAt(stack.size - 1)

            if (ob == null || seen.contains(ob)) {
                continue
            }
            seen.add(ob)

            val obSize: Long
            val obClazz: KClass<*> =
                checkNotNull(ob::class) { "jvm bug detected (Object.getClass() == null). please report this to your vendor" }
            if (isArrayObject(ob)) {
                obSize = handleArray(accumulator, stack, ob)
            } else {
                obSize = handleOther(accumulator, classCache, stack, ob, obClazz)
            }

            totalSize += obSize
            // Dump size of each object for comparisons across JVMs and flags.
            // System.out.println("  += " + obClazz + " | " + obSize);
        }

        // Help the GC ().
        seen.clear()
        stack.clear()
        classCache.clear()

        return totalSize
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun handleOther(
        accumulator: Accumulator,
        classCache: MutableMap<KClass<*>, ClassCache>,
        stack: MutableList<Any>,
        ob: Any,
        obClazz: KClass<*>
    ): Long {
        if (ob is Accountable) {
            return ob.ramBytesUsed()
        }
        // Ignore JDK objects we can't access or handle properly.

        val isIgnorable: (Any) -> Boolean /*java.util.function.Predicate<Any>*/ =
            /*java.util.function.Predicate*/ { clazz: Any ->
                (clazz is CharsetEncoder)
                        || (clazz is CharsetDecoder)
                        /*|| (clazz is java.util.concurrent.locks.ReentrantReadWriteLock)*/ // TODO this classis not yet ported to jdkport package
                        || (clazz is AtomicReference<*>)
            }
        if (isIgnorable(ob)) {
            return accumulator.accumulateObject(ob, 0, emptyList(), stack)
        }

        /*
     * Consider an object. Push any references it has to the processing stack
     * and accumulate this object's shallow size.
     */
        try {
            val alignedShallowInstanceSize: Long =
                RamUsageEstimator.shallowSizeOf(ob)

            // Java 9+: Best guess for some known types, as we cannot precisely look into runtime
            // classes:
            val func: ((Any) -> Long)? = SIMPLE_TYPES[obClazz]
            if (func != null) { // some simple type like String where the size is easy to get from public
                // properties
                return accumulator.accumulateObject(
                    ob,
                    alignedShallowInstanceSize + func(ob),
                    emptyList(),
                    stack
                )
            } else if (ob is Enum<*>) {
                return alignedShallowInstanceSize
            } else if (ob is ByteBuffer) {
                // Approximate ByteBuffers with their underlying storage (ignores field overhead).
                return byteArraySize(ob.capacity)
            }

            val cachedInfo = classCache.getOrPut(obClazz) { createCacheEntry(obClazz) }
            val fieldValues: MutableList<Any> = mutableListOf()
            for (getter in cachedInfo.referenceGetters) {
                val value = getter(ob)
                if (value != null) {
                    fieldValues.add(value)
                }
            }
            return accumulator.accumulateObject(
                ob, cachedInfo.alignedShallowInstanceSize, fieldValues, stack
            )
        } catch (e: Exception) {
            throw RuntimeException("Unexpected failure in RAM estimation", e)
        }
    }

    private fun handleArray(
        accumulator: Accumulator,
        stack: MutableList<Any>,
        ob: Any
    ): Long {
        /*
     * Consider an array, possibly of primitive types. Push any of its references to
     * the processing stack and accumulate this array's shallow size.
     */
        val shallowSize: Long = RamUsageEstimator.shallowSizeOf(ob)
        val values: MutableList<Any> = mutableListOf()
        when (ob) {
            is Array<*> -> {
                for (v in ob) {
                    if (v != null) {
                        values.add(v)
                    }
                }
            }
            is ByteArray, is ShortArray, is IntArray, is LongArray, is CharArray,
            is FloatArray, is DoubleArray, is BooleanArray -> {
                // primitive arrays have no object references
            }
        }
        return accumulator.accumulateArray(ob, shallowSize, values, stack)
    }

    /**
     * This map contains a function to calculate sizes of some "simple types" like String just from
     * their public properties. This is needed for Java 9, which does not allow to look into runtime
     * class fields.
     */
    private val SIMPLE_TYPES: MutableMap<KClass<*>, (Any) -> Long> =
        mutableMapOf<KClass<*>, (Any) -> Long>().also { map ->
            // String types:
            map[String::class] = { v -> charArraySize((v as String).length) }
            map[StringBuilder::class] = { v -> charArraySize((v as StringBuilder).length) }
            map[StringBuffer::class] = { v -> charArraySize((v as StringBuffer).length) }
            // Approximate the underlying long[] buffer.
            map[BitSet::class] = { v -> (v as BitSet).size().toLong() / Byte.SIZE_BITS }
            // Types with large buffers:
            map[ByteArrayOutputStream::class] = { v -> byteArraySize((v as ByteArrayOutputStream).size()) }
            // Ignorable JDK classes.
            map[ByteOrder::class] = { _: Any -> 0L }
        }

    /** Create a cached information about shallow size and reference fields for a given class.  */
    /*@org.apache.lucene.util.SuppressForbidden(reason = "We need to access private fields of measured objects.")*/
    private fun createCacheEntry(clazz: KClass<*>): ClassCache {
        // NOTE: java.lang.reflect.Field is not available in Kotlin common.
        // The reflective traversal code is left out intentionally for now.
        // TODO: reintroduce reflective field walking via platform-specific implementations.
        return ClassCache(
            RamUsageEstimator.alignObjectSize(RamUsageEstimator.NUM_BYTES_OBJECT_HEADER.toLong()),
            emptyList()
        )
    }

    private fun byteArraySize(len: Int): Long {
        return RamUsageEstimator.alignObjectSize(RamUsageEstimator.NUM_BYTES_ARRAY_HEADER.toLong() + len)
    }

    /**
     * An accumulator of object references. This class allows for customizing RAM usage estimation.
     */
    open class Accumulator {
        /**
         * Accumulate transitive references for the provided fields of the given object into `queue
        ` *  and return the shallow size of this object.
         */
        open fun accumulateObject(
            o: Any,
            shallowSize: Long,
            fieldValues: Collection<Any>,
            queue: MutableCollection<Any>
        ): Long {
            queue.addAll(fieldValues)
            return shallowSize
        }

        /**
         * Accumulate transitive references for the provided values of the given array into `queue
        ` *  and return the shallow size of this array.
         */
        open fun accumulateArray(
            array: Any,
            shallowSize: Long,
            values: MutableList<Any>,
            queue: MutableCollection<Any>
        ): Long {
            queue.addAll(values)
            return shallowSize
        }
    }

    /** Cached information about a given class.  */
    private class ClassCache(
        val alignedShallowInstanceSize: Long,
        referenceGetters: List<(Any) -> Any?>
    ) {
        val referenceGetters: List<(Any) -> Any?> = referenceGetters
    }

    private fun isArrayObject(ob: Any): Boolean {
        return ob is Array<*> ||
                ob is ByteArray || ob is ShortArray || ob is IntArray || ob is LongArray ||
                ob is CharArray || ob is FloatArray || ob is DoubleArray || ob is BooleanArray
    }

    private fun charArraySize(len: Int): Long {
        return RamUsageEstimator.alignObjectSize(
            RamUsageEstimator.NUM_BYTES_ARRAY_HEADER.toLong() + Character.BYTES.toLong() * len
        )
    }
}
