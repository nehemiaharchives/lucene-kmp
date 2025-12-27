package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.BytesRefHash
import org.gnit.lucenekmp.util.CharsRef
import org.gnit.lucenekmp.util.CharsRefBuilder
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.UnicodeUtil
import org.gnit.lucenekmp.util.fst.ByteSequenceOutputs
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.util.fst.FSTCompiler

/**
 * Provides the ability to override any KeywordAttribute-aware stemmer with custom
 * dictionary-based stemming.
 */
class StemmerOverrideFilter(
    input: TokenStream,
    private val stemmerOverrideMap: StemmerOverrideMap
) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAtt: KeywordAttribute = addAttribute(KeywordAttribute::class)
    private val fstReader: FST.BytesReader? = stemmerOverrideMap.bytesReader
    private val scratchArc = FST.Arc<BytesRef>()
    private var spare = CharArray(0)

    /**
     * Create a new StemmerOverrideFilter, performing dictionary-based stemming with the provided
     * dictionary.
     *
     * Any dictionary-stemmed terms will be marked with KeywordAttribute so they will not be stemmed
     * with stemmers down the chain.
     */
    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            if (fstReader == null) {
                // No overrides
                return true
            }
            if (!keywordAtt.isKeyword) {
                val stem = stemmerOverrideMap.get(termAtt.buffer(), termAtt.length, scratchArc, fstReader)
                if (stem != null) {
                    spare = ArrayUtil.grow(termAtt.buffer(), stem.length)
                    val length = UnicodeUtil.UTF8toUTF16(stem, spare)
                    if (spare != termAtt.buffer()) {
                        termAtt.copyBuffer(spare, 0, length)
                    } else {
                        termAtt.setLength(length)
                    }
                    keywordAtt.isKeyword = true
                }
            }
            return true
        }
        return false
    }

    /**
     * A read-only 4-byte FST backed map that allows fast case-insensitive key value lookups for
     * StemmerOverrideFilter.
     */
    class StemmerOverrideMap(
        private val fst: FST<BytesRef>?,
        private val ignoreCase: Boolean
    ) {
        /** Returns a BytesReader to pass to get(). */
        val bytesReader: FST.BytesReader?
            get() = fst?.getBytesReader()

        /**
         * Returns the value mapped to the given key or null if the key is not in the FST dictionary.
         */
        @Throws(IOException::class)
        fun get(
            buffer: CharArray,
            bufferLen: Int,
            scratchArc: FST.Arc<BytesRef>,
            fstReader: FST.BytesReader
        ): BytesRef? {
            var pendingOutput = fst!!.outputs.noOutput
            var matchOutput: BytesRef? = null
            var bufUpto = 0
            fst.getFirstArc(scratchArc)
            while (bufUpto < bufferLen) {
                val codePoint = Character.codePointAt(buffer, bufUpto, bufferLen)
                if (
                    fst.findTargetArc(
                        if (ignoreCase) Character.toLowerCase(codePoint) else codePoint,
                        scratchArc,
                        scratchArc,
                        fstReader
                    ) == null
                ) {
                    return null
                }
                val arcOutput = scratchArc.output() ?: fst.outputs.noOutput
                pendingOutput = fst.outputs.add(pendingOutput, arcOutput)
                bufUpto += Character.charCount(codePoint)
            }
            if (scratchArc.isFinal) {
                val finalOutput = scratchArc.nextFinalOutput() ?: fst.outputs.noOutput
                matchOutput = fst.outputs.add(pendingOutput, finalOutput)
            }
            return matchOutput
        }
    }

    /** This builder builds an FST for the StemmerOverrideFilter. */
    class Builder(private val ignoreCase: Boolean = false) {
        private val hash: BytesRefHash = BytesRefHash()
        private val spare = BytesRefBuilder()
        private val outputValues = ArrayList<CharSequence>()
        private val charsSpare = CharsRefBuilder()

        /**
         * Adds an input string and its stemmer override output to this builder.
         *
         * @return false iff the input has already been added to this builder otherwise true.
         */
        fun add(input: CharSequence, output: CharSequence): Boolean {
            val length = input.length
            if (ignoreCase) {
                // convert on the fly to lowercase
                charsSpare.grow(length)
                val buffer = charsSpare.chars()
                var i = 0
                while (i < length) {
                    i += Character.toChars(Character.toLowerCase(Character.codePointAt(input, i)), buffer, i)
                }
                spare.copyChars(buffer, 0, length)
            } else {
                if (input is CharsRef) {
                    spare.copyChars(input.chars, input.offset, input.length)
                } else {
                    spare.copyChars(input, 0, length)
                }
            }
            if (hash.add(spare.get()) >= 0) {
                outputValues.add(output)
                return true
            }
            return false
        }

        /**
         * Returns a StemmerOverrideMap to be used with the StemmerOverrideFilter.
         */
        @Throws(IOException::class)
        fun build(): StemmerOverrideMap {
            val outputs = ByteSequenceOutputs.singleton
            val fstCompiler = FSTCompiler.Builder<BytesRef>(FST.INPUT_TYPE.BYTE4, outputs).build()
            val sort = hash.sort()
            val intsSpare = IntsRefBuilder()
            val size = hash.size()
            val spare = BytesRef()
            for (i in 0 until size) {
                val id = sort[i]
                val bytesRef = hash.get(id, spare)
                intsSpare.copyUTF8Bytes(bytesRef)
                fstCompiler.add(intsSpare.get(), BytesRef(outputValues[id]))
            }
            val fstMetadata = fstCompiler.compile()
            val fst = FST.fromFSTReader(fstMetadata, fstCompiler.getFSTReader())
            return StemmerOverrideMap(fst, ignoreCase)
        }
    }
}
