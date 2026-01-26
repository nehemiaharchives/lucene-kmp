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
import org.gnit.lucenekmp.codecs.LiveDocsFormat
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentCommitInfo
import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.CharsRefBuilder
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.StringHelper

/**
 * reads/writes plaintext live docs
 *
 * <p><b>FOR RECREATIONAL USE ONLY</b>
 *
 * @lucene.experimental
 */
class SimpleTextLiveDocsFormat : LiveDocsFormat() {

    @Throws(IOException::class)
    override fun readLiveDocs(dir: Directory, info: SegmentCommitInfo, context: IOContext): Bits {
        assert(info.hasDeletions())
        val scratch = BytesRefBuilder()
        val scratchUTF16 = CharsRefBuilder()

        val fileName =
            IndexFileNames.fileNameFromGeneration(
                info.info.name,
                LIVEDOCS_EXTENSION,
                info.delGen
            )!!
        var input: ChecksumIndexInput? = null
        var success = false
        try {
            input = dir.openChecksumInput(fileName)

            SimpleTextUtil.readLine(input, scratch)
            assert(StringHelper.startsWith(scratch.get(), SIZE))
            val size = parseIntAt(scratch.get(), SIZE.length, scratchUTF16)

            val bits = BitSet(size)

            SimpleTextUtil.readLine(input, scratch)
            while (scratch.get() != END) {
                assert(StringHelper.startsWith(scratch.get(), DOC))
                val docid = parseIntAt(scratch.get(), DOC.length, scratchUTF16)
                bits.set(docid)
                SimpleTextUtil.readLine(input, scratch)
            }

            SimpleTextUtil.checkFooter(input)

            success = true
            return SimpleTextBits(bits, size)
        } finally {
            if (success) {
                IOUtils.close(input)
            } else {
                IOUtils.closeWhileHandlingException(input)
            }
        }
    }

    private fun parseIntAt(bytes: BytesRef, offset: Int, scratch: CharsRefBuilder): Int {
        scratch.copyUTF8Bytes(bytes.bytes, bytes.offset + offset, bytes.length - offset)
        return ArrayUtil.parseInt(scratch.chars(), 0, scratch.length())
    }

    @Throws(IOException::class)
    override fun writeLiveDocs(
        bits: Bits,
        dir: Directory,
        info: SegmentCommitInfo,
        newDelCount: Int,
        context: IOContext
    ) {
        val size = bits.length()
        val scratch = BytesRefBuilder()

        val fileName =
            IndexFileNames.fileNameFromGeneration(
                info.info.name,
                LIVEDOCS_EXTENSION,
                info.nextDelGen
            )!!
        var out: IndexOutput? = null
        var success = false
        try {
            out = dir.createOutput(fileName, context)
            SimpleTextUtil.write(out, SIZE)
            SimpleTextUtil.write(out, size.toString(), scratch)
            SimpleTextUtil.writeNewline(out)

            for (i in 0 until size) {
                if (bits.get(i)) {
                    SimpleTextUtil.write(out, DOC)
                    SimpleTextUtil.write(out, i.toString(), scratch)
                    SimpleTextUtil.writeNewline(out)
                }
            }

            SimpleTextUtil.write(out, END)
            SimpleTextUtil.writeNewline(out)
            SimpleTextUtil.writeChecksum(out, scratch)
            success = true
        } finally {
            if (success) {
                IOUtils.close(out)
            } else {
                IOUtils.closeWhileHandlingException(out)
            }
        }
    }

    @Throws(IOException::class)
    override fun files(info: SegmentCommitInfo, files: MutableCollection<String>) {
        if (info.hasDeletions()) {
            files.add(
                IndexFileNames.fileNameFromGeneration(
                    info.info.name,
                    LIVEDOCS_EXTENSION,
                    info.delGen
                )!!
            )
        }
    }

    // read-only
    private class SimpleTextBits(private val bits: BitSet, private val size: Int) : Bits {
        override fun get(index: Int): Boolean {
            return bits[index]
        }

        override fun length(): Int {
            return size
        }
    }

    companion object {
        const val LIVEDOCS_EXTENSION = "liv"

        val SIZE: BytesRef = BytesRef("size ")
        val DOC: BytesRef = BytesRef("  doc ")
        val END: BytesRef = BytesRef("END")
    }
}
