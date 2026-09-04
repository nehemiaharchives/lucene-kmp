package org.gnit.lucenekmp.jdkport

/**
 * ported to keep API surface compatible with Java lucene
 * However, as I ever know, only QueryParserBase and some class are using Locale for the purpose of generating
 * RangeQuery and Locale is used to feed DateFormat to get date instance. This operation can be ignored as
 * we can implement equivalent without using Locale.
 *
 * Also, in initialization process, locale is set to be defaultLocale(), so in most cases no need to to change it.
 *
 * If lucene use Locale more extensively in the future, we may need to implement Locale in detail.
 *
*/
@Ported(from = "java.util.Locale")
class Locale(
    val language: String? = null,
    val country: String? = null,
    val variant: String? = null
) {
    companion object {
        val ROOT: Locale = Locale("", "")
        val ENGLISH: Locale = Locale("en")
        val JAPANESE: Locale = Locale("ja")
        val US: Locale = Locale(language = "en", country = "US")

        fun forLanguageTag(languageTag: String): Locale {
            val parts = languageTag.split('-')
            return Locale(
                language = parts.getOrNull(0)?.lowercase(),
                country = parts.getOrNull(1)?.uppercase(),
                variant = parts.drop(2).joinToString("-").ifEmpty { null },
            )
        }

        fun getDefault() = US
    }

    class Builder {
        private var languageTag: String = ""

        fun setLanguageTag(languageTag: String): Builder {
            this.languageTag = languageTag
            return this
        }

        fun build(): Locale {
            return forLanguageTag(languageTag)
        }
    }

    fun toLanguageTag(): String {
        return listOfNotNull(language, country, variant).filter { it.isNotEmpty() }.joinToString("-")
    }

    override fun toString(): String {
        return listOfNotNull(language, country, variant).filter { it.isNotEmpty() }.joinToString("_")
    }
}
