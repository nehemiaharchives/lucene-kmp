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
package org.gnit.lucenekmp.tests.codecs.asserting

import okio.IOException
import org.gnit.lucenekmp.codecs.LiveDocsFormat
import org.gnit.lucenekmp.index.SegmentCommitInfo
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet

/** Just like the default live docs format but with additional asserts. */
class AssertingLiveDocsFormat : LiveDocsFormat() {
    private val `in`: LiveDocsFormat = TestUtil.getDefaultCodec().liveDocsFormat()

    @Throws(IOException::class)
    override fun readLiveDocs(dir: Directory, info: SegmentCommitInfo, context: IOContext): Bits {
        val raw = `in`.readLiveDocs(dir, info, context)
        check(raw, info.info.maxDoc(), info.delCount)
        return AssertingBits(raw)
    }

    @Throws(IOException::class)
    override fun writeLiveDocs(
        bits: Bits,
        dir: Directory,
        info: SegmentCommitInfo,
        newDelCount: Int,
        context: IOContext
    ) {
        check(bits, info.info.maxDoc(), info.delCount + newDelCount)
        `in`.writeLiveDocs(bits, dir, info, newDelCount, context)
    }

    private fun check(bits: Bits, expectedLength: Int, expectedDeleteCount: Int) {
        assert(bits.length() == expectedLength)
        var deletedCount = 0
        for (i in 0..<bits.length()) {
            if (!bits.get(i)) {
                deletedCount++
            }
        }
        assert(deletedCount == expectedDeleteCount) {
            "deleted: $deletedCount != expected: $expectedDeleteCount"
        }
    }

    @Throws(IOException::class)
    override fun files(info: SegmentCommitInfo, files: MutableCollection<String>) {
        `in`.files(info, files)
    }

    override fun toString(): String {
        return "Asserting($`in`)"
    }

    class AssertingBits internal constructor(val `in`: Bits) : Bits {
        init {
            assert(`in`.length() >= 0)
        }

        override fun get(index: Int): Boolean {
            assert(index >= 0)
            assert(index < `in`.length()) { "index=$index vs in.length()=${`in`.length()}" }
            return `in`.get(index)
        }

        override fun length(): Int {
            return `in`.length()
        }

        override fun applyMask(bitSet: FixedBitSet, offset: Int) {
            assert(offset >= 0)
            `in`.applyMask(bitSet, offset)
        }

        override fun toString(): String {
            return "Asserting($`in`)"
        }
    }
}
