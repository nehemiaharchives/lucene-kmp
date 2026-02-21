package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// TODO: maybe we should test this with mocks, but it's easy
// enough to test the basics via Codec
class TestNamedSPILoader : LuceneTestCase() {

    @Test
    fun testLookup() {
        val currentName = TestUtil.getDefaultCodec().name
        val codec = Codec.forName(currentName)
        assertEquals(currentName, codec.name)
    }

    // we want an exception if it's not found.
    @Test
    fun testBogusLookup() {
        expectThrows(IllegalArgumentException::class) {
            Codec.forName("dskfdskfsdfksdfdsf")
        }
    }

    @Test
    fun testAvailableServices() {
        val codecs = Codec.availableCodecs()
        assertTrue(codecs.contains(TestUtil.getDefaultCodec().name))
    }
}
