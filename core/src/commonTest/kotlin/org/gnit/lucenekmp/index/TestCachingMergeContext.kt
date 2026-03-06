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
package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCachingMergeContext : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testNumDeletesToMerge() {
        val mergeContext = MockMergeContext()
        val cachingMergeContext = CachingMergeContext(mergeContext)
        val info = newSegmentCommitInfo()
        assertEquals(1, cachingMergeContext.numDeletesToMerge(info))
        assertEquals(1, cachingMergeContext.cachedNumDeletesToMerge.size)
        assertEquals(1, cachingMergeContext.cachedNumDeletesToMerge[info])
        assertEquals(mergeContext.count, 1)

        mergeContext.numDeletesToMerge(info)
        assertEquals(mergeContext.count, 2)

        assertEquals(1, cachingMergeContext.numDeletesToMerge(info))
        assertEquals(1, cachingMergeContext.cachedNumDeletesToMerge.size)
        assertEquals(1, cachingMergeContext.cachedNumDeletesToMerge[info])
    }

    private fun newSegmentCommitInfo(): SegmentCommitInfo {
        val dir = ByteBuffersDirectory()
        val si = SegmentInfo(
            dir,
            Version.LATEST,
            Version.LATEST,
            "_0",
            1,
            isCompoundFile = false,
            hasBlocks = false,
            null,
            mutableMapOf(),
            StringHelper.randomId(),
            mutableMapOf(),
            null
        )
        return SegmentCommitInfo(si, 0, 0, 0, 0, 0, StringHelper.randomId())
    }

    private class MockMergeContext : MergePolicy.MergeContext {
        var count = 0

        @Throws(IOException::class)
        override fun numDeletesToMerge(info: SegmentCommitInfo): Int {
            this.count += 1
            return this.count
        }

        override fun numDeletedDocs(info: SegmentCommitInfo): Int {
            return 0
        }

        override val infoStream: InfoStream
            get() = InfoStream.NO_OUTPUT

        override val mergingSegments: MutableSet<SegmentCommitInfo>
            get() = mutableSetOf()
    }
}
