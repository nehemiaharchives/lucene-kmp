package morfologik.stemming

import morfologik.fsa.ByteSequenceIterator
import morfologik.fsa.FSA
import morfologik.fsa.FSATraversal
import morfologik.fsa.MatchResult
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.CharBuffer
import org.gnit.lucenekmp.jdkport.CharsetDecoder
import org.gnit.lucenekmp.jdkport.CharsetEncoder

/**
 * Dictionary lookup of an inflected word over a dictionary.
 */
class DictionaryLookup(private val dictionary: Dictionary) : IStemmer, Iterable<WordData> {
    private val matcher: FSATraversal = FSATraversal(dictionary.fsa)
    private val finalStatesIterator: ByteSequenceIterator = ByteSequenceIterator(dictionary.fsa, dictionary.fsa.getRootNode())
    private val rootNode: Int = dictionary.fsa.getRootNode()

    private val dictionaryMetadata: DictionaryMetadata = dictionary.metadata
    private val encoder: CharsetEncoder = dictionary.metadata.getEncoder()
    private val decoder: CharsetDecoder = dictionary.metadata.getDecoder()
    private val fsa: FSA = dictionary.fsa
    private val separatorChar: Char = dictionary.metadata.getSeparatorAsChar()
    private val sequenceEncoder: ISequenceEncoder = dictionary.metadata.getSequenceEncoderType().get()
    private val prefixBytes: Int = dictionary.metadata.getSequenceEncoderPrefixBytes()

    private var forms: Array<WordData> = emptyArray()
    private val formsList: ArrayViewList<WordData> = ArrayViewList(forms, 0, forms.size)

    private var byteBuffer: ByteBuffer = ByteBuffer.allocate(0)
    private var charBuffer: CharBuffer = CharBuffer.allocate(0)
    private val matchResult: MatchResult = MatchResult()

    override fun lookup(word: CharSequence): List<WordData> {
        val separator = dictionaryMetadata.getSeparator()

        var input: CharSequence = word
        if (dictionaryMetadata.getInputConversionPairs().isNotEmpty()) {
            input = applyReplacements(input, dictionaryMetadata.getInputConversionPairs())
        }

        formsList.wrap(forms, 0, 0)

        charBuffer = BufferUtils.clearAndEnsureCapacity(charBuffer, input.length)
        for (i in 0 until input.length) {
            val chr = input[i]
            if (chr == separatorChar) {
                return formsList
            }
            charBuffer.put(chr)
        }
        charBuffer.flip()

        try {
            byteBuffer = BufferUtils.charsToBytes(encoder, charBuffer, byteBuffer)
        } catch (e: UnmappableInputException) {
            return formsList
        }

        val match = matcher.match(matchResult, byteBuffer.array(), 0, byteBuffer.remaining(), rootNode)

        if (match.kind == MatchResult.SEQUENCE_IS_A_PREFIX) {
            val arc = fsa.getArc(match.node, separator)
            if (arc != 0 && !fsa.isArcFinal(arc)) {
                var formsCount = 0

                finalStatesIterator.restartFrom(fsa.getEndNode(arc))
                while (finalStatesIterator.hasNext()) {
                    val bb = finalStatesIterator.next()
                    val ba = bb.array()
                    val bbSize = bb.remaining()

                    if (formsCount >= forms.size) {
                        val newSize = forms.size + 10
                        val expanded = Array(newSize) { idx ->
                            if (idx < forms.size) forms[idx] else WordData(decoder)
                        }
                        forms = expanded
                    }

                    val wordData = forms[formsCount++]
                    if (dictionaryMetadata.getOutputConversionPairs().isEmpty()) {
                        wordData.update(byteBuffer, input)
                    } else {
                        wordData.update(byteBuffer, applyReplacements(input, dictionaryMetadata.getOutputConversionPairs()))
                    }

                    var sepPos = prefixBytes
                    while (sepPos < bbSize) {
                        if (ba[sepPos] == separator) {
                            break
                        }
                        sepPos++
                    }

                    wordData.stemBuffer = sequenceEncoder.decode(
                        wordData.stemBuffer,
                        byteBuffer,
                        ByteBuffer.wrap(ba, 0, sepPos)
                    )

                    sepPos++

                    val tagSize = bbSize - sepPos
                    if (tagSize > 0) {
                        wordData.tagBuffer = BufferUtils.clearAndEnsureCapacity(wordData.tagBuffer, tagSize)
                        wordData.tagBuffer.put(ba, sepPos, tagSize)
                        wordData.tagBuffer.flip()
                    }
                }

                formsList.wrap(forms, 0, formsCount)
            }
        }

        return formsList
    }

    override fun iterator(): Iterator<WordData> {
        return DictionaryIterator(dictionary, decoder, true)
    }

    fun getDictionary(): Dictionary = dictionary

    fun getSeparatorChar(): Char = separatorChar

    companion object {
        fun applyReplacements(word: CharSequence, replacements: LinkedHashMap<String, String>): String {
            var result = word.toString()
            for ((key, value) in replacements) {
                result = result.replace(key, value)
            }
            return result
        }
    }
}
