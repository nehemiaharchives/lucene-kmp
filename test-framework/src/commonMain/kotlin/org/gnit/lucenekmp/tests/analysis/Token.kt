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

import kotlin.reflect.KClass
import org.gnit.lucenekmp.analysis.tokenattributes.FlagsAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PackedTokenAttributeImpl
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermFrequencyAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermToBytesRefAttribute
import org.gnit.lucenekmp.util.Attribute
import org.gnit.lucenekmp.util.AttributeFactory
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.BytesRef

/**
 * A Token is an occurrence of a term from the text of a field. It consists of the
 * term's text, start and end offsets, and optionally flags and payload.
 */
class Token() : PackedTokenAttributeImpl(), FlagsAttribute, PayloadAttribute {
    override var flags: Int = 0
    override var payload: BytesRef? = null

    constructor(text: CharSequence, start: Int, end: Int) : this() {
        append(text)
        setOffset(start, end)
    }

    constructor(text: CharSequence, posInc: Int, start: Int, end: Int) : this(text, start, end) {
        setPositionIncrement(posInc)
    }

    constructor(text: CharSequence, posInc: Int, start: Int, end: Int, posLength: Int) :
        this(text, posInc, start, end) {
        positionLength = posLength
    }

    override fun clear() {
        super.clear()
        flags = 0
        payload = null
    }

    override fun clone(): Token {
        val t = super.clone() as Token
        if (payload != null) {
            t.payload = BytesRef.deepCopyOf(payload!!)
        }
        return t
    }

    /**
     * Copy the prototype token's fields into this one. Payloads are shared.
     */
    fun reinit(prototype: Token) {
        prototype.copyToWithoutPayloadClone(this)
    }

    private fun copyToWithoutPayloadClone(target: AttributeImpl) {
        super.copyTo(target)
        (target as FlagsAttribute).flags = flags
        (target as PayloadAttribute).payload = payload
    }

    override fun copyTo(target: AttributeImpl) {
        super.copyTo(target)
        (target as FlagsAttribute).flags = flags
        (target as PayloadAttribute).payload = payload?.let { BytesRef.deepCopyOf(it) }
    }

    override fun reflectWith(reflector: AttributeReflector) {
        super.reflectWith(reflector)
        reflector.reflect(FlagsAttribute::class, "flags", flags)
        reflector.reflect(PayloadAttribute::class, "payload", payload)
    }

    companion object {
        init {
            AttributeSource.registerAttributeInterfaces(
                Token::class,
                arrayOf(
                    FlagsAttribute::class,
                    PayloadAttribute::class,
                    CharTermAttribute::class,
                    TypeAttribute::class,
                    PositionIncrementAttribute::class,
                    PositionLengthAttribute::class,
                    OffsetAttribute::class,
                    TermFrequencyAttribute::class,
                    TermToBytesRefAttribute::class
                )
            )
        }

        val TOKEN_ATTRIBUTE_FACTORY: AttributeFactory =
            object : AttributeFactory.StaticImplementationAttributeFactory<Token>(
                AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY,
                Token::class
            ) {
                override fun createInstance(): Token = Token()
            }
    }
}
