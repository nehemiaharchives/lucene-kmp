package org.gnit.lucenekmp.analysis.ja.dict

import org.gnit.lucenekmp.analysis.util.CSVUtil

/** Morphological information for user dictionary. */
class UserMorphData(private val data: Array<String>) : JaMorphData {
    companion object {
        const val WORD_COST: Int = -100000
        const val LEFT_ID: Int = 5
        const val RIGHT_ID: Int = 5
    }

    override fun getLeftId(wordId: Int): Int = LEFT_ID

    override fun getRightId(wordId: Int): Int = RIGHT_ID

    override fun getWordCost(wordId: Int): Int = WORD_COST

    override fun getReading(morphId: Int, surface: CharArray, off: Int, len: Int): String? {
        return getFeature(morphId, 0)
    }

    override fun getPartOfSpeech(morphId: Int): String? = getFeature(morphId, 1)

    override fun getBaseForm(morphId: Int, surface: CharArray, off: Int, len: Int): String? = null

    override fun getPronunciation(morphId: Int, surface: CharArray, off: Int, len: Int): String? = null

    override fun getInflectionType(morphId: Int): String? = null

    override fun getInflectionForm(wordId: Int): String? = null

    private fun getAllFeaturesArray(wordId: Int): Array<String>? {
        val index = wordId - UserDictionary.CUSTOM_DICTIONARY_WORD_ID_OFFSET
        if (index < 0 || index >= data.size) {
            return null
        }
        val allFeatures = data[index]
        return allFeatures.split(UserDictionary.INTERNAL_SEPARATOR).toTypedArray()
    }

    private fun getFeature(wordId: Int, vararg fields: Int): String? {
        val allFeatures = getAllFeaturesArray(wordId) ?: return null
        val sb = StringBuilder()
        if (fields.isEmpty()) {
            for (feature in allFeatures) {
                sb.append(CSVUtil.quoteEscape(feature)).append(",")
            }
        } else if (fields.size == 1) {
            sb.append(allFeatures[fields[0]]).append(",")
        } else {
            for (field in fields) {
                sb.append(CSVUtil.quoteEscape(allFeatures[field])).append(",")
            }
        }
        return sb.deleteAt(sb.length - 1).toString()
    }
}
