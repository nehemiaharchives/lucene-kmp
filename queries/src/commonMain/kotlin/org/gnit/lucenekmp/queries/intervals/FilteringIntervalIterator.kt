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

package org.gnit.lucenekmp.queries.intervals

import okio.IOException

internal abstract class FilteringIntervalIterator(
    val a: IntervalIterator,
    val b: IntervalIterator
) : ConjunctionIntervalIterator(listOf(a, b)) {

    var bpos: Boolean = false

    override fun start(): Int {
        if (bpos == false) {
            return NO_MORE_INTERVALS
        }
        return a.start()
    }

    override fun end(): Int {
        if (bpos == false) {
            return NO_MORE_INTERVALS
        }
        return a.end()
    }

    override fun gaps(): Int {
        return a.gaps()
    }

    @Throws(IOException::class)
    override fun reset() {
        bpos = b.nextInterval() != NO_MORE_INTERVALS
    }
}
