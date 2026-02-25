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
package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.Nightly
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * Estimates how [RamUsageEstimator] estimates physical memory consumption of Java objects.
 */
class TestStressRamUsageEstimator : LuceneTestCase() {
    internal class Entry {
        var o: Any? = null
        var next: Entry? = null

        fun createNext(o: Any?): Entry {
            val e = Entry()
            e.o = o
            e.next = next
            this.next = e
            return e
        }
    }

    internal var guard: Any? = null

    // This shows an easy stack overflow because we're counting recursively.
    @Nightly
    @Test
    fun testLargeSetOfByteArrays() {
        // java.lang.System.gc()
        val before = 0L // java.lang.Runtime.getRuntime().totalMemory()
        val all = arrayOfNulls<Any>(1000000)
        for (i in all.indices) {
            all[i] = ByteArray(random().nextInt(3))
        }
        // java.lang.System.gc()
        val after = 0L // java.lang.Runtime.getRuntime().totalMemory()
        println("mx:  " + RamUsageEstimator.humanReadableUnits(after - before))
        println("rue: " + RamUsageEstimator.humanReadableUnits(shallowSizeOf(all)))

        guard = all
    }

    private fun shallowSizeOf(all: Array<Any?>): Long {
        var s = RamUsageEstimator.shallowSizeOf(all as Array<Any>)
        for (o in all) {
            s += RamUsageEstimator.shallowSizeOf(o)
        }
        return s
    }

    private fun shallowSizeOf(all: Array<Array<Any?>>): Long {
        var s = RamUsageEstimator.shallowSizeOf(all as Array<Any>)
        for (o in all) {
            s += RamUsageEstimator.shallowSizeOf(o as Any)
            for (o2 in o) {
                s += RamUsageEstimator.shallowSizeOf(o2)
            }
        }
        return s
    }

    @Ignore // this test passes on local computer but takes too long time for CI so ignored
    @Nightly
    @Test
    fun testSimpleByteArrays() {
        var all = emptyArray<Array<Any?>>()
        try {
            while (true) {
                // Check the current memory consumption and provide the estimate.
                // java.lang.System.gc()
                val estimated = shallowSizeOf(all)
                if (estimated > 50 * RamUsageEstimator.ONE_MB) {
                    break
                }

                // Make another batch of objects.
                val seg = arrayOfNulls<Any>(10000)
                all = ArrayUtil.growExact(all, all.size + 1)
                all[all.size - 1] = seg
                for (i in seg.indices) {
                    seg[i] = ByteArray(random().nextInt(7))
                }
            }
        } catch (_: /*OutOfMemory*/Error) {
            // Release and quit.
        }
    }
}
