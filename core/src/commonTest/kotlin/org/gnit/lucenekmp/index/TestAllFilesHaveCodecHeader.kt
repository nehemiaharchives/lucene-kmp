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
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Test that a plain default puts codec headers in all files */
class TestAllFilesHaveCodecHeader : LuceneTestCase() {
    @Test
    fun test() {
        val dir = newDirectory()

        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setCodec(TestUtil.getDefaultCodec())
        val riw = RandomIndexWriter(random(), dir, conf)
        // Use LineFileDocs so we (hopefully) get most Lucene features
        // tested, e.g. IntPoint was recently added to it:
        val docs = LineFileDocs(random())
        for (i in 0 until 100) {
            riw.addDocument(docs.nextDoc())
            if (random().nextInt(7) == 0) {
                riw.commit()
            }
            if (random().nextInt(20) == 0) {
                riw.deleteDocuments(Term("docid", i.toString()))
            }
            if (random().nextInt(15) == 0) {
                riw.updateNumericDocValue(Term("docid", i.toString()), "page_views", i.toLong())
            }
        }
        riw.close()
        checkHeaders(dir, HashMap())
        dir.close()
    }

    @Throws(IOException::class)
    private fun checkHeaders(dir: Directory, namesToExtensions: MutableMap<String, String>) {
        val sis = SegmentInfos.readLatestCommit(dir)
        checkHeader(dir, requireNotNull(sis.segmentsFileName), namesToExtensions, sis.getId())

        for (si in sis) {
            assertNotNull(si.info.getId())
            for (file in si.files()) {
                checkHeader(dir, file, namesToExtensions, si.info.getId())
            }
            if (si.info.useCompoundFile) {
                si.info.codec.compoundFormat().getCompoundReader(dir, si.info).use { cfsDir ->
                    for (cfsFile in cfsDir.listAll()) {
                        checkHeader(cfsDir, cfsFile, namesToExtensions, si.info.getId())
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun checkHeader(
        dir: Directory,
        file: String,
        namesToExtensions: MutableMap<String, String>,
        id: ByteArray,
    ) {
        dir.openInput(file, IOContext.READONCE).use { input ->
            val `val` = CodecUtil.readBEInt(input)
            assertEquals(
                CodecUtil.CODEC_MAGIC,
                `val`,
                "$file has no codec header, instead found: $`val`",
            )
            val codecName = input.readString()
            assertFalse(codecName.isEmpty())
            var extension = IndexFileNames.getExtension(file)
            if (extension == null) {
                assertTrue(file.startsWith(IndexFileNames.SEGMENTS))
                extension = "<segments> (not a real extension, designates segments file)"
            }
            val previous = namesToExtensions.put(codecName, extension)
            if (previous != null && previous != extension) {
                fail("extensions $previous and $extension share same codecName $codecName")
            }
            // read version
            input.readInt()
            // read object id
            CodecUtil.checkIndexHeaderID(input, id)
        }
    }
}
