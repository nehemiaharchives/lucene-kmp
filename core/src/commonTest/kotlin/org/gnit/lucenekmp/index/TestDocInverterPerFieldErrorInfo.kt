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

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.PrintStream
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.PrintStreamInfoStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Test adding to the info stream when there's an exception thrown during field analysis. */
class TestDocInverterPerFieldErrorInfo : LuceneTestCase() {
    companion object {
        private val storedTextType = FieldType(TextField.TYPE_NOT_STORED)
    }

    private class BadNews(message: String) : RuntimeException(message)

    private class ThrowingAnalyzer : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            val tokenizer: Tokenizer = MockTokenizer()
            return if (fieldName == "distinctiveFieldName") {
                val tosser =
                    object : TokenFilter(tokenizer) {
                        @Throws(IOException::class)
                        override fun incrementToken(): Boolean {
                            throw BadNews("Something is icky.")
                        }
                    }
                TokenStreamComponents(tokenizer, tosser)
            } else {
                TokenStreamComponents(tokenizer)
            }
        }
    }

    @Test
    fun testInfoStreamGetsFieldName() {
        val dir: Directory = newDirectory()
        val c = IndexWriterConfig(ThrowingAnalyzer())
        val infoBytes = ByteArrayOutputStream()
        val infoPrintStream = PrintStream(infoBytes, true, StandardCharsets.UTF_8)
        val printStreamInfoStream = PrintStreamInfoStream(infoPrintStream)
        c.setInfoStream(printStreamInfoStream)
        val writer = IndexWriter(dir, c)
        val doc = Document()
        doc.add(newField("distinctiveFieldName", "aaa ", storedTextType))
        expectThrows(BadNews::class) { writer.addDocument(doc) }
        infoPrintStream.flush()
        val infoStream = infoBytes.toString(StandardCharsets.UTF_8)
        assertTrue(infoStream.contains("distinctiveFieldName"))

        writer.close()
        dir.close()
    }

    @Test
    fun testNoExtraNoise() {
        val dir: Directory = newDirectory()
        val c = IndexWriterConfig(ThrowingAnalyzer())
        val infoBytes = ByteArrayOutputStream()
        val infoPrintStream = PrintStream(infoBytes, true, StandardCharsets.UTF_8)
        val printStreamInfoStream = PrintStreamInfoStream(infoPrintStream)
        c.setInfoStream(printStreamInfoStream)
        val writer = IndexWriter(dir, c)
        val doc = Document()
        doc.add(newField("boringFieldName", "aaa ", storedTextType))
        // should not throw BadNews
        writer.addDocument(doc)
        infoPrintStream.flush()
        val infoStream = infoBytes.toString(StandardCharsets.UTF_8)
        assertFalse(infoStream.contains("boringFieldName"))

        writer.close()
        dir.close()
    }
}
