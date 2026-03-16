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
import org.gnit.lucenekmp.codecs.SegmentInfoFormat
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext

class CrankySegmentInfoFormat(val delegate: SegmentInfoFormat, val random: Random) :
    SegmentInfoFormat() {

    @Throws(IOException::class)
    override fun read(
        directory: Directory,
        segmentName: String,
        segmentID: ByteArray,
        context: IOContext
    ): SegmentInfo {
        return delegate.read(directory, segmentName, segmentID, context)
    }

    @Throws(IOException::class)
    override fun write(dir: Directory, info: SegmentInfo, ioContext: IOContext) {
        if (random.nextInt(100) == 0) {
            throw IOException("Fake IOException from SegmentInfoFormat.write()")
        }
        delegate.write(dir, info, ioContext)
    }
}
