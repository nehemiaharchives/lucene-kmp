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
package org.gnit.lucenekmp.facet.taxonomy

/**
 * Returns 3 arrays for traversing the taxonomy:
 *
 * <ul>
 *   <li>`parents`: `parents[i]` denotes the parent of category ordinal `i`.
 *   <li>`children`: `children[i]` denotes a child of category ordinal `i`.
 *   <li>`siblings`: `siblings[i]` denotes the sibling of category ordinal `i`.
 * </ul>
 *
 * To traverse the taxonomy tree, you typically start with `children[0]` (ordinal 0 is
 * reserved for ROOT), and then depends if you want to do DFS or BFS, you call `* children[children[0]]` or `siblings[children[0]]` and so forth, respectively.
 *
 * **NOTE:** you are not expected to modify the values of the arrays, since the arrays are
 * shared with other threads.
 *
 * @lucene.experimental
 */
abstract class ParallelTaxonomyArrays {
  /** Abstraction that looks like an int[], but read-only. */
  abstract class IntArray {
    /** Sole constructor. */
    constructor()

    /**
     * Equivalent to array[i].
     *
     * @param i the index of the value to retrieve
     * @return the value at position i
     */
    abstract fun get(i: Int): Int

    /**
     * Equivalent to array.length.
     *
     * @return the allocated size of the array
     */
    abstract fun length(): Int
  }

  /** Sole constructor * */
  constructor()

  /**
   * Returns the parents array, where `parents[i]` denotes the parent of category ordinal
   * `i`.
   */
  abstract fun parents(): IntArray

  /**
   * Returns the children array, where `children[i]` denotes a child of category ordinal
   * `i`.
   */
  abstract fun children(): IntArray

  /**
   * Returns the siblings array, where `siblings[i]` denotes the sibling of category ordinal
   * `i`.
   */
  abstract fun siblings(): IntArray
}
