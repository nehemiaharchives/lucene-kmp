package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.jdkport.CharBuffer
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.StringBuffer
import org.gnit.lucenekmp.jdkport.fromCharArray
import org.gnit.lucenekmp.jdkport.getChars

/** Default implementation of [CharTermAttribute].  */
open class CharTermAttributeImpl
/** Initialize this attribute with empty term text  */
    : AttributeImpl(), CharTermAttribute, TermToBytesRefAttribute {
    private var termBuffer = CharArray(ArrayUtil.oversize(MIN_BUFFER_SIZE, Char.SIZE_BYTES))
    private var termLength = 0

    /**
     * May be used by subclasses to convert to different charsets / encodings for implementing [ ][.getBytesRef].
     */
    protected var builder: BytesRefBuilder = BytesRefBuilder()


    override fun newInstance(): AttributeImpl {
        return CharTermAttributeImpl()
    }

    override fun copyBuffer(buffer: CharArray, offset: Int, length: Int) {
        growTermBuffer(length)
        buffer.copyInto(termBuffer, 0, offset, length)
        termLength = length
    }

    override fun buffer(): CharArray {
        return termBuffer
    }

    override fun resizeBuffer(newSize: Int): CharArray {
        if (termBuffer.size < newSize) {
            // Not big enough; create a new array with slight
            // over allocation and preserve content
            val newCharBuffer = CharArray(ArrayUtil.oversize(newSize, Char.SIZE_BYTES))
            termBuffer.copyInto(newCharBuffer, 0, 0, termLength)
            termBuffer = newCharBuffer
        }
        return termBuffer
    }

    private fun growTermBuffer(newSize: Int) {
        if (termBuffer.size < newSize) {
            // Not big enough; create a new array with slight
            // over allocation:
            termBuffer = CharArray(ArrayUtil.oversize(newSize, Char.SIZE_BYTES))
        }
    }

    override fun setLength(length: Int): CharTermAttribute {
        Objects.checkFromIndexSize(0, length, termBuffer.size)
        termLength = length
        return this
    }

    override fun setEmpty(): CharTermAttribute? {
        termLength = 0
        return this
    }

    override val bytesRef: BytesRef
        // *** TermToBytesRefAttribute interface ***
        get() {
            builder.copyChars(termBuffer, 0, termLength)
            return builder.get()
        }

    // *** CharSequence interface ***
    override val length: Int
        get() = termLength

    fun charAt(index: Int): Char {
        Objects.checkIndex(index, termLength)
        return termBuffer[index]
    }

    override operator fun get(index: Int): Char {
        Objects.checkIndex(index, termLength)
        return termBuffer[index]
    }

    override fun subSequence(start: Int, end: Int): CharSequence {
        Objects.checkFromToIndex(start, end, termLength)
        return String.fromCharArray(termBuffer, start, end - start)
    }

    // *** Appendable interface ***
    override fun append(csq: CharSequence?): CharTermAttribute {
        if (csq == null)  // needed for Appendable compliance
            return appendNull()!!
        return append(csq, 0, csq.length)
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): CharTermAttribute {
        var csq = csq
        var start = start
        if (csq == null)  // needed for Appendable compliance
            csq = "null"
        // TODO: the optimized cases (jdk methods) will already do such checks, maybe re-organize this?
        Objects.checkFromToIndex(start, end, csq.length)
        val len = end - start
        if (len == 0) return this
        resizeBuffer(termLength + len)
        if (len > 4) { // only use instanceof check series for longer CSQs, else simply iterate
            if (csq is String) {
                csq.toCharArray(termBuffer, termLength, start, end)
            } else if (csq is StringBuilder) {
                csq.getChars(start, end, termBuffer, termLength)
            } else if (csq is CharTermAttribute) {
                csq.buffer().copyInto(termBuffer, termLength, start, end)
            } else if (csq is CharBuffer && csq.hasArray()) {
                csq.array().copyInto(
                    termBuffer, termLength, csq.arrayOffset() + csq.position() + start,
                    csq.arrayOffset() + csq.position() + end
                )
            } else if (csq is StringBuffer) {
                csq.getChars(start, end, termBuffer, termLength)
            } else {
                while (start < end) termBuffer[termLength++] = csq.get(start++)
                // no fall-through here, as termLength is updated!
                return this
            }
            termLength += len
            return this
        } else {
            while (start < end) termBuffer[termLength++] = csq.get(start++)
            return this
        }
    }

    override fun append(c: Char): CharTermAttribute {
        resizeBuffer(termLength + 1)[termLength++] = c
        return this
    }

    // *** For performance some convenience methods in addition to CSQ's ***
    override fun append(s: String): CharTermAttribute {
        /*if (s == null)  // needed for Appendable compliance
            return appendNull()*/
        val len = s.length
        s.toCharArray(resizeBuffer(termLength + len), termLength, 0, len)
        termLength += len
        return this
    }

    override fun append(s: StringBuilder): CharTermAttribute {
        /*if (s == null)  // needed for Appendable compliance
            return appendNull()*/
        val len: Int = s.length
        s.getChars(0, len, resizeBuffer(termLength + len), termLength)
        termLength += len
        return this
    }

    override fun append(ta: CharTermAttribute): CharTermAttribute {
        /*if (ta == null)  // needed for Appendable compliance
            return appendNull()*/
        val len: Int = ta.length
        ta.buffer().copyInto(resizeBuffer(termLength + len), termLength, 0, len)
        termLength += len
        return this
    }

    private fun appendNull(): CharTermAttribute? {
        resizeBuffer(termLength + 4)
        termBuffer[termLength++] = 'n'
        termBuffer[termLength++] = 'u'
        termBuffer[termLength++] = 'l'
        termBuffer[termLength++] = 'l'
        return this
    }

    // *** AttributeImpl ***
    override fun hashCode(): Int {
        var code = termLength
        code = code * 31 + ArrayUtil.hashCode(termBuffer, 0, termLength)
        return code
    }

    override fun clear() {
        termLength = 0
    }

    override fun clone(): CharTermAttributeImpl {
        val t = super.clone() as CharTermAttributeImpl
        // Do a deep clone
        t.termBuffer = CharArray(this.termLength)
        this.termBuffer.copyInto(t.termBuffer, 0, 0, this.termLength)
        t.builder = BytesRefBuilder()
        t.builder.copyBytes(builder.get())
        return t
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other is CharTermAttributeImpl) {
            val o = other
            if (termLength != o.termLength) return false
            for (i in 0..<termLength) {
                if (termBuffer[i] != o.termBuffer[i]) {
                    return false
                }
            }
            return true
        }

        return false
    }

    /** Returns solely the term text as specified by the [CharSequence] interface.  */
    override fun toString(): String {
        return String.fromCharArray(termBuffer, 0, termLength)
    }

    override fun reflectWith(reflector: AttributeReflector) {
        reflector.reflect(CharTermAttribute::class, "term", toString())
        reflector.reflect(TermToBytesRefAttribute::class, "bytes", this.bytesRef)
    }

    override fun copyTo(target: AttributeImpl) {
        val t = target as CharTermAttribute
        t.copyBuffer(termBuffer, 0, termLength)
    }

    companion object {
        private const val MIN_BUFFER_SIZE = 10
    }
}
