package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BreakIteratorProviderTest {

    private class DummyBreakIterator(val kind: String) : BreakIterator() {
        private var ci: CharacterIterator = StringCharacterIterator("")
        private var boundaries: IntArray = intArrayOf(0)
        private var boundaryIndex: Int = 0
        private var currentPos: Int = 0

        override fun first(): Int {
            boundaryIndex = 0
            currentPos = boundaries[0]
            return currentPos
        }

        override fun last(): Int {
            boundaryIndex = boundaries.size - 1
            currentPos = boundaries[boundaryIndex]
            return currentPos
        }

        override fun next(n: Int): Int {
            if (n == 0) {
                return currentPos
            }

            boundaryIndex += n
            if (boundaryIndex < 0) {
                boundaryIndex = 0
                currentPos = boundaries[0]
                return DONE
            }
            if (boundaryIndex >= boundaries.size) {
                boundaryIndex = boundaries.size - 1
                currentPos = boundaries[boundaryIndex]
                return DONE
            }

            currentPos = boundaries[boundaryIndex]
            return currentPos
        }

        override fun next(): Int = next(1)

        override fun previous(): Int = next(-1)

        override fun following(offset: Int): Int {
            val start = boundaries[0]
            val end = boundaries[boundaries.size - 1]
            require(!(offset < start || offset > end)) { "offset is out of bounds: $offset" }

            for (i in boundaries.indices) {
                if (boundaries[i] > offset) {
                    boundaryIndex = i
                    currentPos = boundaries[i]
                    return currentPos
                }
            }

            boundaryIndex = boundaries.size - 1
            currentPos = boundaries[boundaryIndex]
            return DONE
        }

        override fun current(): Int = currentPos

        override val text: CharacterIterator
            get() = ci

        override fun setText(newText: CharacterIterator) {
            ci = newText
            boundaries = intArrayOf(ci.beginIndex, ci.endIndex)
            boundaryIndex = 0
            currentPos = boundaries[0]
        }

        override fun cloneImpl(): BreakIterator {
            val copy = DummyBreakIterator(kind)
            val clonedCi = (ci.clone() as? CharacterIterator) ?: ci
            copy.setText(clonedCi)
            copy.boundaryIndex = boundaryIndex
            copy.currentPos = currentPos
            return copy
        }
    }

    private class DummyBreakIteratorProvider(
        private val locales: Array<Locale>
    ) : BreakIteratorProvider() {
        override fun getAvailableLocales(): Array<Locale> = locales

        override fun getWordInstance(locale: Locale): BreakIterator = DummyBreakIterator("word")

        override fun getLineInstance(locale: Locale): BreakIterator = DummyBreakIterator("line")

        override fun getCharacterInstance(locale: Locale): BreakIterator = DummyBreakIterator("character")

        override fun getSentenceInstance(locale: Locale): BreakIterator = DummyBreakIterator("sentence")
    }

    @Test
    fun testIsSupportedLocaleUsesAvailableLocales() {
        val provider = DummyBreakIteratorProvider(arrayOf(Locale.ROOT, Locale.US))

        assertTrue(provider.isSupportedLocale(Locale.ROOT))
        assertTrue(provider.isSupportedLocale(Locale.US))
        assertFalse(provider.isSupportedLocale(Locale("fr", "FR")))
    }

    @Test
    fun testGetInstancesReturnExpectedKinds() {
        val provider = DummyBreakIteratorProvider(arrayOf(Locale.ROOT))

        val word = provider.getWordInstance(Locale.ROOT) as DummyBreakIterator
        val line = provider.getLineInstance(Locale.ROOT) as DummyBreakIterator
        val character = provider.getCharacterInstance(Locale.ROOT) as DummyBreakIterator
        val sentence = provider.getSentenceInstance(Locale.ROOT) as DummyBreakIterator

        assertEquals("word", word.kind)
        assertEquals("line", line.kind)
        assertEquals("character", character.kind)
        assertEquals("sentence", sentence.kind)

        val word2 = provider.getWordInstance(Locale.ROOT)
        assertTrue(word !== word2)
    }
}
