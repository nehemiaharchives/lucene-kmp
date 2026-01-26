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
import org.gnit.lucenekmp.codecs.KnnVectorsFormat
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.KnnVectorsWriter
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState

/**
 * For debugging, curiosity, transparency only!! Do not use this codec in production.
 *
 * <p>This codec stores all data in a single human-readable text file (_N.vec). You can view this in
 * any text editor, and even edit it to alter your index.
 *
 * @lucene.experimental
 */
class SimpleTextKnnVectorsFormat : KnnVectorsFormat("SimpleTextKnnVectorsFormat") {

    @Throws(IOException::class)
    override fun fieldsWriter(state: SegmentWriteState): KnnVectorsWriter {
        return SimpleTextKnnVectorsWriter(state)
    }

    @Throws(IOException::class)
    override fun fieldsReader(state: SegmentReadState): KnnVectorsReader {
        return SimpleTextKnnVectorsReader(state)
    }

    override fun getMaxDimensions(fieldName: String): Int {
        return DEFAULT_MAX_DIMENSIONS
    }

    companion object {
        /** Extension of vectors data file */
        const val VECTOR_EXTENSION: String = "vec"

        /** Extension of vectors index file */
        const val META_EXTENSION: String = "gri"
    }
}
