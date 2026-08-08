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
package org.gnit.lucenekmp.queryparser.flexible.messages

import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

/** */
class TestNLS : LuceneTestCase() {
    @Test
    fun testMessageLoading() {
        val invalidSyntax: Message = MessageImpl(MessagesTestBundle.Q0001E_INVALID_SYNTAX, "XXX")
        /*
         * if the default locale is ja, you get ja as a fallback:
         * see ResourceBundle.html#getBundle(java.lang.String, java.util.Locale, java.lang.ClassLoader)
         */
        if (Locale.getDefault().language != "ja")
            assertEquals("Syntax Error: XXX", invalidSyntax.getLocalizedMessage(Locale.ENGLISH))
    }

    @Test
    fun testMessageLoading_ja() {
        val invalidSyntax: Message = MessageImpl(MessagesTestBundle.Q0001E_INVALID_SYNTAX, "XXX")
        assertEquals("構文エラー: XXX", invalidSyntax.getLocalizedMessage(Locale.JAPANESE))
    }

    @Test
    fun testNLSLoading() {
        var message =
            NLS.getLocalizedMessage(
                MessagesTestBundle.Q0004E_INVALID_SYNTAX_ESCAPE_UNICODE_TRUNCATION,
                Locale.ENGLISH,
            )
        /*
         * if the default locale is ja, you get ja as a fallback:
         * see ResourceBundle.html#getBundle(java.lang.String, java.util.Locale, java.lang.ClassLoader)
         */
        if (Locale.getDefault().language != "ja")
            assertEquals("Truncated unicode escape sequence.", message)

        message =
            NLS.getLocalizedMessage(MessagesTestBundle.Q0001E_INVALID_SYNTAX, Locale.ENGLISH, "XXX")
        /*
         * if the default locale is ja, you get ja as a fallback:
         * see ResourceBundle.html#getBundle(java.lang.String, java.util.Locale, java.lang.ClassLoader)
         */
        if (Locale.getDefault().language != "ja") assertEquals("Syntax Error: XXX", message)
    }

    @Test
    fun testNLSLoading_ja() {
        var message =
            NLS.getLocalizedMessage(
                MessagesTestBundle.Q0004E_INVALID_SYNTAX_ESCAPE_UNICODE_TRUNCATION,
                Locale.JAPANESE,
            )
        assertEquals("切り捨てられたユニコード・エスケープ・シーケンス。", message)

        message =
            NLS.getLocalizedMessage(MessagesTestBundle.Q0001E_INVALID_SYNTAX, Locale.JAPANESE, "XXX")
        assertEquals("構文エラー: XXX", message)
    }

    @Test
    fun testNLSLoading_xx_XX() {
        val locale = Locale.Builder().setLanguageTag("xx-XX").build()
        var message =
            NLS.getLocalizedMessage(
                MessagesTestBundle.Q0004E_INVALID_SYNTAX_ESCAPE_UNICODE_TRUNCATION,
                locale,
            )
        /*
         * if the default locale is ja, you get ja as a fallback:
         * see ResourceBundle.html#getBundle(java.lang.String, java.util.Locale, java.lang.ClassLoader)
         */
        if (Locale.getDefault().language != "ja")
            assertEquals("Truncated unicode escape sequence.", message)

        message = NLS.getLocalizedMessage(MessagesTestBundle.Q0001E_INVALID_SYNTAX, locale, "XXX")
        /*
         * if the default locale is ja, you get ja as a fallback:
         * see ResourceBundle.html#getBundle(java.lang.String, java.util.Locale, java.lang.ClassLoader)
         */
        if (Locale.getDefault().language != "ja") assertEquals("Syntax Error: XXX", message)
    }

    @Test
    fun testMissingMessage() {
        val locale = Locale.ENGLISH
        val message =
            NLS.getLocalizedMessage(MessagesTestBundle.Q0005E_MESSAGE_NOT_IN_BUNDLE, locale)

        assertEquals(
            "Message with key:Q0005E_MESSAGE_NOT_IN_BUNDLE and locale: " +
                locale.toLanguageTag() +
                " not found.",
            message,
        )
    }
}
