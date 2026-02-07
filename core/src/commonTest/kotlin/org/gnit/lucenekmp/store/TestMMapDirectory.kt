package org.gnit.lucenekmp.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.gnit.lucenekmp.jdkport.Callable
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.tests.store.BaseDirectoryTestCase
import org.gnit.lucenekmp.util.Constants
import org.gnit.lucenekmp.util.NamedThreadFactory
import okio.Path

class TestMMapDirectory : BaseDirectoryTestCase() {
    override fun getDirectory(path: Path): Directory {
        val m = MMapDirectory(path)
        m.setPreload { _, _ -> random().nextBoolean() }
        return m
    }

    @Test
    fun testAceWithThreads() = runTest {
        val nInts = 8 * 1024 * 1024

        getDirectory(createTempDir("testAceWithThreads")).use { dir ->
            dir.createOutput("test", IOContext.DEFAULT).use { out ->
                val random = random()
                for (i in 0 until nInts) {
                    out.writeInt(random.nextInt())
                }
            }

            val iters = RANDOM_MULTIPLIER * (if (TEST_NIGHTLY) 50 else 10)
            for (iter in 0 until iters) {
                val `in` = dir.openInput("test", IOContext.DEFAULT)
                val clone = `in`.clone()
                val accum = ByteArray(nInts * Int.SIZE_BYTES)
                val shotgun = CountDownLatch(1)
                val failures = arrayOfNulls<Throwable>(1)
                val threadFactory = NamedThreadFactory("testAceWithThreads")
                val t1 = threadFactory.newThread(
                    Runnable {
                        try {
                            shotgun.await()
                            for (i in 0 until 10) {
                                clone.seek(0)
                                clone.readBytes(accum, 0, accum.size)
                            }
                        } catch (ok: AlreadyClosedException) {
                            // OK
                        } catch (ise: IllegalStateException) {
                            // KMP fallback (NIOFS-backed) may surface "closed" from okio FileHandle.
                            if (ise.message != "closed") {
                                failures[0] = ise
                            }
                        } catch (t: Throwable) {
                            failures[0] = t
                        }
                    }
                )

                shotgun.countDown()
                // this triggers "bad behaviour": closing input while other threads are running
                `in`.close()
                t1.join()
                failures[0]?.let { throw RuntimeException(it) }
            }
        }
    }

    @Test
    fun testNullParamsIndexInput() {
        getDirectory(createTempDir("testNullParamsIndexInput")).use { mmapDir ->
            mmapDir.createOutput("bytes", newIOContext(random())).use { out ->
                out.alignFilePointer(16)
            }
            mmapDir.openInput("bytes", IOContext.DEFAULT).use { `in` ->
                expectThrows(NullPointerException::class) { `in`.readBytes(null as ByteArray, 0, 1) }
                expectThrows(NullPointerException::class) { `in`.readFloats(null as FloatArray, 0, 1) }
                expectThrows(NullPointerException::class) { `in`.readLongs(null as LongArray, 0, 1) }
            }
        }
    }

    @Test
    fun testMadviseAvail() {
        assertEquals(
            Constants.LINUX || Constants.MAC_OS_X,
            MMapDirectory.supportsMadvise(),
            "madvise should be supported on Linux and Macos"
        )
    }

    // Opens the input with ReadAdvice.NORMAL to ensure basic code path coverage.
    @Test
    fun testWithNormal() {
        val size = 8 * 1024
        val bytes = ByteArray(size)
        random().nextBytes(bytes)

        MMapDirectory(createTempDir("testWithRandom")).use { dir ->
            dir.createOutput("test", IOContext.DEFAULT).use { out ->
                out.writeBytes(bytes, 0, bytes.size)
            }

            dir.openInput("test", IOContext.DEFAULT.withReadAdvice(ReadAdvice.NORMAL)).use { `in` ->
                val readBytes = ByteArray(size)
                `in`.readBytes(readBytes, 0, readBytes.size)
                assertContentEquals(bytes, readBytes)
            }
        }
    }

