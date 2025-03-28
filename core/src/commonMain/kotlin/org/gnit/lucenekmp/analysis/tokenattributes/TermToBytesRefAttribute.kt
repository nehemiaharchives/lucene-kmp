package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.util.Attribute
import org.gnit.lucenekmp.util.BytesRef

/**
 * This attribute is requested by TermsHashPerField to index the contents. This attribute can be
 * used to customize the final byte[] encoding of terms.
 *
 *
 * Consumers of this attribute call [.getBytesRef] for each term. Example:
 *
 * <pre class="prettyprint">
 * final TermToBytesRefAttribute termAtt = tokenStream.getAttribute(TermToBytesRefAttribute.class);
 *
 * while (tokenStream.incrementToken() {
 * final BytesRef bytes = termAtt.getBytesRef();
 *
 * if (isInteresting(bytes)) {
 *
 * // because the bytes are reused by the attribute (like CharTermAttribute's char[] buffer),
 * // you should make a copy if you need persistent access to the bytes, otherwise they will
 * // be rewritten across calls to incrementToken()
 *
 * doSomethingWith(BytesRef.deepCopyOf(bytes));
 * }
 * }
 * ...
</pre> *
 *
 * @lucene.internal This is a very expert and internal API, please use [CharTermAttribute] and
 * its implementation for UTF-8 terms; to index binary terms, use [BytesTermAttribute] and
 * its implementation.
 */
interface TermToBytesRefAttribute : Attribute {
    /**
     * Retrieve this attribute's BytesRef. The bytes are updated from the current term. The
     * implementation may return a new instance or keep the previous one.
     *
     * @return a BytesRef to be indexed (only stays valid until token stream gets incremented)
     */
    val bytesRef: BytesRef
}
