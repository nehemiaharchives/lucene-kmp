package org.gnit.lucenekmp.jdkport

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.buffered
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class FilesTest {

    @Test
    fun testNewInputStream() {
        val path: Path = Path("/tmp/testfile.txt")
        SystemFileSystem.sink(path).buffered().use { it.write("Hello, World!".encodeToByteArray()) }

        val inputStream = Files.newInputStream(path)
        val content = inputStream.readBytes().decodeToString()
        assertEquals("Hello, World!", content)
    }

    @Test
    fun testNewOutputStream() {
        val path: Path = Path("/tmp/testfile.txt")

        val outputStream = Files.newOutputStream(path)
        outputStream.write("Hello, World!".encodeToByteArray())
        outputStream.close()

        val content = SystemFileSystem.source(path).buffered().use { it.readBytes().decodeToString() }
        assertEquals("Hello, World!", content)
    }

    @Test
    fun testNewBufferedReader() {
        val path: Path = Path("/tmp/testfile.txt")
        SystemFileSystem.sink(path).buffered().use { it.write("Hello, World!".encodeToByteArray()) }

        val reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)
        val content = reader.readText()
        assertEquals("Hello, World!", content)
    }
}
