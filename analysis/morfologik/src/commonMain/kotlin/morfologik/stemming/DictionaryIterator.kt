package morfologik.stemming

import morfologik.fsa.ByteSequenceIterator
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.CharBuffer
import org.gnit.lucenekmp.jdkport.CharsetDecoder

/**
 * An iterator over [WordData] entries of a [Dictionary].
 */
class DictionaryIterator(
    private val dictionary: Dictionary,
    private val decoder: CharsetDecoder,
    private val decodeStems: Boolean
) : Iterator<WordData> {
    private val entriesIter: Iterator<ByteBuffer> = ByteSequenceIterator(dictionary.fsa)
    private val entry: WordData = WordData(decoder)
    private val separator: Byte = dictionary.metadata.getSeparator()
    private val sequenceEncoder: ISequenceEncoder = dictionary.metadata.getSequenceEncoderType().get()
    private val prefixBytes: Int = dictionary.metadata.getSequenceEncoderPrefixBytes()

    private var inflectedBuffer: ByteBuffer = ByteBuffer.allocate(0)
    private var inflectedCharBuffer: CharBuffer = CharBuffer.allocate(0)
    private var temp: ByteBuffer = ByteBuffer.allocate(0)

    override fun hasNext(): Boolean = entriesIter.hasNext()

    override fun next(): WordData {
        val entryBuffer = entriesIter.next()

        var ba = entryBuffer.array()
        var bbSize = entryBuffer.remaining()

        var sepPos = 0
        while (sepPos < bbSize) {
            if (ba[sepPos] == separator) break
            sepPos++
        }
        if (sepPos == bbSize) {
            throw RuntimeException("Invalid dictionary entry format (missing separator).")
        }

        inflectedBuffer = BufferUtils.clearAndEnsureCapacity(inflectedBuffer, sepPos)
        inflectedBuffer.put(ba, 0, sepPos)
        inflectedBuffer.flip()

        inflectedCharBuffer = BufferUtils.bytesToChars(decoder, inflectedBuffer, inflectedCharBuffer)
        entry.update(inflectedBuffer, inflectedCharBuffer)

        temp = BufferUtils.clearAndEnsureCapacity(temp, bbSize - sepPos)
        sepPos++
        temp.put(ba, sepPos, bbSize - sepPos)
        temp.flip()

        ba = temp.array()
        bbSize = temp.remaining()

        sepPos = prefixBytes
        while (sepPos < bbSize) {
            if (ba[sepPos] == separator) break
            sepPos++
        }

        if (decodeStems) {
            entry.stemBuffer = sequenceEncoder.decode(
                entry.stemBuffer,
                inflectedBuffer,
                ByteBuffer.wrap(ba, 0, sepPos)
            )
        } else {
            entry.stemBuffer = BufferUtils.clearAndEnsureCapacity(entry.stemBuffer, sepPos)
            entry.stemBuffer.put(ba, 0, sepPos)
            entry.stemBuffer.flip()
        }

        if (sepPos + 1 <= bbSize) {
            sepPos++
        }

        entry.tagBuffer = BufferUtils.clearAndEnsureCapacity(entry.tagBuffer, bbSize - sepPos)
        entry.tagBuffer.put(ba, sepPos, bbSize - sepPos)
        entry.tagBuffer.flip()

        return entry
    }

}
