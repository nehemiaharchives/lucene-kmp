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
import org.gnit.lucenekmp.codecs.PointsFormat
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.codecs.PointsWriter
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState

class CrankyPointsFormat(var delegate: PointsFormat, var random: Random) : PointsFormat() {
    @Throws(IOException::class)
    override fun fieldsWriter(state: SegmentWriteState): PointsWriter {
        return CrankyPointsWriter(delegate.fieldsWriter(state), random)
    }

    @Throws(IOException::class)
    override fun fieldsReader(state: SegmentReadState): PointsReader {
        return CrankyPointsReader(delegate.fieldsReader(state), random)
    }

    class CrankyPointsWriter(val delegate: PointsWriter, val random: Random) : PointsWriter() {
        @Throws(IOException::class)
        override fun writeField(fieldInfo: FieldInfo, values: PointsReader) {
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException")
            }
            delegate.writeField(fieldInfo, values)
        }

        @Throws(IOException::class)
        override fun finish() {
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException")
            }
            delegate.finish()
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException")
            }
        }

        @Throws(IOException::class)
        override fun merge(mergeState: MergeState) {
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException")
            }
            delegate.merge(mergeState)
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException")
            }
        }

        @Throws(IOException::class)
        override fun close() {
            delegate.close()
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException")
            }
        }
    }

    class CrankyPointsReader(val delegate: PointsReader, val random: Random) : PointsReader() {
        @Throws(IOException::class)
        override fun checkIntegrity() {
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException")
            }
            delegate.checkIntegrity()
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException")
            }
        }

        @Throws(IOException::class)
        override fun getValues(field: String): PointValues? {
            val delegate = this.delegate.getValues(field) ?: return null

            return object : PointValues() {
                override val pointTree: PointTree
                    get() {
                        val pointTree = delegate.pointTree
                        return object : PointTree {
                            override fun clone(): PointTree {
                                return pointTree.clone()
                            }

                            @Throws(IOException::class)
                            override fun moveToChild(): Boolean {
                                return pointTree.moveToChild()
                            }

                            @Throws(IOException::class)
                            override fun moveToSibling(): Boolean {
                                return pointTree.moveToSibling()
                            }

                            @Throws(IOException::class)
                            override fun moveToParent(): Boolean {
                                return pointTree.moveToParent()
                            }

                            override val minPackedValue: ByteArray
                                get() = pointTree.minPackedValue

                            override val maxPackedValue: ByteArray
                                get() = pointTree.maxPackedValue

                            override fun size(): Long {
                                return pointTree.size()
                            }

                            @Throws(IOException::class)
                            override fun visitDocIDs(visitor: IntersectVisitor) {
                                if (random.nextInt(100) == 0) {
                                    throw IOException("Fake IOException")
                                }
                                pointTree.visitDocIDs(visitor)
                                if (random.nextInt(100) == 0) {
                                    throw IOException("Fake IOException")
                                }
                            }

                            @Throws(IOException::class)
                            override fun visitDocValues(visitor: IntersectVisitor) {
                                if (random.nextInt(100) == 0) {
                                    throw IOException("Fake IOException")
                                }
                                pointTree.visitDocValues(visitor)
                                if (random.nextInt(100) == 0) {
                                    throw IOException("Fake IOException")
                                }
                            }
                        }
                    }

                override val minPackedValue: ByteArray
                    get() {
                        if (random.nextInt(100) == 0) {
                            throw IOException("Fake IOException")
                        }
                        return delegate.minPackedValue
                    }

                override val maxPackedValue: ByteArray
                    get() {
                        if (random.nextInt(100) == 0) {
                            throw IOException("Fake IOException")
                        }
                        return delegate.maxPackedValue
                    }

                override val numDimensions: Int
                    get() {
                        if (random.nextInt(100) == 0) {
                            throw IOException("Fake IOException")
                        }
                        return delegate.numDimensions
                    }

                override val numIndexDimensions: Int
                    get() {
                        if (random.nextInt(100) == 0) {
                            throw IOException("Fake IOException")
                        }
                        return delegate.numIndexDimensions
                    }

                override val bytesPerDimension: Int
                    get() {
                        if (random.nextInt(100) == 0) {
                            throw IOException("Fake IOException")
                        }
                        return delegate.bytesPerDimension
                    }

                override fun size(): Long {
                    return delegate.size()
                }

                override val docCount: Int
                    get() = delegate.docCount
            }
        }

        @Throws(IOException::class)
        override fun close() {
            delegate.close()
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException")
            }
        }
    }
}
