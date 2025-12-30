package org.gnit.lucenekmp.analysis.ko

import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.analysis.ko.KoreanTokenizer.DecompoundMode
import org.gnit.lucenekmp.analysis.ko.dict.UserDictionary
import org.gnit.lucenekmp.jdkport.CodingErrorAction
import org.gnit.lucenekmp.jdkport.InputStreamReader
import org.gnit.lucenekmp.util.AttributeFactory
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.ResourceLoaderAware
import okio.IOException
import kotlin.properties.Delegates

/** Factory for [KoreanTokenizer]. */
class KoreanTokenizerFactory : TokenizerFactory, ResourceLoaderAware {
    private var userDictionaryPath: String? = null
    private var userDictionaryEncoding: String? = null
    private var userDictionary: UserDictionary? = null

    private lateinit var mode: DecompoundMode
    private var outputUnknownUnigrams by Delegates.notNull<Boolean>()
    private var discardPunctuation by Delegates.notNull<Boolean>()

    constructor(args: MutableMap<String, String>) : super(args) {
        userDictionaryPath = args.remove(USER_DICT_PATH)
        userDictionaryEncoding = args.remove(USER_DICT_ENCODING)
        mode =
            DecompoundMode.valueOf(
                get(args, DECOMPOUND_MODE, KoreanTokenizer.DEFAULT_DECOMPOUND.toString()).uppercase()
            )
        outputUnknownUnigrams = getBoolean(args, OUTPUT_UNKNOWN_UNIGRAMS, false)
        discardPunctuation = getBoolean(args, DISCARD_PUNCTUATION, true)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    constructor() {
        throw defaultCtorException()
    }

    @Throws(IOException::class)
    override fun inform(loader: ResourceLoader) {
        if (userDictionaryPath != null) {
            loader.openResource(userDictionaryPath!!).use { stream ->
                var encoding = userDictionaryEncoding
                if (encoding == null) {
                    encoding = IOUtils.UTF_8
                }
                val decoder =
                    org.gnit.lucenekmp.jdkport.Charset.forName(encoding)
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                val reader = InputStreamReader(stream, decoder)
                userDictionary = UserDictionary.open(reader)
            }
        } else {
            userDictionary = null
        }
    }

    override fun create(factory: AttributeFactory): KoreanTokenizer {
        return KoreanTokenizer(factory, userDictionary, mode, outputUnknownUnigrams, discardPunctuation)
    }

    companion object {
        const val NAME: String = "korean"
        private const val USER_DICT_PATH = "userDictionary"
        private const val USER_DICT_ENCODING = "userDictionaryEncoding"
        private const val DECOMPOUND_MODE = "decompoundMode"
        private const val OUTPUT_UNKNOWN_UNIGRAMS = "outputUnknownUnigrams"
        private const val DISCARD_PUNCTUATION = "discardPunctuation"
    }
}
