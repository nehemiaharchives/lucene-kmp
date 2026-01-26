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
import org.gnit.lucenekmp.codecs.NormsConsumer
import org.gnit.lucenekmp.codecs.NormsFormat
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.index.EmptyDocValuesProducer
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState

/**
 * plain-text norms format.
 *
 * <p><b>FOR RECREATIONAL USE ONLY</b>
 *
 * @lucene.experimental
 */
class SimpleTextNormsFormat : NormsFormat() {
    @Throws(IOException::class)
    override fun normsConsumer(state: SegmentWriteState): NormsConsumer {
        return SimpleTextNormsConsumer(state)
    }

    @Throws(IOException::class)
    override fun normsProducer(state: SegmentReadState): NormsProducer {
        return SimpleTextNormsProducer(state)
    }

    /**
     * Reads plain-text norms.
     *
     * <p><b>FOR RECREATIONAL USE ONLY</b>
     *
     * @lucene.experimental
     */
    class SimpleTextNormsProducer(state: SegmentReadState) : NormsProducer() {
        private val impl: SimpleTextDocValuesReader

        init {
            // All we do is change the extension from .dat -> .len;
            // otherwise this is a normal simple doc values file:
            impl = SimpleTextDocValuesReader(state, NORMS_SEG_EXTENSION)
        }

        @Throws(IOException::class)
        override fun getNorms(field: FieldInfo): NumericDocValues {
            return impl.getNumeric(field)
        }

        @Throws(IOException::class)
        override fun close() {
            impl.close()
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            impl.checkIntegrity()
        }

        override fun toString(): String {
            return this::class.simpleName + "(" + impl + ")"
        }
    }

    /**
     * Writes plain-text norms.
     *
     * <p><b>FOR RECREATIONAL USE ONLY</b>
     *
     * @lucene.experimental
     */
    class SimpleTextNormsConsumer(state: SegmentWriteState) : NormsConsumer() {
        private val impl: SimpleTextDocValuesWriter

        init {
            // All we do is change the extension from .dat -> .len;
            // otherwise this is a normal simple doc values file:
            impl = SimpleTextDocValuesWriter(state, NORMS_SEG_EXTENSION)
        }

        @Throws(IOException::class)
        override fun addNormsField(field: FieldInfo, normsProducer: NormsProducer) {
            impl.addNumericField(
                field,
                object : EmptyDocValuesProducer() {
                    @Throws(IOException::class)
                    override fun getNumeric(field: FieldInfo): NumericDocValues? {
                        return normsProducer.getNorms(field)
                    }
                }
            )
        }

        @Throws(IOException::class)
        override fun close() {
            impl.close()
        }
    }

    companion object {
        private const val NORMS_SEG_EXTENSION = "len"
    }
}
