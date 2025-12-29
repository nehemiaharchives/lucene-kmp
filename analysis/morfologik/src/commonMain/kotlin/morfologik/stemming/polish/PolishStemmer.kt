package morfologik.stemming.polish

import morfologik.stemming.Dictionary
import morfologik.stemming.DictionaryLookup
import morfologik.stemming.IStemmer
import morfologik.stemming.WordData
import okio.Buffer
import org.gnit.lucenekmp.jdkport.OkioSourceInputStream

/**
 * A dictionary-based stemmer for the Polish language.
 */
class PolishStemmer : IStemmer, Iterable<WordData> {
    companion object {
        private var dictionary: Dictionary? = null

        private fun loadDictionary(): Dictionary {
            val dictStream = OkioSourceInputStream(Buffer().apply { write(polishDictData) })
            try {
                val infoStream = OkioSourceInputStream(Buffer().apply { write(polishInfoData) })
                try {
                    return Dictionary.read(dictStream, infoStream)
                } finally {
                    infoStream.close()
                }
            } finally {
                dictStream.close()
            }
        }
    }

    private val lookup: DictionaryLookup

    init {
        if (dictionary == null) {
            dictionary = loadDictionary()
        }
        lookup = DictionaryLookup(checkNotNull(dictionary))
    }

    fun getDictionary(): Dictionary = checkNotNull(dictionary)

    override fun lookup(word: CharSequence): List<WordData> = lookup.lookup(word)

    override fun iterator(): Iterator<WordData> = lookup.iterator()
}
