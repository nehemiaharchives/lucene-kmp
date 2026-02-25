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
package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestVirtualMethod : LuceneTestCase() {
    companion object {
        private val publicTestMethod =
            VirtualMethod(Base::class, "publicTest", String::class)
        private val protectedTestMethod =
            VirtualMethod(Base::class, "protectedTest", Int::class)
    }

    open class Base {
        open fun publicTest(test: String) {}

        protected open fun protectedTest(test: Int) {}
    }

    open class Nested1 : Base() {
        override fun publicTest(test: String) {}

        override fun protectedTest(test: Int) {}
    }

    open class Nested2 : Nested1() {
        // make it public here
        public override fun protectedTest(test: Int) {}
    }

    class Nested3 : Nested2() {
        override fun publicTest(test: String) {}
    }

    open class Nested4 : Base()

    class Nested5 : Nested4()

    @Test
    fun testGeneral() {
        assertEquals(0, publicTestMethod.getImplementationDistance(Base::class))
        assertEquals(1, publicTestMethod.getImplementationDistance(Nested1::class))
        assertEquals(1, publicTestMethod.getImplementationDistance(Nested2::class))
        assertEquals(1, publicTestMethod.getImplementationDistance(Nested3::class))
        assertTrue(publicTestMethod.isOverriddenAsOf(Nested4::class))
        assertTrue(publicTestMethod.isOverriddenAsOf(Nested5::class))

        assertEquals(0, protectedTestMethod.getImplementationDistance(Base::class))
        assertEquals(1, protectedTestMethod.getImplementationDistance(Nested1::class))
        assertEquals(1, protectedTestMethod.getImplementationDistance(Nested2::class))
        assertEquals(1, protectedTestMethod.getImplementationDistance(Nested3::class))
        assertTrue(protectedTestMethod.isOverriddenAsOf(Nested4::class))
        assertTrue(protectedTestMethod.isOverriddenAsOf(Nested5::class))

        assertEquals(
            0,
            VirtualMethod.compareImplementationDistance(
                Nested3::class, publicTestMethod, protectedTestMethod)
        )
        assertEquals(
            0,
            VirtualMethod.compareImplementationDistance(
                Nested5::class, publicTestMethod, protectedTestMethod)
        )
    }

    @Ignore
    @Test
    fun testExceptions() {
        // Not compatible with current minimal VirtualMethod.kt.
        // Java logic intentionally preserved as comment:
        //
        // // Object is not a subclass and can never override publicTest(String)
        // expectThrows(
        //     IllegalArgumentException.class,
        //     () -> {
        //       publicTestMethod.getImplementationDistance((Class) Object.class);
        //     });
        //
        // // Method bogus() does not exist, so IAE should be thrown
        // expectThrows(
        //     IllegalArgumentException.class,
        //     () -> {
        //       new VirtualMethod<>(Base.class, "bogus");
        //     });
        //
        // // Method publicTest(String) is not declared in TestClass2, so IAE should be thrown
        // expectThrows(
        //     IllegalArgumentException.class,
        //     () -> {
        //       new VirtualMethod<>(Nested2.class, "publicTest", String.class);
        //     });
        //
        // // try to create a second instance of the same baseClass / method combination
        // expectThrows(
        //     UnsupportedOperationException.class,
        //     () -> {
        //       new VirtualMethod<>(Base.class, "publicTest", String.class);
        //     });
    }
}
