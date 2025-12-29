package org.gnit.lucenekmp.analysis.morfologik

import morfologik.stemming.Dictionary
import morfologik.stemming.DictionaryMetadata
import morfologik.stemming.polish.PolishStemmer
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.ResourceLoaderAware

/**
 * Filter factory for [MorfologikFilter].
 */
class MorfologikFilterFactory(args: MutableMap<String, String>) : TokenFilterFactory(args), ResourceLoaderAware {
    companion object {
        const val NAME: String = "morfologik"
        const val DICTIONARY_ATTRIBUTE: String = "dictionary"
    }

    private var resourceName: String? = null
    private var dictionary: Dictionary? = null

    init {
        val dictionaryResourceAttribute = "dictionary-resource"
        val dictionaryResource = get(args, dictionaryResourceAttribute)
        if (dictionaryResource != null && dictionaryResource.isNotEmpty()) {
            throw IllegalArgumentException(
                "The $dictionaryResourceAttribute attribute is no longer supported. Use the '$DICTIONARY_ATTRIBUTE' attribute instead (see LUCENE-6833)."
            )
        }

        resourceName = get(args, DICTIONARY_ATTRIBUTE)

        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    constructor() : this(mutableMapOf()) {
        throw defaultCtorException()
    }

    @Throws(okio.IOException::class)
    override fun inform(loader: ResourceLoader) {
        if (resourceName == null) {
            dictionary = PolishStemmer().getDictionary()
        } else {
            val dict = loader.openResource(resourceName!!)
            val metaName = DictionaryMetadata.getExpectedMetadataFileName(resourceName!!)
            val meta = loader.openResource(metaName)
            try {
                dictionary = Dictionary.read(dict, meta)
            } finally {
                dict.close()
                meta.close()
            }
        }
    }

    override fun create(input: TokenStream): TokenStream {
        val dict = requireNotNull(dictionary) { "MorfologikFilterFactory was not fully initialized." }
        return MorfologikFilter(input, dict)
    }
}
