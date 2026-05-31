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
package org.gnit.lucenekmp.analysis.compound.hyphenation

/** This class implements a simple byte vector with access to the underlying array. */
class ByteVector {
    private var blockSize = DEFAULT_BLOCK_SIZE
    private var array: ByteArray
    private var n = 0

    constructor() : this(DEFAULT_BLOCK_SIZE)

    constructor(capacity: Int) {
        array = ByteArray(capacity)
    }

    fun getArray(): ByteArray {
        return array
    }

    fun length(): Int {
        return n
    }

    fun capacity(): Int {
        return array.size
    }

    fun put(index: Int, `val`: Byte) {
        array[index] = `val`
    }

    fun get(index: Int): Byte {
        return array[index]
    }

    fun alloc(size: Int): Int {
        val index = n
        val len = array.size
        if (n + size >= len) {
            val aux = ByteArray(len + blockSize)
            array.copyInto(aux, 0, 0, len)
            array = aux
        }
        n += size
        return index
    }

    fun trimToSize() {
        if (n < array.size) {
            val aux = ByteArray(n)
            array.copyInto(aux, 0, 0, n)
            array = aux
        }
    }

    companion object {
        private const val DEFAULT_BLOCK_SIZE = 2048
    }
}
