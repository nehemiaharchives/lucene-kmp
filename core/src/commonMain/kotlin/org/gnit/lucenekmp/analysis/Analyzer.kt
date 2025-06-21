package org.gnit.lucenekmp.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermToBytesRefAttribute
import org.gnit.lucenekmp.jdkport.Consumer
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.util.*
import kotlin.jvm.Transient


/**
 * An Analyzer builds TokenStreams, which analyze text. It thus represents a policy for extracting
 * index terms from text.
 *
 *
 * In order to define what analysis is done, subclasses must define their [ ] in [.createComponents]. The components
 * are then reused in each call to [.tokenStream].
 *
 *
 * Simple example:
 *
 * <pre class="prettyprint">
 * Analyzer analyzer = new Analyzer() {
 * @Override
 * protected TokenStreamComponents createComponents(String fieldName) {
 * Tokenizer source = new FooTokenizer(reader);
 * TokenStream filter = new FooFilter(source);
 * filter = new BarFilter(filter);
 * return new TokenStreamComponents(source, filter);
 * }
 * @Override
 * protected TokenStream normalize(String fieldName, TokenStream in) {
 * // Assuming FooFilter is about normalization and BarFilter is about
 * // stemming, only FooFilter should be applied
 * return new FooFilter(in);
 * }
 * };
</pre> *
 *
 * For more examples, see the [Analysis package documentation][org.apache.lucene.analysis].
 *
 *
 * For some concrete implementations bundled with Lucene, look in the analysis modules:
 *
 *
 *  * [Common]({@docRoot}/../analysis/common/overview-summary.html): Analyzers for
 * indexing content in different languages and domains.
 *  * [ICU]({@docRoot}/../analysis/icu/overview-summary.html): Exposes functionality
 * from ICU to Apache Lucene.
 *  * [Kuromoji]({@docRoot}/../analysis/kuromoji/overview-summary.html): Morphological
 * analyzer for Japanese text.
 *  * [Morfologik]({@docRoot}/../analysis/morfologik/overview-summary.html):
 * Dictionary-driven lemmatization for the Polish language.
 *  * [Phonetic]({@docRoot}/../analysis/phonetic/overview-summary.html): Analysis for
 * indexing phonetic signatures (for sounds-alike search).
 *  * [Smart Chinese]({@docRoot}/../analysis/smartcn/overview-summary.html): Analyzer
 * for Simplified Chinese, which indexes words.
 *  * [Stempel]({@docRoot}/../analysis/stempel/overview-summary.html): Algorithmic
 * Stemmer for the Polish Language.
 *
 *
 * @since 3.1
 */
