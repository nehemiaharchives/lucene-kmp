package org.gnit.lucenekmp.analysis.synonym

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.CharsRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.fst.Util
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Base class for testing synonym parsers. */
abstract class BaseSynonymParserTestCase : BaseTokenStreamTestCase() {
    companion object {
        /**
         * Helper method to validate synonym parsing.
         *
         * @param synonymMap the generated synonym map after parsing
         * @param word word (phrase) we are validating the synonyms for. Should be the value that comes
         * out of the analyzer. All spaces will be replaced by word separators.
         * @param includeOrig if synonyms should include original
         * @param synonyms actual synonyms. All word separators are replaced with a single space.
         */
        @Throws(Exception::class)
        fun assertEntryEquals(
            synonymMap: SynonymMap,
            word: String,
            includeOrig: Boolean,
            synonyms: Array<String>
        ) {
            val normalizedWord = word.replace(' ', SynonymMap.WORD_SEPARATOR)
            val value = Util.get(requireNotNull(synonymMap.fst), Util.toUTF32(CharsRef(normalizedWord), IntsRefBuilder()))
            assertNotNull(value, "No synonyms found for: $normalizedWord")

            val bytesReader = ByteArrayDataInput(value.bytes, value.offset, value.length)
            val code = bytesReader.readVInt()

            val keepOrig = (code and 0x1) == 0
            assertEquals(
                includeOrig,
                keepOrig,
                "Include original different than expected. Expected $includeOrig was $keepOrig"
            )

            val count = code ushr 1
            assertEquals(
                synonyms.size,
                count,
                "Invalid synonym count. Expected ${synonyms.size} was $count"
            )

            val synonymSet = synonyms.toSet()

            val scratchBytes = BytesRef()
            for (i in 0 until count) {
                synonymMap.words.get(bytesReader.readVInt(), scratchBytes)
                val synonym = scratchBytes.utf8ToString().replace(SynonymMap.WORD_SEPARATOR, ' ')
                assertTrue(synonymSet.contains(synonym), "Unexpected synonym found: $synonym")
            }
        }

        /**
         * Validates that there are no synonyms for the given word.
         *
         * @param synonymMap the generated synonym map after parsing
         * @param word word (phrase) we are validating the synonyms for. Should be the value that comes
         * out of the analyzer. All spaces will be replaced by word separators.
         */
        @Throws(IOException::class)
        fun assertEntryAbsent(synonymMap: SynonymMap, word: String) {
            val normalizedWord = word.replace(' ', SynonymMap.WORD_SEPARATOR)
            val value = Util.get(requireNotNull(synonymMap.fst), Util.toUTF32(CharsRef(normalizedWord), IntsRefBuilder()))
            assertNull(value, "There should be no synonyms for: $normalizedWord")
        }

        @Throws(Exception::class)
        fun assertEntryEquals(
            synonymMap: SynonymMap,
            word: String,
            includeOrig: Boolean,
            synonym: String
        ) {
            assertEntryEquals(synonymMap, word, includeOrig, arrayOf(synonym))
        }

        @Throws(IOException::class)
        fun assertAnalyzesToPositions(
            a: Analyzer,
            input: String,
            output: Array<String>,
            types: Array<String>,
            posIncrements: IntArray,
            posLengths: IntArray
        ) {
            assertAnalyzesTo(a, input, output, null, null, types, posIncrements, posLengths)
        }
    }
}
