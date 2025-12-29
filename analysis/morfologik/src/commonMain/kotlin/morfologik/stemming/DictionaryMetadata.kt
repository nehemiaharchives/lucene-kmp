package morfologik.stemming

import okio.Path
import okio.Path.Companion.toPath
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.CharBuffer
import org.gnit.lucenekmp.jdkport.CharacterCodingException
import org.gnit.lucenekmp.jdkport.Charset
import org.gnit.lucenekmp.jdkport.CharsetDecoder
import org.gnit.lucenekmp.jdkport.CharsetEncoder
import org.gnit.lucenekmp.jdkport.CodingErrorAction
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.jdkport.InputStreamReader
import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.jdkport.UnsupportedCharsetException
import org.gnit.lucenekmp.jdkport.Writer

/**
 * Description of attributes, their types and default values.
 */
class DictionaryMetadata(attrs: Map<DictionaryAttribute, String>) {
    private val boolAttributes: MutableMap<DictionaryAttribute, Boolean> = LinkedHashMap()
    private val attributes: MutableMap<DictionaryAttribute, String> = LinkedHashMap()

    private var separator: Byte = 0
    private var separatorChar: Char = '\u0000'
    private var encoding: String = ""
    private var charset: Charset = Charset.UTF_8
    private var locale: Locale = Locale.getDefault()
    private var replacementPairs: LinkedHashMap<String, List<String>> = LinkedHashMap()
    private var inputConversion: LinkedHashMap<String, String> = LinkedHashMap()
    private var outputConversion: LinkedHashMap<String, String> = LinkedHashMap()
    private var equivalentChars: LinkedHashMap<Char, List<Char>> = LinkedHashMap()
    private var encoderType: EncoderType = EncoderType.SUFFIX

    init {
        attributes.putAll(attrs)

        val attributeMap = LinkedHashMap(DEFAULT_ATTRIBUTES)
        attributeMap.putAll(attrs)

        val requiredAttributes = REQUIRED_ATTRIBUTES.toMutableSet()

        for ((key, value) in attributeMap) {
            requiredAttributes.remove(key)
            val converted = key.fromString(value)
            when (key) {
                DictionaryAttribute.ENCODING -> {
                    encoding = value
                    charset = converted as? Charset
                        ?: throw IllegalArgumentException("Invalid encoding: $value")
                }
                DictionaryAttribute.SEPARATOR -> {
                    separatorChar = converted as Char
                }
                DictionaryAttribute.LOCALE -> {
                    locale = converted as Locale
                }
                DictionaryAttribute.ENCODER -> {
                    encoderType = converted as EncoderType
                }
                DictionaryAttribute.INPUT_CONVERSION -> {
                    @Suppress("UNCHECKED_CAST")
                    inputConversion = converted as LinkedHashMap<String, String>
                }
                DictionaryAttribute.OUTPUT_CONVERSION -> {
                    @Suppress("UNCHECKED_CAST")
                    outputConversion = converted as LinkedHashMap<String, String>
                }
                DictionaryAttribute.REPLACEMENT_PAIRS -> {
                    @Suppress("UNCHECKED_CAST")
                    replacementPairs = converted as LinkedHashMap<String, List<String>>
                }
                DictionaryAttribute.EQUIVALENT_CHARS -> {
                    @Suppress("UNCHECKED_CAST")
                    equivalentChars = converted as LinkedHashMap<Char, List<Char>>
                }
                DictionaryAttribute.IGNORE_PUNCTUATION,
                DictionaryAttribute.IGNORE_NUMBERS,
                DictionaryAttribute.IGNORE_CAMEL_CASE,
                DictionaryAttribute.IGNORE_ALL_UPPERCASE,
                DictionaryAttribute.IGNORE_DIACRITICS,
                DictionaryAttribute.CONVERT_CASE,
                DictionaryAttribute.RUN_ON_WORDS,
                DictionaryAttribute.FREQUENCY_INCLUDED -> {
                    boolAttributes[key] = converted as Boolean
                }
                DictionaryAttribute.AUTHOR,
                DictionaryAttribute.LICENSE,
                DictionaryAttribute.CREATION_DATE -> {
                    key.fromString(value)
                }
            }
        }

        if (requiredAttributes.isNotEmpty()) {
            throw IllegalArgumentException(
                "At least one the required attributes was not provided: $requiredAttributes"
            )
        }

        val encoder = getEncoder()
        try {
            val encoded = encoder.encode(CharBuffer.wrap(charArrayOf(separatorChar)))
            if (encoded.remaining() > 1) {
                throw IllegalArgumentException(
                    "Separator character is not a single byte in encoding $encoding: $separatorChar"
                )
            }
            separator = encoded.get()
        } catch (e: CharacterCodingException) {
            throw IllegalArgumentException(
                "Separator character cannot be converted to a byte in $encoding: $separatorChar",
                e
            )
        }
    }

