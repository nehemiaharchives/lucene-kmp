package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.util.AttributeFactory

/** Extension to [Analyzer] suitable for Analyzers which wrap other Analyzers. */
abstract class AnalyzerWrapper(reuseStrategy: Analyzer.ReuseStrategy) : Analyzer(reuseStrategy) {

    /** Retrieves the wrapped Analyzer appropriate for analyzing the field with the given name */
    protected abstract fun getWrappedAnalyzer(fieldName: String): Analyzer

    /** Wraps / alters the given TokenStreamComponents. By default returns the components. */
    protected open fun wrapComponents(
        fieldName: String,
        components: Analyzer.TokenStreamComponents
    ): Analyzer.TokenStreamComponents {
        return components
    }

    /** Wraps / alters the given TokenStream for normalization purposes. By default returns the input. */
    protected open fun wrapTokenStreamForNormalization(fieldName: String, `in`: TokenStream): TokenStream {
        return `in`
    }

    /** Wraps / alters the given Reader. By default returns the reader. */
    protected open fun wrapReader(fieldName: String, reader: Reader): Reader {
        return reader
    }

    /** Wraps / alters the given Reader for normalization. By default returns the reader. */
    protected open fun wrapReaderForNormalization(fieldName: String, reader: Reader): Reader {
        return reader
    }

    protected override fun createComponents(fieldName: String): Analyzer.TokenStreamComponents {
        val wrappedAnalyzer = getWrappedAnalyzer(fieldName)
        var wrappedComponents = wrappedAnalyzer.reuseStrategy.getReusableComponents(wrappedAnalyzer, fieldName)
        if (wrappedComponents == null) {
            // force creation of components via a temporary tokenStream call
            try {
                wrappedAnalyzer.tokenStream(fieldName, "")
            } catch (ignored: Exception) {
            }
            wrappedComponents = wrappedAnalyzer.reuseStrategy.getReusableComponents(wrappedAnalyzer, fieldName)
                ?: throw IllegalStateException("wrapped analyzer did not create components for field=$fieldName")
        }
        val wrapperComponents = wrapComponents(fieldName, wrappedComponents)
        val outer = Analyzer.TokenStreamComponents(wrapperComponents.getSource(), wrapperComponents.tokenStream)
        Companion.wrappedMap[outer] = wrappedComponents
        return outer
    }

    protected override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        // Delegate normalization wrapping but avoid calling protected normalize on wrapped analyzer
        return wrapTokenStreamForNormalization(fieldName, `in`)
    }

    override fun getPositionIncrementGap(fieldName: String?): Int {
        return getWrappedAnalyzer(fieldName ?: "").getPositionIncrementGap(fieldName)
    }

    override fun getOffsetGap(fieldName: String?): Int {
        return getWrappedAnalyzer(fieldName ?: "").getOffsetGap(fieldName)
    }

    protected override fun initReader(fieldName: String, reader: Reader): Reader {
        // Avoid calling protected initReader on wrapped analyzer; just wrap and return
        return wrapReader(fieldName, reader)
    }

    protected override fun initReaderForNormalization(fieldName: String?, reader: Reader): Reader {
        return wrapReaderForNormalization(fieldName ?: "", reader)
    }

    /** A ReuseStrategy that checks the wrapped analyzer's strategy for reusability. */
    class UnwrappingReuseStrategy(private val reuseStrategy: Analyzer.ReuseStrategy) : Analyzer.ReuseStrategy() {
        override fun getReusableComponents(analyzer: Analyzer, fieldName: String): Analyzer.TokenStreamComponents? {
            if (analyzer is AnalyzerWrapper) {
                val wrappedAnalyzer = analyzer.getWrappedAnalyzer(fieldName)
                if (wrappedAnalyzer.reuseStrategy.getReusableComponents(wrappedAnalyzer, fieldName) == null) {
                    return null
                }
            }
            return reuseStrategy.getReusableComponents(analyzer, fieldName)
        }

        override fun setReusableComponents(analyzer: Analyzer, fieldName: String, components: Analyzer.TokenStreamComponents) {
            reuseStrategy.setReusableComponents(analyzer, fieldName, components)
            if (analyzer is AnalyzerWrapper) {
                val wrappedComponents = Companion.wrappedMap[components]
                val wrappedAnalyzer = analyzer.getWrappedAnalyzer(fieldName)
                wrappedAnalyzer.reuseStrategy.setReusableComponents(wrappedAnalyzer, fieldName, wrappedComponents!!)
            }
        }
    }
    companion object {
        private val wrappedMap: MutableMap<Analyzer.TokenStreamComponents, Analyzer.TokenStreamComponents> = mutableMapOf()
    }
}
