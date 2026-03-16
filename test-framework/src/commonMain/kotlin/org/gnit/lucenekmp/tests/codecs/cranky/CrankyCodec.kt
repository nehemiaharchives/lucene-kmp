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
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.CompoundFormat
import org.gnit.lucenekmp.codecs.DocValuesFormat
import org.gnit.lucenekmp.codecs.FieldInfosFormat
import org.gnit.lucenekmp.codecs.FilterCodec
import org.gnit.lucenekmp.codecs.LiveDocsFormat
import org.gnit.lucenekmp.codecs.NormsFormat
import org.gnit.lucenekmp.codecs.PointsFormat
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.codecs.SegmentInfoFormat
import org.gnit.lucenekmp.codecs.StoredFieldsFormat
import org.gnit.lucenekmp.codecs.TermVectorsFormat

/** Codec for testing that throws random IOExceptions */
class CrankyCodec(delegate: Codec, val random: Random) : FilterCodec(delegate.name, delegate) {
    /** Wrap the provided codec with crankiness. Try passing Asserting for the most fun. */
    override fun docValuesFormat(): DocValuesFormat {
        return CrankyDocValuesFormat(delegate.docValuesFormat(), random)
    }

    override fun fieldInfosFormat(): FieldInfosFormat {
        return CrankyFieldInfosFormat(delegate.fieldInfosFormat(), random)
    }

    override fun liveDocsFormat(): LiveDocsFormat {
        return CrankyLiveDocsFormat(delegate.liveDocsFormat(), random)
    }

    override fun normsFormat(): NormsFormat {
        return CrankyNormsFormat(delegate.normsFormat(), random)
    }

    override fun postingsFormat(): PostingsFormat {
        return CrankyPostingsFormat(delegate.postingsFormat(), random)
    }

    override fun segmentInfoFormat(): SegmentInfoFormat {
        return CrankySegmentInfoFormat(delegate.segmentInfoFormat(), random)
    }

    override fun storedFieldsFormat(): StoredFieldsFormat {
        return CrankyStoredFieldsFormat(delegate.storedFieldsFormat(), random)
    }

    override fun termVectorsFormat(): TermVectorsFormat {
        return CrankyTermVectorsFormat(delegate.termVectorsFormat(), random)
    }

    override fun compoundFormat(): CompoundFormat {
        return CrankyCompoundFormat(delegate.compoundFormat(), random)
    }

    override fun pointsFormat(): PointsFormat {
        return CrankyPointsFormat(delegate.pointsFormat(), random)
    }

    override fun toString(): String {
        return "Cranky($delegate)"
    }
}
