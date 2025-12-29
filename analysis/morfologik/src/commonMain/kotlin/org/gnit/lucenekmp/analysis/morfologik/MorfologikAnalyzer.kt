package org.gnit.lucenekmp.analysis.morfologik

import morfologik.stemming.Dictionary
import morfologik.stemming.polish.PolishStemmer
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer

/**
 * Analyzer using Morfologik library.
 */
open class MorfologikAnalyzer(private val dictionary: Dictionary = PolishStemmer().getDictionary()) : Analyzer() {
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val src: Tokenizer = StandardTokenizer(MorfologikAttributeFactory())
        return TokenStreamComponents(src, MorfologikFilter(src, dictionary))
    }
}
