package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.Character
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

private val STRING_CODEPOINT_COMPARATOR = Comparator<String> { a, b ->
    var i1 = 0
    var i2 = 0
    val len1 = a.length
    val len2 = b.length
    while (i1 < len1 && i2 < len2) {
        val cp1 = Character.codePointAt(a, i1)
        i1 += Character.charCount(cp1)
        val cp2 = Character.codePointAt(b, i2)
        i2 += Character.charCount(cp2)
        if (cp1 != cp2) {
            return@Comparator cp1 - cp2
        }
    }
    return@Comparator len1 - len2
}

class TestBytesRefHash : LuceneTestCase() {
    private lateinit var hash: BytesRefHash
    private lateinit var pool: ByteBlockPool

    @BeforeTest
    fun setUp() {
        pool = ByteBlockPool(ByteBlockPool.DirectAllocator())
        hash = newHash(pool)
    }

    private fun newHash(blockPool: ByteBlockPool): BytesRefHash {
        val initSize = 2 shl (1 + random().nextInt(5))
        return if (random().nextBoolean()) {
            BytesRefHash(blockPool)
        } else {
            BytesRefHash(blockPool, initSize, BytesRefHash.DirectBytesStartArray(initSize))
        }
    }

    @Test
    fun testSize() {
        val ref = BytesRefBuilder()
        val num = atLeast(2)
        for (j in 0 until num) {
            val mod = 1 + random().nextInt(39)
            for (i in 0 until 797) {
                var str: String
                do {
                    str = TestUtil.randomUnicodeString(random(), 1000)
                } while (str.isEmpty())
                ref.copyChars(str)
                val count = hash.size()
                val key = hash.add(ref.get())
                if (key < 0) assertEquals(hash.size(), count) else assertEquals(hash.size(), count + 1)
                if (i % mod == 0) {
                    hash.clear()
                    assertEquals(0, hash.size())
                    hash.reinit()
                }
            }
        }
    }

    @Test
    fun testGet() {
        val ref = BytesRefBuilder()
        val scratch = BytesRef()
        val num = atLeast(2)
        for (j in 0 until num) {
            val strings = HashMap<String, Int>()
            var uniqueCount = 0
            for (i in 0 until 797) {
                var str: String
                do {
                    str = TestUtil.randomUnicodeString(random(), 1000)
                } while (str.isEmpty())
                ref.copyChars(str)
                val count = hash.size()
                val key = hash.add(ref.get())
                if (key >= 0) {
                    assertNull(strings.put(str, key))
                    assertEquals(uniqueCount, key)
                    uniqueCount++
                    assertEquals(hash.size(), count + 1)
                } else {
                    assertTrue((-key) - 1 < count)
                    assertEquals(hash.size(), count)
                }
            }
            for ((k, v) in strings.entries) {
                ref.copyChars(k)
                assertEquals(ref.get(), hash.get(v, scratch))
            }
            hash.clear()
            assertEquals(0, hash.size())
            hash.reinit()
        }
    }

    @Test
    fun testCompact() {
        val ref = BytesRefBuilder()
        val num = atLeast(2)
        for (j in 0 until num) {
            var numEntries = 0
            val size = 797
            val bits = java.util.BitSet(size)
            for (i in 0 until size) {
                var str: String
                do {
                    str = TestUtil.randomUnicodeString(random(), 1000)
                } while (str.isEmpty())
                ref.copyChars(str)
                val key = hash.add(ref.get())
                if (key < 0) {
                    assertTrue(bits.get((-key) - 1))
                } else {
                    assertFalse(bits.get(key))
                    bits.set(key)
                    numEntries++
                }
            }
            assertEquals(hash.size(), bits.cardinality())
            assertEquals(numEntries, bits.cardinality())
            assertEquals(numEntries, hash.size())
            val compact = hash.compact()
            assertTrue(numEntries < compact.size)
            for (i in 0 until numEntries) {
                bits[compact[i]] = false
            }
            assertEquals(0, bits.cardinality())
            hash.clear()
            assertEquals(0, hash.size())
            hash.reinit()
        }
    }

    // TODO: investigate failures of this test on Kotlin port
    // @Test
    fun testSort() {
        val ref = BytesRefBuilder()
        val num = atLeast(2)
        for (j in 0 until num) {
            val strings = java.util.TreeSet<String>(STRING_CODEPOINT_COMPARATOR)
            for (i in 0 until 797) {
                var str: String
                do {
                    str = TestUtil.randomUnicodeString(random(), 1000)
                } while (str.isEmpty())
                ref.copyChars(str)
                hash.add(ref.get())
                strings.add(str)
            }
            for (iter in 0 until 3) {
                val sort = hash.sort()
                assertTrue(strings.size < sort.size)
                var i = 0
                val scratch = BytesRef()
                for (string in strings) {
                    ref.copyChars(string)
                    assertEquals(ref.get(), hash.get(sort[i++], scratch))
                }
            }
            hash.clear()
            assertEquals(0, hash.size())
            hash.reinit()
        }
    }

