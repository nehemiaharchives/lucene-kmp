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

import org.gnit.lucenekmp.index.MergePolicy.OneMerge
import org.gnit.lucenekmp.jdkport.Executor
import org.gnit.lucenekmp.store.Directory

/**
 * A [MergeScheduler] which never executes any merges. It is also a singleton and can be
 * accessed through [NoMergeScheduler.INSTANCE]. Use it if you want to prevent an
 * [IndexWriter] from ever executing merges, regardless of the [MergePolicy] used. Note that
 * you can achieve the same thing by using [NoMergePolicy], however with [NoMergeScheduler]
 * you also ensure that no unnecessary code of any [MergeScheduler] implementation is ever
 * executed. Hence it is recommended to use both if you want to disable merges from ever
 * happening.
 */
class NoMergeScheduler private constructor() : MergeScheduler() {

    override fun close() {}

    override suspend fun merge(mergeSource: MergeSource, trigger: MergeTrigger) {}

    override fun wrapForMerge(merge: OneMerge, `in`: Directory): Directory {
        return `in`
    }

    fun clone(): MergeScheduler {
        return this
    }

    override fun getIntraMergeExecutor(merge: OneMerge): Executor {
        throw UnsupportedOperationException("NoMergeScheduler does not support merges")
    }

    companion object {
        /** The single instance of [NoMergeScheduler] */
        val INSTANCE: MergeScheduler = NoMergeScheduler()
    }
}
