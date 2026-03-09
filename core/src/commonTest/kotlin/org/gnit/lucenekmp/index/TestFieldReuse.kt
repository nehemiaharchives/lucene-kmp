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
package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.standard.StandardAnalyzer
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.InvertableType
import org.gnit.lucenekmp.document.StoredValue
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** test tokenstream reuse by DefaultIndexingChain */
class TestFieldReuse : BaseTokenStreamTestCase() {
    @Test
    fun testStringField() {
        val analyzer = StandardAnalyzer()
        var stringField = Field("foo", "bar", StringField.TYPE_NOT_STORED)

        // passing null
        var ts = stringField.tokenStream(analyzer, null)
        assertNotNull(ts)
        assertTokenStreamContents(ts, arrayOf("bar"), intArrayOf(0), intArrayOf(3))

        // now reuse previous stream
        stringField = Field("foo", "baz", StringField.TYPE_NOT_STORED)
        val ts2 = stringField.tokenStream(analyzer, ts)
        assertSame(ts, ts2)
        assertNotNull(ts)
        assertTokenStreamContents(ts, arrayOf("baz"), intArrayOf(0), intArrayOf(3))

        // pass a bogus stream and ensure it's still ok
        stringField = Field("foo", "beer", StringField.TYPE_NOT_STORED)
        val bogus: TokenStream = CannedTokenStream()
        ts = stringField.tokenStream(analyzer, bogus)
        assertNotSame(ts, bogus)
        assertNotNull(ts)
        assertTokenStreamContents(ts, arrayOf("beer"), intArrayOf(0), intArrayOf(4))
    }

    internal class MyField : IndexableField {
        var lastSeen: TokenStream? = null
        var lastReturned: TokenStream? = null

        override fun name(): String {
            return "foo"
        }

        override fun fieldType(): IndexableFieldType {
            return StringField.TYPE_NOT_STORED
        }

        override fun tokenStream(analyzer: Analyzer, reuse: TokenStream?): TokenStream {
            lastSeen = reuse
            return CannedTokenStream(Token("unimportant", 0, 10)).also { lastReturned = it }
        }

        override fun binaryValue(): BytesRef? {
            return null
        }

        override fun stringValue(): String? {
            return null
        }

        override val charSequenceValue: CharSequence?
            get() = null

        override fun readerValue(): Reader? {
            return null
        }

        override fun numericValue(): Number? {
            return null
        }

        override fun storedValue(): StoredValue? {
            return null
        }

        override fun invertableType(): InvertableType {
            return InvertableType.TOKEN_STREAM
        }
    }

    @Test
    fun testIndexWriterActuallyReuses() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig()
        val iw = IndexWriter(dir, iwc)
        val field1 = MyField()
        iw.addDocument(listOf(field1))
        val previous = field1.lastReturned
        assertNotNull(previous)

        val field2 = MyField()
        iw.addDocument(listOf(field2))
        assertSame(previous, field2.lastSeen)
        iw.close()
        dir.close()
    }
}
