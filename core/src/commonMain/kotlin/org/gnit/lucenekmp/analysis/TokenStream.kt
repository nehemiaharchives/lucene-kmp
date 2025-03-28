package org.gnit.lucenekmp.analysis

import kotlinx.io.IOException
import org.gnit.lucenekmp.analysis.tokenattributes.PackedTokenAttributeImpl
import org.gnit.lucenekmp.util.AttributeFactory
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeSource
import kotlin.reflect.KClass


/**
 * A `TokenStream` enumerates the sequence of tokens, either from [Field]s of a
 * [Document] or from query text.
 *
 *
 * This is an abstract class; concrete subclasses are:
 *
 *
 *  * [Tokenizer], a `TokenStream` whose input is a Reader; and
 *  * [TokenFilter], a `TokenStream` whose input is another `TokenStream
` * .
 *
 *
 * `TokenStream` extends [AttributeSource], which provides access to all of the
 * token [Attribute]s for the `TokenStream`. Note that only one instance per [ ] is created and reused for every token. This approach reduces object creation and
 * allows local caching of references to the [AttributeImpl]s. See [.incrementToken]
 * for further details.
 *
 *
 * **The workflow of the new `TokenStream` API is as follows:**
 *
 *
 *  1. Instantiation of `TokenStream`/[TokenFilter]s which add/get attributes
 * to/from the [AttributeSource].
 *  1. The consumer calls [TokenStream.reset].
 *  1. The consumer retrieves attributes from the stream and stores local references to all
 * attributes it wants to access.
 *  1. The consumer calls [.incrementToken] until it returns false consuming the
 * attributes after each call.
 *  1. The consumer calls [.end] so that any end-of-stream operations can be performed.
 *  1. The consumer calls [.close] to release any resource when finished using the `
 * TokenStream`.
 *
 *
 * To make sure that filters and consumers know which attributes are available, the attributes must
 * be added during instantiation. Filters and consumers are not required to check for availability
 * of attributes in [.incrementToken].
 *
 *
 * You can find some example code for the new API in the analysis package level Javadoc.
 *
 *
 * Sometimes it is desirable to capture a current state of a `TokenStream`, e.g., for
 * buffering purposes (see [CachingTokenFilter], TeeSinkTokenFilter). For this usecase [ ][AttributeSource.captureState] and [AttributeSource.restoreState] can be used.
 *
 *
 * The `TokenStream`-API in Lucene is based on the decorator pattern. Therefore all
 * non-abstract subclasses must be final or have at least a final implementation of [ ][.incrementToken]! This is checked when Java assertions are enabled.
 */
abstract class TokenStream : AttributeSource, AutoCloseable {
    /** A TokenStream using the default attribute factory.  */
    protected constructor() : super(DEFAULT_TOKEN_ATTRIBUTE_FACTORY) {
        require(assertFinal())
    }

    /** A TokenStream that uses the same attributes as the supplied one.  */
    protected constructor(input: AttributeSource) : super(input) {
        require(assertFinal())
    }

    /**
     * A TokenStream using the supplied AttributeFactory for creating new [Attribute] instances.
     */
    protected constructor(factory: AttributeFactory) : super(factory) {
        require(assertFinal())
    }

    private fun assertFinal(): Boolean {
        /*try {
            val clazz: KClass<*> = this::class
            if (!clazz.desiredAssertionStatus()) return true
            require(
                clazz.isAnonymousClass()
                        || (clazz.getModifiers() and (java.lang.reflect.Modifier.FINAL or java.lang.reflect.Modifier.PRIVATE)) != 0 || java.lang.reflect.Modifier.isFinal(
                    clazz.getMethod("incrementToken").getModifiers()
                )
            ) { "TokenStream implementation classes or at least their incrementToken() implementation must be final" }
            return true
        } catch (nsme: java.lang.NoSuchMethodException) {
            return false
        }*/
        return true
    }

    /**
     * Consumers (i.e., [IndexWriter]) use this method to advance the stream to the next token.
     * Implementing classes must implement this method and update the appropriate [ ]s with the attributes of the next token.
     *
     *
     * The producer must make no assumptions about the attributes after the method has been
     * returned: the caller may arbitrarily change it. If the producer needs to preserve the state for
     * subsequent calls, it can use [.captureState] to create a copy of the current attribute
     * state.
     *
     *
     * This method is called for every token of a document, so an efficient implementation is
     * crucial for good performance. To avoid calls to [.addAttribute] and [ ][.getAttribute], references to all [AttributeImpl]s that this stream uses should be
     * retrieved during instantiation.
     *
     *
     * To ensure that filters and consumers know which attributes are available, the attributes
     * must be added during instantiation. Filters and consumers are not required to check for
     * availability of attributes in [.incrementToken].
     *
     * @return false for end of stream; true otherwise
     */
    @Throws(IOException::class)
    abstract fun incrementToken(): Boolean

    /**
     * This method is called by the consumer after the last token has been consumed, after [ ][.incrementToken] returned `false` (using the new `TokenStream` API).
     * Streams implementing the old API should upgrade to use this feature.
     *
     *
     * This method can be used to perform any end-of-stream operations, such as setting the final
     * offset of a stream. The final offset of a stream might differ from the offset of the last token
     * eg in case one or more whitespaces followed after the last token, but a WhitespaceTokenizer was
     * used.
     *
     *
     * Additionally any skipped positions (such as those removed by a stopfilter) can be applied to
     * the position increment, or any adjustment of other attributes where the end-of-stream value may
     * be important.
     *
     *
     * If you override this method, always call `super.end()`.
     *
     * @throws IOException If an I/O error occurs
     */
    @Throws(IOException::class)
    open fun end() {
        endAttributes() // LUCENE-3849: don't consume dirty atts
    }

    /**
     * This method is called by a consumer before it begins consumption using [ ][.incrementToken].
     *
     *
     * Resets this stream to a clean state. Stateful implementations must implement this method so
     * that they can be reused, just as if they had been created fresh.
     *
     *
     * If you override this method, always call `super.reset()`, otherwise some internal
     * state will not be correctly reset (e.g., [Tokenizer] will throw [ ] on further usage).
     */
    @Throws(IOException::class)
    open fun reset() {
    }

    /**
     * Releases resources associated with this stream.
     *
     *
     * If you override this method, always call `super.close()`, otherwise some internal
     * state will not be correctly reset (e.g., [Tokenizer] will throw [ ] on reuse).
     */
    override fun close() {
    }

    companion object {
        /** Default [AttributeFactory] instance that should be used for TokenStreams.  */
        val DEFAULT_TOKEN_ATTRIBUTE_FACTORY: AttributeFactory = AttributeFactory.getStaticImplementation(
            AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY, PackedTokenAttributeImpl::class as KClass<AttributeImpl>
        )
    }
}
