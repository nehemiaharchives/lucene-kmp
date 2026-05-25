package org.gnit.lucenekmp.analysis.he

open class Lemmatizer(private val dictHeb: DictHebMorph) {
    fun isLegalPrefix(str: String): Boolean {
        return dictHeb.getPref().containsKey(str)
    }

    fun tryStrippingPrefix(word: String): String {
        val firstQuote = word.indexOf('"')
        if (firstQuote > -1 && firstQuote < word.length - 2) {
            if (isLegalPrefix(word.substring(0, firstQuote))) {
                return word.substring(firstQuote + 1, firstQuote + 1 + word.length - firstQuote - 1)
            }
        }
        val firstSingleQuote = word.indexOf('\'')
        if (firstSingleQuote == -1) return word
        if ((firstQuote > -1) && (firstSingleQuote > firstQuote)) return word
        if (isLegalPrefix(word.substring(0, firstSingleQuote))) {
            return word.substring(firstSingleQuote + 1, firstSingleQuote + 1 + word.length - firstSingleQuote - 1)
        }
        return word
    }

    fun lemmatize(word: String): MutableList<HebrewToken> {
        return lemmatize(word, ArrayList())
    }

    fun lemmatize(word: String, ret: MutableList<HebrewToken>): MutableList<HebrewToken> {
        var prefLen: Byte = 0
        var prefixMask: Int?
        var md: MorphData? = dictHeb.lookup(word)
        if (md != null) {
            for (lemma in md.getLemmas()) ret.add(HebrewToken(word, 0, lemma, 1.0f))
            if (md.haltIfFound()) return ret
        } else if (word.endsWith("'")) {
            md = dictHeb.lookup(word.substring(0, word.length - 1))
            if (md != null) {
                for (lemma in md.getLemmas()) ret.add(HebrewToken(word, 0, lemma, 1.0f))
                if (md.haltIfFound()) return ret
            }
        }

        prefLen = 0
        while (true) {
            if (word.length - prefLen < 2) break
            prefLen++
            prefixMask = dictHeb.getPref()[word.substring(0, prefLen.toInt())]
            if (prefixMask == null) break
            md = dictHeb.lookup(word.substring(prefLen.toInt()))
            if (md != null && ((md.getPrefixes() and prefixMask) > 0)) {
                for (lemma in md.getLemmas()) {
                    if ((lemma.getPrefix().getValue().toInt() and prefixMask) > 0) {
                        ret.add(HebrewToken(word, prefLen, lemma, 0.9f))
                    }
                }
                if (md.haltIfFound()) return ret
            }
        }
        return ret
    }

    fun lemmatizeTolerant(word: String): MutableList<HebrewToken> {
        return lemmatizeTolerant(word, ArrayList())
    }

    fun lemmatizeTolerant(word: String, ret: MutableList<HebrewToken>): MutableList<HebrewToken> {
        val mDict = dictHeb.getRadix()
        val mPref = dictHeb.getPref()
        if (word.length > 20) return ret
        var prefLen: Byte = 0
        var prefixMask: Int?
        var tolerated = mDict.lookupTolerant(word, LookupTolerators.TolerateEmKryiaAll)
        if (tolerated != null) {
            for (lr in tolerated) {
                for (lemma in lr.getData().getLemmas()) {
                    ret.add(HebrewToken(lr.getWord(), 0, lemma, lr.getScore()))
                }
            }
        }
        prefLen = 0
        while (true) {
            if (word.length - prefLen < 2) break
            prefLen++
            prefixMask = mPref[word.substring(0, prefLen.toInt())]
            if (prefixMask == null) break
            tolerated = mDict.lookupTolerant(word.substring(prefLen.toInt()), LookupTolerators.TolerateEmKryiaAll)
            if (tolerated != null) {
                for (lr in tolerated) {
                    for (lemma in lr.getData().getLemmas()) {
                        if ((lemma.getPrefix().getValue().toInt() and prefixMask) > 0) {
                            ret.add(HebrewToken(word.substring(0, prefLen.toInt()) + lr.getWord(), prefLen, lemma, lr.getScore() * 0.9f))
                        }
                    }
                }
            }
        }
        return ret
    }

    companion object {
        fun removeNiqqud(word: String): String {
            val sb = StringBuilder(word.length)
            for (ch in word) {
                if (ch.code < 1455 || ch.code > 1476) sb.append(ch)
            }
            return sb.toString()
        }
    }
}
