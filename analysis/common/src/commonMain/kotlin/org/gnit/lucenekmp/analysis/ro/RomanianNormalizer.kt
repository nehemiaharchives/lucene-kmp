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
package org.gnit.lucenekmp.analysis.ro

/**
 * Normalizer for Romanian.
 *
 * Cedilla forms are normalized to forms with comma.
 */
class RomanianNormalizer {
    /**
     * Normalize an input buffer of Romanian text
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     */
    fun normalize(s: CharArray, len: Int): Int {
        for (i in 0 until len) {
            when (s[i]) {
                CAPITAL_S_WITH_CEDILLA -> s[i] = CAPITAL_S_WITH_COMMA_BELOW
                SMALL_S_WITH_CEDILLA -> s[i] = SMALL_S_WITH_COMMA_BELOW
                CAPITAL_T_WITH_CEDILLA -> s[i] = CAPITAL_T_WITH_COMMA_BELOW
                SMALL_T_WITH_CEDILLA -> s[i] = SMALL_T_WITH_COMMA_BELOW
            }
        }
        return len
    }

    companion object {
        const val CAPITAL_S_WITH_COMMA_BELOW = '\u0218'
        const val SMALL_S_WITH_COMMA_BELOW = '\u0219'
        const val CAPITAL_T_WITH_COMMA_BELOW = '\u021A'
        const val SMALL_T_WITH_COMMA_BELOW = '\u021B'

        const val CAPITAL_S_WITH_CEDILLA = '\u015E'
        const val SMALL_S_WITH_CEDILLA = '\u015F'
        const val CAPITAL_T_WITH_CEDILLA = '\u0162'
        const val SMALL_T_WITH_CEDILLA = '\u0163'
    }
}
