package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.jdkport.Reader

/**
 * Extension to [Analyzer] suitable for Analyzers which wrap other Analyzers.
 *
 * [getWrappedAnalyzer] allows the Analyzer to wrap multiple Analyzers which are selected on a per
 * field basis.
 *
 * [wrapComponents] allows the TokenStreamComponents of the wrapped Analyzer to then be wrapped
 * (such as adding a new [TokenFilter] to form new TokenStreamComponents.
 *
 * [wrapReader] allows the Reader of the wrapped Analyzer to then be wrapped (such as adding a new
 * [CharFilter].
 *
 * **Important:** If you do not want to wrap the TokenStream using [wrapComponents] or the Reader
 * using [wrapReader] and just delegate to other analyzers (like by field name), use
 * `DelegatingAnalyzerWrapper` as superclass.
 *
 * @since 4.0.0
 */
abstract class AnalyzerWrapper
/**
 * Creates a new AnalyzerWrapper with the given reuse strategy.
 *
 * If you want to wrap a single delegate Analyzer you can probably reuse its strategy when
 * instantiating this subclass: `super(delegate.getReuseStrategy());`.
 *
 * If you choose different analyzers per field, use `PER_FIELD_REUSE_STRATEGY`.
 */ protected constructor(reuseStrategy: ReuseStrategy) : Analyzer(UnwrappingReuseStrategy(reuseStrategy)) {

    /**
     * Retrieves the wrapped Analyzer appropriate for analyzing the field with the given name
     *
     * @param fieldName Name of the field which is to be analyzed
     * @return Analyzer for the field with the given name. Assumed to be non-null
     */
    protected abstract fun getWrappedAnalyzer(fieldName: String): Analyzer

    /**
     * Wraps / alters the given TokenStreamComponents, taken from the wrapped Analyzer, to form new
     * components. It is through this method that new TokenFilters can be added by AnalyzerWrappers.
     * By default, the given components are returned.
     *
     * @param fieldName Name of the field which is to be analyzed
     * @param components TokenStreamComponents taken from the wrapped Analyzer
     * @return Wrapped / altered TokenStreamComponents.
     */
    protected open fun wrapComponents(fieldName: String, components: TokenStreamComponents): TokenStreamComponents {
        return components
    }

    /**
     * Wraps / alters the given TokenStream for normalization purposes, taken from the wrapped
     * Analyzer, to form new components. It is through this method that new TokenFilters can be added
     * by AnalyzerWrappers. By default, the given token stream are returned.
     *
     * @param fieldName Name of the field which is to be analyzed
     * @param in TokenStream taken from the wrapped Analyzer
     * @return Wrapped / altered TokenStreamComponents.
     */
    protected open fun wrapTokenStreamForNormalization(fieldName: String, `in`: TokenStream): TokenStream {
        return `in`
    }

    /**
     * Wraps / alters the given Reader. Through this method AnalyzerWrappers can implement [ ][Analyzer.initReader]. By default, the given reader is returned.
     *
     * @param fieldName name of the field which is to be analyzed
     * @param reader the reader to wrap
     * @return the wrapped reader
     */
    protected open fun wrapReader(fieldName: String, reader: Reader): Reader {
        return reader
    }

    /**
     * Wraps / alters the given Reader. Through this method AnalyzerWrappers can implement [ ][Analyzer.initReaderForNormalization]. By default, the given reader is returned.
     *
     * @param fieldName name of the field which is to be analyzed
     * @param reader the reader to wrap
     * @return the wrapped reader
     */
    protected open fun wrapReaderForNormalization(fieldName: String, reader: Reader): Reader {
        return reader
    }

    protected final override fun createComponents(fieldName: String): TokenStreamComponents {
        val wrappedComponents = getWrappedAnalyzer(fieldName).createComponentsInternal(fieldName)
        val wrapperComponents = wrapComponents(fieldName, wrappedComponents)
        return TokenStreamComponentsWrapper(wrapperComponents, wrappedComponents)
    }

    protected final override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return wrapTokenStreamForNormalization(
            fieldName,
            getWrappedAnalyzer(fieldName).normalizeInternal(fieldName, `in`)
        )
    }

    override fun getPositionIncrementGap(fieldName: String?): Int {
        return getWrappedAnalyzer(fieldName ?: "").getPositionIncrementGap(fieldName)
    }

    override fun getOffsetGap(fieldName: String?): Int {
        return getWrappedAnalyzer(fieldName ?: "").getOffsetGap(fieldName)
    }

    final override fun initReader(fieldName: String, reader: Reader): Reader {
        return getWrappedAnalyzer(fieldName).initReaderInternal(fieldName, wrapReader(fieldName, reader))
    }

    protected final override fun initReaderForNormalization(fieldName: String?, reader: Reader): Reader {
        return getWrappedAnalyzer(fieldName ?: "").initReaderForNormalizationInternal(
            fieldName,
            wrapReaderForNormalization(fieldName ?: "", reader)
        )
    }

    /**
     * A [org.gnit.lucenekmp.analysis.Analyzer.ReuseStrategy] that checks the wrapped analyzer's
     * strategy for reusability. If the wrapped analyzer's strategy returns null, components need to
     * be re-created.
     */
    class UnwrappingReuseStrategy(private val reuseStrategy: ReuseStrategy) : ReuseStrategy() {
        override fun getReusableComponents(analyzer: Analyzer, fieldName: String): TokenStreamComponents? {
            if (analyzer is AnalyzerWrapper) {
                val wrappedAnalyzer = analyzer.getWrappedAnalyzer(fieldName)
                if (wrappedAnalyzer.reuseStrategy.getReusableComponents(wrappedAnalyzer, fieldName) == null) {
                    return null
                }
            }
            return reuseStrategy.getReusableComponents(analyzer, fieldName)
        }

        override fun setReusableComponents(analyzer: Analyzer, fieldName: String, components: TokenStreamComponents) {
            reuseStrategy.setReusableComponents(analyzer, fieldName, components)

            if (analyzer is AnalyzerWrapper) {
                val wrapperComponents = components as TokenStreamComponentsWrapper
                val wrappedAnalyzer = analyzer.getWrappedAnalyzer(fieldName)
                wrappedAnalyzer
                    .reuseStrategy
                    .setReusableComponents(wrappedAnalyzer, fieldName, wrapperComponents.getWrappedComponents())
            }
        }
    }

    /**
     * A [Analyzer.TokenStreamComponents] that decorates the wrapper with access to the wrapped
     * components.
     */
    internal class TokenStreamComponentsWrapper(
        wrapper: TokenStreamComponents,
        private val wrapped: TokenStreamComponents
    ) : TokenStreamComponents(wrapper.getSource(), wrapper.tokenStream) {

        fun getWrappedComponents(): TokenStreamComponents {
            return wrapped
        }
    }

}
