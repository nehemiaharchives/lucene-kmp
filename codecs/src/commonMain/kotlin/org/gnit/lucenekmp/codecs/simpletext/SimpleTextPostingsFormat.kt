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
import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState

/**
 * For debugging, curiosity, transparency only!! Do not use this codec in production.
 *
 * <p>This codec stores all postings data in a single human-readable text file (_N.pst). You can
 * view this in any text editor, and even edit it to alter your index.
 *
 * @lucene.experimental
 */
internal class SimpleTextPostingsFormat : PostingsFormat("SimpleText") {

    @Throws(IOException::class)
    override fun fieldsConsumer(state: SegmentWriteState): FieldsConsumer {
        return SimpleTextFieldsWriter(state)
    }

    @Throws(IOException::class)
    override fun fieldsProducer(state: SegmentReadState): FieldsProducer {
        return SimpleTextFieldsReader(state)
    }

    companion object {
        /** Extension of freq postings file */
        const val POSTINGS_EXTENSION = "pst"

        fun getPostingsFileName(segment: String, segmentSuffix: String): String {
            return IndexFileNames.segmentFileName(segment, segmentSuffix, POSTINGS_EXTENSION)
        }
    }
}
