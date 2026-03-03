package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.jdkport.Reader

/**
 * An analyzer wrapper, that doesn't allow to wrap components or readers. By disallowing it, it
 * means that the thread local resources can be delegated to the delegate analyzer, and not also be
 * allocated on this analyzer. This wrapper class is the base class of all analyzers that just
 * delegate to another analyzer, e.g. per field name.
 *
 * This solves the problem of per field analyzer wrapper, where it also maintains a thread local
 * per field token stream components, while it can safely delegate those and not also hold these
 * data structures, which can become expensive memory wise.
 *
 * **Please note:** This analyzer uses a private [Analyzer.ReuseStrategy], which is returned by [ ][Analyzer.reuseStrategy]. This strategy is used when delegating. If you wrap this analyzer again and
 * reuse this strategy, no delegation is done and the given fallback is used.
 *
 * @since 4.10.0
 */
abstract class DelegatingAnalyzerWrapper
/**
 * Constructor.
 *
 * @param fallbackStrategy is the strategy to use if delegation is not possible This is to support
 * the common pattern: `new OtherWrapper(thisWrapper.getReuseStrategy())`
 */ protected constructor(fallbackStrategy: ReuseStrategy) : AnalyzerWrapper(DelegatingReuseStrategy(fallbackStrategy)) {

    init {
        // häckidy-hick-hack, because we cannot call super() with a reference to "this":
        (reuseStrategy as DelegatingReuseStrategy).wrapper = this
    }

    final override fun wrapComponents(fieldName: String, components: TokenStreamComponents): TokenStreamComponents {
        return super.wrapComponents(fieldName, components)
    }

    final override fun wrapTokenStreamForNormalization(fieldName: String, `in`: TokenStream): TokenStream {
        return super.wrapTokenStreamForNormalization(fieldName, `in`)
    }

    final override fun wrapReader(fieldName: String, reader: Reader): Reader {
        return super.wrapReader(fieldName, reader)
    }

    final override fun wrapReaderForNormalization(fieldName: String, reader: Reader): Reader {
        return super.wrapReaderForNormalization(fieldName, reader)
    }

    /**
     * A [org.gnit.lucenekmp.analysis.Analyzer.ReuseStrategy] that delegates to the wrapped
     * analyzer's strategy for reusability of components.
     */
    class DelegatingReuseStrategy(private val fallbackStrategy: ReuseStrategy) : ReuseStrategy() {
        internal var wrapper: DelegatingAnalyzerWrapper? = null

        override fun getReusableComponents(analyzer: Analyzer, fieldName: String): TokenStreamComponents? {
            return if (analyzer == wrapper) {
                val wrappedAnalyzer = wrapper!!.getWrappedAnalyzer(fieldName)
                wrappedAnalyzer.reuseStrategy.getReusableComponents(wrappedAnalyzer, fieldName)
            } else {
                fallbackStrategy.getReusableComponents(analyzer, fieldName)
            }
        }

        override fun setReusableComponents(analyzer: Analyzer, fieldName: String, components: TokenStreamComponents) {
            if (analyzer == wrapper) {
                val wrappedAnalyzer = wrapper!!.getWrappedAnalyzer(fieldName)
                wrappedAnalyzer.reuseStrategy.setReusableComponents(wrappedAnalyzer, fieldName, components)
            } else {
                fallbackStrategy.setReusableComponents(analyzer, fieldName, components)
            }
        }
    }
}
