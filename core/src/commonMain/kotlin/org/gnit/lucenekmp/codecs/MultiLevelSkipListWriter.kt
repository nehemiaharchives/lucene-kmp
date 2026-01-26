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
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.MathUtil
import kotlin.math.min

/**
 * This abstract class writes skip lists with multiple levels.
 *
 * <pre>
 *
 * Example for skipInterval = 3:
 *                                                     c            (skip level 2)
 *                 c                 c                 c            (skip level 1)
 *     x     x     x     x     x     x     x     x     x     x      (skip level 0)
 * d d d d d d d d d d d d d d d d d d d d d d d d d d d d d d d d  (posting list)
 *     3     6     9     12    15    18    21    24    27    30     (df)
 *
 * d - document
 * x - skip data
 * c - skip data with child pointer
 *
 * Skip level i contains every skipInterval-th entry from skip level i-1.
 * Therefore the number of entries on level i is: floor(df / ((skipInterval ^ (i + 1))).
 *
 * Each skip entry on a level `i>0` contains a pointer to the corresponding skip entry in list i-1.
 * This guarantees a logarithmic amount of skips to find the target document.
 *
 * While this class takes care of writing the different skip levels,
 * subclasses must define the actual format of the skip data.
 * </pre>
 *
 * @lucene.experimental
 */
abstract class MultiLevelSkipListWriter protected constructor(
    private val skipInterval: Int,
    private val skipMultiplier: Int,
    maxSkipLevels: Int,
    df: Int
) {
    /** number of levels in this skip list */
    protected val numberOfSkipLevels: Int

    /** for every skip level a different buffer is used */
    private var skipBuffer: Array<ByteBuffersDataOutput>? = null

    /** Length of the window at which the skips are placed on skip level 1 */
    private val windowLength: Int

    /**
     * Creates a `MultiLevelSkipListWriter`, where `skipInterval` and `skipMultiplier` are the same.
     */
    protected constructor(skipInterval: Int, maxSkipLevels: Int, df: Int) : this(
        skipInterval,
        skipInterval,
        maxSkipLevels,
        df
    )

    init {
        // calculate the maximum number of skip levels for this document frequency
        numberOfSkipLevels = if (df > skipInterval) {
            // also make sure it does not exceed maxSkipLevels
            min(1 + MathUtil.log(df / skipInterval, skipMultiplier), maxSkipLevels)
        } else {
            1
        }
        windowLength = Math.toIntExact(skipInterval.toLong() * skipMultiplier.toLong())
    }

    /** Allocates internal skip buffers. */
    protected fun init() {
        skipBuffer = Array(numberOfSkipLevels) { ByteBuffersDataOutput.newResettableInstance() }
    }

    /** Creates new buffers or empties the existing ones */
    protected open fun resetSkip() {
        if (skipBuffer == null) {
            init()
        } else {
            for (i in skipBuffer!!.indices) {
                skipBuffer!![i].reset()
            }
        }
    }

    /**
     * Subclasses must implement the actual skip data encoding in this method.
     *
     * @param level the level skip data shall be writing for
     * @param skipBuffer the skip buffer to write to
     */
    @Throws(IOException::class)
    protected abstract fun writeSkipData(level: Int, skipBuffer: DataOutput)

    /**
     * Writes the current skip data to the buffers. The current document frequency determines the max
     * level is skip data is to be written to.
     *
     * @param df the current document frequency
     * @throws IOException If an I/O error occurs
     */
    @Throws(IOException::class)
    fun bufferSkip(df: Int) {
        assert(df % skipInterval == 0)
        var numLevels = 1
        // This optimizes the most common case i.e. numLevels = 1, it does a single modulo check to
        // catch that case
        var dfVar = df
        if (dfVar % windowLength == 0) {
            numLevels++
            dfVar /= windowLength
            // determine max level
            while ((dfVar % skipMultiplier) == 0 && numLevels < numberOfSkipLevels) {
                numLevels++
                dfVar /= skipMultiplier
            }
        }

        var childPointer = 0L

        val skipBuffer = requireNotNull(skipBuffer)
        for (level in 0 until numLevels) {
            writeSkipData(level, skipBuffer[level])

            val newChildPointer = skipBuffer[level].size()

            if (level != 0) {
                // store child pointers for all levels except the lowest
                writeChildPointer(childPointer, skipBuffer[level])
            }

            // remember the childPointer for the next level
            childPointer = newChildPointer
        }
    }

    /**
     * Writes the buffered skip lists to the given output.
     *
     * @param output the IndexOutput the skip lists shall be written to
     * @return the pointer the skip list starts
     */
    @Throws(IOException::class)
    open fun writeSkip(output: IndexOutput): Long {
        val skipPointer = output.filePointer
        // System.out.println("skipper.writeSkip fp=" + skipPointer);
        val skipBuffer = this.skipBuffer
        if (skipBuffer == null || skipBuffer.isEmpty()) return skipPointer

        for (level in numberOfSkipLevels - 1 downTo 1) {
            val length = skipBuffer[level].size()
            if (length > 0) {
                writeLevelLength(length, output)
                skipBuffer[level].copyTo(output)
            }
        }
        skipBuffer[0].copyTo(output)

        return skipPointer
    }

    /**
     * Writes the length of a level to the given output.
     *
     * @param levelLength the length of a level
     * @param output the IndexOutput the length shall be written to
     */
    @Throws(IOException::class)
    protected open fun writeLevelLength(levelLength: Long, output: IndexOutput) {
        output.writeVLong(levelLength)
    }

    /**
     * Writes the child pointer of a block to the given output.
     *
     * @param childPointer block of higher level point to the lower level
     * @param skipBuffer the skip buffer to write to
     */
    @Throws(IOException::class)
    protected open fun writeChildPointer(childPointer: Long, skipBuffer: DataOutput) {
        skipBuffer.writeVLong(childPointer)
    }
}
