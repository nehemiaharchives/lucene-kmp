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
import org.gnit.lucenekmp.index.LeafReaderContext

/**
 * A [Collector] implementation which wraps another [Collector] and makes sure only
 * documents with scores > 0 are collected.
 */
class PositiveScoresOnlyCollector(`in`: Collector) : FilterCollector(`in`) {
    @Throws(IOException::class)
    override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
        return ScoreCachingWrappingScorer.wrap(
            object : FilterLeafCollector(super.getLeafCollector(context)) {
                private var currentScorer: Scorable? = null

                override var scorer: Scorable?
                    get() = currentScorer
                    set(scorer) {
                        currentScorer = scorer
                        `in`.scorer = scorer
                    }

                @Throws(IOException::class)
                override fun collect(doc: Int) {
                    if (currentScorer!!.score() > 0) {
                        `in`.collect(doc)
                    }
                }
            }
        )
    }
}
