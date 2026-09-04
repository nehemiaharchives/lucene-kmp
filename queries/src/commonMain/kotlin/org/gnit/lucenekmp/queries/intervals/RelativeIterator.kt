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

internal abstract class RelativeIterator(
    val a: IntervalIterator,
    val b: IntervalIterator
) : IntervalIterator() {

    var bpos: Boolean = false

    override fun docID(): Int {
        return a.docID()
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        val doc = a.nextDoc()
        reset()
        return doc
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        val doc = a.advance(target)
        reset()
        return doc
    }

    override fun cost(): Long {
        return a.cost()
    }

    @Throws(IOException::class)
    protected open fun reset() {
        val doc = a.docID()
        bpos = b.docID() == doc || (b.docID() < doc && b.advance(doc) == doc)
    }

    override fun start(): Int {
        return a.start()
    }

    override fun end(): Int {
        return a.end()
    }

    override fun gaps(): Int {
        return a.gaps()
    }

    override fun matchCost(): Float {
        return a.matchCost() + b.matchCost()
    }
}