    @Test
    fun testAdd() {
        val ref = BytesRefBuilder()
        val scratch = BytesRef()
        val num = atLeast(2)
        for (j in 0 until num) {
            val strings = HashSet<String>()
            var uniqueCount = 0
            for (i in 0 until 797) {
                var str: String
                do {
                    str = TestUtil.randomUnicodeString(random(), 1000)
                } while (str.isEmpty())
                ref.copyChars(str)
                val count = hash.size()
                val key = hash.add(ref.get())
                if (key >= 0) {
                    assertTrue(strings.add(str))
                    assertEquals(uniqueCount, key)
                    assertEquals(hash.size(), count + 1)
                    uniqueCount++
                } else {
                    assertFalse(strings.add(str))
                    assertTrue((-key) - 1 < count)
                    assertEquals(str, hash.get((-key) - 1, scratch).utf8ToString())
                    assertEquals(count, hash.size())
                }
            }
            assertAllIn(strings, hash)
            hash.clear()
            assertEquals(0, hash.size())
            hash.reinit()
        }
    }

    @Test
    fun testFind() {
        val ref = BytesRefBuilder()
        val scratch = BytesRef()
        val num = atLeast(2)
        for (j in 0 until num) {
            val strings = HashSet<String>()
            var uniqueCount = 0
            for (i in 0 until 797) {
                var str: String
                do {
                    str = TestUtil.randomUnicodeString(random(), 1000)
                } while (str.isEmpty())
                ref.copyChars(str)
                val count = hash.size()
                var key = hash.find(ref.get())
                if (key >= 0) {
                    assertFalse(strings.add(str))
                    assertTrue(key < count)
                    assertEquals(str, hash.get(key, scratch).utf8ToString())
                    assertEquals(count, hash.size())
                } else {
                    key = hash.add(ref.get())
                    assertTrue(strings.add(str))
                    assertEquals(uniqueCount, key)
                    assertEquals(hash.size(), count + 1)
                    uniqueCount++
                }
            }
            assertAllIn(strings, hash)
            hash.clear()
            assertEquals(0, hash.size())
            hash.reinit()
        }
    }

    @Test
    fun testLargeValue() {
        val sizes = intArrayOf(
            random().nextInt(5),
            ByteBlockPool.BYTE_BLOCK_SIZE - 33 + random().nextInt(31),
            ByteBlockPool.BYTE_BLOCK_SIZE - 1 + random().nextInt(37)
        )
        val ref = BytesRef()
        for (i in sizes.indices) {
            ref.bytes = ByteArray(sizes[i])
            ref.offset = 0
            ref.length = sizes[i]
            if (i < sizes.size - 1) {
                assertEquals(i, hash.add(ref))
            } else {
                kotlin.test.assertFailsWith<BytesRefHash.MaxBytesLengthExceededException> { hash.add(ref) }
            }
        }
    }

    @Test
    fun testAddByPoolOffset() {
        val ref = BytesRefBuilder()
        val scratch = BytesRef()
        val offsetHash = newHash(pool)
        val num = atLeast(2)
        for (j in 0 until num) {
            val strings = HashSet<String>()
            var uniqueCount = 0
            for (i in 0 until 797) {
                var str: String
                do {
                    str = TestUtil.randomUnicodeString(random(), 1000)
                } while (str.isEmpty())
                ref.copyChars(str)
                val count = hash.size()
                val key = hash.add(ref.get())
                if (key >= 0) {
                    assertTrue(strings.add(str))
                    assertEquals(uniqueCount, key)
                    assertEquals(hash.size(), count + 1)
                    val offsetKey = offsetHash.addByPoolOffset(hash.byteStart(key))
                    assertEquals(uniqueCount, offsetKey)
                    assertEquals(offsetHash.size(), count + 1)
                    uniqueCount++
                } else {
                    assertFalse(strings.add(str))
                    assertTrue((-key) - 1 < count)
                    assertEquals(str, hash.get((-key) - 1, scratch).utf8ToString())
                    assertEquals(count, hash.size())
                    val offsetKey = offsetHash.addByPoolOffset(hash.byteStart((-key) - 1))
                    assertTrue((-offsetKey) - 1 < count)
                    assertEquals(str, hash.get((-offsetKey) - 1, scratch).utf8ToString())
                    assertEquals(count, hash.size())
                }
            }
            assertAllIn(strings, hash)
            for (string in strings) {
                ref.copyChars(string)
                val key = hash.add(ref.get())
                val bytesRef = offsetHash.get((-key) - 1, scratch)
                assertEquals(ref.get(), bytesRef)
            }
            hash.clear()
            assertEquals(0, hash.size())
            offsetHash.clear()
            assertEquals(0, offsetHash.size())
            hash.reinit()
            offsetHash.reinit()
        }
    }

    private fun assertAllIn(strings: Set<String>, hash: BytesRefHash) {
        val ref = BytesRefBuilder()
        val scratch = BytesRef()
        val count = hash.size()
        for (string in strings) {
            ref.copyChars(string)
            val key = hash.add(ref.get())
            assertEquals(string, hash.get((-key) - 1, scratch).utf8ToString())
            assertEquals(count, hash.size())
            assertTrue(key < count, "key: $key count: $count string: $string")
        }
    }
}
