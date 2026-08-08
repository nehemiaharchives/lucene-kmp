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
package org.gnit.lucenekmp.queryparser.flexible.standard.builders

import org.gnit.lucenekmp.queryparser.flexible.core.builders.QueryTreeBuilder
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.BooleanQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.BoostQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FuzzyQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.GroupQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.MatchAllDocsQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.MatchNoDocsQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.ModifierQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.SlopQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.TokenizedPhraseQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.IntervalQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.MinShouldMatchNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.MultiPhraseQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.PointQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.PointRangeQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.PrefixWildcardQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.RegexpQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.SynonymQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.TermRangeQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.WildcardQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.processors.StandardQueryNodeProcessorPipeline
import org.gnit.lucenekmp.search.Query

/**
 * This query tree builder only defines the necessary map to build a [Query] tree object. It should
 * be used to generate a [Query] tree object from a query node tree processed by a
 * [StandardQueryNodeProcessorPipeline].
 *
 * @see QueryTreeBuilder
 * @see StandardQueryNodeProcessorPipeline
 */
class StandardQueryTreeBuilder : QueryTreeBuilder(), StandardQueryBuilder {

    init {
        setBuilder(GroupQueryNode::class, GroupQueryNodeBuilder())
        setBuilder(FieldQueryNode::class, FieldQueryNodeBuilder())
        setBuilder(BooleanQueryNode::class, BooleanQueryNodeBuilder())
        setBuilder(FuzzyQueryNode::class, FuzzyQueryNodeBuilder())
        setBuilder(PointQueryNode::class, DummyQueryNodeBuilder())
        setBuilder(PointRangeQueryNode::class, PointRangeQueryNodeBuilder())
        setBuilder(BoostQueryNode::class, BoostQueryNodeBuilder())
        setBuilder(ModifierQueryNode::class, ModifierQueryNodeBuilder())
        setBuilder(WildcardQueryNode::class, WildcardQueryNodeBuilder())
        setBuilder(TokenizedPhraseQueryNode::class, PhraseQueryNodeBuilder())
        setBuilder(MatchNoDocsQueryNode::class, MatchNoDocsQueryNodeBuilder())
        setBuilder(PrefixWildcardQueryNode::class, PrefixWildcardQueryNodeBuilder())
        setBuilder(TermRangeQueryNode::class, TermRangeQueryNodeBuilder())
        setBuilder(RegexpQueryNode::class, RegexpQueryNodeBuilder())
        setBuilder(SlopQueryNode::class, SlopQueryNodeBuilder())
        setBuilder(SynonymQueryNode::class, SynonymQueryNodeBuilder())
        setBuilder(MultiPhraseQueryNode::class, MultiPhraseQueryNodeBuilder())
        setBuilder(MatchAllDocsQueryNode::class, MatchAllDocsQueryNodeBuilder())
        setBuilder(MinShouldMatchNode::class, MinShouldMatchNodeBuilder())
        setBuilder(IntervalQueryNode::class, IntervalQueryNodeBuilder())
    }

    override fun build(queryNode: QueryNode): Query {
        return super.build(queryNode) as Query
    }
}
