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

/** Keeps highest results, first by largest int value, then tie-break by smallest ord. */
open class TopOrdAndIntQueue : TopOrdAndNumberQueue {
    /** Sole constructor. */
    constructor(topN: Int) : super(topN)

    /** Holds an ordinal and an int value. */
    class OrdAndInt : OrdAndValue {
        /** The value corresponding to the ordinal is an int. */
        @kotlin.jvm.JvmField
        var value: Int = 0

        /** Default constructor. */
        constructor()

        override fun lessThan(other: OrdAndValue): Boolean {
            val otherOrdAndInt = other as OrdAndInt
            if (value < otherOrdAndInt.value) {
                return true
            }
            if (value > otherOrdAndInt.value) {
                return false
            }
            return ord > otherOrdAndInt.ord
        }

        override fun getValue(): Number = value
    }

    override fun newOrdAndValue(): OrdAndValue = OrdAndInt()
}
