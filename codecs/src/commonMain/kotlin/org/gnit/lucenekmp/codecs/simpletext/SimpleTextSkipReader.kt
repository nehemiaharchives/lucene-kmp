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
package org.gnit.lucenekmp.codecs.simpletext

import okio.IOException
import org.gnit.lucenekmp.codecs.MultiLevelSkipListReader
import org.gnit.lucenekmp.index.Impact
import org.gnit.lucenekmp.index.Impacts
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.BufferedChecksumIndexInput
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.CharsRefBuilder
import org.gnit.lucenekmp.util.StringHelper

/**
 * This class reads skip lists with multiple levels.
 *
 * <p>See [SimpleTextFieldsWriter] for the information about the encoding of the multi level
 * skip lists.
 *
 * @lucene.experimental
 */
internal class SimpleTextSkipReader(skipStream: IndexInput) :
    MultiLevelSkipListReader(
        skipStream,
        SimpleTextSkipWriter.maxSkipLevels,
        SimpleTextSkipWriter.BLOCK_SIZE,
        SimpleTextSkipWriter.skipMultiplier
    ) {

    private val scratchUTF16 = CharsRefBuilder()
    private val scratch = BytesRefBuilder()
    private val impacts: Impacts
    private var perLevelImpacts: MutableList<MutableList<Impact>> = ArrayList()
    private var nextSkipDocFP = -1L
    private var numLevels = 1
    private var hasSkipList = false

    init {
        impacts =
            object : Impacts() {
                override fun numLevels(): Int {
                    return numLevels
                }

                override fun getDocIdUpTo(level: Int): Int {
                    return skipDoc[level]
                }

                override fun getImpacts(level: Int): MutableList<Impact> {
                    assert(level < numLevels)
                    return perLevelImpacts[level]
                }
            }
        init()
    }

    @Throws(IOException::class)
    override fun skipTo(target: Int): Int {
        if (!hasSkipList) {
            return -1
        }
        val result = super.skipTo(target)
        if (numberOfSkipLevels > 0) {
            numLevels = numberOfSkipLevels
        } else {
            // End of postings don't have skip data anymore, so we fill with dummy data
            // like SlowImpactsEnum.
            numLevels = 1
            perLevelImpacts.add(0, mutableListOf(Impact(Int.MAX_VALUE, 1L)))
        }
        return result
    }

    @Throws(IOException::class)
    override fun readSkipData(level: Int, skipStream: IndexInput): Int {
        perLevelImpacts[level].clear()
        var skipDoc = DocIdSetIterator.NO_MORE_DOCS
        val input: ChecksumIndexInput = BufferedChecksumIndexInput(skipStream)
        var freq = 1
        while (true) {
            SimpleTextUtil.readLine(input, scratch)
            if (scratch.get() == SimpleTextFieldsWriter.END) {
                SimpleTextUtil.checkFooter(input)
                break
            } else if (
                scratch.get() == SimpleTextSkipWriter.IMPACTS_END ||
                scratch.get() == SimpleTextFieldsWriter.TERM ||
                scratch.get() == SimpleTextFieldsWriter.FIELD
            ) {
                break
            } else if (StringHelper.startsWith(scratch.get(), SimpleTextSkipWriter.SKIP_LIST)) {
                // continue
            } else if (StringHelper.startsWith(scratch.get(), SimpleTextSkipWriter.SKIP_DOC)) {
                scratchUTF16.copyUTF8Bytes(
                    scratch.bytes(),
                    SimpleTextSkipWriter.SKIP_DOC.length,
                    scratch.length() - SimpleTextSkipWriter.SKIP_DOC.length
                )
                skipDoc = ArrayUtil.parseInt(scratchUTF16.chars(), 0, scratchUTF16.length())
                // Because the MultiLevelSkipListReader stores doc id delta,but simple text codec stores doc
                // id
                skipDoc -= super.skipDoc[level]
            } else if (StringHelper.startsWith(scratch.get(), SimpleTextSkipWriter.SKIP_DOC_FP)) {
                scratchUTF16.copyUTF8Bytes(
                    scratch.bytes(),
                    SimpleTextSkipWriter.SKIP_DOC_FP.length,
                    scratch.length() - SimpleTextSkipWriter.SKIP_DOC_FP.length
                )
                nextSkipDocFP =
                    ArrayUtil.parseInt(scratchUTF16.chars(), 0, scratchUTF16.length()).toLong()
            } else if (
                StringHelper.startsWith(scratch.get(), SimpleTextSkipWriter.IMPACTS) ||
                StringHelper.startsWith(scratch.get(), SimpleTextSkipWriter.IMPACT)
            ) {
                // continue;
            } else if (StringHelper.startsWith(scratch.get(), SimpleTextSkipWriter.FREQ)) {
                scratchUTF16.copyUTF8Bytes(
                    scratch.bytes(),
                    SimpleTextSkipWriter.FREQ.length,
                    scratch.length() - SimpleTextSkipWriter.FREQ.length
                )
                freq = ArrayUtil.parseInt(scratchUTF16.chars(), 0, scratchUTF16.length())
            } else if (StringHelper.startsWith(scratch.get(), SimpleTextSkipWriter.NORM)) {
                scratchUTF16.copyUTF8Bytes(
                    scratch.bytes(),
                    SimpleTextSkipWriter.NORM.length,
                    scratch.length() - SimpleTextSkipWriter.NORM.length
                )
                val norm = scratchUTF16.toString().toLong()
                val impact = Impact(freq, norm)
                perLevelImpacts[level].add(impact)
            }
        }
        return skipDoc
    }

    @Throws(IOException::class)
    override fun readLevelLength(skipStream: IndexInput): Long {
        SimpleTextUtil.readLine(skipStream, scratch)
        scratchUTF16.copyUTF8Bytes(
            scratch.bytes(),
            SimpleTextSkipWriter.LEVEL_LENGTH.length,
            scratch.length() - SimpleTextSkipWriter.LEVEL_LENGTH.length
        )
        return scratchUTF16.toString().toLong()
    }

    @Throws(IOException::class)
    override fun readChildPointer(skipStream: IndexInput): Long {
        SimpleTextUtil.readLine(skipStream, scratch)
        scratchUTF16.copyUTF8Bytes(
            scratch.bytes(),
            SimpleTextSkipWriter.CHILD_POINTER.length,
            scratch.length() - SimpleTextSkipWriter.CHILD_POINTER.length
        )
        return scratchUTF16.toString().toLong()
    }

    @Throws(IOException::class)
    fun reset(skipPointer: Long, docFreq: Int) {
        init()
        if (skipPointer > 0) {
            super.init(skipPointer, docFreq)
            hasSkipList = true
        }
    }

    private fun init() {
        nextSkipDocFP = -1
        numLevels = 1
        perLevelImpacts = ArrayList(maxNumberOfSkipLevels)
        for (level in 0 until maxNumberOfSkipLevels) {
            val impacts = ArrayList<Impact>()
            impacts.add(Impact(Int.MAX_VALUE, 1L))
            perLevelImpacts.add(level, impacts)
        }
        hasSkipList = false
    }

    fun getImpacts(): Impacts {
        return impacts
    }

    fun getNextSkipDocFP(): Long {
        return nextSkipDocFP
    }

    fun getNextSkipDoc(): Int {
        if (!hasSkipList) {
            return DocIdSetIterator.NO_MORE_DOCS
        }
        return skipDoc[0]
    }

    fun hasSkipList(): Boolean {
        return hasSkipList
    }
}
