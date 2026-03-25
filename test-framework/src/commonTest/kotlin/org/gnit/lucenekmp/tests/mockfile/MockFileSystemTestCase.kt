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
package org.gnit.lucenekmp.tests.mockfile

import okio.IOException
import okio.Path
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.Constants
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Base class for testing mockfilesystems. This tests things that really need to work: Path
 * equals()/hashcode(), directory listing glob and filtering, URI conversion, etc.
 */
abstract class MockFileSystemTestCase : LuceneTestCase() {
    /** wraps Path with custom behavior */
    protected abstract fun wrap(path: Path): Path

    /** Test that Path.hashcode/equals are sane */
    @Suppress("SelfAssertion")
    @Throws(IOException::class)
    open fun testHashCodeEquals() {
        val dir = wrap(createTempDir())

        val f1 = dir.resolve("file1")
        val f1Again = dir.resolve("file1")
        val f2 = dir.resolve("file2")

        assertEquals(f1, f1)
        assertFalse(f1.equals(null))
        assertEquals(f1, f1Again)
        assertEquals(f1.hashCode(), f1Again.hashCode())
        assertFalse(f1 == f2)
        dir.getFileSystem().close()
    }

    /** Test that URIs are not corrumpted */
    @Throws(IOException::class)
    open fun testURI() {
        implTestURI("file1") // plain ASCII
    }

    @Throws(IOException::class)
    open fun testURIumlaute() {
        implTestURI("äÄöÖüÜß") // Umlaute and s-zet
    }

    @Throws(IOException::class)
    open fun testURIchinese() {
        implTestURI("中国") // chinese
    }

    @Throws(IOException::class)
    private fun implTestURI(fileName: String) {
        assumeFalse(
            "broken on J9: see https://issues.apache.org/jira/browse/LUCENE-6517",
            Constants.JAVA_VENDOR.startsWith("IBM")
        )
        val dir = wrap(createTempDir())

        val f1 =
            try {
                dir.resolve(fileName)
            } catch (ipe: IllegalArgumentException) {
                assumeNoException("couldn't resolve '$fileName'", ipe)
                return
            }

        val uri = f1.toString()
        val f2 = (dir.getFileSystem() as FilterFileSystem).provider().getPath(uri)
        assertEquals(f1, f2)

        dir.getFileSystem().close()
    }

    /** Tests that newDirectoryStream with a filter works correctly */
    @Throws(IOException::class)
    open fun testDirectoryStreamFiltered() {
        val dir = wrap(createTempDir())

        val file = Files.newOutputStream(dir.resolve("file1"))
        file.write(5)
        file.close()
        val stream = Files.newDirectoryStream(dir)
        var count = 0
        for (path in stream) {
            assertTrue(FilterPath.fileSystemOrNull(path) != null)
            if (!path.name.startsWith("extra")) {
                count++
            }
        }
        assertEquals(1, count)
        dir.getFileSystem().close()
    }

    /** Tests that newDirectoryStream with globbing works correctly */
    @Throws(IOException::class)
    open fun testDirectoryStreamGlobFiltered() {
        val dir = wrap(createTempDir())

        var file = Files.newOutputStream(dir.resolve("foo"))
        file.write(5)
        file.close()
        file = Files.newOutputStream(dir.resolve("bar"))
        file.write(5)
        file.close()
        val stream = Files.newDirectoryStream(dir, "f*")
        var count = 0
        for (path in stream) {
            assertTrue(FilterPath.fileSystemOrNull(path) != null)
            ++count
        }
        assertEquals(1, count)
        dir.getFileSystem().close()
    }
}
