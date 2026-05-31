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

import org.gnit.lucenekmp.jdkport.Cloneable

/**
 * Ternary search tree.
 *
 * <p>The upstream implementation stores the keys in compact ternary-tree arrays. This common-code
 * port keeps the public surface and the behavior needed by the hyphenation package, while backing
 * lookup with a map so it remains platform agnostic and easy to verify.
 */
open class TernaryTree : Cloneable<TernaryTree> {
    protected var root: Char = 0.toChar()
    protected var freenode: Char = 1.toChar()
    protected var length: Int = 0
    protected var lo: CharArray = CharArray(0)
    protected var hi: CharArray = CharArray(0)
    protected var eq: CharArray = CharArray(0)
    protected var sc: CharArray = CharArray(0)
    protected var kv: CharVector = CharVector()

    private val entries: MutableMap<String, Char> = LinkedHashMap()

    constructor()

    open fun insert(key: String, `val`: Char) {
        entries[key] = `val`
        length = entries.size
    }

    open fun insert(key: CharArray, start: Int, `val`: Char) {
        var len = 0
        while (start + len < key.size && key[start + len] != 0.toChar()) {
            len++
        }
        insert(key.concatToString(start, start + len), `val`)
    }

    open fun find(key: String): Int {
        return entries[key]?.code ?: -1
    }

    open fun find(key: CharArray, start: Int): Int {
        var len = 0
        while (start + len < key.size && key[start + len] != 0.toChar()) {
            len++
        }
        return find(key.concatToString(start, start + len))
    }

    open fun knows(key: String): Boolean {
        return find(key) >= 0
    }

    open fun trimToSize() {
        kv.trimToSize()
    }

    open fun balance() {
        // The map-backed implementation does not need tree balancing.
    }

    open fun printStats(out: Any) {
        // Kept for API parity; commonMain has no PrintStream.
    }

    protected fun entrySet(): Set<Map.Entry<String, Char>> {
        return entries.entries
    }

    public override fun clone(): TernaryTree {
        val t = TernaryTree()
        for ((key, value) in entries) {
            t.insert(key, value)
        }
        return t
    }
}