    fun getAttributes(): Map<DictionaryAttribute, String> = LinkedHashMap(attributes)

    fun getEncoding(): String = encoding
    fun getSeparator(): Byte = separator
    fun getLocale(): Locale = locale

    fun getInputConversionPairs(): LinkedHashMap<String, String> = inputConversion
    fun getOutputConversionPairs(): LinkedHashMap<String, String> = outputConversion
    fun getReplacementPairs(): LinkedHashMap<String, List<String>> = replacementPairs
    fun getEquivalentChars(): LinkedHashMap<Char, List<Char>> = equivalentChars

    fun isFrequencyIncluded(): Boolean = boolAttributes[DictionaryAttribute.FREQUENCY_INCLUDED] == true
    fun isIgnoringPunctuation(): Boolean = boolAttributes[DictionaryAttribute.IGNORE_PUNCTUATION] == true
    fun isIgnoringNumbers(): Boolean = boolAttributes[DictionaryAttribute.IGNORE_NUMBERS] == true
    fun isIgnoringCamelCase(): Boolean = boolAttributes[DictionaryAttribute.IGNORE_CAMEL_CASE] == true
    fun isIgnoringAllUppercase(): Boolean = boolAttributes[DictionaryAttribute.IGNORE_ALL_UPPERCASE] == true
    fun isIgnoringDiacritics(): Boolean = boolAttributes[DictionaryAttribute.IGNORE_DIACRITICS] == true
    fun isConvertingCase(): Boolean = boolAttributes[DictionaryAttribute.CONVERT_CASE] == true
    fun isSupportingRunOnWords(): Boolean = boolAttributes[DictionaryAttribute.RUN_ON_WORDS] == true

    fun getDecoder(): CharsetDecoder {
        try {
            return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        } catch (e: UnsupportedCharsetException) {
            throw RuntimeException("FSA's encoding charset is not supported: $encoding")
        }
    }

    fun getEncoder(): CharsetEncoder {
        try {
            return charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        } catch (e: UnsupportedCharsetException) {
            throw RuntimeException("FSA's encoding charset is not supported: $encoding")
        }
    }

    fun getSequenceEncoderType(): EncoderType = encoderType

    fun getSequenceEncoderPrefixBytes(): Int = encoderType.prefixBytes

    fun getSeparatorAsChar(): Char = separatorChar

    @Throws(okio.IOException::class)
    fun write(writer: Writer) {
        writer.write("# ${this::class.qualifiedName}\n")
        for ((key, value) in getAttributes()) {
            writer.write("${key.propertyName}=$value\n")
        }
    }

