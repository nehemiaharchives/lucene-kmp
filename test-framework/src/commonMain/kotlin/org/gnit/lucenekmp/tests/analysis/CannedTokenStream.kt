/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.tests.analysis

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute

/** TokenStream from a canned list of Tokens. */
class CannedTokenStream : TokenStream {
    private val tokens: Array<out Token>
    private var upto = 0
    private val offsetAtt: OffsetAttribute
    private val posIncrAtt: PositionIncrementAttribute
    private val finalOffset: Int
    private val finalPosInc: Int
    private val tokenAtt: Token

    constructor(vararg tokens: Token) : this(0, 0, *tokens)

    /** If you want trailing holes, pass a non-zero finalPosInc. */
    constructor(finalPosInc: Int, finalOffset: Int, vararg tokens: Token) :
        super(Token.TOKEN_ATTRIBUTE_FACTORY) {
        this.tokens = tokens
        this.finalOffset = finalOffset
        this.finalPosInc = finalPosInc
        offsetAtt = addAttribute(OffsetAttribute::class)
        posIncrAtt = addAttribute(PositionIncrementAttribute::class)
        tokenAtt = offsetAtt as Token
    }

    override fun end() {
        super.end()
        posIncrAtt.setPositionIncrement(finalPosInc)
        offsetAtt.setOffset(finalOffset, finalOffset)
    }

    override fun reset() {
        super.reset()
        upto = 0
    }

    override fun incrementToken(): Boolean {
        return if (upto < tokens.size) {
            clearAttributes()
            tokens[upto++].copyTo(tokenAtt)
            true
        } else {
            false
        }
    }
}

