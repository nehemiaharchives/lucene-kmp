package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.DecimalFormat
import org.gnit.lucenekmp.jdkport.Field
import org.gnit.lucenekmp.jdkport.PrivilegedAction
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

/**
 * Estimates the size (memory representation) of Java objects.
 *
 * <p>This class uses assumptions that were discovered for the Hotspot virtual machine. If you use a
 * non-OpenJDK/Oracle-based JVM, the measurements may be slightly wrong.
 *
 * @see #shallowSizeOf(Object)
 * @see #shallowSizeOfInstance(Class)
 * @lucene.internal
 */
class RamUsageEstimator {

    companion object {


        /** One kilobyte bytes.  */
        const val ONE_KB: Long = 1024

        /** One megabyte bytes.  */
        const val ONE_MB: Long = ONE_KB * ONE_KB

        /** One gigabyte bytes.  */
        const val ONE_GB: Long = ONE_KB * ONE_MB

        /** No instantiation.  */
        private fun RamUsageEstimator() {}

        /** True, iff compressed references (oops) are enabled by this JVM  */
        const val COMPRESSED_REFS_ENABLED: Boolean = false

        /** Number of bytes this JVM uses to represent an object reference.  */
        const val NUM_BYTES_OBJECT_REF: Int = 4

        /** Number of bytes to represent an object header (no fields, no alignments).  */
        const val NUM_BYTES_OBJECT_HEADER: Int = 8

        /** Number of bytes to represent an array header (no content, but with alignments).  */
        const val NUM_BYTES_ARRAY_HEADER: Int = 8

        /**
         * A constant specifying the object alignment boundary inside the JVM. Objects will always take a
         * full multiple of this constant, possibly wasting some space.
         */
        const val NUM_BYTES_OBJECT_ALIGNMENT: Int = 8

        /**
         * Approximate memory usage that we assign to all unknown queries - this maps roughly to a
         * BooleanQuery with a couple term clauses.
         */
        const val QUERY_DEFAULT_RAM_BYTES_USED: Int = 1024

        /**
         * Approximate memory usage that we assign to all unknown objects - this maps roughly to a few
         * primitive fields and a couple short String-s.
         */
        const val UNKNOWN_DEFAULT_RAM_BYTES_USED: Int = 256

        /** Sizes of primitive classes.  */
        val primitiveSizes: Map<KClass<*>, Int> = mapOf(
            Boolean::class to 1,
            Byte::class to 1,
            Char::class to Char.SIZE_BYTES,
            Short::class to Short.SIZE_BYTES,
            Int::class to Int.SIZE_BYTES,
            Float::class to Float.SIZE_BYTES,
            Double::class to Double.SIZE_BYTES,
            Long::class to Long.SIZE_BYTES,
        )

        val INTEGER_SIZE = shallowSizeOfInstance(Int::class).toInt()
        val LONG_SIZE = shallowSizeOfInstance(Long::class).toInt()
        val STRING_SIZE = shallowSizeOfInstance(String::class).toInt()
        val JVM_IS_HOTSPOT_64BIT = false

        /** Approximate memory usage that we assign to a Hashtable / HashMap entry.  */
        const val HASHTABLE_RAM_BYTES_PER_ENTRY: Long =  // key + value *
            // hash tables need to be oversized to avoid collisions, assume 2x capacity
            (2L * NUM_BYTES_OBJECT_REF) * 2

        /** Approximate memory usage that we assign to a LinkedHashMap entry.  */
        const val LINKED_HASHTABLE_RAM_BYTES_PER_ENTRY: Long =
            HASHTABLE_RAM_BYTES_PER_ENTRY + 2L * NUM_BYTES_OBJECT_REF // previous & next references

        /** Aligns an object size to be the next multiple of [.NUM_BYTES_OBJECT_ALIGNMENT].  */
        fun alignObjectSize(size: Long): Long {
            var size = size
            size += NUM_BYTES_OBJECT_ALIGNMENT.toLong() - 1L
            return size - (size % NUM_BYTES_OBJECT_ALIGNMENT)
        }

        /**
         * Return the shallow size of the provided [Integer] object. Ignores the possibility that
         * this object is part of the VM IntegerCache
         */
        fun sizeOf(ignored: Int): Long {
            return INTEGER_SIZE.toLong()
        }

        /**
         * Return the shallow size of the provided [Long] object. Ignores the possibility that this
         * object is part of the VM LongCache
         */
        fun sizeOf(ignored: Long): Long {
            return LONG_SIZE.toLong()
        }

        /** Returns the size in bytes of the byte[] object.  */
        fun sizeOf(arr: ByteArray): Long {
            return alignObjectSize(NUM_BYTES_ARRAY_HEADER.toLong() + arr.size)
        }

        /** Returns the size in bytes of the boolean[] object.  */
        fun sizeOf(arr: BooleanArray): Long {
            return alignObjectSize(NUM_BYTES_ARRAY_HEADER.toLong() + arr.size)
        }

        /** Returns the size in bytes of the char[] object.  */
        fun sizeOf(arr: CharArray): Long {
            return alignObjectSize(NUM_BYTES_ARRAY_HEADER.toLong() + Char.SIZE_BYTES.toLong() * arr.size)
        }

        /** Returns the size in bytes of the short[] object.  */
        fun sizeOf(arr: ShortArray): Long {
            return alignObjectSize(NUM_BYTES_ARRAY_HEADER.toLong() + Short.SIZE_BYTES.toLong() * arr.size)
        }

        /** Returns the size in bytes of the int[] object.  */
        fun sizeOf(arr: IntArray): Long {
            return alignObjectSize(NUM_BYTES_ARRAY_HEADER.toLong() + Int.SIZE_BYTES.toLong() * arr.size)
        }

        /** Returns the size in bytes of the float[] object.  */
        fun sizeOf(arr: FloatArray): Long {
            return alignObjectSize(NUM_BYTES_ARRAY_HEADER.toLong() + Float.SIZE_BYTES.toLong() * arr.size)
        }


        /** Returns the size in bytes of the long[] object.  */
        fun sizeOf(arr: LongArray): Long {
            return alignObjectSize(NUM_BYTES_ARRAY_HEADER.toLong() + Long.SIZE_BYTES.toLong() * arr.size)
        }

        /** Returns the size in bytes of the double[] object.  */
        fun sizeOf(arr: DoubleArray): Long {
            return alignObjectSize(NUM_BYTES_ARRAY_HEADER.toLong() + Double.SIZE_BYTES.toLong() * arr.size)
        }

        /** Returns the size in bytes of the String[] object.  */
        fun sizeOf(arr: Array<String>): Long {
            var size: Long = shallowSizeOf(arr as Array<Any>)
            for (s in arr) {
                if (s == null) {
                    continue
                }
                size += sizeOf(s)
            }
            return size
        }

        /** Recurse only into immediate descendants.  */
        const val MAX_DEPTH: Int = 1

        /**
         * Returns the size in bytes of a Map object, including sizes of its keys and values, supplying
         * [.UNKNOWN_DEFAULT_RAM_BYTES_USED] when object type is not well known. This method
         * recurses up to [.MAX_DEPTH].
         */
        fun sizeOfMap(map: Map<*, *>?): Long {
            return sizeOfMap(map, 0, UNKNOWN_DEFAULT_RAM_BYTES_USED.toLong())
        }

        /**
         * Returns the size in bytes of a Map object, including sizes of its keys and values, supplying
         * default object size when object type is not well known. This method recurses up to [ ][.MAX_DEPTH].
         */
        fun sizeOfMap(map: Map<*, *>?, defSize: Long): Long {
            return sizeOfMap(map, 0, defSize)
        }

        private fun sizeOfMap(map: Map<*, *>?, depth: Int, defSize: Long): Long {
            if (map == null) {
                return 0
            }
            var size = shallowSizeOf(map)
            if (depth > MAX_DEPTH) {
                return size
            }
            var sizeOfEntry: Long = -1
            for (entry in map.entries) {
                if (sizeOfEntry == -1L) {
                    sizeOfEntry = shallowSizeOf(entry)
                }
                size += sizeOfEntry
                size += sizeOfObject(entry.key, depth, defSize)
                size += sizeOfObject(entry.value, depth, defSize)
            }
            return alignObjectSize(size)
        }

        /**
         * Returns the size in bytes of a Collection object, including sizes of its values, supplying
         * [.UNKNOWN_DEFAULT_RAM_BYTES_USED] when object type is not well known. This method
         * recurses up to [.MAX_DEPTH].
         */
        fun sizeOfCollection(collection: Collection<*>?): Long {
            return sizeOfCollection(collection, 0, UNKNOWN_DEFAULT_RAM_BYTES_USED.toLong())
        }

        /**
         * Returns the size in bytes of a Collection object, including sizes of its values, supplying
         * default object size when object type is not well known. This method recurses up to [ ][.MAX_DEPTH].
         */
        fun sizeOfCollection(collection: Collection<*>?, defSize: Long): Long {
            return sizeOfCollection(collection, 0, defSize)
        }

        private fun sizeOfCollection(collection: Collection<*>?, depth: Int, defSize: Long): Long {
            if (collection == null) {
                return 0
            }
            var size = shallowSizeOf(collection)
            if (depth > MAX_DEPTH) {
                return size
            }
            // assume array-backed collection and add per-object references
            size += (NUM_BYTES_ARRAY_HEADER + collection.size * NUM_BYTES_OBJECT_REF).toLong()
            for (o in collection) {
                size += sizeOfObject(o, depth, defSize)
            }
            return alignObjectSize(size)
        }

        // TODO implement later
        private class RamUsageQueryVisitor internal constructor(var root: Query, var defSize: Long) : QueryVisitor() {
            var total: Long = 0

            init {
                total = if (defSize > 0) {
                    defSize
                } else {
                    shallowSizeOf(root)
                }
            }

            // TODO implement later
            /*override fun consumeTerms(query: Query, vararg terms: Term?) {
                if (query !== root) {
                    total += if (defSize > 0) {
                        defSize
                    } else {
                        shallowSizeOf(query)
                    }
                }
                if (terms != null) {
                    total += sizeOf(terms)
                }
            }

            override fun visitLeaf(query: Query) {
                if (query === root) {
                    return
                }
                total += if (query is Accountable) {
                    (query as Accountable).ramBytesUsed()
                } else {
                    if (defSize > 0) {
                        defSize
                    } else {
                        shallowSizeOf(query)
                    }
                }
            }

            override fun getSubVisitor(occur: BooleanClause.Occur?, parent: Query?): QueryVisitor {
                return this
            }*/
        }

        /**
         * Returns the size in bytes of a Query object. Unknown query types will be estimated as [ ][.QUERY_DEFAULT_RAM_BYTES_USED].
         */
        fun sizeOf(q: Query): Long {
            return sizeOf(q, QUERY_DEFAULT_RAM_BYTES_USED.toLong())
        }

        /**
         * Returns the size in bytes of a Query object. Unknown query types will be estimated using [ ][.shallowSizeOf], or using the supplied `defSize` parameter if its value is
         * greater than 0.
         */
        fun sizeOf(q: Query, defSize: Long): Long {
            if (q is Accountable) {
                return (q as Accountable).ramBytesUsed()
            } else {
                val visitor: RamUsageQueryVisitor =
                    RamUsageQueryVisitor(q, defSize)
                q.visit(visitor)
                return alignObjectSize(visitor.total)
            }
        }

        /**
         * Best effort attempt to estimate the size in bytes of an undetermined object. Known types will
         * be estimated according to their formulas, and all other object sizes will be estimated as
         * [.UNKNOWN_DEFAULT_RAM_BYTES_USED].
         */
        fun sizeOfObject(o: Any?): Long {
            return sizeOfObject(o, 0, UNKNOWN_DEFAULT_RAM_BYTES_USED.toLong())
        }

        /**
         * Best effort attempt to estimate the size in bytes of an undetermined object. Known types will
         * be estimated according to their formulas, and all other object sizes will be estimated using
         * [.shallowSizeOf], or using the supplied `defSize` parameter if its
         * value is greater than 0.
         */
        fun sizeOfObject(o: Any?, defSize: Long): Long {
            return sizeOfObject(o, 0, defSize)
        }

        private fun sizeOfObject(o: Any?, depth: Int, defSize: Long): Long {
            var depth = depth
            if (o == null) {
                return 0
            }
            var size: Long
            if (o is Accountable) {
                size = (o as Accountable).ramBytesUsed()
            } else if (o is String) {
                size = sizeOf(o as String?)
            } else if (o is BooleanArray) {
                size = sizeOf(o)
            } else if (o is ByteArray) {
                size = sizeOf(o)
            } else if (o is CharArray) {
                size = sizeOf(o)
            } else if (o is DoubleArray) {
                size = sizeOf(o)
            } else if (o is FloatArray) {
                size = sizeOf(o)
            } else if (o is IntArray) {
                size = sizeOf(o)
            } else if (o is Int) {
                size = sizeOf(o)
            } else if (o is Long) {
                size = sizeOf(o)
            } else if (o is LongArray) {
                size = sizeOf(o)
            } else if (o is ShortArray) {
                size = sizeOf(o)
            } else if (o is Array<*>) {
                size = shallowSizeOf(o as Array<Any>)
                if (depth < MAX_DEPTH) {
                    for (elem in o) {
                        size += sizeOfObject(elem, depth + 1, defSize)
                    }
                }
            } else if (o is Query) {
                size = sizeOf(o as Query, defSize)
            } else if (o is Map<*, *>) {
                size = sizeOfMap(o as Map<*, *>?, ++depth, defSize)
            } else if (o is Collection<*>) {
                size = sizeOfCollection(o as Collection<*>?, ++depth, defSize)
            } else {
                size = if (defSize > 0) {
                    defSize
                } else {
                    shallowSizeOf(o)
                }
            }
            return size
        }

        /**
         * Returns the size in bytes of the [Accountable] object, using its [ ][Accountable.ramBytesUsed] method.
         */
        fun sizeOf(accountable: Accountable): Long {
            return accountable.ramBytesUsed()
        }

        /** Returns the size in bytes of the String object.  */
        fun sizeOf(s: String?): Long {
            if (s == null) {
                return 0
            }

            // may not be true in Java 9+ and CompactStrings - but we have no way to determine this

            // char[] + hashCode
            val size: Long =
                STRING_SIZE + NUM_BYTES_ARRAY_HEADER.toLong() + Char.SIZE_BYTES.toLong() * s.length
            return alignObjectSize(size)
        }

        /** Returns the size in bytes of the byte[] object.  */
        fun shallowSizeOf(arr: ByteArray): Long {
            return sizeOf(arr)
        }

        /** Returns the size in bytes of the boolean[] object.  */
        fun shallowSizeOf(arr: BooleanArray): Long {
            return sizeOf(arr)
        }

        /** Returns the size in bytes of the char[] object.  */
        fun shallowSizeOf(arr: CharArray): Long {
            return sizeOf(arr)
        }

        /** Returns the size in bytes of the short[] object.  */
        fun shallowSizeOf(arr: ShortArray): Long {
            return sizeOf(arr)
        }

        /** Returns the size in bytes of the int[] object.  */
        fun shallowSizeOf(arr: IntArray): Long {
            return sizeOf(arr)
        }

        /** Returns the size in bytes of the float[] object.  */
        fun shallowSizeOf(arr: FloatArray): Long {
            return sizeOf(arr)
        }

        /** Returns the size in bytes of the long[] object.  */
        fun shallowSizeOf(arr: LongArray): Long {
            return sizeOf(arr)
        }

        /** Returns the size in bytes of the double[] object.  */
        fun shallowSizeOf(arr: DoubleArray): Long {
            return sizeOf(arr)
        }

        /** Returns the shallow size in bytes of the Object[] object.  */ // Use this method instead of #shallowSizeOf(Object) to avoid costly reflection
        fun shallowSizeOf(arr: Array<Any>): Long {
            return alignObjectSize(
                NUM_BYTES_ARRAY_HEADER.toLong() + NUM_BYTES_OBJECT_REF.toLong() * arr.size
            )
        }

        /**
         * Estimates a "shallow" memory usage of the given object. For arrays, this will be the memory
         * taken by array storage (no subreferences will be followed). For objects, this will be the
         * memory taken by the fields.
         *
         *
         * JVM object alignments are also applied.
         */
        fun shallowSizeOf(obj: Any?): Long {
            if (obj == null) return 0
            return when (obj) {
                is ByteArray -> shallowSizeOf(obj)
                is BooleanArray -> shallowSizeOf(obj)
                is CharArray -> shallowSizeOf(obj)
                is ShortArray -> shallowSizeOf(obj)
                is IntArray -> shallowSizeOf(obj)
                is FloatArray -> shallowSizeOf(obj)
                is LongArray -> shallowSizeOf(obj)
                is DoubleArray -> shallowSizeOf(obj)
                is Array<*> -> shallowSizeOfArray(obj)
                else -> shallowSizeOfInstance(obj::class)
            }
        }

        /**
         * Returns the shallow instance size in bytes an instance of the given class would occupy. This
         * works with all conventional classes and primitive types, but not with arrays (the size then
         * depends on the number of elements and varies from object to object).
         *
         * @see .shallowSizeOf
         * @throws IllegalArgumentException if `clazz` is an array class.
         */
        fun shallowSizeOfInstance(clazz: KClass<*>): Long {
            var clazz: KClass<*> = clazz
            require(clazz !is Array<*>) { "This method does not work with array classes." }
            if (clazz.isPrimitive()) return primitiveSizes[clazz]!!.toLong()

            var size = NUM_BYTES_OBJECT_HEADER.toLong()

            // Walk type hierarchy
            // TODO too complicated. skip.
            /*while (clazz != null) {
                val target: KClass<*> = clazz
                val fields: Array<Field>
                try {
                    fields = target.
                        RamUsageEstimator.doPrivileged<Array<java.lang.reflect.Field>>(PrivilegedAction<Array<java.lang.reflect.Field>> { target.getDeclaredFields() } as PrivilegedAction<Array<java.lang.reflect.Field?>?>)
                } catch (e: Exception) {
                    throw RuntimeException("Can't access fields of class: $target", e)
                }

                for (f in fields) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        size = RamUsageEstimator.adjustForField(size, f)
                    }
                }
                clazz = clazz.getSuperclass()
            }*/
            return alignObjectSize(size)
        }

        // Extracted to a method to give the SuppressForbidden annotation the smallest possible scope
        //@SuppressForbidden(reason = "security manager")
        private fun <T> doPrivileged(action: PrivilegedAction<T>): T {
            /*return AccessController.doPrivileged<T>(action)*/
            TODO() // This is a placeholder for the actual implementation, as Kotlin does not have a direct equivalent of AccessController.
        }


        /** Return shallow size of any `array`.  */
        private fun shallowSizeOfArray(array: Array<*>): Long {
            var size = NUM_BYTES_ARRAY_HEADER.toLong()
            val len: Int = array.size
            if (len > 0) {

                // TODO not possible with kotlin common code, need walk around
                /* val arrayElementClazz: KClass<*> = array.javaClass.getComponentType()
                size += if (arrayElementClazz.isPrimitive()) {
                    len.toLong() * primitiveSizes[arrayElementClazz]!!
                } else {
                    NUM_BYTES_OBJECT_REF.toLong() * len
                }*/

                size += NUM_BYTES_OBJECT_REF.toLong() * len
            }
            return alignObjectSize(size)
        }

        /**
         * This method returns the maximum representation size of an object. `sizeSoFar` is the
         * object's size measured so far. `f` is the field being probed.
         *
         * The returned offset will be the maximum of whatever was measured so far and `f`
         * field's offset and representation size (unaligned).
         */
        fun adjustForField(sizeSoFar: Long, f: Field): Long {
            /*val type: java.lang.Class<*> = f.getType()
            val fsize = if (type.isPrimitive()) primitiveSizes[type]!! else NUM_BYTES_OBJECT_REF
            return sizeSoFar + fsize*/
            TODO() // not possible with kotlin common code, need walk around
        }

        fun humanReadableUnits(bytes: Long): String {
            return humanReadableUnits(bytes, DecimalFormat)
        }

        /**
         * Returns [bytes] in human-readable units (GB, MB, KB or bytes).
         */
        fun humanReadableUnits(bytes: Long, df: DecimalFormat): String {
            val ONE_KB = 1024L
            val ONE_MB = ONE_KB * 1024L
            val ONE_GB = ONE_MB * 1024L

            return when {
                bytes / ONE_GB > 0 -> {
                    val value = bytes.toFloat() / ONE_GB
                    "${formatDecimal(value)} GB"
                }
                bytes / ONE_MB > 0 -> {
                    val value = bytes.toFloat() / ONE_MB
                    "${formatDecimal(value)} MB"
                }
                bytes / ONE_KB > 0 -> {
                    val value = bytes.toFloat() / ONE_KB
                    "${formatDecimal(value)} KB"
                }
                else -> "$bytes bytes"
            }
        }

        /**
         * Formats a float to display at most one decimal place, removing trailing zeros.
         */
        private fun formatDecimal(value: Float): String {
            val intValue = value.toInt()

            // If it's a whole number or very close to it, return the integer part
            if (value - intValue < 0.1f) {
                return intValue.toString()
            }

            // Round to one decimal place
            val roundedValue = (value * 10).toInt() / 10.0f
            val decimalPart = ((roundedValue * 10) % 10).toInt()

            // If after rounding it's a whole number, return the integer part
            return if (decimalPart == 0) {
                roundedValue.toInt().toString()
            } else {
                // Otherwise return with one decimal place
                "${roundedValue.toInt()}.${decimalPart}"
            }
        }

        /**
         * Return the size of the provided array of [Accountable]s by summing up the shallow size of
         * the array and the [memory usage][Accountable.ramBytesUsed] reported by each [ ].
         */
        @JvmName("sizeOfAccountableNullable")
        fun sizeOf(accountables: Array<Accountable?>): Long {
            var size = shallowSizeOf(accountables)
            for (accountable in accountables) {
                if (accountable != null) {
                    size += accountable.ramBytesUsed()
                }
            }
            return size
        }

        fun sizeOf(accountables: Array<Accountable>): Long {
            var size = shallowSizeOf(accountables)
            for (accountable in accountables) {
                size += accountable.ramBytesUsed()
            }
            return size
        }

    }// end of companion object
}

fun KClass<*>.isPrimitive(): Boolean {
    val clazz = this
    return clazz == Boolean::class ||
            clazz == Byte::class ||
            clazz == Char::class ||
            clazz == Short::class ||
            clazz == Int::class ||
            clazz == Float::class ||
            clazz == Double::class ||
            clazz == Long::class
}
