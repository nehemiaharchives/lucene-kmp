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
package org.gnit.lucenekmp.tests.codecs.cranky

import kotlin.random.Random
import okio.IOException
import org.gnit.lucenekmp.codecs.LiveDocsFormat
import org.gnit.lucenekmp.index.SegmentCommitInfo
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.Bits

class CrankyLiveDocsFormat(val delegate: LiveDocsFormat, val random: Random) : LiveDocsFormat() {
    @Throws(IOException::class)
    override fun readLiveDocs(dir: Directory, info: SegmentCommitInfo, context: IOContext): Bits {
        return delegate.readLiveDocs(dir, info, context)
    }

    @Throws(IOException::class)
    override fun writeLiveDocs(
        bits: Bits,
        dir: Directory,
        info: SegmentCommitInfo,
        newDelCount: Int,
        context: IOContext
    ) {
        if (random.nextInt(100) == 0) {
            throw IOException("Fake IOException from LiveDocsFormat.writeLiveDocs()")
        }
        delegate.writeLiveDocs(bits, dir, info, newDelCount, context)
    }

    @Throws(IOException::class)
    override fun files(info: SegmentCommitInfo, files: MutableCollection<String>) {
        // TODO: is this called only from write? if so we should throw exception!
        delegate.files(info, files)
    }
}
