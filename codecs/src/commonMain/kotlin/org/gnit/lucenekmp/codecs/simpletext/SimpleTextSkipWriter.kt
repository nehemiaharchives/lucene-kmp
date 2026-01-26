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
import org.gnit.lucenekmp.codecs.CompetitiveImpactAccumulator
import org.gnit.lucenekmp.codecs.MultiLevelSkipListWriter
import org.gnit.lucenekmp.index.Impact
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder

/**
 * plain text skip data.
 *
 * @lucene.experimental
 */
internal class SimpleTextSkipWriter(writeState: SegmentWriteState) :
    MultiLevelSkipListWriter(BLOCK_SIZE, skipMultiplier, maxSkipLevels, writeState.segmentInfo.maxDoc()) {

    private val wroteHeaderPerLevelMap: MutableMap<Int, Boolean> = HashMap()
    private var curDoc = 0
    private var curDocFilePointer = 0L
    private val curCompetitiveFreqNorms: Array<CompetitiveImpactAccumulator>
    private val scratch = BytesRefBuilder()

    init {
        curCompetitiveFreqNorms = Array(maxSkipLevels) { CompetitiveImpactAccumulator() }
        resetSkip()
    }

    @Throws(IOException::class)
    override fun writeSkipData(level: Int, skipBuffer: DataOutput) {
        val wroteHeader = wroteHeaderPerLevelMap[level] ?: false
        if (!wroteHeader) {
            SimpleTextUtil.write(skipBuffer, LEVEL)
            SimpleTextUtil.write(skipBuffer, level.toString(), scratch)
            SimpleTextUtil.writeNewline(skipBuffer)

            wroteHeaderPerLevelMap[level] = true
        }
        SimpleTextUtil.write(skipBuffer, SKIP_DOC)
        SimpleTextUtil.write(skipBuffer, curDoc.toString(), scratch)
        SimpleTextUtil.writeNewline(skipBuffer)

        SimpleTextUtil.write(skipBuffer, SKIP_DOC_FP)
        SimpleTextUtil.write(skipBuffer, curDocFilePointer.toString(), scratch)
        SimpleTextUtil.writeNewline(skipBuffer)

        val competitiveFreqNorms = curCompetitiveFreqNorms[level]
        val impacts: Collection<Impact> = competitiveFreqNorms.getCompetitiveFreqNormPairs()
        assert(impacts.isNotEmpty())
        if (level + 1 < numberOfSkipLevels) {
            curCompetitiveFreqNorms[level + 1].addAll(competitiveFreqNorms)
        }
        SimpleTextUtil.write(skipBuffer, IMPACTS)
        SimpleTextUtil.writeNewline(skipBuffer)
        for (impact in impacts) {
            SimpleTextUtil.write(skipBuffer, IMPACT)
            SimpleTextUtil.writeNewline(skipBuffer)
            SimpleTextUtil.write(skipBuffer, FREQ)
            SimpleTextUtil.write(skipBuffer, impact.freq.toString(), scratch)
            SimpleTextUtil.writeNewline(skipBuffer)
            SimpleTextUtil.write(skipBuffer, NORM)
            SimpleTextUtil.write(skipBuffer, impact.norm.toString(), scratch)
            SimpleTextUtil.writeNewline(skipBuffer)
        }
        SimpleTextUtil.write(skipBuffer, IMPACTS_END)
        SimpleTextUtil.writeNewline(skipBuffer)
        competitiveFreqNorms.clear()
    }

    public override fun resetSkip() {
        super.resetSkip()
        wroteHeaderPerLevelMap.clear()
        curDoc = -1
        curDocFilePointer = -1
        for (acc in curCompetitiveFreqNorms) {
            acc.clear()
        }
    }

    @Throws(IOException::class)
    override fun writeSkip(output: IndexOutput): Long {
        val skipOffset = output.filePointer
        SimpleTextUtil.write(output, SKIP_LIST)
        SimpleTextUtil.writeNewline(output)
        super.writeSkip(output)
        return skipOffset
    }

    @Throws(IOException::class)
    fun bufferSkip(
        doc: Int,
        docFilePointer: Long,
        numDocs: Int,
        competitiveImpactAccumulator: CompetitiveImpactAccumulator
    ) {
        assert(doc > curDoc)
        curDoc = doc
        curDocFilePointer = docFilePointer
        curCompetitiveFreqNorms[0].addAll(competitiveImpactAccumulator)
        bufferSkip(numDocs)
    }

    @Throws(IOException::class)
    override fun writeLevelLength(levelLength: Long, output: IndexOutput) {
        SimpleTextUtil.write(output, LEVEL_LENGTH)
        SimpleTextUtil.write(output, levelLength.toString(), scratch)
        SimpleTextUtil.writeNewline(output)
    }

    @Throws(IOException::class)
    override fun writeChildPointer(childPointer: Long, skipBuffer: DataOutput) {
        SimpleTextUtil.write(skipBuffer, CHILD_POINTER)
        SimpleTextUtil.write(skipBuffer, childPointer.toString(), scratch)
        SimpleTextUtil.writeNewline(skipBuffer)
    }

    companion object {
        const val skipMultiplier = 3
        const val maxSkipLevels = 4

        const val BLOCK_SIZE = 8

        val SKIP_LIST: BytesRef = BytesRef("    skipList ")
        val LEVEL_LENGTH: BytesRef = BytesRef("      levelLength ")
        val LEVEL: BytesRef = BytesRef("      level ")
        val SKIP_DOC: BytesRef = BytesRef("        skipDoc ")
        val SKIP_DOC_FP: BytesRef = BytesRef("        skipDocFP ")
        val IMPACTS: BytesRef = BytesRef("        impacts ")
        val IMPACT: BytesRef = BytesRef("          impact ")
        val FREQ: BytesRef = BytesRef("            freq ")
        val NORM: BytesRef = BytesRef("            norm ")
        val IMPACTS_END: BytesRef = BytesRef("        impactsEnd ")
        val CHILD_POINTER: BytesRef = BytesRef("        childPointer ")
    }
}
