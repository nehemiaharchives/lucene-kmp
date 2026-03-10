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

import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestIndexCommit : LuceneTestCase() {

    @Test
    fun testEqualsHashCode() {
        // LUCENE-2417: equals and hashCode() impl was inconsistent
        newDirectory().use { dir: Directory ->
            val ic1: IndexCommit =
                object : IndexCommit() {
                    override val segmentsFileName: String
                        get() = "a"

                    override val directory: Directory
                        get() = dir

                    override val fileNames: MutableCollection<String>
                        get() = mutableListOf()

                    override fun delete() {}

                    override val generation: Long
                        get() = 0

                    override val userData: MutableMap<String, String>
                        get() = mutableMapOf()

                    override val isDeleted: Boolean
                        get() = false

                    override val segmentCount: Int
                        get() = 2
                }

            val ic2: IndexCommit =
                object : IndexCommit() {
                    override val segmentsFileName: String
                        get() = "b"

                    override val directory: Directory
                        get() = dir

                    override val fileNames: MutableCollection<String>
                        get() = mutableListOf()

                    override fun delete() {}

                    override val generation: Long
                        get() = 0

                    override val userData: MutableMap<String, String>
                        get() = mutableMapOf()

                    override val isDeleted: Boolean
                        get() = false

                    override val segmentCount: Int
                        get() = 2
                }

            assertEquals(ic1, ic2)
            assertEquals(ic1.hashCode(), ic2.hashCode(), "hash codes are not equals")
        }
    }
}
