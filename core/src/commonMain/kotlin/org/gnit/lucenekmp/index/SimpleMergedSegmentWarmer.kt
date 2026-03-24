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
import org.gnit.lucenekmp.index.IndexWriter.IndexReaderWarmer
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.InfoStream

/** A very simple merged segment warmer that just ensures data structures are initialized. */
class SimpleMergedSegmentWarmer(private val infoStream: InfoStream) : IndexReaderWarmer {
    /**
     * Creates a new SimpleMergedSegmentWarmer
     *
     * @param infoStream InfoStream to log statistics about warming.
     */
    @Throws(IOException::class)
    override fun warm(reader: LeafReader) {
        val startTime = System.currentTimeMillis()
        var indexedCount = 0
        var docValuesCount = 0
        var normsCount = 0
        for (info in reader.fieldInfos) {
            if (info.indexOptions != IndexOptions.NONE) {
                reader.terms(info.name)
                indexedCount++

                if (info.hasNorms()) {
                    reader.getNormValues(info.name)
                    normsCount++
                }
            }

            if (info.docValuesType != DocValuesType.NONE) {
                when (info.docValuesType) {
                    DocValuesType.NUMERIC -> reader.getNumericDocValues(info.name)
                    DocValuesType.BINARY -> reader.getBinaryDocValues(info.name)
                    DocValuesType.SORTED -> reader.getSortedDocValues(info.name)
                    DocValuesType.SORTED_NUMERIC -> reader.getSortedNumericDocValues(info.name)
                    DocValuesType.SORTED_SET -> reader.getSortedSetDocValues(info.name)
                    DocValuesType.NONE -> assert(false)
                }
                docValuesCount++
            }
        }

        reader.storedFields().document(0)
        reader.termVectors().get(0)

        if (infoStream.isEnabled("SMSW")) {
            infoStream.message(
                "SMSW",
                "Finished warming segment: $reader, indexed=$indexedCount, docValues=$docValuesCount, norms=$normsCount, time=${System.currentTimeMillis() - startTime}"
            )
        }
    }
}
