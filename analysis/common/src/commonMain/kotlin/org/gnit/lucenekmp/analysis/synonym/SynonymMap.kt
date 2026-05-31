package org.gnit.lucenekmp.analysis.synonym

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.internal.hppc.IntHashSet
import org.gnit.lucenekmp.store.ByteArrayDataOutput
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.BytesRefHash
import org.gnit.lucenekmp.util.CharsRef
import org.gnit.lucenekmp.util.CharsRefBuilder
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.fst.ByteSequenceOutputs
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.util.fst.FSTCompiler
import org.gnit.lucenekmp.util.fst.Util

/**
 * A map of synonyms, keys and values are phrases.
 *
 * @lucene.experimental
 */
class SynonymMap(
    val fst: FST<BytesRef>?,
    val words: BytesRefHash,
    val maxHorizontalContext: Int
) {
    companion object {
        /** for multiword support, you must separate words with this separator */
        const val WORD_SEPARATOR: Char = '\u0000'
    }

    /** Builds an FSTSynonymMap. */
    open class Builder(private val dedup: Boolean = true) {
        private val workingSet = linkedMapOf<CharsRef, MapEntry>()
        private val words = BytesRefHash()
        private val utf8Scratch = BytesRefBuilder()
        private var maxHorizontalContext = 0

        private class MapEntry {
            var includeOrig = false
            val ords = IntArrayList()
        }

        companion object {
            /** Sugar: just joins the provided terms with [SynonymMap.WORD_SEPARATOR]. */
            fun join(words: Array<String>, reuse: CharsRefBuilder): CharsRef {
                var upto = 0
                var buffer = reuse.chars()
                for (word in words) {
                    val wordLen = word.length
                    val needed = if (upto == 0) wordLen else 1 + upto + wordLen
                    if (needed > buffer.size) {
                        reuse.grow(needed)
                        buffer = reuse.chars()
                    }
                    if (upto > 0) {
                        buffer[upto++] = WORD_SEPARATOR
                    }
                    word.toCharArray().copyInto(buffer, upto, 0, wordLen)
                    upto += wordLen
                }
                reuse.setLength(upto)
                return reuse.get()
            }
        }

        private fun hasHoles(chars: CharsRef): Boolean {
            val end = chars.offset + chars.length
            for (idx in chars.offset + 1 until end) {
                if (chars.chars[idx] == WORD_SEPARATOR && chars.chars[idx - 1] == WORD_SEPARATOR) {
                    return true
                }
            }
            if (chars.chars[chars.offset] == '\u0000') return true
            if (chars.chars[chars.offset + chars.length - 1] == '\u0000') return true
            return false
        }

        private fun add(
            input: CharsRef,
            numInputWords: Int,
            output: CharsRef,
            numOutputWords: Int,
            includeOrig: Boolean
        ) {
            require(numInputWords > 0) { "numInputWords must be > 0 (got $numInputWords)" }
            require(input.length > 0) { "input.length must be > 0 (got ${input.length})" }
            require(numOutputWords > 0) { "numOutputWords must be > 0 (got $numOutputWords)" }
            require(output.length > 0) { "output.length must be > 0 (got ${output.length})" }

            check(!hasHoles(input)) { "input has holes: $input" }
            check(!hasHoles(output)) { "output has holes: $output" }

            utf8Scratch.copyChars(output.chars, output.offset, output.length)
            var ord = words.add(utf8Scratch.get())
            if (ord < 0) ord = (-ord) - 1

            var e = workingSet[input]
            if (e == null) {
                e = MapEntry()
                workingSet[CharsRef.deepCopyOf(input)] = e
            }

            e.ords.add(ord)
            e.includeOrig = e.includeOrig || includeOrig
            maxHorizontalContext = maxOf(maxHorizontalContext, numInputWords)
            maxHorizontalContext = maxOf(maxHorizontalContext, numOutputWords)
        }

        private fun countWords(chars: CharsRef): Int {
            var wordCount = 1
            var upto = chars.offset
            val limit = chars.offset + chars.length
            while (upto < limit) {
                if (chars.chars[upto++] == WORD_SEPARATOR) {
                    wordCount++
                }
            }
            return wordCount
        }

        fun add(input: CharsRef, output: CharsRef, includeOrig: Boolean) {
            add(input, countWords(input), output, countWords(output), includeOrig)
        }

        @Throws(IOException::class)
        fun build(): SynonymMap {
            val outputs = ByteSequenceOutputs.singleton
            val fstCompiler = FSTCompiler.Builder(FST.INPUT_TYPE.BYTE4, outputs).build()
            val scratch = BytesRefBuilder()
            val scratchOutput = ByteArrayDataOutput()
            val dedupSet = if (dedup) IntHashSet() else null
            val spare = ByteArray(5)
            val sortedKeys = workingSet.keys.toTypedArray()
            sortedKeys.sortWith(CharsRef.UTF16SortedAsUTF8Comparator)
            val scratchIntsRef = IntsRefBuilder()

            for (input in sortedKeys) {
                val entry = workingSet[input]!!
                val numEntries = entry.ords.size()
                scratch.grow(5 + numEntries * 5)
                scratchOutput.reset(scratch.bytes())

                var count = 0
                for (i in 0 until numEntries) {
                    if (dedupSet != null) {
                        val ent = entry.ords.get(i)
                        if (dedupSet.contains(ent)) continue
                        dedupSet.add(ent)
                    }
                    scratchOutput.writeVInt(entry.ords.get(i))
                    count++
                }

                val pos = scratchOutput.position
                scratchOutput.writeVInt((count shl 1) or if (entry.includeOrig) 0 else 1)
                val pos2 = scratchOutput.position
                val vIntLen = pos2 - pos
                scratch.bytes().copyInto(spare, 0, pos, pos + vIntLen)
                scratch.bytes().copyInto(scratch.bytes(), vIntLen, 0, pos)
                spare.copyInto(scratch.bytes(), 0, 0, vIntLen)
                dedupSet?.clear()

                scratch.setLength(scratchOutput.position)
                fstCompiler.add(Util.toUTF32(input, scratchIntsRef), scratch.toBytesRef())
            }

            val fstReader = fstCompiler.getFSTReader()
            val fst = FST.fromFSTReader(fstCompiler.compile(), fstReader)
            return SynonymMap(fst, words, maxHorizontalContext)
        }
    }

    /** Abstraction for parsing synonym files. */
    abstract class Parser(dedup: Boolean, private val analyzer: Analyzer) : Builder(dedup) {
        @Throws(IOException::class)
        abstract fun parse(input: Reader)

        /** Sugar: analyzes the text with the analyzer and separates by [SynonymMap.WORD_SEPARATOR]. */
        @Throws(IOException::class)
        fun analyze(text: String, reuse: CharsRefBuilder): CharsRef {
            analyzer.tokenStream("", text).use { ts ->
                val termAtt = ts.addAttribute(CharTermAttribute::class)
                val posIncAtt = ts.addAttribute(PositionIncrementAttribute::class)
                ts.reset()
                reuse.clear()
                while (ts.incrementToken()) {
                    val length = termAtt.length
                    require(length > 0) { "term: $text analyzed to a zero-length token" }
                    require(posIncAtt.getPositionIncrement() == 1) {
                        "term: $text analyzed to a token ($termAtt) with position increment != 1 (got: ${posIncAtt.getPositionIncrement()})"
                    }
                    reuse.grow(reuse.length() + length + 1)
                    var end = reuse.length()
                    if (reuse.length() > 0) {
                        reuse.setCharAt(end++, WORD_SEPARATOR)
                        reuse.setLength(reuse.length() + 1)
                    }
                    termAtt.buffer().copyInto(reuse.chars(), end, 0, length)
                    reuse.setLength(reuse.length() + length)
                }
                ts.end()
            }
            require(reuse.length() != 0) { "term: $text was completely eliminated by analyzer" }
            return reuse.get()
        }
    }
}

