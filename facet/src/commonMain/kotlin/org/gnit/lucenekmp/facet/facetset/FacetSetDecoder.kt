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
package org.gnit.lucenekmp.facet.facetset

import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.util.BytesRef

/**
 * A functional interface for decoding facet set values into comparable `long` ones. You can use it
 * by e.g. implementing a static method with the same signature and then pass it as `* YourClass::decode`.
 *
 * @lucene.experimental
 */
fun interface FacetSetDecoder {
    companion object {
        /**
   * An implementation of [FacetSetDecoder.decode] for long/double
   * dimension values which were encoded with [LongFacetSet] and [DoubleFacetSet]
   * respectively.
   */
        fun decodeLongs(bytesRef: BytesRef, start: Int, dest: LongArray): Int {
            LongPoint.unpack(bytesRef, start, dest)
            return dest.size * Long.SIZE_BYTES
        }

        /**
   * An implementation of [FacetSetDecoder.decode] for int/float
   * dimension values which were encoded with [IntFacetSet] and [FloatFacetSet]
   * respectively.
   */
        fun decodeInts(bytesRef: BytesRef, start: Int, dest: LongArray): Int {
            var i = 0
            var offset = start
            while (i < dest.size) {
                dest[i] = IntPoint.decodeDimension(bytesRef.bytes, offset).toLong()
                i++
                offset += Int.SIZE_BYTES
            }
            return dest.size * Int.SIZE_BYTES
        }
    }

    /**
   * Decodes the facet set dimension values into the given destination buffer and returns the number
   * of bytes read.
   */
    fun decode(bytesRef: BytesRef, start: Int, dest: LongArray): Int
}
