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
package org.gnit.lucenekmp.facet.taxonomy.writercache

/**
 * Similar to [StringBuilder], but with a more efficient growing strategy. This class uses
 * char array blocks to grow.
 *
 * @lucene.experimental
 */
internal class CharBlockArray(blockSize: Int = DefaultBlockSize) : Appendable, CharSequence {
    internal class Block(size: Int) {
        val chars = CharArray(size)
        var length = 0
    }

    var blocks = ArrayList<Block>()
    lateinit var current: Block
    var blockSize = blockSize
    override var length = 0

    init {
        addBlock()
    }

    private fun addBlock() {
        if (blockSize.toLong() * (blocks.size + 1) > Int.MAX_VALUE) {
            throw IllegalStateException("cannot store more than 2 GB in CharBlockArray")
        }
        current = Block(blockSize)
        blocks.add(current)
    }

    fun blockIndex(index: Int): Int = index / blockSize
    fun indexInBlock(index: Int): Int = index % blockSize

    override fun append(chars: CharSequence?): CharBlockArray {
        return append(chars, 0, chars!!.length)
    }

    override fun append(c: Char): CharBlockArray {
        if (current.length == blockSize) {
            addBlock()
        }
        current.chars[current.length++] = c
        length++
        return this
    }

    override fun append(chars: CharSequence?, start: Int, length: Int): CharBlockArray {
        // Upstream treats the third argument as a length, despite Appendable's end-index contract.
        val end = start + length
        for (i in start until end) {
            append(chars!![i])
        }
        return this
    }

    fun append(chars: CharArray, start: Int, length: Int): CharBlockArray {
        var offset = start
        var remain = length
        while (remain > 0) {
            if (current.length == blockSize) {
                addBlock()
            }
            var toCopy = remain
            val remainingInBlock = blockSize - current.length
            if (remainingInBlock < toCopy) {
                toCopy = remainingInBlock
            }
            chars.copyInto(current.chars, current.length, offset, offset + toCopy)
            offset += toCopy
            remain -= toCopy
            current.length += toCopy
        }

        this.length += length
        return this
    }

    fun append(s: String): CharBlockArray {
        var remain = s.length
        var offset = 0
        while (remain > 0) {
            if (current.length == blockSize) {
                addBlock()
            }
            var toCopy = remain
            val remainingInBlock = blockSize - current.length
            if (remainingInBlock < toCopy) {
                toCopy = remainingInBlock
            }
            s.toCharArray(current.chars, current.length, offset, offset + toCopy)
            offset += toCopy
            remain -= toCopy
            current.length += toCopy
        }

        length += s.length
        return this
    }

    override fun get(index: Int): Char {
        val b = blocks[blockIndex(index)]
        return b.chars[indexInBlock(index)]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        var remaining = endIndex - startIndex
        val sb = StringBuilder(remaining)
        var blockIdx = blockIndex(startIndex)
        var indexInBlock = indexInBlock(startIndex)
        while (remaining > 0) {
            val b = blocks[blockIdx++]
            val numToAppend = minOf(remaining, b.length - indexInBlock)
            sb.appendRange(b.chars, indexInBlock, indexInBlock + numToAppend)
            remaining -= numToAppend
            indexInBlock = 0 // 2nd+ iterations read from start of the block
        }
        return sb.toString()
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (b in blocks) {
            sb.appendRange(b.chars, 0, b.length)
        }
        return sb.toString()
    }

    companion object {
        private const val DefaultBlockSize = 32 * 1024 // 32 KB default size
    }
}
