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
package org.gnit.lucenekmp.tests.index

import okio.IOException
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.MultiReader
import org.gnit.lucenekmp.internal.tests.IndexPackageAccess
import org.gnit.lucenekmp.internal.tests.TestSecrets
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.util.IOUtils

/** A [MultiReader] that has its own cache key, occasionally useful for testing purposes. */
class OwnCacheKeyMultiReader(vararg subReaders: IndexReader) : MultiReader(*subReaders) {

    private val readerClosedListeners: MutableSet<ClosedListener> = mutableSetOf()
    private val readerClosedListenersLock = ReentrantLock()

    private val cacheHelper =
        object : CacheHelper {
            private val cacheKey: CacheKey = INDEX_PACKAGE_ACCESS.newCacheKey()

            override val key: CacheKey
                get() = cacheKey

            override suspend fun addClosedListener(listener: ClosedListener) {
                ensureOpen()
                readerClosedListeners.add(listener)
            }
        }

    override val readerCacheHelper: CacheHelper
        get() = cacheHelper

    @Throws(IOException::class)
    override fun notifyReaderClosedListeners() {
        readerClosedListenersLock.withLock {
            IOUtils.applyToAll(readerClosedListeners) { l: ClosedListener ->
                l.onClose(cacheHelper.key)
            }
        }
    }

    companion object {
        private val INDEX_PACKAGE_ACCESS: IndexPackageAccess = TestSecrets.getIndexPackageAccess()
    }
}
