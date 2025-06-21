package org.gnit.lucenekmp.store

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.gnit.lucenekmp.jdkport.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.Ignore

class TestFilterIndexOutput : BaseDataOutputTestCase<FilterIndexOutput>() {
    private lateinit var fakeFileSystem: FakeFileSystem

    @BeforeTest
    fun setup() {
        fakeFileSystem = FakeFileSystem()
        Files.setFileSystem(fakeFileSystem)
    }

    @AfterTest
    fun teardown() {
        Files.resetFileSystem()
    }

    override fun newInstance(): FilterIndexOutput {
        return object : FilterIndexOutput(
            "test",
            "test",
            ByteBuffersIndexOutput(ByteBuffersDataOutput.newResettableInstance(), "test", "test")
        ) {}
    }

    override fun toBytes(instance: FilterIndexOutput): ByteArray {
        return (instance.delegate as ByteBuffersIndexOutput).toArrayCopy()
    }

    @Test
    fun testUnwrap() {
        val path = "/dir".toPath()
        fakeFileSystem.createDirectories(path)
        val dir = NIOFSDirectory(path, FSLockFactory.default, fakeFileSystem)
        val output = dir.createOutput("test", IOContext.DEFAULT)
        val filterIndexOutput = object : FilterIndexOutput("wrapper of test", "FilterDirectory{test}", output) {}
        assertEquals(output, filterIndexOutput.delegate)
        assertEquals(output, FilterIndexOutput.unwrap(filterIndexOutput))
        filterIndexOutput.close()
        dir.close()
    }

    @Test
    @Ignore // TODO reflection not available in common tests
    fun testOverrides() {
        // The original test verifies that FilterIndexOutput only overrides abstract methods of IndexOutput.
        // Kotlin/Native lacks full reflection support, so this test is ignored for now.
    }
}
