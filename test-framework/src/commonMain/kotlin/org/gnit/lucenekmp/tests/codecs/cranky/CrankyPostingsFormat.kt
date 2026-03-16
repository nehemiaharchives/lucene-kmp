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
import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState

class CrankyPostingsFormat(val delegate: PostingsFormat, val random: Random) :
    PostingsFormat(delegate.name) {

    @Throws(IOException::class)
    override fun fieldsConsumer(state: SegmentWriteState): FieldsConsumer {
        if (random.nextInt(100) == 0) {
            throw IOException("Fake IOException from PostingsFormat.fieldsConsumer()")
        }
        return CrankyFieldsConsumer(delegate.fieldsConsumer(state), random)
    }

    @Throws(IOException::class)
    override fun fieldsProducer(state: SegmentReadState): FieldsProducer {
        return delegate.fieldsProducer(state)
    }

    class CrankyFieldsConsumer(val delegate: FieldsConsumer, val random: Random) : FieldsConsumer() {
        @Throws(IOException::class)
        override fun write(fields: Fields, norms: NormsProducer?) {
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException from FieldsConsumer.write()")
            }
            delegate.write(fields, norms)
        }

        override fun close() {
            delegate.close()
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException from FieldsConsumer.close()")
            }
        }
    }
}
