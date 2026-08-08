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
package org.gnit.lucenekmp.queryparser.flexible.core.builders

import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNodeImpl
import org.gnit.lucenekmp.queryparser.flexible.core.parser.EscapeQuerySyntax
import org.gnit.lucenekmp.queryparser.flexible.core.util.UnescapedCharSequence
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestQueryTreeBuilder : LuceneTestCase() {

    @Test
    fun testSetFieldBuilder() {
        var qtb = QueryTreeBuilder()
        qtb.setBuilder("field", DummyBuilder())
        var result = qtb.build(FieldQueryNode(UnescapedCharSequence("field"), "foo", 0, 0))
        assertEquals("OK", result)

        // LUCENE-4890
        qtb = QueryTreeBuilder()
        qtb.setBuilder(DummyQueryNodeInterface::class, DummyBuilder())
        result = qtb.build(DummyQueryNode())
        assertEquals("OK", result)
    }

    private interface DummyQueryNodeInterface : QueryNode

    private abstract class AbstractDummyQueryNode : QueryNodeImpl(), DummyQueryNodeInterface

    private class DummyQueryNode : AbstractDummyQueryNode() {

        override fun toQueryString(escapeSyntaxParser: EscapeQuerySyntax): CharSequence {
            return "DummyQueryNode"
        }

        override fun cloneNode(): QueryNodeImpl {
            return DummyQueryNode()
        }
    }

    private class DummyBuilder : QueryBuilder {

        override fun build(queryNode: QueryNode): Any {
            return "OK"
        }
    }
}
