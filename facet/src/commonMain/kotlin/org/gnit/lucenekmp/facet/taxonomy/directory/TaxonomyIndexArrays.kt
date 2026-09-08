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
package org.gnit.lucenekmp.facet.taxonomy.directory

import org.gnit.lucenekmp.facet.taxonomy.ParallelTaxonomyArrays
import org.gnit.lucenekmp.facet.taxonomy.TaxonomyReader
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.internal.hppc.IntHashSet
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.Accountables
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.assert
import kotlin.concurrent.Volatile
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.jdkport.UnmodifiableMutableList

/**
 * A [ParallelTaxonomyArrays] that are initialized from the taxonomy index.
 *
 * @lucene.experimental
 */
internal class TaxonomyIndexArrays : ParallelTaxonomyArrays, Accountable {
    private val parents: ChunkedIntArray

    // the following two arrays are lazily initialized. note that we only keep a
    // single boolean member as volatile, instead of declaring the arrays
    // volatile. the code guarantees that only after the boolean is set to true,
    // the arrays are returned.
    @Volatile private var initializedChildren = false
    private var children: ChunkedIntArray? = null
    private var siblings: ChunkedIntArray? = null
    private val lock = ReentrantLock()

    class ChunkedIntArray(val values: Array<kotlin.IntArray>) : ParallelTaxonomyArrays.IntArray() {
        override fun get(i: Int): Int = values[i shr CHUNK_SIZE_BITS][i and CHUNK_MASK]
        fun set(i: Int, `val`: Int) { values[i shr CHUNK_SIZE_BITS][i and CHUNK_MASK] = `val` }
        override fun length(): Int = ((values.size - 1) shl CHUNK_SIZE_BITS) + values[values.size - 1].size
    }

    /** Used by [add] after the array grew. Also, used for testing. */
    constructor(parents: Array<kotlin.IntArray>) {
        this.parents = ChunkedIntArray(parents)
    }

    constructor(reader: IndexReader) {
        val parentArray = allocateChunkedArray(reader.maxDoc(), 0)
        assert(parentArray.isNotEmpty())
        if (parentArray[0].isNotEmpty()) {
            initParents(parentArray, reader, 0)
            parentArray[0][0] = TaxonomyReader.INVALID_ORDINAL
        }
        parents = ChunkedIntArray(parentArray)
    }

    constructor(reader: IndexReader, copyFrom: TaxonomyIndexArrays) {
        // note that copyParents.length may be equal to reader.maxDoc(). this is not a bug
        // it may be caused if e.g. the taxonomy segments were merged, and so an updated
        // NRT reader was obtained, even though nothing was changed. this is not very likely
        // to happen.
        val parentArray = allocateChunkedArray(reader.maxDoc(), copyFrom.parents.values.size - 1)
        assert(parentArray.isNotEmpty())

        copyChunkedArray(copyFrom.parents.values, parentArray)
        initParents(parentArray, reader, copyFrom.parents.length())
        parents = ChunkedIntArray(parentArray)
        if (copyFrom.initializedChildren) {
            initChildrenSiblings(copyFrom)
        }
    }

    private fun initChildrenSiblings(copyFrom: TaxonomyIndexArrays?) = lock.withLock {
        if (!initializedChildren) { // must do this check !
            val startFrom: Int
            if (copyFrom == null) {
                startFrom = 0
            } else {
                startFrom = copyFrom.parents.values.size - 1
            }
            val childrenArray = allocateChunkedArray(parents.length(), startFrom)
            val siblingsArray = allocateChunkedArray(parents.length(), startFrom)
            // Rely on these arrays being copied by reference, since we may modify them below
            children = ChunkedIntArray(childrenArray)
            siblings = ChunkedIntArray(siblingsArray)
            if (copyFrom != null) {
                // called from the ctor, after we know copyFrom has initialized children/siblings
                copyChunkedArray(copyFrom.children!!.values, childrenArray)
                copyChunkedArray(copyFrom.siblings!!.values, siblingsArray)
                computeChildrenSiblings(copyFrom.parents.length())
            } else {
                computeChildrenSiblings(0)
            }
            initializedChildren = true
        }
    }

    private fun computeChildrenSiblings(first: Int) {
        var first = first
        // reset the youngest child of all ordinals. while this should be done only
        // for the leaves, we don't know up front which are the leaves, so we reset
        // all of them.
        val length = parents.length()
        for (i in first until length) {
            children!!.set(i, TaxonomyReader.INVALID_ORDINAL)
        }

        // the root category has no parent, and therefore no siblings
        if (first == 0) {
            first = 1
            siblings!!.set(0, TaxonomyReader.INVALID_ORDINAL)
        }

        val firstChunkStart = first - (first and CHUNK_MASK)
        val reallocatedChildChunks = IntHashSet()
        for (i in first until length) {
            val parent = parents.get(i)
            // The existing youngest child of the parent is the next older sibling of i.
            // note that parents[i] is always < i, so the right-hand-side of
            // the following line is already set when we get here
            siblings!!.set(i, children!!.get(parent))
            // The new youngest child of the parent is i.
            if (parent < firstChunkStart) {
                val chunkIdx = parent shr CHUNK_SIZE_BITS
                if (reallocatedChildChunks.contains(chunkIdx) == false) {
                    reallocatedChildChunks.add(chunkIdx)
                    val oldChildren = children!!.values[chunkIdx]
                    children!!.values[chunkIdx] = kotlin.IntArray(CHUNK_SIZE)
                    oldChildren.copyInto(children!!.values[chunkIdx])
                }
            }
            children!!.set(parent, i)
        }
    }

