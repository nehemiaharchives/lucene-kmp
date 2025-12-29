package morfologik.stemming

import org.gnit.lucenekmp.jdkport.ByteBuffer

/**
 * The logic of encoding one sequence of bytes relative to another sequence of
 * bytes. The "base" form and the "derived" form are typically the stem of
 * a word and the inflected form of a word.
 */
interface ISequenceEncoder {
    /**
     * Encodes [target] relative to [source], optionally reusing the provided [ByteBuffer].
     */
    fun encode(reuse: ByteBuffer?, source: ByteBuffer, target: ByteBuffer): ByteBuffer

    /**
     * Decodes [encoded] relative to [source], optionally reusing the provided [ByteBuffer].
     */
    fun decode(reuse: ByteBuffer?, source: ByteBuffer, encoded: ByteBuffer): ByteBuffer
}
