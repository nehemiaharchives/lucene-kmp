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
package org.gnit.lucenekmp.codecs.simpletext

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.tests.index.BaseCompoundFormatTestCase
import kotlin.test.Test

class TestSimpleTextCompoundFormat : BaseCompoundFormatTestCase() {
    override val codec: Codec = SimpleTextCodec()

    @Test
    override fun testEmpty() = super.testEmpty()

    @Test
    override fun testSingleFile() = super.testSingleFile()

    @Test
    override fun testTwoFiles() = super.testTwoFiles()

    @Test
    override fun testDoubleClose() = super.testDoubleClose()

    @Test
    override fun testPassIOContext() = super.testPassIOContext()

    @Test
    override fun testLargeCFS() = super.testLargeCFS()

    @Test
    override fun testListAll() = super.testListAll()

    @Test
    override fun testCreateOutputDisabled() = super.testCreateOutputDisabled()

    @Test
    override fun testDeleteFileDisabled() = super.testDeleteFileDisabled()

    @Test
    override fun testRenameFileDisabled() = super.testRenameFileDisabled()

    @Test
    override fun testSyncDisabled() = super.testSyncDisabled()

    @Test
    override fun testMakeLockDisabled() = super.testMakeLockDisabled()

    @Test
    override fun testRandomFiles() = super.testRandomFiles()

    @Test
    override fun testManySubFiles() = super.testManySubFiles()

    @Test
    override fun testClonedStreamsClosing() = super.testClonedStreamsClosing()

    @Test
    override fun testRandomAccess() = super.testRandomAccess()

    @Test
    override fun testRandomAccessClones() = super.testRandomAccessClones()

    @Test
    override fun testFileNotFound() = super.testFileNotFound()

    @Test
    override fun testReadPastEOF() = super.testReadPastEOF()

    @Test
    override fun testMergeStability() = super.testMergeStability()

    @Test
    override fun testResourceNameInsideCompoundFile() = super.testResourceNameInsideCompoundFile()

    @Test
    override fun testMissingCodecHeadersAreCaught() {
        // SimpleText does not catch broken sub-files in CFS!
    }

    @Test
    override fun testCorruptFilesAreCaught() {
        // SimpleText does not catch broken sub-files in CFS!
    }

    @Test
    override fun testCheckIntegrity() {
        // SimpleText does not catch broken sub-files in CFS!
    }
}