    companion object {
        private val DEFAULT_ATTRIBUTES: Map<DictionaryAttribute, String> = DictionaryMetadataBuilder()
            .frequencyIncluded(false)
            .ignorePunctuation()
            .ignoreNumbers()
            .ignoreCamelCase()
            .ignoreAllUppercase()
            .ignoreDiacritics()
            .convertCase()
            .supportRunOnWords()
            .toMap()

        private val REQUIRED_ATTRIBUTES: Set<DictionaryAttribute> = setOf(
            DictionaryAttribute.SEPARATOR,
            DictionaryAttribute.ENCODER,
            DictionaryAttribute.ENCODING
        )

        const val METADATA_FILE_EXTENSION: String = "info"

        fun builder(): DictionaryMetadataBuilder = DictionaryMetadataBuilder()

        fun getExpectedMetadataFileName(dictionaryFile: String): String {
            val dotIndex = dictionaryFile.lastIndexOf('.')
            return if (dotIndex >= 0) {
                dictionaryFile.substring(0, dotIndex) + "." + METADATA_FILE_EXTENSION
            } else {
                dictionaryFile + "." + METADATA_FILE_EXTENSION
            }
        }

        fun getExpectedMetadataLocation(dictionary: Path): Path {
            val fileName = dictionary.name
            val expectedName = getExpectedMetadataFileName(fileName)
            val parent = dictionary.parent
            return if (parent != null) parent.resolve(expectedName) else expectedName.toPath()
        }

        @Throws(okio.IOException::class)
        fun read(metadataStream: InputStream): DictionaryMetadata {
            val map = LinkedHashMap<DictionaryAttribute, String>()
            val properties = loadProperties(metadataStream)

            if (!properties.containsKey(DictionaryAttribute.ENCODER.propertyName)) {
                val hasDeprecated = properties.containsKey("fsa.dict.uses-suffixes") ||
                    properties.containsKey("fsa.dict.uses-infixes") ||
                    properties.containsKey("fsa.dict.uses-prefixes")

                val usesSuffixes = properties["fsa.dict.uses-suffixes"]?.trim()?.lowercase() == "true"
                val usesPrefixes = properties["fsa.dict.uses-prefixes"]?.trim()?.lowercase() == "true"
                val usesInfixes = properties["fsa.dict.uses-infixes"]?.trim()?.lowercase() == "true"

                val encoder = when {
                    usesInfixes -> EncoderType.INFIX
                    usesPrefixes -> EncoderType.PREFIX
                    usesSuffixes -> EncoderType.SUFFIX
                    else -> EncoderType.NONE
                }

                if (!hasDeprecated) {
                    throw okio.IOException(
                        "Use an explicit ${DictionaryAttribute.ENCODER.propertyName}=${encoder.name} metadata key: "
                    )
                }

                throw okio.IOException(
                    "Deprecated encoder keys in metadata. Use ${DictionaryAttribute.ENCODER.propertyName}=${encoder.name}"
                )
            }

            for ((key, value) in properties) {
                map[DictionaryAttribute.fromPropertyName(key)] = value
            }

            return DictionaryMetadata(map)
        }

        private fun loadProperties(stream: InputStream): Map<String, String> {
            val reader = InputStreamReader(stream, Charset.UTF_8)
            val text = readAll(reader)
            val result = LinkedHashMap<String, String>()
            for (line in text.lines()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                    continue
                }
                val idxEq = trimmed.indexOf('=')
                val idxColon = trimmed.indexOf(':')
                val idx = when {
                    idxEq >= 0 && idxColon >= 0 -> minOf(idxEq, idxColon)
                    idxEq >= 0 -> idxEq
                    else -> idxColon
                }
                if (idx <= 0) continue
                val key = trimmed.substring(0, idx).trim()
                val value = trimmed.substring(idx + 1).trim()
                result[key] = value
            }
            return result
        }

        private fun readAll(reader: InputStreamReader): String {
            val sb = StringBuilder()
            val buf = CharArray(4096)
            while (true) {
                val n = reader.read(buf, 0, buf.size)
                if (n <= 0) break
                sb.append(buf.concatToString(0, n))
            }
            return sb.toString()
        }
    }
}
