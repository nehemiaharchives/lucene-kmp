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

import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.NamedThreadFactory
import org.gnit.lucenekmp.util.Version
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalAtomicApi::class)
class TestDocumentsWriterPerThreadPool : LuceneTestCase() {
    @Test
    fun testLockReleaseAndClose() {
        newDirectory().use { directory ->
            val pool =
                DocumentsWriterPerThreadPool {
                    DocumentsWriterPerThread(
                        Version.LATEST.major,
                        "",
                        directory,
                        directory,
                        newIndexWriterConfig(),
                        DocumentsWriterDeleteQueue(InfoStream.default),
                        FieldInfos.Builder(FieldInfos.FieldNumbers(null, null)),
                        AtomicLong(0),
                        false,
                    )
                }

            val first = pool.getAndLock()
            assertEquals(1, runBlocking { pool.size() })
            val second = pool.getAndLock()
            assertEquals(2, runBlocking { pool.size() })
            runBlocking { pool.marksAsFreeAndUnlock(first) }
            assertEquals(2, runBlocking { pool.size() })
            val third = pool.getAndLock()
            assertSame(first, third)
            assertEquals(2, runBlocking { pool.size() })
            assertTrue(pool.checkout(third))
            assertEquals(1, runBlocking { pool.size() })

            pool.close()
            assertEquals(1, runBlocking { pool.size() })
            runBlocking { pool.marksAsFreeAndUnlock(second) }
            assertEquals(1, runBlocking { pool.size() })
            for (lastPerThead in pool.filterAndLock { true }) {
                assertTrue(pool.checkout(lastPerThead))
                lastPerThead.unlock()
            }
            assertEquals(0, runBlocking { pool.size() })
        }
    }

    @Test
    fun testCloseWhileNewWritersLocked() {
        newDirectory().use { directory ->
            val pool =
                DocumentsWriterPerThreadPool {
                    DocumentsWriterPerThread(
                        Version.LATEST.major,
                        "",
                        directory,
                        directory,
                        newIndexWriterConfig(),
                        DocumentsWriterDeleteQueue(InfoStream.default),
                        FieldInfos.Builder(FieldInfos.FieldNumbers(null, null)),
                        AtomicLong(0),
                        false,
                    )
                }

            val first = pool.getAndLock()
            runBlocking { pool.lockNewWriters() }
            val latch = CountDownLatch(1)
            val threadFactory = NamedThreadFactory("TestDocumentsWriterPerThreadPool")
            val t =
                threadFactory.newThread {
                    try {
                        latch.countDown()
                        pool.getAndLock()
                        fail()
                    } catch (_: AlreadyClosedException) {
                        // fine
                    }
                }
            latch.await()
            while (!t.isCompleted) {
                first.unlock()
                pool.close()
                runBlocking { pool.unlockNewWriters() }
                break
            }
            runBlocking { t.join() }
            for (perThread in pool.filterAndLock { true }) {
                assertTrue(pool.checkout(perThread))
                perThread.unlock()
            }
            assertEquals(0, runBlocking { pool.size() })
        }
    }
}
