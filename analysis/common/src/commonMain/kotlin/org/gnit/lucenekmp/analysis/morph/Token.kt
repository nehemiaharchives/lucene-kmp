package org.gnit.lucenekmp.analysis.morph

import org.gnit.lucenekmp.jdkport.fromCharArray

/** Analyzed token with morphological data. */
abstract class Token(
    val surfaceForm: CharArray,
    val offset: Int,
    val length: Int,
    val startOffset: Int,
    val endOffset: Int,
    val type: TokenType
) {
    var positionIncrement: Int = 1
    var positionLength: Int = 1

    val surfaceFormString: String
        get() = String.fromCharArray(surfaceForm, offset, length)
}