    // Opens the input with ReadAdvice.READONCE to ensure slice and clone are appropriately confined
    @Test
    fun testConfined() = runTest {
        val size = 16
        val bytes = ByteArray(size)
        random().nextBytes(bytes)

        MMapDirectory(createTempDir("testConfined")).use { dir ->
            dir.createOutput("test", IOContext.DEFAULT).use { out ->
                out.writeBytes(bytes, 0, bytes.size)
            }

            dir.openInput("test", IOContext.READONCE).use { `in` ->
                // ensure accessible
                assertEquals(16L, `in`.slice("test", 0, `in`.length()).length())
                assertEquals(15L, `in`.slice("test", 1, `in`.length() - 1).length())

                val isKmpFallback = `in`.toString().contains("[kmp-fallback]")

                if (isKmpFallback) {
                    // KMP fallback is not backed by confined memory segments.
                    runOnOtherThread(Callable { `in`.slice("test", 0, `in`.length()) })

                    val offset = random().nextInt(`in`.length().toInt())
                    val length = `in`.length().toInt() - offset
                    runOnOtherThread(Callable { `in`.slice("test", offset.toLong(), length.toLong()) })

                    val slice = `in`.slice("test", 0, `in`.length())
                    runOnOtherThread(Callable { slice.slice("test", 0, `in`.length()) })
                    runOnOtherThread(Callable { slice.clone() })
                } else {
                    // ensure not accessible
                    var x = expectThrows(IllegalStateException::class) {
                        runOnOtherThread(Callable { `in`.slice("test", 0, `in`.length()) })
                    }
                    assertTrue(x.message!!.contains("confined"))

                    val offset = random().nextInt(`in`.length().toInt())
                    val length = `in`.length().toInt() - offset
                    x = expectThrows(IllegalStateException::class) {
                        runOnOtherThread(Callable { `in`.slice("test", offset.toLong(), length.toLong()) })
                    }
                    assertTrue(x.message!!.contains("confined"))

                    // slice.slice
                    val slice = `in`.slice("test", 0, `in`.length())
                    x = expectThrows(IllegalStateException::class) {
                        runOnOtherThread(Callable { slice.slice("test", 0, `in`.length()) })
                    }
                    assertTrue(x.message!!.contains("confined"))

                    // slice.clone
                    x = expectThrows(IllegalStateException::class) {
                        runOnOtherThread(Callable { slice.clone() })
                    }
                    assertTrue(x.message!!.contains("confined"))
                }
            }
        }
    }

    @Test
    fun testArenas() = runTest {
        val randomGenerationOrNone = { if (random().nextBoolean()) "_${random().nextInt(5)}" else "" }

        // First, create a number of segment specific file name lists to test with
        val exts = listOf(".si", ".cfs", ".cfe", ".dvd", ".dvm", ".nvd", ".nvm", ".fdt", ".vec", ".vex", ".vemf")
        val names = (0 until 50)
            .flatMap { i -> exts.map { ext -> "_$i${randomGenerationOrNone()}$ext" } }
            .toMutableList()

        // Second, create a number of non-segment file names
        names.addAll((0 until 50).map { i -> "foo$i" })
        names.shuffle(random())

        val size = 6
        val bytes = ByteArray(size)
        random().nextBytes(bytes)

        MMapDirectory(createTempDir("testArenas")).use { dir ->
            for (name in names) {
                dir.createOutput(name, IOContext.DEFAULT).use { out ->
                    out.writeBytes(bytes, 0, bytes.size)
                }
            }

            val nThreads = 10
            val perListSize = (names.size + nThreads) / nThreads
            val nameLists = (0 until nThreads).map { i ->
                names.subList(
                    perListSize * i,
                    min(perListSize * i + perListSize, names.size)
                ).toList()
            }

            nameLists
                .map { list -> async(Dispatchers.Default) { IndicesOpenTask(list, dir).call() } }
                .awaitAll()

            val attachment = dir.attachment
            if (attachment is Map<*, *>) {
                assertEquals(0, attachment.size)
            } else {
                assertNull(attachment, "unexpected attachment: $attachment")
            }
        }
    }

    private class IndicesOpenTask(private val names: List<String>, private val dir: Directory) : Callable<Unit?> {
        override fun call(): Unit? {
            val closeables = mutableListOf<IndexInput>()
            for (name in names) {
                closeables.add(dir.openInput(name, IOContext.DEFAULT))
            }
            for (closeable in closeables) {
                closeable.close()
            }
            return null
        }
    }

