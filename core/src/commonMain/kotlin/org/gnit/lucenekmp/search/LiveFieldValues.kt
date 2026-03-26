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
package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.jdkport.ReentrantLock
import kotlin.concurrent.Volatile

/**
 * Tracks live field values across NRT reader reopens. This holds a map for all updated ids since
 * the last reader reopen. Once the NRT reader is reopened, it prunes the map. This means you must
 * reopen your NRT reader periodically otherwise the RAM consumption of this class will grow
 * unbounded!
 *
 * <p>NOTE: you must ensure the same id is never updated at the same time by two threads, because in
 * this case you cannot in general know which thread "won".
 */

// TODO: should this class handle deletions better...?
abstract class LiveFieldValues<S, T : Any>(
    private val mgr: ReferenceManager<S>,
    private val missingValue: T
) : ReferenceManager.RefreshListener, AutoCloseable {
    @Volatile
    private var current: MutableMap<String, T> = mutableMapOf()

    @Volatile
    private var old: MutableMap<String, T> = mutableMapOf()

    private val mapsLock = ReentrantLock()

    /** The missingValue must be non-null. */
    init {
        mgr.addListener(this)
    }

    override fun close() {
        mgr.removeListener(this)
    }

    @Throws(IOException::class)
    override fun beforeRefresh() {
        mapsLock.lock()
        try {
            old = current
            // Start sending all updates after this point to the new
            // map.  While reopen is running, any lookup will first
            // try this new map, then fallback to old, then to the
            // current searcher:
            current = mutableMapOf()
        } finally {
            mapsLock.unlock()
        }
    }

    @Throws(IOException::class)
    override fun afterRefresh(didRefresh: Boolean) {
        mapsLock.lock()
        try {
            // Now drop all the old values because they are now
            // visible via the searcher that was just opened; if
            // didRefresh is false, it's possible old has some
            // entries in it, which is fine: it means they were
            // actually already included in the previously opened
            // reader.  So we can safely clear old here:
            old = mutableMapOf()
        } finally {
            mapsLock.unlock()
        }
    }

    /**
     * Call this after you've successfully added a document to the index, to record what value you
     * just set the field to.
     */
    fun add(id: String, value: T) {
        mapsLock.lock()
        try {
            current[id] = value
        } finally {
            mapsLock.unlock()
        }
    }

    /** Call this after you've successfully deleted a document from the index. */
    fun delete(id: String) {
        mapsLock.lock()
        try {
            current[id] = missingValue
        } finally {
            mapsLock.unlock()
        }
    }

    /** Returns the approximate number of id/value pairs buffered in RAM. */
    fun size(): Int {
        mapsLock.lock()
        return try {
            current.size + old.size
        } finally {
            mapsLock.unlock()
        }
    }

    /** Returns the current value for this id, or null if the id isn't in the index or was deleted. */
    @Throws(IOException::class)
    operator fun get(id: String): T? {
        // First try to get the "live" value:
        mapsLock.lock()
        val currentValue =
            try {
                current[id]
            } finally {
                mapsLock.unlock()
            }
        if (currentValue == missingValue) {
            // Deleted but the deletion is not yet reflected in
            // the reader:
            return null
        } else if (currentValue != null) {
            return currentValue
        } else {
            mapsLock.lock()
            val oldValue =
                try {
                    old[id]
                } finally {
                    mapsLock.unlock()
                }
            if (oldValue == missingValue) {
                // Deleted but the deletion is not yet reflected in
                // the reader:
                return null
            } else if (oldValue != null) {
                return oldValue
            } else {
                // It either does not exist in the index, or, it was
                // already flushed & NRT reader was opened on the
                // segment, so fallback to current searcher:
                val s = mgr.acquire()
                try {
                    return lookupFromSearcher(s, id)
                } finally {
                    mgr.release(s)
                }
            }
        }
    }

    /**
     * This is called when the id/value was already flushed and opened in an NRT IndexSearcher. You
     * must implement this to go look up the value (eg, via doc values, field cache, stored fields,
     * etc.).
     */
    @Throws(IOException::class)
    protected abstract fun lookupFromSearcher(s: S, id: String): T?
}
