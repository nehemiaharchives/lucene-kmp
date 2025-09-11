package org.gnit.lucenekmp.index

import kotlin.test.Test
import kotlin.test.assertEquals
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.tests.util.LuceneTestCase

class TestIndexCommit : LuceneTestCase() {

    @Test
    fun testEqualsHashCode() {
        newDirectory().use { dir ->
            val ic1 = object : IndexCommit() {
                override val segmentsFileName: String? = "a"
                override val directory: Directory = dir
                override val fileNames: MutableCollection<String> = mutableListOf()
                override fun delete() {}
                override val generation: Long = 0
                override val userData: MutableMap<String, String> = mutableMapOf()
                override val isDeleted: Boolean = false
                override val segmentCount: Int = 2
            }
            val ic2 = object : IndexCommit() {
                override val segmentsFileName: String? = "b"
                override val directory: Directory = dir
                override val fileNames: MutableCollection<String> = mutableListOf()
                override fun delete() {}
                override val generation: Long = 0
                override val userData: MutableMap<String, String> = mutableMapOf()
                override val isDeleted: Boolean = false
                override val segmentCount: Int = 2
            }
            assertEquals(ic1, ic2)
            assertEquals(ic1.hashCode(), ic2.hashCode(), "hash codes are not equals")
        }
    }

    private fun newDirectory(): Directory = ByteBuffersDirectory()
}

