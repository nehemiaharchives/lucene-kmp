package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestReaderWrapperDVTypeCheck : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testNoDVFieldOnSegment() {
        val dir: Directory = newDirectory()
        val cfg: IndexWriterConfig = IndexWriterConfig(MockAnalyzer(random()))
                .setCodec(TestUtil.alwaysDocValuesFormat(TestUtil.getDefaultDocValuesFormat()))
        val iw = RandomIndexWriter(random(), dir, cfg)

        var sdvExist = false
        var ssdvExist = false

        val seed: Long = random().nextLong()
        run {
            val indexRandom = Random(seed)
            val docs: Int = TestUtil.nextInt(indexRandom, 1, 4)

            // System.out.println("docs:"+docs);
            for (i in 0..<docs) {
                val d = Document()
                d.add(newStringField("id", "" + i, Store.NO))
                if (rarely(indexRandom)) {
                    // System.out.println("on:"+i+" rarely: true");
                    d.add(SortedDocValuesField("sdv", BytesRef("" + i)))
                    sdvExist = true
                } else {
                    // System.out.println("on:"+i+" rarely: false");
                }
                val numSortedSet: Int = indexRandom.nextInt(5) - 3
                for (j in 0..<numSortedSet) {
                    // System.out.println("on:"+i+" add ssdv:"+j);
                    d.add(SortedSetDocValuesField("ssdv", BytesRef("" + j)))
                    ssdvExist = true
                }
                iw.addDocument(d)
                iw.commit()
            }
        }
        iw.forceMerge(1)
        val reader: DirectoryReader = iw.reader

        // System.out.println("sdv:"+ sdvExist+ " ssdv:"+ssdvExist+", segs: "+reader.leaves().size() +",
        // "+reader.leaves());
        iw.close()
        val wrapper: LeafReader = getOnlyLeafReader(reader)

        run {
            // final Random indexRandom = new Random(seed);
            val sdv: SortedDocValues? = wrapper.getSortedDocValues("sdv")
            val ssdv: SortedSetDocValues? = wrapper.getSortedSetDocValues("ssdv")

            assertNull(wrapper.getSortedDocValues("ssdv"), "confusing DV type")
            assertNull(wrapper.getSortedSetDocValues("sdv"), "confusing DV type")

            assertNull(wrapper.getSortedDocValues("NOssdv"), "absent field")
            assertNull(wrapper.getSortedSetDocValues("NOsdv"), "absent field")

            assertTrue(sdvExist == (sdv != null), "optional sdv field")
            assertTrue(ssdvExist == (ssdv != null), "optional ssdv field")
        }
        reader.close()

        dir.close()
    }
}
