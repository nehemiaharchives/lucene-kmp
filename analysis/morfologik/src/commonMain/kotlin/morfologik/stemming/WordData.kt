package morfologik.stemming

import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.CharBuffer
import org.gnit.lucenekmp.jdkport.CharsetDecoder
import org.gnit.lucenekmp.jdkport.Cloneable

/**
 * Stem and tag data associated with a given word.
 */
class WordData internal constructor(private val decoder: CharsetDecoder) : Cloneable<WordData> {
    companion object {
        private const val COLLECTIONS_ERROR_MESSAGE = "Not suitable for use in Java collections framework (volatile content). Refer to documentation."
    }

    private var wordCharSequence: CharSequence? = null
    private var stemCharSequence: CharBuffer = CharBuffer.allocate(0)
    private var tagCharSequence: CharBuffer = CharBuffer.allocate(0)

    var wordBuffer: ByteBuffer = ByteBuffer.allocate(0)
        internal set
    var stemBuffer: ByteBuffer = ByteBuffer.allocate(0)
        internal set
    var tagBuffer: ByteBuffer = ByteBuffer.allocate(0)
        internal set

    /**
     * A constructor for tests only.
     */
    constructor(stem: String?, tag: String?, encoding: String) : this(
        (DictionaryAttribute.ENCODING.fromString(encoding) as org.gnit.lucenekmp.jdkport.Charset).newDecoder()
    ) {
        val charset = DictionaryAttribute.ENCODING.fromString(encoding) as org.gnit.lucenekmp.jdkport.Charset
        if (stem != null) {
            val encoded = charset.newEncoder().encode(CharBuffer.wrap(stem))
            stemBuffer = BufferUtils.clearAndEnsureCapacity(stemBuffer, encoded.remaining())
            stemBuffer.put(encoded)
            stemBuffer.flip()
        }
        if (tag != null) {
            val encoded = charset.newEncoder().encode(CharBuffer.wrap(tag))
            tagBuffer = BufferUtils.clearAndEnsureCapacity(tagBuffer, encoded.remaining())
            tagBuffer.put(encoded)
            tagBuffer.flip()
        }
    }

    fun getStemBytes(target: ByteBuffer?): ByteBuffer {
        var out = BufferUtils.clearAndEnsureCapacity(target, stemBuffer.remaining())
        stemBuffer.mark()
        out.put(stemBuffer)
        stemBuffer.reset()
        out.flip()
        return out
    }

    fun getTagBytes(target: ByteBuffer?): ByteBuffer {
        var out = BufferUtils.clearAndEnsureCapacity(target, tagBuffer.remaining())
        tagBuffer.mark()
        out.put(tagBuffer)
        tagBuffer.reset()
        out.flip()
        return out
    }

    fun getWordBytes(target: ByteBuffer?): ByteBuffer {
        var out = BufferUtils.clearAndEnsureCapacity(target, wordBuffer.remaining())
        wordBuffer.mark()
        out.put(wordBuffer)
        wordBuffer.reset()
        out.flip()
        return out
    }

    fun getTag(): CharSequence? {
        tagCharSequence = BufferUtils.bytesToChars(decoder, tagBuffer, tagCharSequence)
        return if (tagCharSequence.remaining() == 0) null else tagCharSequence
    }

    fun getStem(): CharSequence? {
        stemCharSequence = BufferUtils.bytesToChars(decoder, stemBuffer, stemCharSequence)
        return if (stemCharSequence.remaining() == 0) null else stemCharSequence
    }

    fun getWord(): CharSequence? = wordCharSequence

    override fun equals(other: Any?): Boolean {
        throw UnsupportedOperationException(COLLECTIONS_ERROR_MESSAGE)
    }

    override fun hashCode(): Int {
        throw UnsupportedOperationException(COLLECTIONS_ERROR_MESSAGE)
    }

    override fun toString(): String {
        return "WordData[${getWord()},${getStem()},${getTag()}]"
    }

    public override fun clone(): WordData {
        val clone = WordData(decoder)
        clone.wordCharSequence = cloneCharSequence(wordCharSequence)
        clone.wordBuffer = getWordBytes(null)
        clone.stemBuffer = getStemBytes(null)
        clone.tagBuffer = getTagBytes(null)
        return clone
    }

    private fun cloneCharSequence(chs: CharSequence?): CharSequence? {
        if (chs == null || chs is String) return chs
        return chs.toString()
    }

    internal fun update(wordBuffer: ByteBuffer, word: CharSequence) {
        stemCharSequence.clear()
        tagCharSequence.clear()
        stemBuffer.clear()
        tagBuffer.clear()

        this.wordBuffer = wordBuffer
        this.wordCharSequence = word
    }
}