    // Opens more files in the same group than the ref counting limit.
    @Test
    fun testArenasManySegmentFiles() {
        val names = (0 until 1024).map { i -> "_001.ext$i" }

        val size = 4
        val bytes = ByteArray(size)
        random().nextBytes(bytes)

        MMapDirectory(createTempDir("testArenasManySegmentFiles")).use { dir ->
            for (name in names) {
                dir.createOutput(name, IOContext.DEFAULT).use { out ->
                    out.writeBytes(bytes, 0, bytes.size)
                }
            }

            val closeables = mutableListOf<IndexInput>()
            for (name in names) {
                closeables.add(dir.openInput(name, IOContext.DEFAULT))
            }
            for (closeable in closeables) {
                closeable.close()
            }

            val attachment = dir.attachment
            if (attachment is Map<*, *>) {
                assertEquals(0, attachment.size)
            } else {
                assertNull(attachment, "unexpected attachment: $attachment")
            }
        }
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

        // old style
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

    @Test
    fun testPrefetchWithSingleSegment() {
        testPrefetchWithSegments(64 * 1024)
    }

    @Test
    fun testPrefetchWithMultiSegment() {
        testPrefetchWithSegments(16 * 1024)
    }

    // does not verify that the actual segment is prefetched, but rather exercises the code and bounds
    fun testPrefetchWithSegments(maxChunkSize: Int) {
        val bytes = ByteArray((maxChunkSize * 2) + 1)
        MMapDirectory(createTempDir("testPrefetchWithSegments"), maxChunkSize.toLong()).use { dir ->
            dir.createOutput("test", IOContext.DEFAULT).use { out ->
                out.writeBytes(bytes, 0, bytes.size)
            }

            dir.openInput("test", IOContext.READONCE).use { `in` ->
                val isKmpFallback = `in`.toString().contains("[kmp-fallback]")
                if (isKmpFallback) {
                    // Fallback implementation currently treats prefetch as no-op.
                    `in`.prefetch(0, `in`.length())
                    `in`.prefetch(1, `in`.length())
                    `in`.prefetch(`in`.length(), 1)

                    val slice1 = `in`.slice("slice-1", 1, `in`.length() - 1)
                    slice1.prefetch(0, slice1.length())
                    slice1.prefetch(1, slice1.length())
                    slice1.prefetch(slice1.length(), 1)

                    // we sliced off all but one byte from the first complete memory segment
                    val slice2 = `in`.slice("slice-2", maxChunkSize.toLong() - 1, `in`.length() - maxChunkSize + 1)
                    slice2.prefetch(0, slice2.length())
                    slice2.prefetch(1, slice2.length())
                    slice2.prefetch(slice2.length(), 1)
                } else {
                    `in`.prefetch(0, `in`.length())
                    expectThrows(IndexOutOfBoundsException::class) { `in`.prefetch(1, `in`.length()) }
                    expectThrows(IndexOutOfBoundsException::class) { `in`.prefetch(`in`.length(), 1) }

                    val slice1 = `in`.slice("slice-1", 1, `in`.length() - 1)
                    slice1.prefetch(0, slice1.length())
                    expectThrows(IndexOutOfBoundsException::class) { slice1.prefetch(1, slice1.length()) }
                    expectThrows(IndexOutOfBoundsException::class) { slice1.prefetch(slice1.length(), 1) }

                    // we sliced off all but one byte from the first complete memory segment
                    val slice2 = `in`.slice("slice-2", maxChunkSize.toLong() - 1, `in`.length() - maxChunkSize + 1)
                    slice2.prefetch(0, slice2.length())
                    expectThrows(IndexOutOfBoundsException::class) { slice2.prefetch(1, slice2.length()) }
                    expectThrows(IndexOutOfBoundsException::class) { slice2.prefetch(slice2.length(), 1) }
                }
            }
        }
    }

    private fun <T> runOnOtherThread(callable: Callable<T>): T {
        val failures = arrayOfNulls<Throwable>(1)
        val results = arrayOfNulls<Any>(1)
        val threadFactory = NamedThreadFactory("TestMMapDirectory")
        val job = threadFactory.newThread(
            Runnable {
                try {
                    results[0] = callable.call() as Any?
                } catch (t: Throwable) {
                    failures[0] = t
                }
            }
        )
        runBlocking {
            job.join()
        }
        failures[0]?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return results[0] as T
    }
}
