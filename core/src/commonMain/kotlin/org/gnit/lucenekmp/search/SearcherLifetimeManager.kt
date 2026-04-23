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
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.Volatile

/**
 * Keeps track of current plus old IndexSearchers, closing the old ones once they have timed out.
 *
 * <p>Use it like this:
 *
 * <pre class="prettyprint">
 *   SearcherLifetimeManager mgr = new SearcherLifetimeManager();
 * </pre>
 *
 * Per search-request, if it's a "new" search request, then obtain the latest searcher you have (for
 * example, by using [SearcherManager]), and then record this searcher:
 *
 * <pre class="prettyprint">
 *   // Record the current searcher, and save the returend
 *   // token into user's search results (eg as a  hidden
 *   // HTML form field):
 *   long token = mgr.record(searcher);
 * </pre>
 *
 * When a follow-up search arrives, for example the user clicks next page, drills down/up, etc.,
 * take the token that you saved from the previous search and:
 *
 * <pre class="prettyprint">
 *   // If possible, obtain the same searcher as the last
 *   // search:
 *   IndexSearcher searcher = mgr.acquire(token);
 *   if (searcher != null) {
 *     // Searcher is still here
 *     try {
 *       // do searching...
 *     } finally {
 *       mgr.release(searcher);
 *       // Do not use searcher after this!
 *       searcher = null;
 *     }
 *   } else {
 *     // Searcher was pruned -- notify user session timed
 *     // out, or, pull fresh searcher again
 *   }
 * </pre>
 *
 * Finally, in a separate thread, ideally the same thread that's periodically reopening your
 * searchers, you should periodically prune old searchers:
 *
 * <pre class="prettyprint">
 *   mgr.prune(new PruneByAge(600.0));
 * </pre>
 *
 * <p><b>NOTE</b>: keeping many searchers around means you'll use more resources (open files, RAM)
 * than a single searcher. However, as long as you are using
 * [DirectoryReader.openIfChanged], the searchers will usually share almost all
 * segments and the added resource usage is contained. When a large merge has completed, and you
 * reopen, because that is a large change, the new searcher will use higher additional RAM than
 * other searchers; but large merges don't complete very often and it's unlikely you'll hit two of
 * them in your expiration window. Still you should budget plenty of heap in the JVM to have a good
 * safety margin.
 *
 * @lucene.experimental
 */
class SearcherLifetimeManager : AutoCloseable {

    private class SearcherTracker(val searcher: IndexSearcher) : Comparable<SearcherTracker>, AutoCloseable {
        val recordTimeSec: Double
        val version: Long
        private val closeLock = ReentrantLock()
        private var closed = false

        init {
            version = (searcher.indexReader as DirectoryReader).version
            searcher.indexReader.incRef()
            // Use nanoTime not currentTimeMillis since it [in
            // theory] reduces risk from clock shift
            recordTimeSec = System.nanoTime() / NANOS_PER_SEC
        }

        // Newer searchers are sort before older ones:
        override fun compareTo(other: SearcherTracker): Int {
            return other.recordTimeSec.compareTo(recordTimeSec)
        }

        override fun close() {
            closeLock.withLock {
                if (!closed) {
                    closed = true
                    searcher.indexReader.decRef()
                }
            }
        }
    }

    /** See [prune]. */
    interface Pruner {
        /**
         * Return true if this searcher should be removed.
         *
         * @param ageSec how much time has passed since this searcher was the current (live) searcher
         * @param searcher Searcher
         */
        fun doPrune(ageSec: Double, searcher: IndexSearcher): Boolean
    }

    /**
     * Simple pruner that drops any searcher older by more than the specified seconds, than the
     * newest searcher.
     */
    class PruneByAge(private val maxAgeSec: Double) : Pruner {
        init {
            if (maxAgeSec < 0) {
                throw IllegalArgumentException("maxAgeSec must be > 0 (got $maxAgeSec)")
            }
        }

        override fun doPrune(ageSec: Double, searcher: IndexSearcher): Boolean {
            return ageSec > maxAgeSec
        }
    }

    @Volatile
    private var closed = false

    // TODO: we could get by w/ just a "set"; need to have
    // Tracker hash by its version and have compareTo(Long)
    // compare to its version
    private val searchers = HashMap<Long, SearcherTracker>()
    private val searchersLock = ReentrantLock()

    private fun ensureOpen() {
        if (closed) {
            throw AlreadyClosedException("this SearcherLifetimeManager instance is closed")
        }
    }

