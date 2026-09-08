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

import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.jdkport.assert

/**
 * Utility class with a single method for getting a DocIdSetIterator that skips deleted docs
 *
 * @lucene.experimental
 */
class FacetUtils /** Do not instantiate this class */ private constructor() {
    companion object {
        /**
   * Wrap the given DocIdSetIterator and liveDocs into another DocIdSetIterator that returns
   * non-deleted documents during iteration. This is useful for computing facet counts on match-all
   * style queries that need to exclude deleted documents.
   *
   * [org.gnit.lucenekmp.search.ConjunctionUtils] could be better home for this method if we
   * can identify use cases outside facets module.
   *
   * Making this class pkg-private unfortunately limits the visibility of this method to [org.gnit.lucenekmp.facet.sortedset.SortedSetDocValuesFacetCounts] and [org.gnit.lucenekmp.facet.sortedset.ConcurrentSortedSetDocValuesFacetCounts] classes as Java does
   * not allow pkg-private classes to be visible to sub-packages.
   *
   * @param it [DocIdSetIterator] being wrapped
   * @param liveDocs [Bits] containing set bits for non-deleted docs
   * @return wrapped iterator
   */
        fun liveDocsDISI(it: DocIdSetIterator, liveDocs: Bits): DocIdSetIterator {
            return object : DocIdSetIterator() {
                override fun docID(): Int {
                    return it.docID()
                }

                private fun doNext(doc: Int): Int {
                    var doc = doc
                    assert(doc == it.docID())
                    // Find next document that is not deleted until we exhaust all documents
                    while (doc != NO_MORE_DOCS && liveDocs.get(doc) == false) {
                        doc = it.nextDoc()
                    }
                    return doc
                }

                override fun nextDoc(): Int {
                    return doNext(it.nextDoc())
                }

                override fun advance(target: Int): Int {
                    return doNext(it.advance(target))
                }

                override fun cost(): Long {
                    return it.cost()
                }
            }
        }
    }
}