    /**
   * Adds the given ordinal/parent info and returns either a new instance if the underlying array
   * had to grow, or this instance otherwise.
   *
   * **NOTE:** you should call this method from a thread-safe code.
   */
    fun add(ordinal: Int, parentOrdinal: Int): TaxonomyIndexArrays {
        if (ordinal >= parents.length()) {
            val newParents = allocateChunkedArray(
                ArrayUtil.oversize(ordinal + 1, Int.SIZE_BYTES), parents.values.size - 1)
            copyChunkedArray(parents.values, newParents)
            newParents[ordinal shr CHUNK_SIZE_BITS][ordinal and CHUNK_MASK] = parentOrdinal
            return TaxonomyIndexArrays(newParents)
        }
        parents.set(ordinal, parentOrdinal)
        return this
    }

    /**
   * Returns the parents array, where `parents[i]` denotes the parent of category ordinal
   * `i`.
   */
    override fun parents(): ChunkedIntArray = parents

    /**
   * Returns the children array, where `children[i]` denotes the youngest child of category
   * ordinal `i`. The youngest child is defined as the category that was added last to the
   * taxonomy as an immediate child of `i`.
   */
    override fun children(): ChunkedIntArray {
        if (!initializedChildren) {
            initChildrenSiblings(null)
        }
        // the array is guaranteed to be populated
        return children!!
    }

    /**
   * Returns the siblings array, where `siblings[i]` denotes the sibling of category ordinal
   * `i`. The sibling is defined as the previous youngest child of `parents[i]`.
   */
    override fun siblings(): ChunkedIntArray {
        if (!initializedChildren) {
            initChildrenSiblings(null)
        }
        // the array is guaranteed to be populated
        return siblings!!
    }

    override fun ramBytesUsed(): Long = lock.withLock {
        var ramBytesUsed = RamUsageEstimator.NUM_BYTES_OBJECT_HEADER + 3L * RamUsageEstimator.NUM_BYTES_OBJECT_REF + 1
        ramBytesUsed += RamUsageEstimator.shallowSizeOf(parents)
        if (children != null) ramBytesUsed += RamUsageEstimator.shallowSizeOf(children)
        if (siblings != null) ramBytesUsed += RamUsageEstimator.shallowSizeOf(siblings)
        ramBytesUsed
    }

    override val childResources: MutableCollection<Accountable>
        get() = lock.withLock {
        val resources = ArrayList<Accountable>()
        resources.add(Accountables.namedAccountable("parents", RamUsageEstimator.shallowSizeOf(parents)))
        if (children != null) resources.add(Accountables.namedAccountable("children", RamUsageEstimator.shallowSizeOf(children)))
        if (siblings != null) resources.add(Accountables.namedAccountable("siblings", RamUsageEstimator.shallowSizeOf(siblings)))
        UnmodifiableMutableList(resources)
    }

    companion object {
        private const val CHUNK_SIZE_BITS = 13
        const val CHUNK_SIZE = 1 shl CHUNK_SIZE_BITS
        private const val CHUNK_MASK = CHUNK_SIZE - 1

        private fun allocateChunkedArray(size: Int, startFrom: Int): Array<kotlin.IntArray> {
            val chunkCount = (size shr CHUNK_SIZE_BITS) + 1
            // The earlier chunks are replaced by shared chunks in copyChunkedArray.
            return Array(chunkCount) { i ->
                when {
                    i == chunkCount - 1 -> kotlin.IntArray(size and CHUNK_MASK)
                    i >= startFrom -> kotlin.IntArray(CHUNK_SIZE)
                    else -> kotlin.IntArray(0)
                }
            }
        }

        private fun copyChunkedArray(oldArray: Array<kotlin.IntArray>, newArray: Array<kotlin.IntArray>) {
            // Copy all but the last (maybe partial) chunk from the old array
            if (oldArray.size > 1) oldArray.copyInto(newArray, 0, 0, oldArray.size - 1)
            val lastCopyChunk = oldArray[oldArray.size - 1]
            lastCopyChunk.copyInto(newArray[oldArray.size - 1])
        }

        // Read the parents of the new categories
        private fun initParents(parentsArray: Array<kotlin.IntArray>, reader: IndexReader, first: Int) {
            if (reader.maxDoc() == first) return
            for (leafContext in reader.leaves()) {
                val leafDocNum = leafContext.reader().maxDoc()
                if (leafContext.docBase + leafDocNum <= first) {
                    // skip this leaf if it does not contain new categories
                    continue
                }
                val parentValues = leafContext.reader().getNumericDocValues(Consts.FIELD_PARENT_ORDINAL_NDV)
                if (parentValues == null) {
                    throw CorruptIndexException("Parent data field ${Consts.FIELD_PARENT_ORDINAL_NDV} does not exist", leafContext.reader().toString())
                }
                for (doc in maxOf(first - leafContext.docBase, 0) until leafDocNum) {
                    if (parentValues.advanceExact(doc) == false) {
                        throw CorruptIndexException("Missing parent data for category ${doc + leafContext.docBase}", reader.toString())
                    }
                    val pos = doc + leafContext.docBase
                    val parent = parentValues.longValue()
                    if (parent.toInt().toLong() != parent) throw ArithmeticException("integer overflow")
                    parentsArray[pos shr CHUNK_SIZE_BITS][pos and CHUNK_MASK] = parent.toInt()
                }
            }
        }
    }
}
