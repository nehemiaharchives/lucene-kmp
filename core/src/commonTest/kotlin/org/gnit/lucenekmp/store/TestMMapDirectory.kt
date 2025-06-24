package org.gnit.lucenekmp.store

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import okio.Path
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.jdkport.Callable
import org.gnit.lucenekmp.tests.store.BaseDirectoryTestCase
import org.gnit.lucenekmp.util.Constants

@Ignore
class TestMMapDirectory : BaseDirectoryTestCase() {
    override fun getDirectory(path: Path): Directory {
        val m = MMapDirectory(path)
        m.setPreload { _, _ -> random().nextBoolean() }
        return m
    }

    @Ignore // TODO implement after MMapDirectory class is fully ported
    @Test
    fun testAceWithThreads() {
        // TODO implement after MMapDirectory class is ported
    }

    @Ignore // TODO implement after MMapDirectory class is fully ported
    @Test
    fun testNullParamsIndexInput() {
        // TODO implement after MMapDirectory class is ported
    }

    @Test
    fun testMadviseAvail() {
        assertEquals(
            Constants.LINUX || Constants.MAC_OS_X,
            MMapDirectory.supportsMadvise(),
            "madvise should be supported on Linux and Macos"
        )
    }

    @Ignore // TODO implement after MMapDirectory class is fully ported
    @Test
    fun testWithNormal() {
        // TODO implement after MMapDirectory class is ported
    }

    @Ignore // TODO implement after MMapDirectory class is fully ported
    @Test
    fun testConfined() {
        // TODO implement after MMapDirectory class is ported
    }

    companion object {}

    @Ignore // TODO implement after MMapDirectory class is fully ported
    @Test
    fun testArenas() {
        // TODO implement after MMapDirectory class is ported
    }

    private class IndicesOpenTask(val names: List<String>, val dir: Directory) : Callable<Unit?> {
        // not executed until MMapDirectory ported
        override fun call(): Unit? {
            // TODO implement after MMapDirectory class is ported
            return null
        }
    }

    @Ignore // TODO implement after MMapDirectory class is fully ported
    @Test
    fun testArenasManySegmentFiles() {
        // TODO implement after MMapDirectory class is ported
    }

    @Test
    fun testGroupBySegmentFunc() {
        val func = MMapDirectory.GROUP_BY_SEGMENT
        assertEquals("0", func("_0.doc").get())
        assertEquals("51", func("_51.si").get())
        assertEquals("51-g", func("_51_1.si").get())
        assertEquals("51-g", func("_51_1_gg_ff.si").get())
        assertEquals("51-g", func("_51_2_gg_ff.si").get())
        assertEquals("51-g", func("_51_3_gg_ff.si").get())
        assertEquals("5987654321", func("_5987654321.si").get())
        assertEquals("f", func("_f.si").get())
        assertEquals("ff", func("_ff.si").get())
        assertEquals("51a", func("_51a.si").get())
        assertEquals("f51a", func("_f51a.si").get())
        assertEquals("segment", func("_segment.si").get())

        assertEquals("5", func("_5_Lucene90FieldsIndex-doc_ids_0.tmp").get())

        assertFalse(func("").isPresent)
        assertFalse(func("_").isPresent)
        assertFalse(func("_.si").isPresent)
        assertFalse(func("foo").isPresent)
        assertFalse(func("_foo").isPresent)
        assertFalse(func("__foo").isPresent)
        assertFalse(func("_segment").isPresent)
        assertFalse(func("segment.si").isPresent)
    }

    @Test
    fun testNoGroupingFunc() {
        val func = MMapDirectory.NO_GROUPING
        assertFalse(func("_0.doc").isPresent)
        assertFalse(func("_0.si").isPresent)
        assertFalse(func("_54.si").isPresent)
        assertFalse(func("_ff.si").isPresent)
        assertFalse(func("_.si").isPresent)
        assertFalse(func("foo").isPresent)
        assertFalse(func("_foo").isPresent)
        assertFalse(func("__foo").isPresent)
        assertFalse(func("_segment").isPresent)
        assertFalse(func("_segment.si").isPresent)
        assertFalse(func("segment.si").isPresent)
        assertFalse(func("_51a.si").isPresent)
    }

    @Ignore // TODO implement after MMapDirectory class is fully ported
    @Test
    fun testPrefetchWithSingleSegment() {
        // TODO implement after MMapDirectory class is ported
    }

    @Ignore // TODO implement after MMapDirectory class is fully ported
    @Test
    fun testPrefetchWithMultiSegment() {
        // TODO implement after MMapDirectory class is ported
    }

    fun testPrefetchWithSegments(maxChunkSize: Int) {
        // TODO implement after MMapDirectory class is ported
    }
}

