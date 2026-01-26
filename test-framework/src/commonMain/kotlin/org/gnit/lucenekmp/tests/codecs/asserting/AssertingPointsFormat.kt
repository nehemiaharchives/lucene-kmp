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
import org.gnit.lucenekmp.codecs.PointsFormat
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.codecs.PointsWriter
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.tests.index.AssertingLeafReader
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlinx.coroutines.Job

/** Just like the default point format but with additional asserts. */
class AssertingPointsFormat : PointsFormat {
    private val `in`: PointsFormat

    /** Create a new AssertingPointsFormat */
    constructor() : this(TestUtil.getDefaultCodec().pointsFormat())

    /**
     * Expert: Create an AssertingPointsFormat. This is only intended to pass special parameters for
     * testing.
     */
    // TODO: can we randomize this a cleaner way? e.g. stored fields and vectors do
    // this with a separate codec...
    constructor(`in`: PointsFormat) {
        this.`in` = `in`
    }

    @Throws(IOException::class)
    override fun fieldsWriter(state: SegmentWriteState): PointsWriter {
        return AssertingPointsWriter(state, `in`.fieldsWriter(state))
    }

    @Throws(IOException::class)
    override fun fieldsReader(state: SegmentReadState): PointsReader {
        return AssertingPointsReader(
            state.segmentInfo.maxDoc(),
            `in`.fieldsReader(state),
            state.fieldInfos,
            false
        )
    }

    internal class AssertingPointsReader(
        private val maxDoc: Int,
        private val `in`: PointsReader,
        private val fis: FieldInfos,
        private val merging: Boolean
    ) : PointsReader() {
        private val creationThread: Job? = AssertingCodec.currentJob()

        @Throws(IOException::class)
        override fun close() {
            `in`.close()
            `in`.close() // close again
        }

        @Throws(IOException::class)
        override fun getValues(field: String): PointValues? {
            val fi: FieldInfo? = fis.fieldInfo(field)
            assert(fi != null && fi.pointDimensionCount > 0)
            if (merging) {
                AssertingCodec.assertThread("PointsReader", creationThread)
            }
            val values = `in`.getValues(field)
            if (values == null) {
                return null
            }
            return AssertingLeafReader.AssertingPointValues(values, maxDoc)
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            `in`.checkIntegrity()
        }

        override val mergeInstance: PointsReader
            get() = AssertingPointsReader(maxDoc, `in`.mergeInstance, fis, true)

        override fun toString(): String {
            return "${this::class.simpleName}($`in`)"
        }
    }

    internal class AssertingPointsWriter(
        private val writeState: SegmentWriteState,
        private val `in`: PointsWriter
    ) : PointsWriter() {
        @Throws(IOException::class)
        override fun writeField(fieldInfo: FieldInfo, values: PointsReader) {
            if (fieldInfo.pointDimensionCount == 0) {
                throw IllegalArgumentException(
                    "writing field=\"${fieldInfo.name}\" but pointDimensionalCount is 0"
                )
            }
            `in`.writeField(fieldInfo, values)
        }

        @Throws(IOException::class)
        override fun merge(mergeState: MergeState) {
            `in`.merge(mergeState)
        }

        @Throws(IOException::class)
        override fun finish() {
            `in`.finish()
        }

        @Throws(IOException::class)
        override fun close() {
            `in`.close()
            `in`.close() // close again
        }
    }
}
