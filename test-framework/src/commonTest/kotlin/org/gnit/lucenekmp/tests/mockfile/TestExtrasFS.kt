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

import okio.Path
import org.gnit.lucenekmp.jdkport.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Basic tests for ExtrasFS */
class TestExtrasFS : MockFileSystemTestCase() {
    override fun wrap(path: Path): Path {
        return wrap(path, random().nextBoolean(), random().nextBoolean())
    }

    fun wrap(path: Path, active: Boolean, createDirectory: Boolean): Path {
        val provider = ExtrasFS(path.getFileSystem(), active, createDirectory)
        return provider.wrapPath(path)
    }

    /** test where extra file is created */
    @Test
    @Throws(Exception::class)
    fun testExtraFile() {
        val dir = wrap(createTempDir(), active = true, createDirectory = false)
        Files.createDirectory(dir.resolve("foobar"))

        val seen = mutableListOf<String>()
        val stream = Files.newDirectoryStream(dir.resolve("foobar"))
        for (path in stream) {
            seen.add(path.name)
        }
        assertEquals(listOf("extra0"), seen)
        assertTrue(Files.readAttributes(dir.resolve("foobar").resolve("extra0")).isRegularFile)
    }

    /** test where extra directory is created */
    @Test
    @Throws(Exception::class)
    fun testExtraDirectory() {
        val dir = wrap(createTempDir(), active = true, createDirectory = true)
        Files.createDirectory(dir.resolve("foobar"))

        val seen = mutableListOf<String>()
        val stream = Files.newDirectoryStream(dir.resolve("foobar"))
        for (path in stream) {
            seen.add(path.name)
        }
        assertEquals(listOf("extra0"), seen)
        assertTrue(Files.isDirectory(dir.resolve("foobar").resolve("extra0")))
    }

    /** test where no extras are created: its a no-op */
    @Test
    @Throws(Exception::class)
    fun testNoExtras() {
        val dir = wrap(createTempDir(), active = false, createDirectory = false)
        Files.createDirectory(dir.resolve("foobar"))
        val stream = Files.newDirectoryStream(dir.resolve("foobar"))
        for (path in stream) {
            fail("should not have found file: $path")
        }
    }

    // tests inherited from MockFileSystemTestCase
    @Test
    override fun testHashCodeEquals() = super.testHashCodeEquals()

    @Test
    override fun testURI() = super.testURI()

    @Test
    override fun testURIumlaute() = super.testURIumlaute()

    @Test
    override fun testURIchinese() = super.testURIchinese()

    @Test
    override fun testDirectoryStreamFiltered() = super.testDirectoryStreamFiltered()

    @Test
    override fun testDirectoryStreamGlobFiltered() = super.testDirectoryStreamGlobFiltered()
}
