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
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.jdkport.CharBuffer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.fail

object TestIndexWriter {

    private val storedTextType = FieldType(TextField.TYPE_NOT_STORED)

    @Throws(IOException::class)
    fun addDoc(writer: IndexWriter) {
        val doc = Document()
        doc.add(LuceneTestCase.newTextField("content", "aaa", Field.Store.NO))
        writer.addDocument(doc)
    }

    @Throws(IOException::class)
    fun addDocWithIndex(writer: IndexWriter, index: Int) {
        val doc = Document()
        doc.add(LuceneTestCase.newField("content", "aaa $index", storedTextType))
        doc.add(LuceneTestCase.newField("id", "$index", storedTextType))
        writer.addDocument(doc)
    }

    // TODO: we have the logic in MDW to do this check, and it's better, because it knows about files
    //   it tried to delete but couldn't: we should replace this!!!!
    @Throws(IOException::class)
    fun assertNoUnreferencedFiles(dir: Directory, message: String) {
        val startFiles = dir.listAll()
        IndexWriter(dir, IndexWriterConfig(MockAnalyzer(LuceneTestCase.random()))).rollback()
        val endFiles = dir.listAll()

        Arrays.sort(startFiles)
        Arrays.sort(endFiles)

        if (!startFiles.contentEquals(endFiles)) {
            fail(
                "$message: before delete:\n    " +
                    startFiles.joinToString("\n    ") +
                    "\n  after delete:\n    " +
                    endFiles.joinToString("\n    ")
            )
        }
    }

    internal class StringSplitAnalyzer : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            return TokenStreamComponents(StringSplitTokenizer())
        }
    }

    private class StringSplitTokenizer : Tokenizer() {
        private var tokens: Array<String> = emptyArray()
        private var upto = 0
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

        override fun incrementToken(): Boolean {
            clearAttributes()
            return if (upto < tokens.size) {
                termAtt.setEmpty()
                termAtt.append(tokens[upto])
                upto++
                true
            } else {
                false
            }
        }

        override fun reset() {
            super.reset()
            upto = 0
            val builder = StringBuilder()
            val buffer = CharArray(1024)
            while (true) {
                val n = input.read(CharBuffer.wrap(buffer))
                if (n == -1) {
                    break
                }
                for (i in 0 until n) {
                    builder.append(buffer[i])
                }
            }
            tokens = builder.toString().split(" ").toTypedArray()
        }
    }
}
