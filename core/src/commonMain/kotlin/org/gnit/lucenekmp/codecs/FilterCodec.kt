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
package org.gnit.lucenekmp.codecs

/**
 * A codec that forwards all its method calls to another codec.
 *
 * <p>Extend this class when you need to reuse the functionality of an existing codec. For example,
 * if you want to build a codec that redefines LuceneMN's [LiveDocsFormat]:
 *
 * <pre class="prettyprint">
 *   class CustomCodec : FilterCodec {
 *     constructor() : super("CustomCodec", LuceneMNCodec()) {}
 *
 *     override fun liveDocsFormat(): LiveDocsFormat {
 *       return CustomLiveDocsFormat()
 *     }
 *   }
 * </pre>
 *
 * <p><em>Please note:</em> Don't call [Codec.forName] from the no-arg constructor of your own
 * codec. When the SPI framework loads your own Codec as SPI component, SPI has not yet fully
 * initialized! If you want to extend another Codec, instantiate it directly by calling its
 * constructor.
 *
 * @lucene.experimental
 */
abstract class FilterCodec
/**
 * Sole constructor. When subclassing this codec, create a no-arg ctor and pass the delegate codec
 * and a unique name to this ctor.
 */
protected constructor(name: String, protected val delegate: Codec) : Codec(name) {

    override fun docValuesFormat(): DocValuesFormat {
        return delegate.docValuesFormat()
    }

    override fun fieldInfosFormat(): FieldInfosFormat {
        return delegate.fieldInfosFormat()
    }

    override fun liveDocsFormat(): LiveDocsFormat {
        return delegate.liveDocsFormat()
    }

    override fun normsFormat(): NormsFormat {
        return delegate.normsFormat()
    }

    override fun postingsFormat(): PostingsFormat {
        return delegate.postingsFormat()
    }

    override fun segmentInfoFormat(): SegmentInfoFormat {
        return delegate.segmentInfoFormat()
    }

    override fun storedFieldsFormat(): StoredFieldsFormat {
        return delegate.storedFieldsFormat()
    }

    override fun termVectorsFormat(): TermVectorsFormat {
        return delegate.termVectorsFormat()
    }

    override fun compoundFormat(): CompoundFormat {
        return delegate.compoundFormat()
    }

    override fun pointsFormat(): PointsFormat {
        return delegate.pointsFormat()
    }

    override fun knnVectorsFormat(): KnnVectorsFormat {
        return delegate.knnVectorsFormat()
    }
}
