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

import okio.FileNotFoundException
import okio.IOException
import org.gnit.lucenekmp.codecs.CompoundDirectory
import org.gnit.lucenekmp.codecs.CompoundFormat
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.fromByteArray
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.StringHelper
import kotlin.collections.binarySearch

/**
 * plain text compound format.
 *
 * <p><b>FOR RECREATIONAL USE ONLY</b>
 *
 * @lucene.experimental
 */
class SimpleTextCompoundFormat : CompoundFormat() {

    @Throws(IOException::class)
    override fun getCompoundReader(dir: Directory, si: SegmentInfo): CompoundDirectory {
        val dataFile = IndexFileNames.segmentFileName(si.name, "", DATA_EXTENSION)
        val `in` =
            dir.openInput(dataFile, IOContext.DEFAULT.withReadAdvice(ReadAdvice.NORMAL))

        val scratch = BytesRefBuilder()

        // first get to TOC:
        val pos = `in`.length() - TABLEPOS.length - OFFSETPATTERN.length - 1
        `in`.seek(pos)
        SimpleTextUtil.readLine(`in`, scratch)
        assert(StringHelper.startsWith(scratch.get(), TABLEPOS))
        val tablePos: Long = try {
            stripPrefix(scratch, TABLEPOS).toLong()
        } catch (e: NumberFormatException) {
            throw CorruptIndexException(
                "can't parse CFS trailer, got: ${scratch.get().utf8ToString()}",
                `in`,
                e
            )
        }

        // seek to TOC and read it
        `in`.seek(tablePos)
        SimpleTextUtil.readLine(`in`, scratch)
        assert(StringHelper.startsWith(scratch.get(), TABLE))
        val numEntries = stripPrefix(scratch, TABLE).toInt()

        val fileNames = Array(numEntries) { "" }
        val startOffsets = LongArray(numEntries)
        val endOffsets = LongArray(numEntries)

        for (i in 0 until numEntries) {
            SimpleTextUtil.readLine(`in`, scratch)
            assert(StringHelper.startsWith(scratch.get(), TABLENAME))
            fileNames[i] = si.name + IndexFileNames.stripSegmentName(stripPrefix(scratch, TABLENAME))

            if (i > 0) {
                // files must be unique and in sorted order
                assert(fileNames[i].compareTo(fileNames[i - 1]) > 0)
            }

            SimpleTextUtil.readLine(`in`, scratch)
            assert(StringHelper.startsWith(scratch.get(), TABLESTART))
            startOffsets[i] = stripPrefix(scratch, TABLESTART).toLong()

            SimpleTextUtil.readLine(`in`, scratch)
            assert(StringHelper.startsWith(scratch.get(), TABLEEND))
            endOffsets[i] = stripPrefix(scratch, TABLEEND).toLong()
        }

        return object : CompoundDirectory() {

            @Throws(IOException::class)
            private fun getIndex(name: String): Int {
                val index = Arrays.binarySearch(fileNames, name)
                if (index < 0) {
                    throw FileNotFoundException(
                        "No sub-file found (fileName=$name files: ${fileNames.contentToString()})"
                    )
                }
                return index
            }

            @Throws(IOException::class)
            override fun listAll(): Array<String> {
                ensureOpen()
                return fileNames.copyOf()
            }

            @Throws(IOException::class)
            override fun fileLength(name: String): Long {
                ensureOpen()
                val index = getIndex(name)
                return endOffsets[index] - startOffsets[index]
            }

            @Throws(IOException::class)
            override fun openInput(name: String, context: IOContext): IndexInput {
                ensureOpen()
                val index = getIndex(name)
                return `in`.slice(
                    name,
                    startOffsets[index],
                    endOffsets[index] - startOffsets[index],
                    context.readAdvice
                )
            }

            @Throws(IOException::class)
            override fun close() {
                `in`.close()
            }

            override val pendingDeletions: MutableSet<String>
                get() {
                    return mutableSetOf()
                }

            @Throws(IOException::class)
            override fun checkIntegrity() {
                // No checksums for SimpleText
            }
        }
    }

    @Throws(IOException::class)
    override fun write(dir: Directory, si: SegmentInfo, context: IOContext) {
        val dataFile = IndexFileNames.segmentFileName(si.name, "", DATA_EXTENSION)

        val numFiles = si.files().size
        val names = si.files().toTypedArray()
        names.sort()
        val startOffsets = LongArray(numFiles)
        val endOffsets = LongArray(numFiles)

        val scratch = BytesRefBuilder()

        dir.createOutput(dataFile, context).use { out ->
            for (i in names.indices) {
                // write header for file
                SimpleTextUtil.write(out, HEADER)
                SimpleTextUtil.write(out, names[i], scratch)
                SimpleTextUtil.writeNewline(out)

                // write bytes for file
                startOffsets[i] = out.filePointer
                dir.openInput(names[i], IOContext.READONCE).use { `in` ->
                    out.copyBytes(`in`, `in`.length())
                }
                endOffsets[i] = out.filePointer
            }

            val tocPos = out.filePointer

            // write CFS table
            SimpleTextUtil.write(out, TABLE)
            SimpleTextUtil.write(out, numFiles.toString(), scratch)
            SimpleTextUtil.writeNewline(out)

            for (i in names.indices) {
                SimpleTextUtil.write(out, TABLENAME)
                SimpleTextUtil.write(out, names[i], scratch)
                SimpleTextUtil.writeNewline(out)

                SimpleTextUtil.write(out, TABLESTART)
                SimpleTextUtil.write(out, startOffsets[i].toString(), scratch)
                SimpleTextUtil.writeNewline(out)

                SimpleTextUtil.write(out, TABLEEND)
                SimpleTextUtil.write(out, endOffsets[i].toString(), scratch)
                SimpleTextUtil.writeNewline(out)
            }

            SimpleTextUtil.write(out, TABLEPOS)
            SimpleTextUtil.write(out, formatOffset(tocPos), scratch)
            SimpleTextUtil.writeNewline(out)
        }
    }

    private fun formatOffset(value: Long): String {
        return value.toString().padStart(OFFSETPATTERN.length, '0')
    }

    // helper method to strip strip away 'prefix' from 'scratch' and return as String
    private fun stripPrefix(scratch: BytesRefBuilder, prefix: BytesRef): String {
        return String.fromByteArray(
            scratch.bytes(),
            prefix.length,
            scratch.length() - prefix.length,
            StandardCharsets.UTF_8
        )
    }

    companion object {
        /** Extension of compound file */
        const val DATA_EXTENSION: String = "scf"

        val HEADER = BytesRef("cfs entry for: ")

        val TABLE = BytesRef("table of contents, size: ")
        val TABLENAME = BytesRef("  filename: ")
        val TABLESTART = BytesRef("    start: ")
        val TABLEEND = BytesRef("    end: ")

        val TABLEPOS = BytesRef("table of contents begins at offset: ")

        val OFFSETPATTERN: String

        init {
            val numDigits = Long.MAX_VALUE.toString().length
            val pattern = CharArray(numDigits)
            pattern.fill('0')
            OFFSETPATTERN = pattern.concatToString()
        }
    }
}
