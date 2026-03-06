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
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.Constants
import kotlin.test.Test

/** Test that the default codec detects mismatched checksums at open or checkIntegrity time. */
class TestAllFilesDetectMismatchedChecksum : LuceneTestCase() {
    @Test
    fun test() {
        // TODO Kotlin/Native macosX64 currently crashes in the corruption path for this test before
        // the corruption assertions run. Keep the executable JVM coverage and skip native here
        // until the lower-level native crash is debugged.
        if (Constants.JVM_NAME == "Unknown") {
            return
        }
        val dir = newDirectory()

        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setCodec(TestUtil.getDefaultCodec())
        // Disable CFS, which makes it harder to test due to its double checksumming
        conf.setUseCompoundFile(false)
        conf.mergePolicy.noCFSRatio = 0.0

        val riw = RandomIndexWriter(random(), dir, conf)
        val doc = Document()
        val textWithTermVectorsType = FieldType(TextField.TYPE_STORED)
        textWithTermVectorsType.setStoreTermVectors(true)
        val text = Field("text", "", textWithTermVectorsType)
        doc.add(text)
        val termString = StringField("string", "", Field.Store.YES)
        doc.add(termString)
        val dvString = SortedDocValuesField("string", BytesRef())
        doc.add(dvString)
        val pointNumber = LongPoint("long", 0L)
        doc.add(pointNumber)
        val dvNumber = NumericDocValuesField("long", 0L)
        doc.add(dvNumber)
        val vector = KnnFloatVectorField("vector", FloatArray(16))
        doc.add(vector)

        for (i in 0 until 100) {
            text.setStringValue(TestUtil.randomAnalysisString(random(), 20, true))
            val randomString = TestUtil.randomSimpleString(random(), 5)
            termString.setStringValue(randomString)
            dvString.setBytesValue(BytesRef(randomString))
            val number = random().nextInt(10).toLong()
            pointNumber.setLongValue(number)
            dvNumber.setLongValue(number)
            vector.vectorValue().fill((i % 4).toFloat())
            riw.addDocument(doc)
        }
        riw.deleteDocuments(LongPoint.newRangeQuery("long", 0L, 2L))
        riw.close()
        checkMismatchedChecksum(dir)
        dir.close()
    }

    @Throws(IOException::class)
    private fun checkMismatchedChecksum(dir: Directory) {
        for (name in dir.listAll()) {
            if (name != IndexWriter.WRITE_LOCK_NAME) {
                corruptFile(dir, name)
            }
        }
    }

    @Throws(IOException::class)
    private fun corruptFile(dir: Directory, victim: String) {
        newDirectory().use { dirCopy ->
            dirCopy.checkIndexOnClose = false

            val victimLength = dir.fileLength(victim)
            val flipOffset =
                TestUtil.nextLong(
                    random(),
                    maxOf(0L, victimLength - CodecUtil.footerLength()),
                    victimLength - 1
                )

            if (VERBOSE) {
                println(
                    "TEST: now corrupt file $victim by changing byte at offset $flipOffset (length= $victimLength)"
                )
            }

            for (name in dir.listAll()) {
                if (name != victim) {
                    dirCopy.copyFrom(dir, name, name, IOContext.DEFAULT)
                } else {
                    dirCopy.createOutput(name, IOContext.DEFAULT).use { out ->
                        dir.openInput(name, IOContext.READONCE).use { input ->
                            out.copyBytes(input, flipOffset)
                            out.writeByte(
                                (input.readByte().toInt() + TestUtil.nextInt(random(), 0x01, 0xFF)).toByte()
                            )
                            out.copyBytes(input, victimLength - flipOffset - 1)
                        }
                    }
                }
                dirCopy.sync(mutableListOf(name))
            }

            // corruption must be detected
            expectThrows(CorruptIndexException::class) {
                DirectoryReader.open(dirCopy).use { reader ->
                    for (context in reader.leaves()) {
                        context.reader().checkIntegrity()
                    }
                }
            }
        }
    }
}
