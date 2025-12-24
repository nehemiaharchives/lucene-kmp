package org.gnit.lucenekmp.jdkport

import org.gnit.lucenekmp.jdkport.Objects.hash
import org.gnit.lucenekmp.jdkport.StrictMath.min


/**
 * Concrete implementation of the  [ BreakIteratorProvider][java.text.spi.BreakIteratorProvider] class for the JRE LocaleProviderAdapter.
 *
 * @author Naoto Sato
 * @author Masayoshi Okutsu
 */
@Ported(from = "sun.util.locale.provider.BreakIteratorProviderImpl")
class BreakIteratorProviderImpl(
    private val type: LocaleProviderAdapter.Type,
    val availableLanguageTags: Set<String>
) : BreakIteratorProvider()/*, AvailableLanguageTags*/ {
    /**
     * Returns an array of all locales for which this locale service provider
     * can provide localized objects or names.
     *
     * @return An array of all locales for which this locale service provider
     * can provide localized objects or names.
     */
    override fun getAvailableLocales(): Array<Locale> {
        return LocaleProviderAdapter.toLocaleArray(this.availableLanguageTags)
    }

    /**
     * Returns a new `BreakIterator` instance
     * for [word breaks](../BreakIterator.html#word)
     * for the given locale.
     * @param locale the desired locale
     * @return A break iterator for word breaks
     * @exception NullPointerException if `locale` is null
     * @exception IllegalArgumentException if `locale` isn't
     * one of the locales returned from
     * [     getAvailableLocales()][java.util.spi.LocaleServiceProvider.getAvailableLocales].
     * @see java.text.BreakIterator.getWordInstance
     */
    override fun getWordInstance(locale: Locale): BreakIterator {
        return getBreakInstance(
            locale,
            WORD_INDEX,
            "WordData",
            "WordDictionary"
        )
    }

    /**
     * Returns a new `BreakIterator` instance
     * for [line breaks](../BreakIterator.html#line)
     * for the given locale.
     * @param locale the desired locale
     * @return A break iterator for line breaks
     * @exception NullPointerException if `locale` is null
     * @exception IllegalArgumentException if `locale` isn't
     * one of the locales returned from
     * [     getAvailableLocales()][java.util.spi.LocaleServiceProvider.getAvailableLocales].
     * @see java.text.BreakIterator.getLineInstance
     */
    override fun getLineInstance(locale: Locale): BreakIterator {
        return getBreakInstance(
            locale,
            LINE_INDEX,
            "LineData",
            "LineDictionary"
        )
    }

    /**
     * Returns a new `BreakIterator` instance
     * for [character breaks](../BreakIterator.html#character)
     * for the given locale.
     * @param locale the desired locale
     * @return A break iterator for character breaks
     * @exception NullPointerException if `locale` is null
     * @exception IllegalArgumentException if `locale` isn't
     * one of the locales returned from
     * [     getAvailableLocales()][java.util.spi.LocaleServiceProvider.getAvailableLocales].
     * @see java.text.BreakIterator.getCharacterInstance
     */
    override fun getCharacterInstance(locale: Locale): BreakIterator {
        return GraphemeBreakIterator()
    }

    /**
     * Returns a new `BreakIterator` instance
     * for [sentence breaks](../BreakIterator.html#sentence)
     * for the given locale.
     * @param locale the desired locale
     * @return A break iterator for sentence breaks
     * @exception NullPointerException if `locale` is null
     * @exception IllegalArgumentException if `locale` isn't
     * one of the locales returned from
     * [     getAvailableLocales()][java.util.spi.LocaleServiceProvider.getAvailableLocales].
     * @see java.text.BreakIterator.getSentenceInstance
     */
    override fun getSentenceInstance(locale: Locale): BreakIterator {
        return getBreakInstance(
            locale,
            SENTENCE_INDEX,
            "SentenceData",
            "SentenceDictionary"
        )
    }

    private fun getBreakInstance(
        locale: Locale,
        type: Int,
        ruleName: String,
        dictionaryName: String
    ): BreakIterator {

        //val lr: LocaleResources = LocaleProviderAdapter.forJRE().getLocaleResources(locale)
        val isThai = locale.language == "th"
        val classNames = if (isThai) {
            arrayOf(
                "DictionaryBasedBreakIterator",
                "DictionaryBasedBreakIterator",
                "RuleBasedBreakIterator"
            )
        } else {
            arrayOf(
                "RuleBasedBreakIterator",
                "RuleBasedBreakIterator",
                "RuleBasedBreakIterator"
            )
        }
        /*val ruleFile = lr.getBreakIteratorInfo(ruleName) as String */ // lucene-kmp is self-containing library and it should be able to work without file access, so only embedded data via memory is allowed. no file access
        val ruleData: ByteArray = when (ruleName) {
            "WordData" -> if (isThai) wordBreakIteratorDataTh else wordBreakIteratorData
            "LineData" -> if (isThai) lineBreakIteratorDataTh else lineBreakIteratorData
            "SentenceData" -> sentenceBreakIteratorData
            else -> throw IllegalArgumentException("Invalid break iterator data name \"$ruleName\"")
        }

        try {
            when (classNames[type]) {
                "RuleBasedBreakIterator" -> return RuleBasedBreakIterator(/*ruleFile,*/ ruleData)

                "DictionaryBasedBreakIterator" -> {
                    val dictionaryData: ByteArray = when (dictionaryName) {
                        "WordDictionary", "LineDictionary" -> thaiDictionaryData
                        else -> throw IllegalArgumentException("Invalid dictionary data name \"$dictionaryName\"")
                    }
                    return DictionaryBasedBreakIterator(
                        /*ruleFile,*/ ruleData = ruleData,
                        /*dictionaryFile,*/ dictionaryData = dictionaryData
                    )
                }

                else -> throw IllegalArgumentException(
                    "Invalid break iterator class \"" +
                            classNames[type] + "\""
                )
            }
        } catch (e: /*MissingResource*/Exception) {
            throw /*java.lang.Internal*/Error(e.toString(), e)
        } catch (e: IllegalArgumentException) {
            throw /*java.lang.Internal*/Error(e.toString(), e)
        }
    }

    override fun isSupportedLocale(locale: Locale): Boolean {
        return LocaleProviderAdapter.forType(type).isSupportedProviderLocale(
            locale,
            this.availableLanguageTags
        )
    }

    internal class GraphemeBreakIterator : BreakIterator() {
        lateinit var ci: CharacterIterator
        var offset: Int = 0
        var boundaries: MutableList<Int>? = null
        var boundaryIndex: Int = 0

        init {
            setText("")
        }

        override fun first(): Int {
            boundaryIndex = 0
            return current()
        }

        override fun last(): Int {
            boundaryIndex = boundaries!!.size - 1
            return current()
        }

        override fun next(n: Int): Int {
            if (n == 0) {
                return offset
            }

            boundaryIndex = boundaryIndex + n
            if (boundaryIndex < 0) {
                boundaryIndex = 0
                current()
                return DONE
            } else if (boundaryIndex >= boundaries!!.size) {
                boundaryIndex = boundaries!!.size - 1
                current()
                return DONE
            } else {
                return current()
            }
        }

        override fun next(): Int {
            return next(1)
        }

        override fun previous(): Int {
            return next(-1)
        }

        override fun following(offset: Int): Int {
            val lastBoundary: Int /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */ =
                boundaries!![boundaries!!.size - 1]

            require(!(offset < boundaries!![0] || offset > lastBoundary)) { "offset is out of bounds: $offset" }
            if (offset == this.offset && this.offset == lastBoundary) {
                return DONE
            }

            boundaryIndex =
                Arrays.binarySearch(boundaries!!.toIntArray(), min(offset + 1, lastBoundary))
            if (boundaryIndex < 0) {
                boundaryIndex = -boundaryIndex - 1
            }

            return current()
        }

        override fun current(): Int {
            offset = boundaries!![boundaryIndex]
            return offset
        }

        override val text: CharacterIterator
            get(): CharacterIterator {
                return ci!!
            }

        override fun setText(newText: CharacterIterator) {
            ci = newText
            val text = CharacterIteratorCharSequence(ci)
            val end = ci.endIndex
            boundaries = ArrayList()

            var b = ci.beginIndex
            while (b < end) {
                boundaries!!.add(b)
                b = Grapheme.nextBoundary(text, b, end)
            }
            boundaries!!.add(end)
            boundaryIndex = 0
            offset = ci.index
        }

        // Had to override to suppress the bug in the BreakIterator's default impl.
        // See the comments in the default impl.
        override fun isBoundary(offset: Int): Boolean {
            require(!(offset < boundaries!![0] || offset > boundaries!!.get(boundaries!!.size - 1))) { "offset is out of bounds: $offset" }
            return Arrays.binarySearch(boundaries!!.toIntArray(), offset) >= 0
        }

        override fun hashCode(): Int {
            return hash(ci, offset, boundaries, boundaryIndex)
        }

        override fun equals(other: Any?): Boolean {
            return other is GraphemeBreakIterator &&
                    ci!! == other.ci && offset == other.offset &&
                    boundaries!! == other.boundaries && boundaryIndex == other.boundaryIndex
        }

        override fun cloneImpl(): BreakIterator {
            val result = GraphemeBreakIterator()

            val clonedCi = (this.ci.clone() as? CharacterIterator) ?: this.ci
            result.setText(clonedCi)

            result.boundaryIndex = this.boundaryIndex
            result.offset = this.offset
            result.ci.setIndex(this.offset)

            return result
        }
    }

    /**
     * Implementation only for calling Grapheme.nextBoundary().
     *
     * This is a special-purpose CharSequence that represents characters in the
     * index range [0..endIndex) of the underlying CharacterIterator, even if
     * that CharacterIterator represents the subrange of some string. The calling
     * code in GraphemeBreakIterator takes care to ensure that only valid indexes
     * into the src are used.
     */
    internal class CharacterIteratorCharSequence(var src: CharacterIterator) : CharSequence {
        override val length: Int
            get() {
                // Return the entire CharSequence length (0 to endIndex), not to
                // be confused with the text range length (beginIndex to endIndex)
                // of the underlying CharacterIterator.
                return src.endIndex
            }

        override fun get(index: Int): Char {
            src.setIndex(index)
            return src.current()
        }

        override fun subSequence(start: Int, end: Int): CharSequence {
            // not expected to be called
            throw UnsupportedOperationException()
        }
    }

    companion object {
        private const val WORD_INDEX = 0
        private const val LINE_INDEX = 1
        private const val SENTENCE_INDEX = 2
    }
}
