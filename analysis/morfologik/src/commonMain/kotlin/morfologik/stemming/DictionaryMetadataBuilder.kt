package morfologik.stemming

import org.gnit.lucenekmp.jdkport.Charset
import org.gnit.lucenekmp.jdkport.Locale

/**
 * Helper class to build [DictionaryMetadata] instances.
 */
class DictionaryMetadataBuilder {
    private val attrs: MutableMap<DictionaryAttribute, String> = LinkedHashMap()

    fun separator(c: Char): DictionaryMetadataBuilder {
        attrs[DictionaryAttribute.SEPARATOR] = c.toString()
        return this
    }

    fun encoding(charset: Charset): DictionaryMetadataBuilder = encoding(charset.name())

    fun encoding(charsetName: String): DictionaryMetadataBuilder {
        attrs[DictionaryAttribute.ENCODING] = charsetName
        return this
    }

    fun frequencyIncluded(): DictionaryMetadataBuilder = frequencyIncluded(true)
    fun frequencyIncluded(v: Boolean): DictionaryMetadataBuilder {
        attrs[DictionaryAttribute.FREQUENCY_INCLUDED] = v.toString()
        return this
    }

    fun ignorePunctuation(): DictionaryMetadataBuilder = ignorePunctuation(true)
    fun ignorePunctuation(v: Boolean): DictionaryMetadataBuilder {
        attrs[DictionaryAttribute.IGNORE_PUNCTUATION] = v.toString()
        return this
    }

    fun ignoreNumbers(): DictionaryMetadataBuilder = ignoreNumbers(true)
    fun ignoreNumbers(v: Boolean): DictionaryMetadataBuilder {
        attrs[DictionaryAttribute.IGNORE_NUMBERS] = v.toString()
        return this
    }

    fun ignoreCamelCase(): DictionaryMetadataBuilder = ignoreCamelCase(true)
    fun ignoreCamelCase(v: Boolean): DictionaryMetadataBuilder {
        attrs[DictionaryAttribute.IGNORE_CAMEL_CASE] = v.toString()
        return this
    }

    fun ignoreAllUppercase(): DictionaryMetadataBuilder = ignoreAllUppercase(true)
    fun ignoreAllUppercase(v: Boolean): DictionaryMetadataBuilder {
        attrs[DictionaryAttribute.IGNORE_ALL_UPPERCASE] = v.toString()
        return this
    }

    fun ignoreDiacritics(): DictionaryMetadataBuilder = ignoreDiacritics(true)
    fun ignoreDiacritics(v: Boolean): DictionaryMetadataBuilder {
        attrs[DictionaryAttribute.IGNORE_DIACRITICS] = v.toString()
        return this
    }

    fun convertCase(): DictionaryMetadataBuilder = convertCase(true)
    fun convertCase(v: Boolean): DictionaryMetadataBuilder {
        attrs[DictionaryAttribute.CONVERT_CASE] = v.toString()
        return this
    }

    fun supportRunOnWords(): DictionaryMetadataBuilder = supportRunOnWords(true)
    fun supportRunOnWords(v: Boolean): DictionaryMetadataBuilder {
        attrs[DictionaryAttribute.RUN_ON_WORDS] = v.toString()
        return this
    }

    fun encoder(type: EncoderType): DictionaryMetadataBuilder {
        attrs[DictionaryAttribute.ENCODER] = type.name
        return this
    }

    fun locale(locale: Locale): DictionaryMetadataBuilder = locale(locale.toString())

    fun locale(localeName: String): DictionaryMetadataBuilder {
        attrs[DictionaryAttribute.LOCALE] = localeName
        return this
    }

    fun withReplacementPairs(replacementPairs: Map<String, List<String>>): DictionaryMetadataBuilder {
        val builder = StringBuilder()
        for ((k, vList) in replacementPairs) {
            for (v in vList) {
                if (builder.isNotEmpty()) builder.append(", ")
                builder.append(k).append(" ").append(v)
            }
        }
        attrs[DictionaryAttribute.REPLACEMENT_PAIRS] = builder.toString()
        return this
    }

    fun withEquivalentChars(equivalentChars: Map<Char, List<Char>>): DictionaryMetadataBuilder {
        val builder = StringBuilder()
        for ((k, vList) in equivalentChars) {
            for (v in vList) {
                if (builder.isNotEmpty()) builder.append(", ")
                builder.append(k).append(" ").append(v)
            }
        }
        attrs[DictionaryAttribute.EQUIVALENT_CHARS] = builder.toString()
        return this
    }

    fun withInputConversionPairs(conversionPairs: Map<String, String>): DictionaryMetadataBuilder {
        val builder = StringBuilder()
        for ((k, v) in conversionPairs) {
            if (builder.isNotEmpty()) builder.append(", ")
            builder.append(k).append(" ").append(v)
        }
        attrs[DictionaryAttribute.INPUT_CONVERSION] = builder.toString()
        return this
    }

    fun withOutputConversionPairs(conversionPairs: Map<String, String>): DictionaryMetadataBuilder {
        val builder = StringBuilder()
        for ((k, v) in conversionPairs) {
            if (builder.isNotEmpty()) builder.append(", ")
            builder.append(k).append(" ").append(v)
        }
        attrs[DictionaryAttribute.OUTPUT_CONVERSION] = builder.toString()
        return this
    }

    fun author(author: String): DictionaryMetadataBuilder {
        attrs[DictionaryAttribute.AUTHOR] = author
        return this
    }

    fun creationDate(creationDate: String): DictionaryMetadataBuilder {
        attrs[DictionaryAttribute.CREATION_DATE] = creationDate
        return this
    }

    fun license(license: String): DictionaryMetadataBuilder {
        attrs[DictionaryAttribute.LICENSE] = license
        return this
    }

    fun build(): DictionaryMetadata = DictionaryMetadata(attrs)

    fun toMap(): Map<DictionaryAttribute, String> = LinkedHashMap(attrs)
}
