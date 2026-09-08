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
package org.gnit.lucenekmp.facet

import org.gnit.lucenekmp.util.PriorityQueue

/** Keeps highest results, first by largest value, then tie-break by smallest ord. */
abstract class TopOrdAndNumberQueue : PriorityQueue<TopOrdAndNumberQueue.OrdAndValue> {
    /** Holds a single entry. */
    abstract class OrdAndValue {
        /** Ordinal of the entry. */
        var ord: Int = 0

        /** Default constructor. */
        constructor()

        /** Compare with another [OrdAndValue]. */
        abstract fun lessThan(other: OrdAndValue): Boolean

        /** Get the value stored in this [OrdAndValue]. */
        abstract fun getValue(): Number
    }

    /** Sole constructor. */
    constructor(topN: Int) : super(topN)

    override fun lessThan(a: OrdAndValue, b: OrdAndValue): Boolean = a.lessThan(b)

    /**
   * Create a new [org.gnit.lucenekmp.facet.TopOrdAndNumberQueue.OrdAndValue] of the
   * appropriate type.
   */
    abstract fun newOrdAndValue(): OrdAndValue
}
