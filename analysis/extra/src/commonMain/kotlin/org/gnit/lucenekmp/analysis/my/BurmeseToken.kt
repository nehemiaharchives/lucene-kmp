package org.gnit.lucenekmp.analysis.my

internal data class BurmeseToken(
    val text: String,
    val type: Type,
    val startOffset: Int,
    val endOffset: Int
) {
    enum class Type {
        MYANMAR,
        ALPHANUM
    }
}
