package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.Character
import kotlin.comparisons.naturalOrder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

class TestBytesRefArray : LuceneTestCase() {

    @Test
    fun testAppend() {
        val random: Random = random()
        val list = BytesRefArray(Counter.newCounter())
        val stringList = mutableListOf<String>()
        for (j in 0..<2) {
            if (j > 0 && random.nextBoolean()) {
                list.clear()
                stringList.clear()
            }
            val entries: Int = atLeast(500)
            val spare = BytesRefBuilder()
            val initSize = list.size()
            for (i in 0 until entries) {
                var str = TestUtil.randomUnicodeString(random)
                if (str.isEmpty()) str = "a"
                spare.copyChars(str)
                assertEquals(i + initSize, list.append(spare.get()))
                stringList.add(str)
            }
            for (i in 0 until entries) {
                assertNotNull(list.get(spare, i))
                assertEquals(stringList[i], spare.get().utf8ToString(), "entry $i doesn't match")
            }
            for (i in 0 until entries) {
                val e = random.nextInt(entries)
                assertNotNull(list.get(spare, e))
                assertEquals(stringList[e], spare.get().utf8ToString(), "entry $i doesn't match")
            }
            for (k in 0..<2) {
                val iterator = list.iterator()
                for (string in stringList) {
                    assertEquals(string, iterator.next()!!.utf8ToString())
                }
            }
        }
    }

    @Test
    fun testSort() {
        val random: Random = random()
        val list = BytesRefArray(Counter.newCounter())
        val stringList = mutableListOf<String>()
        for (j in 0..<5) {
            if (j > 0 && random.nextBoolean()) {
                list.clear()
                stringList.clear()
            }
            val entries: Int = atLeast(200)
            val spare = BytesRefBuilder()
            val initSize = list.size()
            for (i in 0 until entries) {
                var str = TestUtil.randomUnicodeString(random)
                if (str.isEmpty()) str = "a"
                spare.copyChars(str)
                assertEquals(initSize + i, list.append(spare.get()))
                stringList.add(str)
            }
            stringList.sortWith(STRING_CODEPOINT_COMPARATOR)
            val iter = list.iterator(naturalOrder())
            var i = 0
            while (true) {
                val next = iter.next() ?: break
                assertEquals(stringList[i], next.utf8ToString(), "entry $i doesn't match")
                i++
            }
            assertNull(iter.next())
            assertEquals(i, stringList.size)
        }
    }

    @Test
    fun testStableSort() {
        val random: Random = random()
        val list = BytesRefArray(Counter.newCounter())
        val stringList = mutableListOf<String>()
        for (j in 0..<5) {
            if (j > 0 && random.nextBoolean()) {
                list.clear()
                stringList.clear()
            }
            val entries: Int = atLeast(200)
            val values = Array(20) {
                var s = TestUtil.randomUnicodeString(random)
                if (s.isEmpty()) s = "a"
                s
            }
            val spare = BytesRefBuilder()
            val initSize = list.size()
            for (i in 0 until entries) {
                val str = values[random.nextInt(values.size)]
                spare.copyChars(str)
                assertEquals(initSize + i, list.append(spare.get()))
                stringList.add(str)
            }
            stringList.sortWith(STRING_CODEPOINT_COMPARATOR)
            val state = list.sort(naturalOrder(), true)
            val iter = list.iterator(state)
            var i = 0
            var lastOrd = -1
            var last: BytesRef? = null
            while (true) {
                val next = iter.next() ?: break
                assertEquals(stringList[i], next.utf8ToString(), "entry $i doesn't match")
                if (next == last) {
                    assertTrue(iter.ord() > lastOrd, "sort not stable: ${iter.ord()} <= $lastOrd")
                }
                last = BytesRef.deepCopyOf(next)
                lastOrd = iter.ord()
                i++
            }
            assertNull(iter.next())
            assertEquals(i, stringList.size)
        }
    }
}
