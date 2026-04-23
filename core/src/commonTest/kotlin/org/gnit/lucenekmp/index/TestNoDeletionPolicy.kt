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
package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestNoDeletionPolicy : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun testNoDeletionPolicy() {
        val idp: IndexDeletionPolicy = NoDeletionPolicy.INSTANCE
        idp.onInit(mutableListOf())
        idp.onCommit(mutableListOf())
    }

    // Commented out because this depends on Java reflection
    /*@Test
    @Throws(Exception::class)
    fun testFinalSingleton() {
        assertTrue(Modifier.isFinal(NoDeletionPolicy::class.java.modifiers))
        val ctors = NoDeletionPolicy::class.java.declaredConstructors
        assertEquals("expected 1 private ctor only: ${ctors.contentToString()}", 1, ctors.size)
        assertTrue(
            "that 1 should be private: ${ctors[0]}",
            Modifier.isPrivate(ctors[0].modifiers)
        )
    }*/

    // Commented out because this depends on Java reflection
    /*@Test
    @Throws(Exception::class)
    fun testMethodsOverridden() {
        // Ensures that all methods of IndexDeletionPolicy are
        // overridden/implemented. That's important to ensure that NoDeletionPolicy
        // overrides everything, so that no unexpected behavior/error occurs.
        // NOTE: even though IndexDeletionPolicy is an interface today, and so all
        // methods must be implemented by NoDeletionPolicy, this test is important
        // in case one day IDP becomes an abstract class.
        for (m in NoDeletionPolicy::class.java.methods) {
            // getDeclaredMethods() returns just those methods that are declared on
            // NoDeletionPolicy. getMethods() returns those that are visible in that
            // context, including ones from Object. So just filter out Object. If in
            // the future IndexDeletionPolicy will become a class that extends a
            // different class than Object, this will need to change.
            if (m.declaringClass != Any::class.java) {
                assertTrue(m.declaringClass == NoDeletionPolicy::class.java, "$m is not overridden !")
            }
        }
    }*/

    @Test
    @Throws(Exception::class)
    fun testAllCommitsRemain() {
        val dir: Directory = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setIndexDeletionPolicy(NoDeletionPolicy.INSTANCE),
            )
        for (i in 0..<10) {
            val doc = Document()
            doc.add(newTextField("c", "a$i", Field.Store.YES))
            writer.addDocument(doc)
            writer.commit()
            assertEquals(i + 1, DirectoryReader.listCommits(dir).size, "wrong number of commits !")
        }
        writer.close()
        dir.close()
    }
}
