package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestCharArraySet : LuceneTestCase() {

    companion object {
        private val TEST_STOP_WORDS = arrayOf(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "if", "in",
            "into", "is", "it", "no", "not", "of", "on", "or", "such", "that", "the",
            "their", "then", "there", "these", "they", "this", "to", "was", "will", "with"
        )
    }

    @Test
    fun testRehash() {
        val cas = CharArraySet(0, true)
        for (stopWord in TEST_STOP_WORDS) {
            cas.add(stopWord)
        }
        assertEquals(TEST_STOP_WORDS.size, cas.size)
        for (testStopWord in TEST_STOP_WORDS) {
            assertTrue(cas.contains(testStopWord))
        }
    }

    @Test
    fun testNonZeroOffset() {
        val words = arrayOf("Hello", "World", "this", "is", "a", "test")
        val findme = "xthisy".toCharArray()
        var set = CharArraySet(10, true)
        set.addAll(words.toList())
        assertTrue(set.contains(findme, 1, 4))
        assertTrue(set.contains(findme.concatToString(1, 5)))

        // test unmodifiable
        set = CharArraySet.unmodifiableSet(set)
        assertTrue(set.contains(findme, 1, 4))
        assertTrue(set.contains(findme.concatToString(1, 5)))
    }

    @Test
    fun testObjectContains() {
        var set = CharArraySet(10, true)
        val value = 1
        set.add(value)
        assertTrue(set.contains(value))
        assertTrue(set.contains("1"))
        assertTrue(set.contains(charArrayOf('1')))
        // test unmodifiable
        set = CharArraySet.unmodifiableSet(set)
        assertTrue(set.contains(value))
        assertTrue(set.contains("1"))
        assertTrue(set.contains(charArrayOf('1')))
    }

    @Test
    fun testClear() {
        val set = CharArraySet(10, true)
        set.addAll(TEST_STOP_WORDS.toList())
        assertEquals(TEST_STOP_WORDS.size, set.size, "Not all words added")
        set.clear()
        assertEquals(0, set.size, "not empty")
        for (testStopWord in TEST_STOP_WORDS) {
            assertFalse(set.contains(testStopWord))
        }
        set.addAll(TEST_STOP_WORDS.toList())
        assertEquals(TEST_STOP_WORDS.size, set.size, "Not all words added")
        for (testStopWord in TEST_STOP_WORDS) {
            assertTrue(set.contains(testStopWord))
        }
    }

    @Test
    fun testModifyOnUnmodifiable() {
        val set = CharArraySet(10, true)
        set.addAll(TEST_STOP_WORDS.toList())
        val size = set.size
        val unmodifiableSet = CharArraySet.unmodifiableSet(set)
        assertEquals(size, unmodifiableSet.size, "Set size changed due to unmodifiableSet call")
        val NOT_IN_SET = "SirGallahad"
        assertFalse(unmodifiableSet.contains(NOT_IN_SET), "Test String already exists in set")

        expectThrows<UnsupportedOperationException>(UnsupportedOperationException::class) { unmodifiableSet.add(NOT_IN_SET.toCharArray()) }
        assertFalse(unmodifiableSet.contains(NOT_IN_SET), "Test String has been added to unmodifiable set")
        assertEquals(size, unmodifiableSet.size, "Size of unmodifiable set has changed")

        expectThrows<UnsupportedOperationException>(UnsupportedOperationException::class) { unmodifiableSet.add(NOT_IN_SET) }
        assertFalse(unmodifiableSet.contains(NOT_IN_SET), "Test String has been added to unmodifiable set")
        assertEquals(size, unmodifiableSet.size, "Size of unmodifiable set has changed")

        expectThrows<UnsupportedOperationException>(UnsupportedOperationException::class) { unmodifiableSet.add(StringBuilder(NOT_IN_SET)) }
        assertFalse(unmodifiableSet.contains(NOT_IN_SET), "Test String has been added to unmodifiable set")
        assertEquals(size, unmodifiableSet.size, "Size of unmodifiable set has changed")

        expectThrows<UnsupportedOperationException>(UnsupportedOperationException::class) { unmodifiableSet.clear() }
        assertFalse(unmodifiableSet.contains(NOT_IN_SET), "Changed unmodifiable set")
        assertEquals(size, unmodifiableSet.size, "Size of unmodifiable set has changed")

        expectThrows<UnsupportedOperationException>(UnsupportedOperationException::class) { unmodifiableSet.add(NOT_IN_SET as Any) }
        assertFalse(unmodifiableSet.contains(NOT_IN_SET), "Test String has been added to unmodifiable set")
        assertEquals(size, unmodifiableSet.size, "Size of unmodifiable set has changed")

        // removeAll and retainAll are not supported in current implementation

        expectThrows<UnsupportedOperationException>(UnsupportedOperationException::class) { unmodifiableSet.addAll(listOf(NOT_IN_SET)) }
        assertFalse(unmodifiableSet.contains(NOT_IN_SET), "Test String has been added to unmodifiable set")

        for (testStopWord in TEST_STOP_WORDS) {
            assertTrue(set.contains(testStopWord))
            assertTrue(unmodifiableSet.contains(testStopWord))
        }
    }

    @Test
    fun testUnmodifiableSet() {
        var set = CharArraySet(10, true)
        set.addAll(TEST_STOP_WORDS.toList())
        set.add(1)
        val size = set.size
        set = CharArraySet.unmodifiableSet(set)
        assertEquals(size, set.size, "Set size changed due to unmodifiableSet call")
        for (stopword in TEST_STOP_WORDS) {
            assertTrue(set.contains(stopword))
        }
        assertTrue(set.contains(1))
        assertTrue(set.contains("1"))
        assertTrue(set.contains(charArrayOf('1')))

        expectThrows<NullPointerException>(NullPointerException::class) { CharArraySet.unmodifiableSet(null!!) }
    }

    @Test
    fun testSupplementaryChars() {
        val upperArr = arrayOf("Abc\uD801\uDC1C", "\uD801\uDC1C\uD801\uDC1CCDE", "A\uD801\uDC1CB")
        val lowerArr = arrayOf("abc\uD801\uDC44", "\uD801\uDC44\uD801\uDC44cde", "a\uD801\uDC44b")
        var set = CharArraySet(TEST_STOP_WORDS.toMutableList(), true)
        set.addAll(upperArr.toList())
        for (i in upperArr.indices) {
            assertTrue(set.contains(upperArr[i]), "Term ${upperArr[i]} is missing in the set")
            assertTrue(set.contains(lowerArr[i]), "Term ${lowerArr[i]} is missing in the set")
        }
        set = CharArraySet(TEST_STOP_WORDS.toMutableList(), false)
        set.addAll(upperArr.toList())
        for (i in upperArr.indices) {
            assertTrue(set.contains(upperArr[i]), "Term ${upperArr[i]} is missing in the set")
            assertFalse(set.contains(lowerArr[i]), "Term ${lowerArr[i]} is in the set but shouldn't")
        }
    }

    @Test
    fun testSingleHighSurrogate() {
        val upperArr = arrayOf("ABC\uD800", "ABC\uD800EfG", "\uD800EfG", "\uD800\uD801\uDC1CB")
        val lowerArr = arrayOf("abc\uD800", "abc\uD800efg", "\uD800efg", "\uD800\uD801\uDC44b")
        var set = CharArraySet(TEST_STOP_WORDS.toMutableList(), true)
        set.addAll(upperArr.toList())
        for (i in upperArr.indices) {
            assertTrue(set.contains(upperArr[i]), "Term ${upperArr[i]} is missing in the set")
            assertTrue(set.contains(lowerArr[i]), "Term ${lowerArr[i]} is missing in the set")
        }
        set = CharArraySet(TEST_STOP_WORDS.toMutableList(), false)
        set.addAll(upperArr.toList())
        for (i in upperArr.indices) {
            assertTrue(set.contains(upperArr[i]), "Term ${upperArr[i]} is missing in the set")
            assertFalse(set.contains(lowerArr[i]), "Term ${lowerArr[i]} is in the set but shouldn't")
        }
    }

    @Test
    fun testCopyCharArraySetBWCompat() {
        val setIgnoreCase = CharArraySet(10, true)
        val setCaseSensitive = CharArraySet(10, false)

        val stopwords = TEST_STOP_WORDS.toList()
        val stopwordsUpper = stopwords.map { it.uppercase() }.toMutableList()
        setIgnoreCase.addAll(stopwords)
        setIgnoreCase.add(1)
        setCaseSensitive.addAll(stopwords)
        setCaseSensitive.add(1)

        val copy = CharArraySet.copy(setIgnoreCase)
        val copyCaseSens = CharArraySet.copy(setCaseSensitive)

        assertEquals(setIgnoreCase.size, copy.size)
        assertEquals(setCaseSensitive.size, copy.size)

        assertTrue(copy.containsAll(stopwords))
        assertTrue(copy.containsAll(stopwordsUpper))
        assertTrue(copyCaseSens.containsAll(stopwords))
        for (string in stopwordsUpper) {
            assertFalse(copyCaseSens.contains(string))
        }
        val newWords = stopwords.map { it + "_1" }
        copy.addAll(newWords)

        assertTrue(copy.containsAll(stopwords))
        assertTrue(copy.containsAll(stopwordsUpper))
        assertTrue(copy.containsAll(newWords))
        for (string in newWords) {
            assertFalse(setIgnoreCase.contains(string))
            assertFalse(setCaseSensitive.contains(string))
        }
    }

    @Test
    fun testCopyCharArraySet() {
        val setIgnoreCase = CharArraySet(10, true)
        val setCaseSensitive = CharArraySet(10, false)

        val stopwords = TEST_STOP_WORDS.toList()
        val stopwordsUpper = stopwords.map { it.uppercase() }.toMutableList()
        setIgnoreCase.addAll(stopwords)
        setIgnoreCase.add(1)
        setCaseSensitive.addAll(stopwords)
        setCaseSensitive.add(1)

        val copy = CharArraySet.copy(setIgnoreCase)
        val copyCaseSens = CharArraySet.copy(setCaseSensitive)

        assertEquals(setIgnoreCase.size, copy.size)
        assertEquals(setCaseSensitive.size, copy.size)

        assertTrue(copy.containsAll(stopwords))
        assertTrue(copy.containsAll(stopwordsUpper))
        assertTrue(copyCaseSens.containsAll(stopwords))
        for (string in stopwordsUpper) {
            assertFalse(copyCaseSens.contains(string))
        }
        val newWords = stopwords.map { it + "_1" }
        copy.addAll(newWords)

        assertTrue(copy.containsAll(stopwords))
        assertTrue(copy.containsAll(stopwordsUpper))
        assertTrue(copy.containsAll(newWords))
        for (string in newWords) {
            assertFalse(setIgnoreCase.contains(string))
            assertFalse(setCaseSensitive.contains(string))
        }
    }

    @Test
    fun testCopyJDKSet() {
        val stopwords = TEST_STOP_WORDS.toList()
        val stopwordsUpper = stopwords.map { it.uppercase() }.toMutableList()
        val set: MutableSet<String> = HashSet(TEST_STOP_WORDS.toList())

        val copy = CharArraySet.copy(set.toMutableSet())

        assertEquals(set.size, copy.size)
        assertEquals(set.size, copy.size)

        assertTrue(copy.containsAll(stopwords))
        for (string in stopwordsUpper) {
            assertFalse(copy.contains(string))
        }

        val newWords = stopwords.map { it + "_1" }
        copy.addAll(newWords)

        assertTrue(copy.containsAll(stopwords))
        assertTrue(copy.containsAll(newWords))
        for (string in newWords) {
            assertFalse(set.contains(string))
        }
    }

    @Test
    fun testCopyEmptySet() {
        assertSame(CharArraySet.EMPTY_SET, CharArraySet.copy(CharArraySet.EMPTY_SET))
    }

    @Test
    fun testEmptySet() {
        assertEquals(0, CharArraySet.EMPTY_SET.size)
        assertTrue(CharArraySet.EMPTY_SET.isEmpty())
        for (stopword in TEST_STOP_WORDS) {
            assertFalse(CharArraySet.EMPTY_SET.contains(stopword))
        }
        assertFalse(CharArraySet.EMPTY_SET.contains("foo"))
        assertFalse(CharArraySet.EMPTY_SET.contains("foo" as Any))
        assertFalse(CharArraySet.EMPTY_SET.contains("foo".toCharArray()))
        assertFalse(CharArraySet.EMPTY_SET.contains("foo".toCharArray(), 0, 3))
    }

    @Test
    fun testContainsWithNull() {
        val set = CharArraySet(1, true)
        expectThrows<NullPointerException>(NullPointerException::class) { set.contains(null as CharArray, 0, 10) }
        expectThrows<NullPointerException>(NullPointerException::class) { set.contains(null as CharSequence) }
        expectThrows<NullPointerException>(NullPointerException::class) { set.contains(null as Any) }
    }

    @Test
    fun testToString() {
        val set = CharArraySet.copy(mutableSetOf("test"))
        assertEquals("[test]", set.toString())
        set.add("test2")
        assertTrue(set.toString().contains(", "))
    }
}

