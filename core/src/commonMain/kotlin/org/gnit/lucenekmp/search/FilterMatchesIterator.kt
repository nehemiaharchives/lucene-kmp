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

package org.gnit.lucenekmp.search

import okio.IOException

/**
 * A MatchesIterator that delegates all calls to another MatchesIterator
 *
 * Create a new FilterMatchesIterator
 *
 * @param `in` the delegate
 */
abstract class FilterMatchesIterator(
    /** The delegate */
    protected val `in`: MatchesIterator
) : MatchesIterator {

    @Throws(IOException::class)
    override fun next(): Boolean {
        return `in`.next()
    }

    override fun startPosition(): Int {
        return `in`.startPosition()
    }

    override fun endPosition(): Int {
        return `in`.endPosition()
    }

    @Throws(IOException::class)
    override fun startOffset(): Int {
        return `in`.startOffset()
    }

    @Throws(IOException::class)
    override fun endOffset(): Int {
        return `in`.endOffset()
    }

    override val subMatches: MatchesIterator?
        get() = `in`.subMatches

    override val query: Query?
        get() = `in`.query
}
