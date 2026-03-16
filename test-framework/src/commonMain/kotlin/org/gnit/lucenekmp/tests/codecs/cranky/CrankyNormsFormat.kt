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
import org.gnit.lucenekmp.codecs.NormsConsumer
import org.gnit.lucenekmp.codecs.NormsFormat
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState

class CrankyNormsFormat(val delegate: NormsFormat, val random: Random) : NormsFormat() {
    @Throws(IOException::class)
    override fun normsConsumer(state: SegmentWriteState): NormsConsumer {
        if (random.nextInt(100) == 0) {
            throw IOException("Fake IOException from NormsFormat.normsConsumer()")
        }
        return CrankyNormsConsumer(delegate.normsConsumer(state), random)
    }

    @Throws(IOException::class)
    override fun normsProducer(state: SegmentReadState): NormsProducer {
        return delegate.normsProducer(state)
    }

    class CrankyNormsConsumer(val delegate: NormsConsumer, val random: Random) : NormsConsumer() {
        @Throws(IOException::class)
        override fun close() {
            delegate.close()
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException from NormsConsumer.close()")
            }
        }

        @Throws(IOException::class)
        override fun addNormsField(field: FieldInfo, normsProducer: NormsProducer) {
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException from NormsConsumer.addNormsField()")
            }
            delegate.addNormsField(field, normsProducer)
        }
    }
}
