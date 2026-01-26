package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RamUsageTester.ramUsed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestRamUsageEstimator : LuceneTestCase() {

    companion object {
        private val strings = arrayOf("test string", "hollow", "catchmaster")
    }

    @Test
    fun testStaticOverloads() {
        val rnd = random()
        run {
            val array = ByteArray(rnd.nextInt(1024))
            assertEquals(RamUsageEstimator.sizeOf(array), ramUsed(array))
            assertEquals(RamUsageEstimator.shallowSizeOf(array), ramUsed(array))
        }
        run {
            val array = BooleanArray(rnd.nextInt(1024))
            assertEquals(ramUsed(array), RamUsageEstimator.sizeOf(array))
            assertEquals(ramUsed(array), RamUsageEstimator.shallowSizeOf(array))
        }
        run {
            val array = CharArray(rnd.nextInt(1024))
            assertEquals(ramUsed(array), RamUsageEstimator.sizeOf(array))
            assertEquals(ramUsed(array), RamUsageEstimator.shallowSizeOf(array))
        }
        run {
            val array = ShortArray(rnd.nextInt(1024))
            assertEquals(ramUsed(array), RamUsageEstimator.sizeOf(array))
            assertEquals(ramUsed(array), RamUsageEstimator.shallowSizeOf(array))
        }
        run {
            val array = IntArray(rnd.nextInt(1024))
            assertEquals(ramUsed(array), RamUsageEstimator.sizeOf(array))
            assertEquals(ramUsed(array), RamUsageEstimator.shallowSizeOf(array))
        }
        run {
            val array = FloatArray(rnd.nextInt(1024))
            assertEquals(ramUsed(array), RamUsageEstimator.sizeOf(array))
            assertEquals(ramUsed(array), RamUsageEstimator.shallowSizeOf(array))
        }
        run {
            val array = LongArray(rnd.nextInt(1024))
            assertEquals(ramUsed(array), RamUsageEstimator.sizeOf(array))
            assertEquals(ramUsed(array), RamUsageEstimator.shallowSizeOf(array))
        }
        run {
            val array = DoubleArray(rnd.nextInt(1024))
            assertEquals(ramUsed(array), RamUsageEstimator.sizeOf(array))
            assertEquals(ramUsed(array), RamUsageEstimator.shallowSizeOf(array))
        }
    }

    @Test
    fun testStrings() {
        val actual = ramUsed(strings)
        val estimated = RamUsageEstimator.sizeOf(strings)
        assertEquals(actual, estimated)
    }



    @Test
    fun testReferenceSize() {
        assertTrue(
            RamUsageEstimator.NUM_BYTES_OBJECT_REF == 4 ||
                RamUsageEstimator.NUM_BYTES_OBJECT_REF == 8
        )
        if (Constants.JRE_IS_64BIT) {
            assertEquals(
                if (RamUsageEstimator.COMPRESSED_REFS_ENABLED) 4 else 8,
                RamUsageEstimator.NUM_BYTES_OBJECT_REF,
                "For 64 bit JVMs, reference size must be 8, unless compressed references are enabled"
            )
        } else {
            assertEquals(
                4,
                RamUsageEstimator.NUM_BYTES_OBJECT_REF,
                "For 32bit JVMs, reference size must always be 4"
            )
            assertFalse(RamUsageEstimator.COMPRESSED_REFS_ENABLED,
                "For 32bit JVMs, compressed references can never be enabled")
        }
    }

    // testHotspotBean is omitted as java.lang.management is not available in KMP

    @Test
    fun testPrintValues() {
        if (!VERBOSE) return
        println("JVM_IS_HOTSPOT_64BIT = ${RamUsageEstimator.JVM_IS_HOTSPOT_64BIT}")
        println("COMPRESSED_REFS_ENABLED = ${RamUsageEstimator.COMPRESSED_REFS_ENABLED}")
        println("NUM_BYTES_OBJECT_ALIGNMENT = ${RamUsageEstimator.NUM_BYTES_OBJECT_ALIGNMENT}")
        println("NUM_BYTES_OBJECT_REF = ${RamUsageEstimator.NUM_BYTES_OBJECT_REF}")
        println("NUM_BYTES_OBJECT_HEADER = ${RamUsageEstimator.NUM_BYTES_OBJECT_HEADER}")
        println("NUM_BYTES_ARRAY_HEADER = ${RamUsageEstimator.NUM_BYTES_ARRAY_HEADER}")
        println("LONG_SIZE = ${RamUsageEstimator.LONG_SIZE}")
    }

    private open class Holder() {
        var field1: Long = 5000L
        var name: String = "name"
        var holder: Holder? = null
        var field2: Long = 0
        var field3: Long = 0
        var field4: Long = 0

        constructor(name: String, field1: Long) : this() {
            this.name = name
            this.field1 = field1
        }
    }

    private class HolderSubclass : Holder() {
        var foo: Byte = 0
        var bar: Int = 0
    }

    private class HolderSubclass2 : Holder()
}
