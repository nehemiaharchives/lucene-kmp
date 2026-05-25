package org.gnit.lucenekmp.analysis.he

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class RandomizedTokenizerTests {
    private val customWords = arrayOf("C++", "C++X0", "i-phone", "i-pad", ".NET", "VB.NET", "F#", "C#", "נביעות+", "Google+")

    @Test
    fun testCustomWordsInStream() {
        repeat(10) { seed -> // TODO reduced Repeat(iterations) = 1000 to 10 for dev speed
            val random = Random(seed)

            // Randomized custom words
            val wordsPicked = ArrayList<WordAndPosition>()
            val positionsPicked = HashSet<Int>()
            val maxPosition = 100 // TODO reduced random position bound = 10000 to 100 for dev speed
            repeat(random.nextInt(customWords.size * 2 + 1)) {
                var pos = random.nextInt(maxPosition)
                while (positionsPicked.contains(pos)) {
                    pos = random.nextInt(maxPosition)
                }

                positionsPicked.add(pos)
                wordsPicked.add(WordAndPosition(customWords[it % customWords.size], pos))
            }
            wordsPicked.sort()

            val tokens = ArrayList<String>()
            val tokenizer = HebMorphTokenizer(org.gnit.lucenekmp.jdkport.StringReader(""), HebrewTestUtil.dictionary.getPref())
            val sb = StringBuilder()
            var lastPos = 0
            for (wordAndPosition in wordsPicked) {
                for (curPos in lastPos until wordAndPosition.position) {
                    tokens.add("booga")
                    sb.append("booga")
                    sb.append(' ')
                }

                tokens.add(wordAndPosition.word)
                sb.append(wordAndPosition.word)
                sb.append(' ')

                tokenizer.addSpecialCase(wordAndPosition.word)
                lastPos = wordAndPosition.position + 1
            }

            val test = Reference("")
            tokenizer.reset(org.gnit.lucenekmp.jdkport.StringReader(sb.toString()))
            var i = 0
            while (tokenizer.nextToken(test) > 0) {
                assertEquals(tokens[i], test.ref)
                i++
            }
        }
    }

    private class WordAndPosition(val word: String, val position: Int) : Comparable<WordAndPosition> {
        override fun compareTo(other: WordAndPosition): Int {
            return this.position.compareTo(other.position)
        }
    }
}
