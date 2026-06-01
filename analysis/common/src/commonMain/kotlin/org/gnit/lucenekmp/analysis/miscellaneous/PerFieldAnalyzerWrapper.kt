package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.DelegatingAnalyzerWrapper

/**
 * This analyzer is used to facilitate scenarios where different fields require different analysis
 * techniques. Use the Map argument in [PerFieldAnalyzerWrapper] to
 * add non-default analyzers for fields.
 *
 * <p>Example usage:
 *
 * <pre class="prettyprint">{@code
 * Map<String,Analyzer> analyzerPerField = new HashMap<>();
 * analyzerPerField.put("firstname", new KeywordAnalyzer());
 * analyzerPerField.put("lastname", new KeywordAnalyzer());
 *
 * PerFieldAnalyzerWrapper aWrapper =
 *   new PerFieldAnalyzerWrapper(new StandardAnalyzer(version), analyzerPerField);
 * }</pre>
 *
 * <p>In this example, StandardAnalyzer will be used for all fields except "firstname" and
 * "lastname", for which KeywordAnalyzer will be used.
 *
 * <p>A PerFieldAnalyzerWrapper can be used like any other analyzer, for both indexing and query
 * parsing.
 *
 * @since 3.1
 */
class PerFieldAnalyzerWrapper(
    private val defaultAnalyzer: Analyzer,
    fieldAnalyzers: Map<String, Analyzer>? = null
) : DelegatingAnalyzerWrapper(PER_FIELD_REUSE_STRATEGY) {
    private val fieldAnalyzers: Map<String, Analyzer> = fieldAnalyzers ?: emptyMap()

    /**
     * Constructs with default analyzer.
     *
     * @param defaultAnalyzer Any fields not specifically defined to use a different analyzer will use
     * the one provided here.
     */
    constructor(defaultAnalyzer: Analyzer) : this(defaultAnalyzer, null)

    override fun getWrappedAnalyzer(fieldName: String): Analyzer {
        val analyzer = fieldAnalyzers[fieldName]
        return analyzer ?: defaultAnalyzer
    }

    override fun toString(): String {
        return "PerFieldAnalyzerWrapper($fieldAnalyzers, default=$defaultAnalyzer)"
    }
}