    /**
     * Records that you are now using this IndexSearcher. Always call this when you've obtained a
     * possibly new [IndexSearcher], for example from [SearcherManager]. It's fine if you
     * already passed the same searcher to this method before.
     *
     * <p>This returns the long token that you can later pass to [acquire] to retrieve the same
     * IndexSearcher. You should record this long token in the search results sent to your user, such
     * that if the user performs a follow-on action (clicks next page, drills down, etc.) the token is
     * returned.
     */
    @Throws(IOException::class)
    fun record(searcher: IndexSearcher): Long {
        ensureOpen()
        // TODO: we don't have to use IR.getVersion to track;
        // could be risky (if it's buggy); we could get better
        // bug isolation if we assign our own private ID:
        val version = (searcher.indexReader as DirectoryReader).version
        searchersLock.withLock {
            var tracker = searchers[version]
            if (tracker == null) {
                // System.out.println("RECORD version=" + version + " ms=" + System.currentTimeMillis());
                tracker = SearcherTracker(searcher)
                if (searchers.containsKey(version)) {
                    // Another thread beat us -- must decRef to undo
                    // incRef done by SearcherTracker ctor:
                    tracker.close()
                } else {
                    searchers[version] = tracker
                }
            } else if (tracker.searcher !== searcher) {
                throw IllegalArgumentException(
                    "the provided searcher has the same underlying reader version yet the searcher instance differs from before (new=$searcher vs old=${tracker.searcher}"
                )
            }
        }

        return version
    }

    /**
     * Retrieve a previously recorded [IndexSearcher], if it has not yet been closed
     *
     * <p><b>NOTE</b>: this may return null when the requested searcher has already timed out. When
     * this happens you should notify your user that their session timed out and that they'll have to
     * restart their search.
     *
     * <p>If this returns a non-null result, you must match later call [release] on this
     * searcher, best from a finally clause.
     */
    fun acquire(version: Long): IndexSearcher? {
        ensureOpen()
        searchersLock.withLock {
            val tracker = searchers[version]
            if (tracker != null && tracker.searcher.indexReader.tryIncRef()) {
                return tracker.searcher
            }
        }

        return null
    }

    /**
     * Release a searcher previously obtained from [acquire].
     *
     * <p><b>NOTE</b>: it's fine to call this after close.
     */
    @Throws(IOException::class)
    fun release(s: IndexSearcher) {
        s.indexReader.decRef()
    }

    /**
     * Calls provided [Pruner] to prune entries. The entries are passed to the Pruner in sorted
     * (newest to oldest IndexSearcher) order.
     *
     * <p><b>NOTE</b>: you must periodically call this, ideally from the same background thread that
     * opens new searchers.
     */
    @Throws(IOException::class)
    fun prune(pruner: Pruner) {
        searchersLock.withLock {
            // Cannot just pass searchers.values() to ArrayList ctor
            // (not thread-safe since the values can change while
            // ArrayList is init'ing itself); must instead iterate
            // ourselves:
            val trackers = ArrayList<SearcherTracker>()
            for (tracker in searchers.values) {
                trackers.add(tracker)
            }
            trackers.sort()
            var lastRecordTimeSec = 0.0
            val now = System.nanoTime() / NANOS_PER_SEC
            for (tracker in trackers) {
                val ageSec =
                    if (lastRecordTimeSec == 0.0) {
                        0.0
                    } else {
                        now - lastRecordTimeSec
                    }
                // First tracker is always age 0.0 sec, since it's
                // still "live"; second tracker's age (= seconds since
                // it was "live") is now minus first tracker's
                // recordTime, etc:
                if (pruner.doPrune(ageSec, tracker.searcher)) {
                    // System.out.println("PRUNE version=" + tracker.version + " age=" + ageSec + " ms=" +
                    // System.currentTimeMillis());
                    searchers.remove(tracker.version)
                    tracker.close()
                }
                lastRecordTimeSec = tracker.recordTimeSec
            }
        }
    }

    /**
     * Close this to future searching; any searches still in process in other threads won't be
     * affected, and they should still call [release] after they are done.
     *
     * <p><b>NOTE</b>: you must ensure no other threads are calling [record] while you call
     * close(); otherwise it's possible not all searcher references will be freed.
     */
    override fun close() {
        searchersLock.withLock {
            closed = true
            val toClose = ArrayList(searchers.values)

            // Remove up front in case exc below, so we don't
            // over-decRef on double-close:
            for (tracker in toClose) {
                searchers.remove(tracker.version)
            }

            IOUtils.close(toClose)

            // Make some effort to catch mis-use:
            if (searchers.size != 0) {
                throw IllegalStateException(
                    "another thread called record while this SearcherLifetimeManager instance was being closed; not all searchers were closed"
                )
            }
        }
    }

    companion object {
        const val NANOS_PER_SEC = 1000000000.0
    }
}
