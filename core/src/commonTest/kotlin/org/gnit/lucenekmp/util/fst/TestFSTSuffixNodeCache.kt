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
package org.gnit.lucenekmp.util.fst

import kotlin.test.Test
import kotlin.test.assertEquals
import org.gnit.lucenekmp.tests.util.LuceneTestCase

class TestFSTSuffixNodeCache : LuceneTestCase() {

    @Test
    fun testCopyFallbackNodeBytes() {
        val outputs = NoOutputs.singleton
        val fstCompiler = FSTCompiler.Builder(FST.INPUT_TYPE.BYTE1, outputs).build()
        val suffixCache = FSTSuffixNodeCache(fstCompiler, 1.0)

        val primaryHashTable = suffixCache.PagedGrowableHash()
        val fallbackHashTable = suffixCache.PagedGrowableHash()
        val nodeLength = atLeast(500)
        val fallbackHashSlot = 1L
        val fallbackBytes = ByteArray(nodeLength)
        random().nextBytes(fallbackBytes)
        fallbackHashTable.copyNodeBytes(fallbackHashSlot, fallbackBytes, nodeLength)

        val storedBytes = fallbackHashTable.getBytes(fallbackHashSlot, nodeLength)
        for (i in 0 until nodeLength) {
            assertEquals(
                fallbackBytes[i],
                storedBytes[i],
                "byte @ index=$i"
            )
        }

        val primaryHashSlot = 2L
        primaryHashTable.copyFallbackNodeBytes(
            primaryHashSlot,
            fallbackHashTable,
            fallbackHashSlot,
            nodeLength
        )

        val copiedBytes = primaryHashTable.getBytes(primaryHashSlot, nodeLength)
        for (i in 0 until nodeLength) {
            assertEquals(
                fallbackBytes[i],
                copiedBytes[i],
                "byte @ index=$i"
            )
        }
    }
}
