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
package org.gnit.lucenekmp.queryparser.flexible.core.nodes

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestQueryNode : LuceneTestCase() {

    /* LUCENE-2227 bug in QueryNodeImpl.add() */
    @Test
    fun testAddChildren() {
        val nodeA: QueryNode = FieldQueryNode("foo", "A", 0, 1)
        val nodeB: QueryNode = FieldQueryNode("foo", "B", 1, 2)
        val bq = BooleanQueryNode(listOf(nodeA))
        bq.add(listOf(nodeB))
        assertEquals(2, bq.children!!.size)
    }

    /* LUCENE-3045 bug in QueryNodeImpl.containsTag(String key)*/
    @Test
    fun testTags() {
        val node: QueryNode = FieldQueryNode("foo", "A", 0, 1)

        node.setTag("TaG", Any())
        assertTrue(node.tagMap.isNotEmpty())
        assertTrue(node.containsTag("tAg"))
        assertTrue(node.getTag("tAg") != null)
    }

    /* LUCENE-5099 - QueryNodeProcessorImpl should set parent to null before returning on processing */
    @Test
    fun testRemoveFromParent() {
        val booleanNode = BooleanQueryNode(emptyList())
        val fieldNode = FieldQueryNode("foo", "A", 0, 1)
        assertNull(fieldNode.parent)

        booleanNode.add(fieldNode)
        assertNotNull(fieldNode.parent)

        fieldNode.removeFromParent()
        assertNull(fieldNode.parent)
        /* LUCENE-5805 - QueryNodeImpl.removeFromParent does a lot of work without any effect */
        assertFalse(booleanNode.children!!.contains(fieldNode))

        booleanNode.add(fieldNode)
        assertNotNull(fieldNode.parent)

        booleanNode.set(emptyList())
        assertNull(fieldNode.parent)
    }

    @Test
    fun testRemoveChildren() {
        val booleanNode = BooleanQueryNode(emptyList())
        val fieldNode = FieldQueryNode("foo", "A", 0, 1)

        booleanNode.add(fieldNode)
        assertTrue(booleanNode.children!!.size == 1)

        booleanNode.removeChildren(fieldNode)
        assertTrue(booleanNode.children!!.size == 0)
        assertNull(fieldNode.parent)
    }
}
