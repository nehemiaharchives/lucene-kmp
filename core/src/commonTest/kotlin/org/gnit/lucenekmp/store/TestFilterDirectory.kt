package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.tests.store.BaseDirectoryTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestFilterDirectory : BaseDirectoryTestCase() {
    override fun getDirectory(path: okio.Path): Directory {
        return object : FilterDirectory(ByteBuffersDirectory()) {}
    }

    @Test
    fun testUnwrap() {
        val dir = ByteBuffersDirectory()
        val dir2 = object : FilterDirectory(dir) {}
        assertEquals(dir, dir2.`in`)
        assertEquals(dir, FilterDirectory.unwrap(dir2))
        dir2.close()
    }
}
