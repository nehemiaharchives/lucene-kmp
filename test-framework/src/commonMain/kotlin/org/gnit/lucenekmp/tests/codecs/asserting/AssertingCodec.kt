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
package org.gnit.lucenekmp.tests.codecs.asserting

import org.gnit.lucenekmp.codecs.DocValuesFormat
import org.gnit.lucenekmp.codecs.FilterCodec
import org.gnit.lucenekmp.codecs.KnnVectorsFormat
import org.gnit.lucenekmp.codecs.LiveDocsFormat
import org.gnit.lucenekmp.codecs.NormsFormat
import org.gnit.lucenekmp.codecs.PointsFormat
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.codecs.StoredFieldsFormat
import org.gnit.lucenekmp.codecs.TermVectorsFormat
import org.gnit.lucenekmp.codecs.perfield.PerFieldDocValuesFormat
import org.gnit.lucenekmp.codecs.perfield.PerFieldKnnVectorsFormat
import org.gnit.lucenekmp.codecs.perfield.PerFieldPostingsFormat
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.CloseableThreadLocal
import kotlinx.coroutines.Job

/** Acts like the default codec but with additional asserts. */
open class AssertingCodec : FilterCodec("Asserting", TestUtil.getDefaultCodec()) {

    companion object {
        init {
            PostingsFormat.registerPostingsFormat("Asserting") { AssertingPostingsFormat() }
            DocValuesFormat.registerDocValuesFormat("Asserting") { AssertingDocValuesFormat() }
            KnnVectorsFormat.registerKnnVectorsFormat("Asserting") { AssertingKnnVectorsFormat() }
        }

        // Mirrors java.lang.Thread.currentThread() checks by tracking the current coroutine Job
        // via a CloseableThreadLocal. If no Job is bound, checks are skipped (best-effort).
        private val currentJobLocal = CloseableThreadLocal<Job?>()

        internal inline fun <T> withCurrentJob(job: Job?, block: () -> T): T {
            val previous = currentJobLocal.get()
            currentJobLocal.set(job)
            try {
                return block()
            } finally {
                currentJobLocal.set(previous)
            }
        }

        internal fun currentJob(): Job? = currentJobLocal.get()

        fun assertThread(`object`: String, creationThread: Job?) {
            val current = currentJobLocal.get()
            if (creationThread == null || current == null) {
                return
            }
            if (creationThread !== current) {
                throw AssertionError(
                    (`object`
                            + " are only supposed to be consumed in "
                            + "the thread in which they have been acquired. But was acquired in "
                            + creationThread
                            + " and consumed in "
                            + current
                            + ".")
                )
            }
        }
    }

    private val postings: PostingsFormat =
        object : PerFieldPostingsFormat() {
            override fun getPostingsFormatForField(field: String): PostingsFormat {
                return this@AssertingCodec.getPostingsFormatForField(field)
            }
        }

    private val docValues: DocValuesFormat =
        object : PerFieldDocValuesFormat() {
            override fun getDocValuesFormatForField(field: String): DocValuesFormat {
                return this@AssertingCodec.getDocValuesFormatForField(field)
            }
        }

    private val knnVectorsFormat: KnnVectorsFormat =
        object : PerFieldKnnVectorsFormat() {
            override fun getKnnVectorsFormatForField(field: String): KnnVectorsFormat {
                return this@AssertingCodec.getKnnVectorsFormatForField(field)
            }
        }

    private val vectors: TermVectorsFormat = AssertingTermVectorsFormat()
    private val storedFields: StoredFieldsFormat = AssertingStoredFieldsFormat()
    private val norms: NormsFormat = AssertingNormsFormat()
    private val liveDocs: LiveDocsFormat = AssertingLiveDocsFormat()
    private val defaultFormat: PostingsFormat = AssertingPostingsFormat()
    private val defaultDVFormat: DocValuesFormat = AssertingDocValuesFormat()
    private val pointsFormat: PointsFormat = AssertingPointsFormat()
    private val defaultKnnVectorsFormat: KnnVectorsFormat = AssertingKnnVectorsFormat()

    override fun postingsFormat(): PostingsFormat {
        return postings
    }

    override fun termVectorsFormat(): TermVectorsFormat {
        return vectors
    }

    override fun storedFieldsFormat(): StoredFieldsFormat {
        return storedFields
    }

    override fun docValuesFormat(): DocValuesFormat {
        return docValues
    }

    override fun normsFormat(): NormsFormat {
        return norms
    }

    override fun liveDocsFormat(): LiveDocsFormat {
        return liveDocs
    }

    override fun pointsFormat(): PointsFormat {
        return pointsFormat
    }

    override fun knnVectorsFormat(): KnnVectorsFormat {
        return knnVectorsFormat
    }

    override fun toString(): String {
        return "Asserting($delegate)"
    }

    /**
     * Returns the postings format that should be used for writing new segments of `field`.
     *
     * <p>The default implementation always returns "Asserting"
     */
    open fun getPostingsFormatForField(field: String): PostingsFormat {
        return defaultFormat
    }

    /**
     * Returns the docvalues format that should be used for writing new segments of `field`
     * .
     *
     * <p>The default implementation always returns "Asserting"
     */
    open fun getDocValuesFormatForField(field: String): DocValuesFormat {
        return defaultDVFormat
    }

    /**
     * Returns the vectors format that should be used for writing new segments of `field`.
     *
     * <p>The default implementation always returns "Asserting"
     */
    open fun getKnnVectorsFormatForField(field: String): KnnVectorsFormat {
        return defaultKnnVectorsFormat
    }
}
