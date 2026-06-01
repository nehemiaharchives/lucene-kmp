package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.FlagsAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute

/**
 * Adds the [TypeAttribute.type] as a synonym, i.e. another token at the same position,
 * optionally with a specified prefix prepended, optionally transfering flags, and optionally
 * ignoring some types. See [TypeAsSynonymFilterFactory] for full details.
 */
class TypeAsSynonymFilter : TokenFilter {
    private val termAtt = addAttribute(CharTermAttribute::class)
    private val typeAtt = addAttribute(TypeAttribute::class)
    private val posIncrAtt = addAttribute(PositionIncrementAttribute::class)
    private val flagsAtt = addAttribute(FlagsAttribute::class)
    private val prefix: String?
    private val ignore: Set<String>?
    private val synFlagsMask: Int

    private var savedToken: State? = null

    constructor(input: TokenStream) : this(input, null, null, 0.inv())

    /**
     * @param input input tokenstream
     * @param prefix Prepend this string to every token type emitted as token text. If null, nothing
     * will be prepended.
     */
    constructor(input: TokenStream, prefix: String?) : this(input, prefix, null, 0.inv())

    /**
     * @param input input tokenstream
     * @param prefix Prepend this string to every token type emitted as token text. If null, nothing
     * will be prepended.
     * @param ignore types to ignore (and not convert to a synonym)
     * @param synFlagsMask a mask to control what flags are propagated to the synonym.
     */
    constructor(input: TokenStream, prefix: String?, ignore: Set<String>?, synFlagsMask: Int) : super(input) {
        this.prefix = prefix
        this.ignore = ignore
        this.synFlagsMask = synFlagsMask
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (savedToken != null) { // Emit last token's type at the same position
            restoreState(savedToken)
            savedToken = null
            termAtt.setEmpty()
            if (prefix != null) {
                termAtt.append(prefix)
            }
            termAtt.append(typeAtt.type())
            posIncrAtt.setPositionIncrement(0)
            // control what flags transfer to synonym
            flagsAtt.flags = flagsAtt.flags and synFlagsMask
            return true
        } else if (input.incrementToken()) { // No pending token type to emit
            val type = typeAtt.type()
            if (ignore == null || !ignore.contains(type)) {
                savedToken = captureState()
            }
            return true
        }
        return false
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        savedToken = null
    }
}
