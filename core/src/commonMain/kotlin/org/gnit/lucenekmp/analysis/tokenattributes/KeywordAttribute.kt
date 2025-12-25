package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.util.Attribute

/**
 * This attribute can be used to mark a token as a keyword. Keyword aware [TokenStream]s can
 * decide to modify a token based on the return value of [.isKeyword] if the token is
 * modified. Stemming filters for instance can use this attribute to conditionally skip a term if
 * [.isKeyword] returns `true`.
 */
interface KeywordAttribute : Attribute {

    /**
     * Marks the current token as keyword if set to `true`.
     *
     * @param isKeyword `true` if the current token is a keyword, otherwise `false
    ` * .
     * @see .isKeyword
     */
    var isKeyword: Boolean

    /**
     * Returns `true` if the current token is a keyword, otherwise `false`
     *
     * @return `true` if the current token is a keyword, otherwise `false`
     * @see .setKeyword
     */
    /*fun setKeyword(isKeyword: Boolean){
        this.isKeyword = isKeyword
    }*/
}
