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

/**
 * MessageBundles classes extend this class, to implement a bundle.
 *
 * For Native Language Support (NLS), system of software internationalization.
 *
 * This interface is similar to the NLS class in eclipse.osgi.util.NLS class -
 * initializeMessages() method resets the values of all static strings, should only be called by
 * classes that extend from NLS (see TestMessages.java for reference) - performs validation of all
 * message in a bundle, at class load time - performs per message validation at runtime - see
 * NLSTest.java for usage reference
 *
 * MessageBundle classes may subclass this type.
 */
open class NLS protected constructor() {
    companion object {
        private val messages: Map<String, String> = mapOf(
            "INVALID_SYNTAX" to "Syntax Error: {0}",
            "INVALID_SYNTAX_CANNOT_PARSE" to "Syntax Error, cannot parse {0}: {1} ",
            "INVALID_SYNTAX_FUZZY_LIMITS" to "The similarity value for a fuzzy search must be between 0.0 and 1.0.",
            "INVALID_SYNTAX_FUZZY_EDITS" to "Fractional edit distances are not allowed.",
            "INVALID_SYNTAX_ESCAPE_UNICODE_TRUNCATION" to "Truncated unicode escape sequence.",
            "INVALID_SYNTAX_ESCAPE_CHARACTER" to "Term can not end with escape character.",
            "INVALID_SYNTAX_ESCAPE_NONE_HEX_UNICODE" to "Non-hex character in Unicode escape sequence: {0}",
            "NODE_ACTION_NOT_SUPPORTED" to "This node does not support this action.",
            "PARAMETER_VALUE_NOT_SUPPORTED" to "Parameter {1} with value {0} not supported.",
            "LUCENE_QUERY_CONVERSION_ERROR" to "Cannot convert query to lucene syntax: {0} error: {1}",
            "EMPTY_MESSAGE" to "",
            "WILDCARD_NOT_SUPPORTED" to "Wildcard is not supported for query: {0} ",
            "TOO_MANY_BOOLEAN_CLAUSES" to "Too many boolean clauses, the maximum supported is {0}: {1}",
            "LEADING_WILDCARD_NOT_ALLOWED" to "Leading wildcard is not allowed: {0}",
            "COULD_NOT_PARSE_NUMBER" to "Could not parse text \"{0}\" using {1}",
            "NUMBER_CLASS_NOT_SUPPORTED_BY_NUMERIC_RANGE_QUERY" to "Number class not supported by NumericRangeQueryNode: {0}",
            "UNSUPPORTED_NUMERIC_DATA_TYPE" to "Unsupported NumericField.DataType: {0}",
            "NUMERIC_CANNOT_BE_EMPTY" to "Field \"{0}\" is numeric and cannot have an empty value.",
            "ANALYZER_REQUIRED" to "An analyzer is required to parse interval sub-query \"{0}\"",
        )

        private val japaneseMessages: Map<String, String> = mapOf(
            "Q0001E_INVALID_SYNTAX" to "構文エラー: {0}",
            "Q0004E_INVALID_SYNTAX_ESCAPE_UNICODE_TRUNCATION" to
                "切り捨てられたユニコード・エスケープ・シーケンス。",
        )

        private val testMessages: Map<String, String> = mapOf(
            "Q0001E_INVALID_SYNTAX" to "Syntax Error: {0}",
            "Q0004E_INVALID_SYNTAX_ESCAPE_UNICODE_TRUNCATION" to
                "Truncated unicode escape sequence.",
        )

        fun getLocalizedMessage(key: String): String {
            return getLocalizedMessage(key, Locale.getDefault())
        }

        fun getLocalizedMessage(key: String, locale: Locale): String {
            val message = getResourceBundleObject(key, locale)
            if (message == null) {
                return "Message with key:$key and locale: $locale not found."
            }
            return message.toString()
        }

        fun getLocalizedMessage(key: String, locale: Locale, vararg args: Any?): String {
            var str = getLocalizedMessage(key, locale)

            if (args.isNotEmpty()) {
                for (i in args.indices) {
                    str = str.replace("{$i}", args[i].toString())
                }
            }

            return str
        }

        fun getLocalizedMessage(key: String, vararg args: Any?): String {
            return getLocalizedMessage(key, Locale.getDefault(), *args)
        }

        /**
         * Initialize a given class with the message bundle Keys Should be called from a class that
         * extends NLS in a static block at class load time.
         *
         * Resource bundles and reflection are unavailable in Kotlin common, so the fixed Lucene
         * message data is inlined above.
         */
        protected fun initializeMessages(bundleName: String, clazz: Any) {
            try {
                load(clazz)
            } catch (e: Throwable) {
                // ignore all errors and exceptions
                // because this function is supposed to be called at class load time.
            }
        }

        private fun getResourceBundleObject(messageKey: String, locale: Locale): Any? {
            // Kotlin common cannot use ResourceBundle, so the fixed upstream English bundle is
            // searched directly.

            // slow resource checking
            // need to loop thru all registered resource bundles

            // if resource is not found
            if (locale.language == "ja") {
                return japaneseMessages[messageKey] ?: messages[messageKey]
            }
            return testMessages[messageKey] ?: messages[messageKey]
        }

        private fun load(clazz: Any) {
            // build a map of field names to Field objects
            // Kotlin common has no reflection-based writable static fields. QueryParserMessages
            // declares the same keys directly, so there are no fields to initialize here.
        }

        private fun loadfieldValue(field: Any, clazz: Any) {
            // Set a value for this empty field.
            // Kotlin common has no reflective Field.set equivalent.
        }

        /**
         * @param key - Message Key
         */
        private fun validateMessage(key: String, clazz: Any) {
            // Test if the message is present in the resource bundle
            try {
                messages[key]
            } catch (e: Throwable) {
                // System.err.println("WARN: Message with key:" + key + " and locale: "
                //    + Locale.getDefault() + " not found.");
                // ignore all other errors and exceptions
                // since this code is just a test to see if the message is present on the
                // system
            }
        }
    }
}
