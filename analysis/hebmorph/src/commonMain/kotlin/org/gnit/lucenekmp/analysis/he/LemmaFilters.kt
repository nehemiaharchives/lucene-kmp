package org.gnit.lucenekmp.analysis.he

abstract class LemmaFilterBase {
    open fun filterCollection(word: String, collection: List<Token>, preallocatedOut: MutableList<Token>): MutableList<Token>? {
        preallocatedOut.clear()

        for (t in collection) {
            if (isValidToken(t)) {
                preallocatedOut.add(t)
            }
        }

        return preallocatedOut
    }

    abstract fun isValidToken(t: Token): Boolean
}

/**
 * BasicLemmaFilter will only filter collections with more than one lemma. For them, any lemma
 * scored below 0.7 is probably a result of some heavy toleration, and will be ignored.
 */
class BasicLemmaFilter : LemmaFilterBase() {
    override fun filterCollection(word: String, collection: List<Token>, preallocatedOut: MutableList<Token>): MutableList<Token>? {
        if (collection.size > 1) {
            val ret = super.filterCollection(word, collection, preallocatedOut)
            if (ret != null && ret.size > 0) {
                return ret
            }
        }
        return null
    }

    override fun isValidToken(t: Token): Boolean {
        if (t is HebrewToken) {
            // Pose a minimum score limit for words
            if (t.getScore() < 0.7f) {
                return false
            }

            // Pose a higher threshold to verbs (easier to get irrelevant verbs from toleration)
            if ((t.getMask() == DescFlag.D_VERB) && (t.getScore() < 0.85f)) {
                return false
            }
        }
        return true
    }
}
