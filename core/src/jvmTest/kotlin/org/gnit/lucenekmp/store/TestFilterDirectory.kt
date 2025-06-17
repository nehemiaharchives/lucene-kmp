package org.gnit.lucenekmp.store

import okio.Path
import org.gnit.lucenekmp.tests.store.BaseDirectoryTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.lang.reflect.Method

class TestFilterDirectory : BaseDirectoryTestCase() {
    override fun getDirectory(path: Path): Directory {
        return object : FilterDirectory(FSDirectory.open(path)) {}
    }

    @Test
    fun testOverrides() {
        val exclude = HashSet<Method>()
        exclude.add(
            Directory::class.java.getMethod(
                "copyFrom",
                Directory::class.java,
                String::class.java,
                String::class.java,
                IOContext::class.java
            )
        )
        exclude.add(Directory::class.java.getMethod("openChecksumInput", String::class.java))
        for (m in FilterDirectory::class.java.methods) {
            if (m.declaringClass == Directory::class.java) {
                assertTrue(exclude.contains(m), "method ${m.name} not overridden!")
            }
        }
    }

    @Test
    fun testUnwrap() {
        val dir = FSDirectory.open(createTempDir("unwrap"))
        val dir2 = object : FilterDirectory(dir) {}
        assertEquals(dir, dir2.`in`)
        assertEquals(dir, FilterDirectory.unwrap(dir2))
        dir2.close()
    }
}

