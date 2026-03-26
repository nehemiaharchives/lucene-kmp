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

package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.jdkport.assert

/** A binary representation of a range that wraps a BinaryDocValues field */
class BinaryRangeDocValues(
    private val `in`: BinaryDocValues,
    private val numDims: Int,
    private val numBytesPerDimension: Int,
) : BinaryDocValues() {
    private val packedValue = ByteArray(2 * numDims * numBytesPerDimension)
    private var docID = -1

    /**
     * Gets the packed value that represents this range
     *
     * @return the packed value that represents this range
     */
    fun getPackedValue(): ByteArray {
        return packedValue
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        docID = `in`.nextDoc()
        if (docID != NO_MORE_DOCS) {
            decodeRanges()
        }
        return docID
    }

    override fun docID(): Int {
        return `in`.docID()
    }

    override fun cost(): Long {
        return `in`.cost()
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        val res = `in`.advance(target)
        if (res != NO_MORE_DOCS) {
            decodeRanges()
        }
        return res
    }

    @Throws(IOException::class)
    override fun advanceExact(target: Int): Boolean {
        val res = `in`.advanceExact(target)
        if (res) {
            decodeRanges()
        }
        return res
    }

    @Throws(IOException::class)
    override fun binaryValue(): BytesRef? {
        return `in`.binaryValue()
    }

    @Throws(IOException::class)
    private fun decodeRanges() {
        val bytesRef = `in`.binaryValue()
        assert(bytesRef != null)
        requireNotNull(bytesRef)
        bytesRef.bytes.copyInto(
            packedValue,
            0,
            bytesRef.offset,
            bytesRef.offset + 2 * numDims * numBytesPerDimension,
        )
    }
}
