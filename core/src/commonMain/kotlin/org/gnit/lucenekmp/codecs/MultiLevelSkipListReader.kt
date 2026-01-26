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
package org.gnit.lucenekmp.codecs

import okio.IOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.MathUtil

/**
 * This abstract class reads skip lists with multiple levels.
 *
 * See [MultiLevelSkipListWriter] for the information about the encoding of the multi level
 * skip lists.
 *
 * Subclasses must implement the abstract method [readSkipData] which
 * defines the actual format of the skip data.
 *
 * @lucene.experimental
 */
abstract class MultiLevelSkipListReader protected constructor(
    skipStream: IndexInput,
    maxSkipLevels: Int,
    skipInterval: Int,
    private val skipMultiplier: Int
) : AutoCloseable {
    /** the maximum number of skip levels possible for this index */
    protected var maxNumberOfSkipLevels: Int

    /** number of levels in this skip list */
    protected var numberOfSkipLevels: Int

    private var docCount: Int

    /** skipStream for each level. */
    private val skipStream: Array<IndexInput?>

    /** The start pointer of each skip level. */
    private val skipPointer: LongArray

    /** skipInterval of each level. */
    private val skipInterval: IntArray

    /**
     * Number of docs skipped per level. It's possible for some values to overflow a signed int, but
     * this has been accounted for.
     */
    private val numSkipped: IntArray

    /** Doc id of current skip entry per level. */
    protected val skipDoc: IntArray

    /** Doc id of last read skip entry with docId <= target. */
    private var lastDoc: Int

    /** Child pointer of current skip entry per level. */
    private val childPointer: LongArray

    /** childPointer of last read skip entry with docId <= target. */
    private var lastChildPointer: Long

    /**
     * Creates a `MultiLevelSkipListReader`.
     */
    protected constructor(skipStream: IndexInput, maxSkipLevels: Int, skipInterval: Int) : this(
        skipStream,
        maxSkipLevels,
        skipInterval,
        skipInterval
    )

    init {
        this.skipStream = arrayOfNulls(maxSkipLevels)
        this.skipPointer = LongArray(maxSkipLevels)
        this.childPointer = LongArray(maxSkipLevels)
        this.numSkipped = IntArray(maxSkipLevels)
        this.maxNumberOfSkipLevels = maxSkipLevels
        this.skipInterval = IntArray(maxSkipLevels)
        this.skipStream[0] = skipStream
        this.skipInterval[0] = skipInterval
        for (i in 1 until maxSkipLevels) {
            // cache skip intervals
            this.skipInterval[i] = this.skipInterval[i - 1] * skipMultiplier
        }
        skipDoc = IntArray(maxSkipLevels)
        numberOfSkipLevels = 0
        docCount = 0
        lastDoc = 0
        lastChildPointer = 0L
    }

    /** Returns the id of the doc to which the last call of [skipTo] has skipped. */
    val doc: Int
        get() = lastDoc

    /**
     * Skips entries to the first beyond the current whose document number is greater than or equal to
     * *target*. Returns the current doc count.
     */
    @Throws(IOException::class)
    open fun skipTo(target: Int): Int {
        // walk up the levels until highest level is found that has a skip
        // for this target
        var level = 0
        while (level < numberOfSkipLevels - 1 && target > skipDoc[level + 1]) {
            level++
        }

        while (level >= 0) {
            if (target > skipDoc[level]) {
                if (!loadNextSkip(level)) {
                    continue
                }
            } else {
                // no more skips on this level, go down one level
                if (level > 0 && lastChildPointer > skipStream[level - 1]!!.filePointer) {
                    seekChild(level - 1)
                }
                level--
            }
        }

        return numSkipped[0] - skipInterval[0] - 1
    }

    @Throws(IOException::class)
    private fun loadNextSkip(level: Int): Boolean {
        // we have to skip, the target document is greater than the current
        // skip list entry
        setLastSkipData(level)

        numSkipped[level] += skipInterval[level]

        // numSkipped may overflow a signed int, so compare as unsigned.
        if (numSkipped[level].toUInt() > docCount.toUInt()) {
            // this skip list is exhausted
            skipDoc[level] = Int.MAX_VALUE
            if (numberOfSkipLevels > level) numberOfSkipLevels = level
            return false
        }

        // read next skip entry
        skipDoc[level] += readSkipData(level, skipStream[level]!!)

        if (level != 0) {
            // read the child pointer if we are not on the leaf level
            childPointer[level] = readChildPointer(skipStream[level]!!) + skipPointer[level - 1]
        }

        return true
    }

    /** Seeks the skip entry on the given level */
    @Throws(IOException::class)
    protected fun seekChild(level: Int) {
        skipStream[level]!!.seek(lastChildPointer)
        numSkipped[level] = numSkipped[level + 1] - skipInterval[level + 1]
        skipDoc[level] = lastDoc
        if (level > 0) {
            childPointer[level] = readChildPointer(skipStream[level]!!) + skipPointer[level - 1]
        }
    }

    override fun close() {
        for (i in 1 until skipStream.size) {
            skipStream[i]?.close()
        }
    }

    /** Initializes the reader, for reuse on a new term. */
    @Throws(IOException::class)
    fun init(skipPointer: Long, df: Int) {
        this.skipPointer[0] = skipPointer
        this.docCount = df
        assert(skipPointer >= 0 && skipPointer <= skipStream[0]!!.length()) {
            "invalid skip pointer: $skipPointer, length=${skipStream[0]!!.length()}"
        }
        skipDoc.fill(0)
        numSkipped.fill(0)
        childPointer.fill(0)

        for (i in 1 until numberOfSkipLevels) {
            skipStream[i] = null
        }
        loadSkipLevels()
    }

    /** Loads the skip levels */
    @Throws(IOException::class)
    private fun loadSkipLevels() {
        numberOfSkipLevels = if (docCount <= skipInterval[0]) {
            1
        } else {
            1 + MathUtil.log(docCount / skipInterval[0], skipMultiplier)
        }

        if (numberOfSkipLevels > maxNumberOfSkipLevels) {
            numberOfSkipLevels = maxNumberOfSkipLevels
        }

        skipStream[0]!!.seek(skipPointer[0])

        for (i in numberOfSkipLevels - 1 downTo 1) {
            // the length of the current level
            val length = readLevelLength(skipStream[0]!!)

            // the start pointer of the current level
            skipPointer[i] = skipStream[0]!!.filePointer

            // clone this stream, it is already at the start of the current level
            skipStream[i] = skipStream[0]!!.clone()

            // move base stream beyond the current level
            skipStream[0]!!.seek(skipStream[0]!!.filePointer + length)
        }

        // use base stream for the lowest level
        skipPointer[0] = skipStream[0]!!.filePointer
    }

    /**
     * Subclasses must implement the actual skip data encoding in this method.
     *
     * @param level the level skip data shall be read from
     * @param skipStream the skip stream to read from
     */
    @Throws(IOException::class)
    protected abstract fun readSkipData(level: Int, skipStream: IndexInput): Int

    /**
     * read the length of the current level written via [
     * MultiLevelSkipListWriter.writeLevelLength].
     *
     * @param skipStream the IndexInput the length shall be read from
     * @return level length
     */
    @Throws(IOException::class)
    protected open fun readLevelLength(skipStream: IndexInput): Long {
        return skipStream.readVLong()
    }

    /**
     * read the child pointer written via [MultiLevelSkipListWriter.writeChildPointer].
     *
     * @param skipStream the IndexInput the child pointer shall be read from
     * @return child pointer
     */
    @Throws(IOException::class)
    protected open fun readChildPointer(skipStream: IndexInput): Long {
        return skipStream.readVLong()
    }

    /** Copies the values of the last read skip entry on this level */
    protected fun setLastSkipData(level: Int) {
        lastDoc = skipDoc[level]
        lastChildPointer = childPointer[level]
    }
}
