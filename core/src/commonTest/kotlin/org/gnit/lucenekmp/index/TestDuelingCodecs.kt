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
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.codecs.asserting.AssertingCodec
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import kotlin.random.Random
import kotlin.test.Test

/** Compares one codec against another */
class TestDuelingCodecs : LuceneTestCase() {
    private var leftDir: Directory? = null
    private var leftReader: IndexReader? = null
    private var leftCodec: Codec? = null

    private var rightDir: Directory? = null
    private var rightReader: IndexReader? = null
    private var rightCodec: Codec? = null
    private var leftWriter: RandomIndexWriter? = null
    private var rightWriter: RandomIndexWriter? = null
    private var seed = 0L
    private var info: String = ""

    private fun setUp() {
        // for now it's default vs Asserting(default) because RandomCodec is not yet ported and
        // the core test source set does not depend directly on the codecs module's SimpleTextCodec.
        // this still gives meaningful dueling coverage through different codec wrappers.
        leftCodec = Codec.default
        rightCodec = AssertingCodec()

        leftDir = newFSDirectory(createTempDir("leftDir"))
        rightDir = newFSDirectory(createTempDir("rightDir"))

        seed = random().nextLong()

        // must use same seed because of random payloads, etc
        val maxTermLength = TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH)
        val leftAnalyzer = MockAnalyzer(Random(seed))
        leftAnalyzer.setMaxTokenLength(maxTermLength)
        val rightAnalyzer = MockAnalyzer(Random(seed))
        rightAnalyzer.setMaxTokenLength(maxTermLength)

        // but these can be different
        // TODO: this turns this into a really big test of Multi*, is that what we want?
        val leftConfig = newIndexWriterConfig(leftAnalyzer)
        leftConfig.setCodec(leftCodec!!)
        // preserve docids
        leftConfig.setMergePolicy(newLogMergePolicy())

        val rightConfig = newIndexWriterConfig(rightAnalyzer)
        rightConfig.setCodec(rightCodec!!)
        // preserve docids
        rightConfig.setMergePolicy(newLogMergePolicy())

        // must use same seed because of random docvalues fields, etc
        leftWriter = RandomIndexWriter(Random(seed), leftDir!!, leftConfig)
        rightWriter = RandomIndexWriter(Random(seed), rightDir!!, rightConfig)

        info = "left: $leftCodec / right: $rightCodec"
    }

    private fun tearDown() {
        IOUtils.close(leftWriter, rightWriter, leftReader, rightReader, leftDir, rightDir)
        leftWriter = null
        rightWriter = null
        leftReader = null
        rightReader = null
        leftDir = null
        rightDir = null
    }

    /** populates a writer with random stuff. this must be fully reproducable with the seed! */
    @Throws(IOException::class)
    fun createRandomIndex(numdocs: Int, writer: RandomIndexWriter, seed: Long) {
        val random = Random(seed)
        // primary source for our data is from linefiledocs, it's realistic.
        val lineFileDocs = LineFileDocs(random)

        // TODO: we should add other fields that use things like docs&freqs but omit positions,
        // because linefiledocs doesn't cover all the possibilities.
        for (i in 0..<numdocs) {
            val document = lineFileDocs.nextDoc()
            // grab the title and add some SortedSet instances for fun
            val title = document.get("titleTokenized")!!
            val split = title.split("\\s+".toRegex())
            document.removeFields("sortedset")
            for (trash in split) {
                document.add(SortedSetDocValuesField("sortedset", BytesRef(trash)))
            }
            // add a numeric dv field sometimes
            document.removeFields("sparsenumeric")
            if (random.nextInt(4) == 2) {
                document.add(NumericDocValuesField("sparsenumeric", random.nextInt().toLong()))
            }
            // add sortednumeric sometimes
            document.removeFields("sparsesortednum")
            if (random.nextInt(5) == 1) {
                document.add(SortedNumericDocValuesField("sparsesortednum", random.nextLong()))
                if (random.nextBoolean()) {
                    document.add(SortedNumericDocValuesField("sparsesortednum", random.nextLong()))
                }
            }
            writer.addDocument(document)
        }

        lineFileDocs.close()
    }

    /** checks the two indexes are equivalent */
    // we use a small amount of docs here, so it works with any codec
    @Test
    @Throws(IOException::class)
    fun testEquals() {
        setUp()
        try {
            val numdocs = atLeast(20)
            createRandomIndex(numdocs, leftWriter!!, seed)
            createRandomIndex(numdocs, rightWriter!!, seed)

            leftReader = leftWriter!!.getReader(applyDeletions = true, writeAllDeletes = false)
            rightReader = rightWriter!!.getReader(applyDeletions = true, writeAllDeletes = false)

            assertReaderEquals(info, leftReader!!, rightReader!!)
        } finally {
            tearDown()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testCrazyReaderEquals() {
        setUp()
        try {
            val numdocs = atLeast(20)
            createRandomIndex(numdocs, leftWriter!!, seed)
            createRandomIndex(numdocs, rightWriter!!, seed)

            leftReader = wrapReader(leftWriter!!.getReader(applyDeletions = true, writeAllDeletes = false))
            rightReader = wrapReader(rightWriter!!.getReader(applyDeletions = true, writeAllDeletes = false))

            // check that our readers are valid
            TestUtil.checkReader(leftReader!!)
            TestUtil.checkReader(rightReader!!)

            assertReaderEquals(info, leftReader!!, rightReader!!)
        } finally {
            tearDown()
        }
    }
}
