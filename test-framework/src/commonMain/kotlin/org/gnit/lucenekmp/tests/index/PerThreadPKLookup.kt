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
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef

/**
 * Utility class to do efficient primary-key (only 1 doc contains the given term) lookups by
 * segment, re-using the enums. This class is not thread safe, so it is the caller's job to create
 * and use one instance of this per thread. Do not use this if a term may appear in more than one
 * document! It will only return the first one it finds.
 */
class PerThreadPKLookup
@Throws(IOException::class)
constructor(
    reader: IndexReader,
    private val idFieldName: String,
    prevEnumIndexes: Map<IndexReader.CacheKey, Int> = emptyMap(),
    reusableTermsEnums: Array<TermsEnum?> = emptyArray(),
    reusablePostingsEnums: Array<PostingsEnum?> = emptyArray(),
) {
    protected val termsEnums: Array<TermsEnum?>
    protected val postingsEnums: Array<PostingsEnum?>
    protected val liveDocs: Array<Bits?>
    protected val docBases: IntArray
    protected val numEnums: Int
    protected val hasDeletions: Boolean
    private val enumIndexes: MutableMap<IndexReader.CacheKey, Int>

    init {
        val leaves = reader.leaves().toMutableList()
        // Larger segments are more likely to have the id, so we sort largest to smallest by numDocs:
        leaves.sortByDescending { it.reader().numDocs() }

        termsEnums = arrayOfNulls(leaves.size)
        postingsEnums = arrayOfNulls(leaves.size)
        liveDocs = arrayOfNulls(leaves.size)
        docBases = IntArray(leaves.size)
        enumIndexes = HashMap()
        var numEnums = 0
        var hasDeletions = false

        for (i in leaves.indices) {
            val context = leaves[i]
            val leafReader = context.reader()
            val cacheHelper = leafReader.coreCacheHelper
            val cacheKey = cacheHelper?.key

            if (cacheKey != null && prevEnumIndexes.containsKey(cacheKey)) {
                // Reuse termsEnum, postingsEnum.
                val seg = prevEnumIndexes[cacheKey]!!
                termsEnums[numEnums] = reusableTermsEnums[seg]
                postingsEnums[numEnums] = reusablePostingsEnums[seg]
            } else {
                // New or empty segment.
                val terms = leafReader.terms(idFieldName)
                if (terms != null) {
                    termsEnums[numEnums] = terms.iterator()
                    checkNotNull(termsEnums[numEnums])
                }
            }

            if (termsEnums[numEnums] != null) {
                if (cacheKey != null) {
                    enumIndexes[cacheKey] = numEnums
                }

                docBases[numEnums] = context.docBase
                liveDocs[numEnums] = leafReader.liveDocs
                hasDeletions = hasDeletions or leafReader.hasDeletions()
                numEnums++
            }
        }

        this.numEnums = numEnums
        this.hasDeletions = hasDeletions
    }

    /** Returns docID if found, else -1. */
    @Throws(IOException::class)
    fun lookup(id: BytesRef): Int {
        for (seg in 0..<numEnums) {
            if (termsEnums[seg]!!.seekExact(id)) {
                postingsEnums[seg] = termsEnums[seg]!!.postings(postingsEnums[seg], 0)
                var docID = -1
                while ((postingsEnums[seg]!!.nextDoc().also { docID = it }) != DocIdSetIterator.NO_MORE_DOCS) {
                    if (liveDocs[seg] == null || liveDocs[seg]!!.get(docID)) {
                        return docBases[seg] + docID
                    }
                }
                assert(hasDeletions)
            }
        }

        return -1
    }

    /** Reuse previous PerThreadPKLookup's termsEnum and postingsEnum. */
    @Throws(IOException::class)
    fun reopen(reader: IndexReader?): PerThreadPKLookup? {
        if (reader == null) {
            return null
        }
        return PerThreadPKLookup(reader, idFieldName, enumIndexes, termsEnums, postingsEnums)
    }
}
