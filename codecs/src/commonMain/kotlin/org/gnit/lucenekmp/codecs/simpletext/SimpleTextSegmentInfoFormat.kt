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
import org.gnit.lucenekmp.codecs.SegmentInfoFormat
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.IndexSorter
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.index.SortFieldProvider
import org.gnit.lucenekmp.jdkport.ParseException
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.fromByteArray
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.CollectionUtil
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version

/**
 * plain text segments file format.
 *
 * <p><b>FOR RECREATIONAL USE ONLY</b>
 *
 * @lucene.experimental
 */
class SimpleTextSegmentInfoFormat : SegmentInfoFormat() {
    @Throws(IOException::class)
    override fun read(
        directory: Directory,
        segmentName: String,
        segmentID: ByteArray,
        context: IOContext
    ): SegmentInfo {
        val scratch = BytesRefBuilder()
        val segFileName =
            IndexFileNames.segmentFileName(segmentName, "", SI_EXTENSION)
        directory.openChecksumInput(segFileName).use { input ->
            SimpleTextUtil.readLine(input, scratch)
            assert(StringHelper.startsWith(scratch.get(), SI_VERSION))
            val version: Version = try {
                Version.parse(readString(SI_VERSION.length, scratch))
            } catch (pe: ParseException) {
                throw CorruptIndexException(
                    "unable to parse version string: ${pe.message}",
                    input,
                    pe
                )
            }

            SimpleTextUtil.readLine(input, scratch)
            assert(StringHelper.startsWith(scratch.get(), SI_MIN_VERSION))
            val minVersion: Version? = try {
                val versionString = readString(SI_MIN_VERSION.length, scratch)
                if (versionString == "null") {
                    null
                } else {
                    Version.parse(versionString)
                }
            } catch (pe: ParseException) {
                throw CorruptIndexException(
                    "unable to parse version string: ${pe.message}",
                    input,
                    pe
                )
            }

            SimpleTextUtil.readLine(input, scratch)
            assert(StringHelper.startsWith(scratch.get(), SI_DOCCOUNT))
            val docCount = readString(SI_DOCCOUNT.length, scratch).toInt()

            SimpleTextUtil.readLine(input, scratch)
            assert(StringHelper.startsWith(scratch.get(), SI_USECOMPOUND))
            val isCompoundFile = readString(SI_USECOMPOUND.length, scratch).toBoolean()

            SimpleTextUtil.readLine(input, scratch)
            assert(StringHelper.startsWith(scratch.get(), SI_HAS_BLOCKS))
            val hasBlocks = readString(SI_HAS_BLOCKS.length, scratch).toBoolean()

            SimpleTextUtil.readLine(input, scratch)
            assert(StringHelper.startsWith(scratch.get(), SI_NUM_DIAG))
            val numDiag = readString(SI_NUM_DIAG.length, scratch).toInt()
            val diagnostics = CollectionUtil.newHashMap<String, String>(numDiag)

            for (i in 0 until numDiag) {
                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), SI_DIAG_KEY))
                val key = readString(SI_DIAG_KEY.length, scratch)

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), SI_DIAG_VALUE))
                val value = readString(SI_DIAG_VALUE.length, scratch)
                diagnostics[key] = value
            }

            SimpleTextUtil.readLine(input, scratch)
            assert(StringHelper.startsWith(scratch.get(), SI_NUM_ATT))
            val numAtt = readString(SI_NUM_ATT.length, scratch).toInt()
            val attributes = CollectionUtil.newHashMap<String, String>(numAtt)

            for (i in 0 until numAtt) {
                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), SI_ATT_KEY))
                val key = readString(SI_ATT_KEY.length, scratch)

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), SI_ATT_VALUE))
                val value = readString(SI_ATT_VALUE.length, scratch)
                attributes[key] = value
            }

            SimpleTextUtil.readLine(input, scratch)
            assert(StringHelper.startsWith(scratch.get(), SI_NUM_FILES))
            val numFiles = readString(SI_NUM_FILES.length, scratch).toInt()
            val files = CollectionUtil.newHashSet<String>(numFiles)

            for (i in 0 until numFiles) {
                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), SI_FILE))
                val fileName = readString(SI_FILE.length, scratch)
                files.add(fileName)
            }

            SimpleTextUtil.readLine(input, scratch)
            assert(StringHelper.startsWith(scratch.get(), SI_ID))
            val id = SimpleTextUtil.fromBytesRefString(readString(SI_ID.length, scratch)).bytes

            if (!segmentID.contentEquals(id)) {
                throw CorruptIndexException(
                    "file mismatch, expected: ${StringHelper.idToString(segmentID)}, got: ${StringHelper.idToString(id)}",
                    input
                )
            }

            SimpleTextUtil.readLine(input, scratch)
            assert(StringHelper.startsWith(scratch.get(), SI_SORT))
            val numSortFields = readString(SI_SORT.length, scratch).toInt()
            val sortField = Array(numSortFields)/*
            for (i in 0 until numSortFields)*/ {
                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), SI_SORT_NAME))
                val provider = readString(SI_SORT_NAME.length, scratch)

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), SI_SORT_TYPE))

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), SI_SORT_BYTES))
                val serializedSort =
                    SimpleTextUtil.fromBytesRefString(readString(SI_SORT_BYTES.length, scratch))
                val bytes =
                    ByteArrayDataInput(
                        serializedSort.bytes,
                        serializedSort.offset,
                        serializedSort.length
                    )
                /*sortField[i] =*/ SortFieldProvider.forName(provider).readSortField(bytes)
                /*assert(bytes.eof())*/
            }

            val indexSort: Sort? = if (sortField.isEmpty()) {
                null
            } else {
                Sort(*sortField)
            }

            SimpleTextUtil.checkFooter(input)

            val info =
                SegmentInfo(
                    directory,
                    version,
                    minVersion,
                    segmentName,
                    docCount,
                    isCompoundFile,
                    hasBlocks,
                    null,
                    diagnostics,
                    id,
                    attributes,
                    indexSort
                )
            info.setFiles(files)
            return info
        }
    }

    private fun readString(offset: Int, scratch: BytesRefBuilder): String {
        return String.fromByteArray(
            scratch.bytes(),
            offset,
            scratch.length() - offset,
            StandardCharsets.UTF_8
        )
    }

    @Throws(IOException::class)
    override fun write(dir: Directory, si: SegmentInfo, ioContext: IOContext) {

        val segFileName =
            IndexFileNames.segmentFileName(si.name, "", SI_EXTENSION)

        dir.createOutput(segFileName, ioContext).use { output ->
            // Only add the file once we've successfully created it, else IFD assert can trip:
            si.addFile(segFileName)
            val scratch = BytesRefBuilder()

            SimpleTextUtil.write(output, SI_VERSION)
            SimpleTextUtil.write(output, si.version.toString(), scratch)
            SimpleTextUtil.writeNewline(output)

            SimpleTextUtil.write(output, SI_MIN_VERSION)
            if (si.minVersion == null) {
                SimpleTextUtil.write(output, "null", scratch)
            } else {
                SimpleTextUtil.write(output, si.minVersion.toString(), scratch)
            }
            SimpleTextUtil.writeNewline(output)

            SimpleTextUtil.write(output, SI_DOCCOUNT)
            SimpleTextUtil.write(output, si.maxDoc().toString(), scratch)
            SimpleTextUtil.writeNewline(output)

            SimpleTextUtil.write(output, SI_USECOMPOUND)
            SimpleTextUtil.write(output, si.useCompoundFile.toString(), scratch)
            SimpleTextUtil.writeNewline(output)

            SimpleTextUtil.write(output, SI_HAS_BLOCKS)
            SimpleTextUtil.write(output, si.hasBlocks.toString(), scratch)
            SimpleTextUtil.writeNewline(output)

            val diagnostics = si.diagnostics
            val numDiagnostics = diagnostics.size
            SimpleTextUtil.write(output, SI_NUM_DIAG)
            SimpleTextUtil.write(output, numDiagnostics.toString(), scratch)
            SimpleTextUtil.writeNewline(output)

            if (numDiagnostics > 0) {
                for (diagEntry in diagnostics.entries) {
                    SimpleTextUtil.write(output, SI_DIAG_KEY)
                    SimpleTextUtil.write(output, diagEntry.key, scratch)
                    SimpleTextUtil.writeNewline(output)

                    SimpleTextUtil.write(output, SI_DIAG_VALUE)
                    SimpleTextUtil.write(output, diagEntry.value, scratch)
                    SimpleTextUtil.writeNewline(output)
                }
            }

            val attributes = si.attributes
            SimpleTextUtil.write(output, SI_NUM_ATT)
            SimpleTextUtil.write(output, attributes.size.toString(), scratch)
            SimpleTextUtil.writeNewline(output)

            for (attEntry in attributes.entries) {
                SimpleTextUtil.write(output, SI_ATT_KEY)
                SimpleTextUtil.write(output, attEntry.key, scratch)
                SimpleTextUtil.writeNewline(output)

                SimpleTextUtil.write(output, SI_ATT_VALUE)
                SimpleTextUtil.write(output, attEntry.value, scratch)
                SimpleTextUtil.writeNewline(output)
            }

            val files = si.files()
            val numFiles = files.size
            SimpleTextUtil.write(output, SI_NUM_FILES)
            SimpleTextUtil.write(output, numFiles.toString(), scratch)
            SimpleTextUtil.writeNewline(output)

            if (numFiles > 0) {
                for (fileName in files) {
                    SimpleTextUtil.write(output, SI_FILE)
                    SimpleTextUtil.write(output, fileName, scratch)
                    SimpleTextUtil.writeNewline(output)
                }
            }

            SimpleTextUtil.write(output, SI_ID)
            SimpleTextUtil.write(output, BytesRef(si.getId()).toString(), scratch)
            SimpleTextUtil.writeNewline(output)

            val indexSort = si.indexSort
            SimpleTextUtil.write(output, SI_SORT)
            val numSortFields = indexSort?.sort?.size ?: 0
            SimpleTextUtil.write(output, numSortFields.toString(), scratch)
            SimpleTextUtil.writeNewline(output)
            for (i in 0 until numSortFields) {
                val sortField = indexSort!!.sort[i]
                val sorter: IndexSorter? = sortField.indexSorter
                if (sorter == null) {
                    throw IllegalStateException("Cannot serialize sort $sortField")
                }

                SimpleTextUtil.write(output, SI_SORT_NAME)
                SimpleTextUtil.write(output, sorter.providerName, scratch)
                SimpleTextUtil.writeNewline(output)

                SimpleTextUtil.write(output, SI_SORT_TYPE)
                SimpleTextUtil.write(output, sortField.toString(), scratch)
                SimpleTextUtil.writeNewline(output)

                SimpleTextUtil.write(output, SI_SORT_BYTES)
                val b = BytesRefOutput()
                SortFieldProvider.write(sortField, b)
                SimpleTextUtil.write(output, b.bytes.get().toString(), scratch)
                SimpleTextUtil.writeNewline(output)
            }
            SimpleTextUtil.writeChecksum(output, scratch)
        }
    }

    internal class BytesRefOutput : DataOutput() {
        val bytes = BytesRefBuilder()

        override fun writeByte(b: Byte) {
            bytes.append(b)
        }

        override fun writeBytes(b: ByteArray, offset: Int, length: Int) {
            bytes.append(b, offset, length)
        }
    }

    companion object {
        val SI_VERSION = BytesRef("    version ")
        val SI_MIN_VERSION = BytesRef("    min version ")
        val SI_DOCCOUNT = BytesRef("    number of documents ")
        val SI_USECOMPOUND = BytesRef("    uses compound file ")
        val SI_HAS_BLOCKS = BytesRef("    has blocks ")
        val SI_NUM_DIAG = BytesRef("    diagnostics ")
        val SI_DIAG_KEY = BytesRef("      key ")
        val SI_DIAG_VALUE = BytesRef("      value ")
        val SI_NUM_ATT = BytesRef("    attributes ")
        val SI_ATT_KEY = BytesRef("      key ")
        val SI_ATT_VALUE = BytesRef("      value ")
        val SI_NUM_FILES = BytesRef("    files ")
        val SI_FILE = BytesRef("      file ")
        val SI_ID = BytesRef("    id ")
        val SI_SORT = BytesRef("    sort ")
        val SI_SORT_TYPE = BytesRef("      type ")
        val SI_SORT_NAME = BytesRef("      name ")
        val SI_SORT_BYTES = BytesRef("      bytes ")

        const val SI_EXTENSION: String = "si"
    }
}
