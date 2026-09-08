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

import org.gnit.lucenekmp.search.DoubleValues

/**
 * Per-segment, per-document double values, which can be calculated at search-time. Documents may
 * produce multiple values. See also [DoubleValues] for a single-valued version.
 *
 * Currently meant only for use within the faceting module. Could be further generalized and made
 * available for more use-cases outside faceting if there is a desire to do so.
 *
 * @lucene.experimental
 */
abstract class MultiDoubleValues {
    /** Instantiates a new MultiDoubleValues */
    constructor()

    /**
   * Retrieves the number of values for the current document. This must always be greater than zero.
   * It is illegal to call this method after [advanceExact] returned `false`.
   */
    abstract fun getValueCount(): Long

    /**
   * Iterates to the next value in the current document. Do not call this more than [getValueCount] times for the document.
   */
    abstract fun nextValue(): Double

    /** Advance to exactly `doc` and return whether `doc` has a value. */
    abstract fun advanceExact(doc: Int): Boolean
}
