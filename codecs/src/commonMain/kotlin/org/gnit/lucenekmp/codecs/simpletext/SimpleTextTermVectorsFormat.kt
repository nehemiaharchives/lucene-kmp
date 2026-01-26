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

import okio.IOException
import org.gnit.lucenekmp.codecs.TermVectorsFormat
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.codecs.TermVectorsWriter
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext

/**
 * plain text term vectors format.
 *
 * <p><b>FOR RECREATIONAL USE ONLY</b>
 *
 * @lucene.experimental
 */
class SimpleTextTermVectorsFormat : TermVectorsFormat() {

    @Throws(IOException::class)
    override fun vectorsReader(
        directory: Directory,
        segmentInfo: SegmentInfo,
        fieldInfos: FieldInfos?,
        context: IOContext
    ): TermVectorsReader {
        return SimpleTextTermVectorsReader(directory, segmentInfo, context)
    }

    @Throws(IOException::class)
    override fun vectorsWriter(
        directory: Directory,
        segmentInfo: SegmentInfo,
        context: IOContext
    ): TermVectorsWriter {
        return SimpleTextTermVectorsWriter(directory, segmentInfo.name, context)
    }
}