abstract class Analyzer
/**
 * Create a new Analyzer, reusing the same set of components per-thread across calls to [ ][.tokenStream].
 */ protected constructor(
    /** Returns the used [ReuseStrategy].  */
    val reuseStrategy: ReuseStrategy = GLOBAL_REUSE_STRATEGY
) :
    AutoCloseable {
    // non final as it gets nulled if closed; pkg private for access by ReuseStrategy's final helper
    // methods:
    var storedValue: CloseableThreadLocal<Any>? = CloseableThreadLocal()

    /**
     * Creates a new [TokenStreamComponents] instance for this analyzer.
     *
     * @param fieldName the name of the fields content passed to the [TokenStreamComponents]
     * sink as a reader
     * @return the [TokenStreamComponents] for this analyzer.
     */
    protected abstract fun createComponents(fieldName: String): TokenStreamComponents

    /**
     * Wrap the given [TokenStream] in order to apply normalization filters. The default
     * implementation returns the [TokenStream] as-is. This is used by [.normalize].
     */
    protected open fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return `in`
    }

    /**
     * Returns a TokenStream suitable for `fieldName`, tokenizing the contents of `
     * reader`.
     *
     *
     * This method uses [.createComponents] to obtain an instance of [ ]. It returns the sink of the components and stores the components
     * internally. Subsequent calls to this method will reuse the previously stored components after
     * resetting them through [TokenStreamComponents.setReader].
     *
     *
     * **NOTE:** After calling this method, the consumer must follow the workflow described in
     * [TokenStream] to properly consume its contents. See the [ Analysis package documentation][org.apache.lucene.analysis] for some examples demonstrating this.
     *
     *
     * **NOTE:** If your data is available as a `String`, use [.tokenStream] which reuses a `StringReader`-like instance internally.
     *
     * @param fieldName the name of the field the created TokenStream is used for
     * @param reader the reader the streams source reads from
     * @return TokenStream for iterating the analyzed content of `reader`
     * @throws AlreadyClosedException if the Analyzer is closed.
     * @see .tokenStream
     */
    fun tokenStream(fieldName: String, reader: Reader): TokenStream {
        var components = reuseStrategy.getReusableComponents(this, fieldName)
        val r: Reader = initReader(fieldName, reader)
        if (components == null) {
            components = createComponents(fieldName)
            reuseStrategy.setReusableComponents(this, fieldName, components)
        }
        components.setReader(r)
        return components.tokenStream
    }

    /**
     * Returns a TokenStream suitable for `fieldName`, tokenizing the contents of `
     * text`.
     *
     *
     * This method uses [.createComponents] to obtain an instance of [ ]. It returns the sink of the components and stores the components
     * internally. Subsequent calls to this method will reuse the previously stored components after
     * resetting them through [TokenStreamComponents.setReader].
     *
     *
     * **NOTE:** After calling this method, the consumer must follow the workflow described in
     * [TokenStream] to properly consume its contents. See the [ Analysis package documentation][org.apache.lucene.analysis] for some examples demonstrating this.
     *
     * @param fieldName the name of the field the created TokenStream is used for
     * @param text the String the streams source reads from
     * @return TokenStream for iterating the analyzed content of `reader`
     * @throws AlreadyClosedException if the Analyzer is closed.
     * @see .tokenStream
     */
    fun tokenStream(fieldName: String, text: String): TokenStream {
        var components = reuseStrategy.getReusableComponents(this, fieldName)
        val strReader: ReusableStringReader =
            if (components?.reusableStringReader == null)
                ReusableStringReader()
            else
                components.reusableStringReader!!
        strReader.setValue(text)
        val r: Reader = initReader(fieldName, strReader)
        if (components == null) {
            components = createComponents(fieldName)
            reuseStrategy.setReusableComponents(this, fieldName, components)
        }

        components.setReader(r)
        components.reusableStringReader = strReader
        return components.tokenStream
    }

    /**
     * Normalize a string down to the representation that it would have in the index.
     *
     *
     * This is typically used by query parsers in order to generate a query on a given term,
     * without tokenizing or stemming, which are undesirable if the string to analyze is a partial
     * word (eg. in case of a wildcard or fuzzy query).
     *
     *
     * This method uses [.initReaderForNormalization] in order to apply
     * necessary character-level normalization and then [.normalize] in
     * order to apply the normalizing token filters.
     */
    fun normalize(fieldName: String, text: String): BytesRef {
        try {
            // apply char filters
            var filteredText: String
            try {
                StringReader(text).use { reader ->
                    val filterReader: Reader = initReaderForNormalization(fieldName, reader)
                    val buffer = CharArray(64)
                    val builder = StringBuilder()
                    while (true) {
                        val read: Int = filterReader.read(buffer, 0, buffer.size)
                        if (read == -1) {
                            break
                        }
                        builder.appendRange(buffer, 0, 0 + read)
                    }
                    filteredText = builder.toString()
                }
            } catch (e: IOException) {
                throw IllegalStateException("Normalization threw an unexpected exception", e)
            }

            val attributeFactory: AttributeFactory = attributeFactory(fieldName)
            normalize(
                fieldName, StringTokenStream(attributeFactory, filteredText, text.length)
            ).use { ts ->
                val termAtt: TermToBytesRefAttribute = ts.addAttribute(TermToBytesRefAttribute::class)
                ts.reset()
                check(ts.incrementToken()) {
                    ("The normalization token stream is "
                            + "expected to produce exactly 1 token, but got 0 for analyzer "
                            + this
                            + " and input \""
                            + text
                            + "\"")
                }
                val term = BytesRef.deepCopyOf(termAtt.bytesRef)
                check(!ts.incrementToken()) {
                    ("The normalization token stream is "
                            + "expected to produce exactly 1 token, but got 2+ for analyzer "
                            + this
                            + " and input \""
                            + text
                            + "\"")
                }
                ts.end()
                return term
            }
        } catch (e: IOException) {
            throw IllegalStateException("Normalization threw an unexpected exception", e)
        }
    }

    /**
     * Override this if you want to add a CharFilter chain.
     *
     *
     * The default implementation returns `reader` unchanged.
     *
     * @param fieldName IndexableField name being indexed
     * @param reader original Reader
     * @return reader, optionally decorated with CharFilter(s)
     */
    protected fun initReader(fieldName: String, reader: Reader): Reader {
        return reader
    }

    /**
     * Wrap the given [Reader] with [CharFilter]s that make sense for normalization. This
     * is typically a subset of the [CharFilter]s that are applied in [.initReader]. This is used by [.normalize].
     */
    protected fun initReaderForNormalization(fieldName: String?, reader: Reader): Reader {
        return reader
    }

    /**
     * Return the [AttributeFactory] to be used for [analysis][.tokenStream] and [ ][.normalize] on the given `FieldName`. The default
     * implementation returns [TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY].
     */
    protected fun attributeFactory(fieldName: String?): AttributeFactory {
        return TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY
    }

    /**
     * Invoked before indexing a IndexableField instance if terms have already been added to that
     * field. This allows custom analyzers to place an automatic position increment gap between
     * IndexbleField instances using the same field name. The default value position increment gap is
     * 0. With a 0 position increment gap and the typical default token position increment of 1, all
     * terms in a field, including across IndexableField instances, are in successive positions,
     * allowing exact PhraseQuery matches, for instance, across IndexableField instance boundaries.
     *
     * @param fieldName IndexableField name being indexed.
     * @return position increment gap, added to the next token emitted from [     ][.tokenStream]. This value must be `>= 0`.
     */
    fun getPositionIncrementGap(fieldName: String?): Int {
        return 0
    }

    /**
     * Just like [.getPositionIncrementGap], except for Token offsets instead. By default this
     * returns 1. This method is only called if the field produced at least one token for indexing.
     *
     * @param fieldName the field just indexed
     * @return offset gap, added to the next token emitted from [.tokenStream].
     * This value must be `>= 0`.
     */
    fun getOffsetGap(fieldName: String?): Int {
        return 1
    }

    /** Frees persistent resources used by this Analyzer  */
    override fun close() {
        if (storedValue != null) {
            storedValue!!.close()
            storedValue = null
        }
    }

    /**
     * This class encapsulates the outer components of a token stream. It provides access to the
     * source (a [Reader] [Consumer] and the outer end (sink), an instance of [ ] which also serves as the [TokenStream] returned by [ ][Analyzer.tokenStream].
     */
    class TokenStreamComponents(source: Consumer<Reader>, result: TokenStream) {
        /** Original source of the tokens.  */
        private val source: Consumer<Reader> = source

        /**
         * Sink tokenstream, such as the outer tokenfilter decorating the chain. This can be the source
         * if there are no filters.
         */
        private val sink: TokenStream = result

        /** Internal cache only used by [Analyzer.tokenStream].  */
        @Transient
        var reusableStringReader: ReusableStringReader? = null

        /**
         * Creates a new [TokenStreamComponents] instance
         *
         * @param tokenizer the analyzer's Tokenizer
         * @param result the analyzer's resulting token stream
         */
        constructor(tokenizer: Tokenizer, result: TokenStream) : this(tokenizer::setReader, result)

        /** Creates a new [TokenStreamComponents] from a Tokenizer  */
        constructor(tokenizer: Tokenizer) : this(tokenizer::setReader, tokenizer)

        /**
         * Resets the encapsulated components with the given reader. If the components cannot be reset,
         * an Exception should be thrown.
         *
         * @param reader a reader to reset the source component
         */
        fun setReader(reader: Reader) {
            source.accept(reader)
        }

        val tokenStream: TokenStream
            /**
             * Returns the sink [TokenStream]
             *
             * @return the sink [TokenStream]
             */
            get() = sink

        /** Returns the component's source  */
        fun getSource(): Consumer<Reader> {
            return source
        }
    }

    /**
     * Strategy defining how TokenStreamComponents are reused per call to [ ][Analyzer.tokenStream].
     */
    abstract class ReuseStrategy
    /** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */ // Explicitly declared so that we have non-empty javadoc
    protected constructor() {
        /**
         * Gets the reusable TokenStreamComponents for the field with the given name.
         *
         * @param analyzer Analyzer from which to get the reused components. Use [     ][.getStoredValue] and [.setStoredValue] to access the
         * data on the Analyzer.
         * @param fieldName Name of the field whose reusable TokenStreamComponents are to be retrieved
         * @return Reusable TokenStreamComponents for the field, or `null` if there was no
         * previous components for the field
         */
        abstract fun getReusableComponents(
            analyzer: Analyzer, fieldName: String
        ): TokenStreamComponents?

        /**
         * Stores the given TokenStreamComponents as the reusable components for the field with the give
         * name.
         *
         * @param fieldName Name of the field whose TokenStreamComponents are being set
         * @param components TokenStreamComponents which are to be reused for the field
         */
        abstract fun setReusableComponents(
            analyzer: Analyzer, fieldName: String, components: TokenStreamComponents
        )

        /**
         * Returns the currently stored value.
         *
         * @return Currently stored value or `null` if no value is stored
         * @throws AlreadyClosedException if the Analyzer is closed.
         */
        protected fun getStoredValue(analyzer: Analyzer): Any {
            if (analyzer.storedValue == null) {
                throw AlreadyClosedException("this Analyzer is closed")
            }
            return analyzer.storedValue!!.get()!!
        }

        /**
         * Sets the stored value.
         *
         * @param storedValue Value to store
         * @throws AlreadyClosedException if the Analyzer is closed.
         */
        protected fun setStoredValue(analyzer: Analyzer, storedValue: Any?) {
            if (analyzer.storedValue == null) {
                throw AlreadyClosedException("this Analyzer is closed")
            }
            analyzer.storedValue!!.set(storedValue!!)
        }
    }

    /**
     * Expert: create a new Analyzer with a custom [ReuseStrategy].
     *
     *
     * NOTE: if you just want to reuse on a per-field basis, it's easier to use a subclass of
     * [AnalyzerWrapper] such as [
 * PerFieldAnalyzerWrapper]({@docRoot}/../analysis/common/org/apache/lucene/analysis/miscellaneous/PerFieldAnalyzerWrapper.html) instead.
     */

    private class StringTokenStream(
        attributeFactory: AttributeFactory,
        private val value: String,
        private val length: Int
    ) :
        TokenStream(attributeFactory) {
        private var used = true
        private val termAttribute: CharTermAttribute = addAttribute(CharTermAttribute::class)
        private val offsetAttribute: OffsetAttribute = addAttribute(OffsetAttribute::class)

        override fun reset() {
            used = false
        }

        override fun incrementToken(): Boolean {
            if (used) {
                return false
            }
            clearAttributes()
            termAttribute.append(value)
            offsetAttribute.setOffset(0, length)
            used = true
            return true
        }

        @Throws(IOException::class)
        override fun end() {
            super.end()
            offsetAttribute.setOffset(length, length)
        }
    }

    companion object {
        /** A predefined [ReuseStrategy] that reuses the same components for every field.  */
        val GLOBAL_REUSE_STRATEGY: ReuseStrategy = object : ReuseStrategy() {
            override fun getReusableComponents(analyzer: Analyzer, fieldName: String): TokenStreamComponents {
                return getStoredValue(analyzer) as TokenStreamComponents
            }

            override fun setReusableComponents(
                analyzer: Analyzer, fieldName: String, components: TokenStreamComponents
            ) {
                setStoredValue(analyzer, components)
            }
        }

        /**
         * A predefined [ReuseStrategy] that reuses components per-field by maintaining a Map of
         * TokenStreamComponent per field name.
         */
        val PER_FIELD_REUSE_STRATEGY: ReuseStrategy = object : ReuseStrategy() {
            override fun getReusableComponents(analyzer: Analyzer, fieldName: String): TokenStreamComponents {
                val componentsPerField : MutableMap<String, TokenStreamComponents> =
                    getStoredValue(analyzer) as MutableMap<String, TokenStreamComponents>
                return componentsPerField[fieldName]!!
            }

            override fun setReusableComponents(
                analyzer: Analyzer, fieldName: String, components: TokenStreamComponents
            ) {
                var componentsPerField: MutableMap<String, TokenStreamComponents> =
                    getStoredValue(analyzer) as MutableMap<String, TokenStreamComponents>
                if (componentsPerField == null) {
                    componentsPerField = mutableMapOf()
                    setStoredValue(analyzer, componentsPerField)
                }
                componentsPerField[fieldName] = components
            }
        }
    }
}
