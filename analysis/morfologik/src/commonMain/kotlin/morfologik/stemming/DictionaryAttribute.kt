package morfologik.stemming

import org.gnit.lucenekmp.jdkport.Charset
import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.jdkport.UnsupportedCharsetException

/**
 * Attributes applying to [Dictionary] and [DictionaryMetadata].
 */
enum class DictionaryAttribute(val propertyName: String) {
    SEPARATOR("fsa.dict.separator") {
        override fun fromString(value: String): Any {
            if (value.length != 1) {
                throw IllegalArgumentException("Attribute $propertyName must be a single character.")
            }
            val charValue = value[0]
            if (charValue.isHighSurrogate() || charValue.isLowSurrogate()) {
                throw IllegalArgumentException(
                    "Field separator character cannot be part of a surrogate pair: $value"
                )
            }
            return charValue
        }
    },

    ENCODING("fsa.dict.encoding") {
        override fun fromString(value: String): Any {
            return charsetForName(value)
        }
    },

    FREQUENCY_INCLUDED("fsa.dict.frequency-included") {
        override fun fromString(value: String): Any = booleanValue(value)
    },

    IGNORE_NUMBERS("fsa.dict.speller.ignore-numbers") {
        override fun fromString(value: String): Any = booleanValue(value)
    },

    IGNORE_PUNCTUATION("fsa.dict.speller.ignore-punctuation") {
        override fun fromString(value: String): Any = booleanValue(value)
    },

    IGNORE_CAMEL_CASE("fsa.dict.speller.ignore-camel-case") {
        override fun fromString(value: String): Any = booleanValue(value)
    },

    IGNORE_ALL_UPPERCASE("fsa.dict.speller.ignore-all-uppercase") {
        override fun fromString(value: String): Any = booleanValue(value)
    },

    IGNORE_DIACRITICS("fsa.dict.speller.ignore-diacritics") {
        override fun fromString(value: String): Any = booleanValue(value)
    },

    CONVERT_CASE("fsa.dict.speller.convert-case") {
        override fun fromString(value: String): Any = booleanValue(value)
    },

    RUN_ON_WORDS("fsa.dict.speller.runon-words") {
        override fun fromString(value: String): Any = booleanValue(value)
    },

    LOCALE("fsa.dict.speller.locale") {
        override fun fromString(value: String): Any = Locale(value)
    },

    ENCODER("fsa.dict.encoder") {
        override fun fromString(value: String): Any {
            try {
                return EncoderType.valueOf(value.trim().uppercase())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Invalid encoder name '${value.trim()}', only these coders are valid: ${EncoderType.values().contentToString()}"
                )
            }
        }
    },

    INPUT_CONVERSION("fsa.dict.input-conversion") {
        override fun fromString(value: String): Any {
            val conversionPairs = LinkedHashMap<String, String>()
            val replacements = value.split(",\\s*".toRegex())
            for (stringPair in replacements) {
                val twoStrings = stringPair.trim().split(" ")
                if (twoStrings.size == 2) {
                    if (!conversionPairs.containsKey(twoStrings[0])) {
                        conversionPairs[twoStrings[0]] = twoStrings[1]
                    } else {
                        throw IllegalArgumentException(
                            "Input conversion cannot specify different values for the same input string: ${twoStrings[0]}"
                        )
                    }
                } else {
                    throw IllegalArgumentException("Attribute $propertyName is not in the proper format: $value")
                }
            }
            return conversionPairs
        }
    },

    OUTPUT_CONVERSION("fsa.dict.output-conversion") {
        override fun fromString(value: String): Any {
            val conversionPairs = LinkedHashMap<String, String>()
            val replacements = value.split(",\\s*".toRegex())
            for (stringPair in replacements) {
                val twoStrings = stringPair.trim().split(" ")
                if (twoStrings.size == 2) {
                    if (!conversionPairs.containsKey(twoStrings[0])) {
                        conversionPairs[twoStrings[0]] = twoStrings[1]
                    } else {
                        throw IllegalArgumentException(
                            "Input conversion cannot specify different values for the same input string: ${twoStrings[0]}"
                        )
                    }
                } else {
                    throw IllegalArgumentException("Attribute $propertyName is not in the proper format: $value")
                }
            }
            return conversionPairs
        }
    },

    REPLACEMENT_PAIRS("fsa.dict.speller.replacement-pairs") {
        override fun fromString(value: String): Any {
            val replacementPairs = LinkedHashMap<String, MutableList<String>>()
            val replacements = value.split(",\\s*".toRegex())
            for (stringPair in replacements) {
                val twoStrings = stringPair.trim().split(" ")
                if (twoStrings.size == 2) {
                    val list = replacementPairs.getOrPut(twoStrings[0]) { mutableListOf() }
                    list.add(twoStrings[1])
                } else {
                    throw IllegalArgumentException("Attribute $propertyName is not in the proper format: $value")
                }
            }
            return LinkedHashMap<String, List<String>>(replacementPairs)
        }
    },

    EQUIVALENT_CHARS("fsa.dict.speller.equivalent-chars") {
        override fun fromString(value: String): Any {
            val equivalentCharacters = LinkedHashMap<Char, MutableList<Char>>()
            val eqChars = value.split(",\\s*".toRegex())
            for (characterPair in eqChars) {
                val twoChars = characterPair.trim().split(" ")
                if (twoChars.size == 2 && twoChars[0].length == 1 && twoChars[1].length == 1) {
                    val fromChar = twoChars[0][0]
                    val toChar = twoChars[1][0]
                    val list = equivalentCharacters.getOrPut(fromChar) { mutableListOf() }
                    list.add(toChar)
                } else {
                    throw IllegalArgumentException("Attribute $propertyName is not in the proper format: $value")
                }
            }
            val out = LinkedHashMap<Char, List<Char>>()
            for ((k, v) in equivalentCharacters) {
                out[k] = v.toList()
            }
            return out
        }
    },

    AUTHOR("fsa.dict.author") {
        override fun fromString(value: String): Any = value
    },

    LICENSE("fsa.dict.license") {
        override fun fromString(value: String): Any = value
    },

    CREATION_DATE("fsa.dict.created") {
        override fun fromString(value: String): Any = value
    };

    abstract fun fromString(value: String): Any

    companion object {
        private fun booleanValue(value: String): Boolean {
            return when (value.trim().lowercase()) {
                "true", "yes", "on" -> true
                "false", "no", "off" -> false
                else -> throw IllegalArgumentException("Not a boolean value: $value")
            }
        }

        private fun charsetForName(value: String): Charset {
            val normalized = value.trim()
            val upper = normalized.uppercase()
            return when {
                upper == "UTF-8" || upper == "UTF8" -> Charset.UTF_8
                upper == "ISO-8859-1" || upper == "ISO_8859_1" -> Charset.ISO_8859_1
                else -> throw UnsupportedCharsetException(normalized)
            }
        }

        private val attrsByPropertyName: Map<String, DictionaryAttribute> = run {
            val map = LinkedHashMap<String, DictionaryAttribute>()
            for (attr in values()) {
                if (map.put(attr.propertyName, attr) != null) {
                    throw RuntimeException("Duplicate property key for: $attr")
                }
            }
            map
        }

        fun fromPropertyName(propertyName: String): DictionaryAttribute {
            return attrsByPropertyName[propertyName]
                ?: throw IllegalArgumentException("No attribute for property: $propertyName")
        }
    }
}
