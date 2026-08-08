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
package org.gnit.lucenekmp.queryparser.flexible.standard.nodes

import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.jdkport.NumberFormat
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.parser.EscapeQuerySyntax
import org.gnit.lucenekmp.queryparser.flexible.standard.StandardQueryParser
import org.gnit.lucenekmp.queryparser.flexible.standard.config.PointsConfig
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.EscapeQuerySyntaxImpl
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestAbstractRangeQueryNode : LuceneTestCase() {
    private val escaper: EscapeQuerySyntax = EscapeQuerySyntaxImpl()
    private val parser = StandardQueryParser()

    /** GITHUB#7865 bug in toQueryString(). */
    @Test
    fun testTermRangeQueryNode() {
        val lower = FieldQueryNode("FIELD", "aaa", 0, 0)
        val upper = FieldQueryNode("FIELD", "zzz", 0, 0)
        val origNode = TermRangeQueryNode(lower, upper, true, true)
        val queryString = origNode.toQueryString(escaper)

        FieldQueryNode("FIELD", "(literal parens)", 0, 0).toQueryString(escaper)

        assertEquals("FIELD:[aaa TO zzz]", queryString.toString())
        val parsedQuery = parser.parse(queryString.toString(), "")
        assertEquals(queryString.toString(), parsedQuery.toString())
    }

    /** GITHUB#7865 bug in toQueryString(). */
    @Test
    fun testPointRangeQueryNode() {
        val format = NumberFormat.getIntegerInstance(Locale.ROOT)
        val lower = PointQueryNode("FIELD", 1, format)
        val upper = PointQueryNode("FIELD", 999, format)

        val origNode =
            PointRangeQueryNode(
                lower,
                upper,
                true,
                true,
                PointsConfig(format, Int::class),
            )
        val queryString = origNode.toQueryString(escaper)

        assertEquals("FIELD:[1 TO 999]", queryString.toString())
        val parsedQuery = parser.parse(queryString.toString(), "")
        assertEquals(queryString.toString(), parsedQuery.toString())
    }
}
