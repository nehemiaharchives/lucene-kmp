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
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.DocHelper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestPerSegmentDeletes : LuceneTestCase() {
    @Test
    @Throws(Exception::class)
    fun testDeletes1() {
        // IndexWriter.debug2 = System.out;
        val dir: Directory =
            MockDirectoryWrapper(Random(random().nextLong()), ByteBuffersDirectory())
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setMergeScheduler(SerialMergeScheduler())
        iwc.setMaxBufferedDocs(5000)
        iwc.setRAMBufferSizeMB(100.0)
        var fsmp = RangeMergePolicy(false)
        iwc.setMergePolicy(fsmp)
        val writer = IndexWriter(dir, iwc)
        for (x in 0..<5) {
            writer.addDocument(DocHelper.createDocument(x, "1", 2))
            // System.out.println("numRamDocs(" + x + ")" + writer.numRamDocs());
        }
        // System.out.println("commit1");
        writer.commit()
        assertEquals(1, writer.cloneSegmentInfos().size())
        for (x in 5..<10) {
            writer.addDocument(DocHelper.createDocument(x, "2", 2))
            // System.out.println("numRamDocs(" + x + ")" + writer.numRamDocs());
        }
        // System.out.println("commit2");
        writer.commit()
        assertEquals(2, writer.cloneSegmentInfos().size())

        for (x in 10..<15) {
            writer.addDocument(DocHelper.createDocument(x, "3", 2))
            // System.out.println("numRamDocs(" + x + ")" + writer.numRamDocs());
        }

        writer.deleteDocuments(Term("id", "1"))

        writer.deleteDocuments(Term("id", "11"))

        writer.flush(false, false)

        // deletes are now resolved on flush, so there shouldn't be
        // any deletes after flush
        assertFalse(writer.hasChangesInRam())

        // get reader flushes pending deletes
        // so there should not be anymore
        val r1 = DirectoryReader.open(writer)
        assertFalse(writer.hasChangesInRam())
        r1.close()

        // delete id:2 from the first segment
        // merge segments 0 and 1
        // which should apply the delete id:2
        writer.deleteDocuments(Term("id", "2"))
        writer.flush(false, false)
        fsmp = writer.config.mergePolicy as RangeMergePolicy
        fsmp.doMerge = true
        fsmp.start = 0
        fsmp.length = 2
        writer.maybeMerge()

        assertEquals(2, writer.cloneSegmentInfos().size())

        // id:2 shouldn't exist anymore because
        // it's been applied in the merge and now it's gone
        val r2 = DirectoryReader.open(writer)
        val id2docs = toDocsArray(Term("id", "2"), null, r2)
        assertTrue(id2docs == null)
        r2.close()

        /*
        // added docs are in the ram buffer
        for (int x = 15; x < 20; x++) {
          writer.addDocument(TestIndexWriterReader.createDocument(x, "4", 2));
          System.out.println("numRamDocs(" + x + ")" + writer.numRamDocs());
        }
        assertTrue(writer.numRamDocs() > 0);
        // delete from the ram buffer
        writer.deleteDocuments(new Term("id", Integer.toString(13)));

        Term id3 = new Term("id", Integer.toString(3));

        // delete from the 1st segment
        writer.deleteDocuments(id3);

        assertTrue(writer.numRamDocs() > 0);

        //System.out
        //    .println("segdels1:" + writer.docWriter.deletesToString());

        //assertTrue(writer.docWriter.segmentDeletes.size() > 0);

        // we cause a merge to happen
        fsmp.doMerge = true;
        fsmp.start = 0;
        fsmp.length = 2;
        System.out.println("maybeMerge "+writer.segmentInfos);

        SegmentInfo info0 = writer.segmentInfos.info(0);
        SegmentInfo info1 = writer.segmentInfos.info(1);

        writer.maybeMerge();
        System.out.println("maybeMerge after "+writer.segmentInfos);
        // there should be docs in RAM
        assertTrue(writer.numRamDocs() > 0);

        // assert we've merged the 1 and 2 segments
        // and still have a segment leftover == 2
        assertEquals(2, writer.segmentInfos.size());
        assertFalse(segThere(info0, writer.segmentInfos));
        assertFalse(segThere(info1, writer.segmentInfos));

        //System.out.println("segdels2:" + writer.docWriter.deletesToString());

        //assertTrue(writer.docWriter.segmentDeletes.size() > 0);

        IndexReader r = writer.getReader();
        IndexReader r1 = r.getSequentialSubReaders()[0];
        printDelDocs(r1.getLiveDocs());
        int[] docs = toDocsArray(id3, null, r);
        System.out.println("id3 docs:"+Arrays.toString(docs));
        // there shouldn't be any docs for id:3
        assertTrue(docs == null);
        r.close();

        part2(writer, fsmp);
         */
        writer.close()
        dir.close()
    }

    /**
     * static boolean hasPendingDeletes(SegmentInfos infos) { for (SegmentInfo info : infos) { if
     * (info.deletes.any()) { return true; } } return false; }
     */
    @Throws(Exception::class)
    fun part2(writer: IndexWriter, fsmp: RangeMergePolicy) {
        for (x in 20..<25) {
            writer.addDocument(DocHelper.createDocument(x, "5", 2))
            // System.out.println("numRamDocs(" + x + ")" + writer.numRamDocs());
        }
        writer.flush(false, false)
        for (x in 25..<30) {
            writer.addDocument(DocHelper.createDocument(x, "5", 2))
            // System.out.println("numRamDocs(" + x + ")" + writer.numRamDocs());
        }
        writer.flush(false, false)

        // System.out.println("infos3:"+writer.segmentInfos);

        val delterm = Term("id", "8")
        writer.deleteDocuments(delterm)
        // System.out.println("segdels3:" + writer.docWriter.deletesToString());

        fsmp.doMerge = true
        fsmp.start = 1
        fsmp.length = 2
        writer.maybeMerge()

        // deletes for info1, the newly created segment from the
        // merge should have no deletes because they were applied in
        // the merge
        // SegmentInfo info1 = writer.segmentInfos.info(1);
        // assertFalse(exists(info1, writer.docWriter.segmentDeletes));

        // System.out.println("infos4:"+writer.segmentInfos);
        // System.out.println("segdels4:" + writer.docWriter.deletesToString());
    }

    fun segThere(info: SegmentCommitInfo, infos: SegmentInfos): Boolean {
        for (si in infos) {
            if (si.info.name == info.info.name) return true
        }
        return false
    }

    companion object {
        fun printDelDocs(bits: Bits?) {
            if (bits == null) return
            for (x in 0..<bits.length()) {
                println("$x:${bits.get(x)}")
            }
        }

        @Throws(IOException::class)
        fun toArray(postingsEnum: PostingsEnum): IntArray {
            var docs = IntArray(0)
            var numDocs = 0
            while (postingsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                val docID = postingsEnum.docID()
                docs = ArrayUtil.grow(docs, numDocs + 1)
                docs[numDocs + 1] = docID
            }
            return ArrayUtil.copyOfSubArray(docs, 0, numDocs)
        }
    }

    @Throws(IOException::class)
    fun toDocsArray(term: Term, bits: Bits?, reader: IndexReader): IntArray? {
        val ctermsEnum = MultiTerms.getTerms(reader, term.field)!!.iterator()
        if (ctermsEnum.seekExact(BytesRef(term.text()))) {
            val postingsEnum = TestUtil.docs(random(), ctermsEnum, null, PostingsEnum.NONE.toInt())
            return toArray(postingsEnum)
        }
        return null
    }

    class RangeMergePolicy(private val useCompoundFile: Boolean) : MergePolicy() {
        var doMerge = false
        var start = 0
        var length = 0

        @Throws(IOException::class)
        override fun findMerges(
            mergeTrigger: MergeTrigger?,
            segmentInfos: SegmentInfos?,
            mergeContext: MergeContext?
        ): MergeSpecification? {
            val ms = MergeSpecification()
            if (doMerge) {
                val om = OneMerge(segmentInfos!!.asList().subList(start, start + length).toMutableList())
                ms.add(om)
                doMerge = false
                return ms
            }
            return null
        }

        @Throws(IOException::class)
        override fun findForcedMerges(
            segmentInfos: SegmentInfos?,
            maxSegmentCount: Int,
            segmentsToMerge: MutableMap<SegmentCommitInfo, Boolean>?,
            mergeContext: MergeContext?
        ): MergeSpecification? {
            return null
        }

        @Throws(IOException::class)
        override fun findForcedDeletesMerges(
            segmentInfos: SegmentInfos?,
            mergeContext: MergeContext?
        ): MergeSpecification? {
            return null
        }

        override fun useCompoundFile(
            infos: SegmentInfos,
            newSegment: SegmentCommitInfo,
            mergeContext: MergeContext
        ): Boolean {
            return useCompoundFile
        }
    }
}
