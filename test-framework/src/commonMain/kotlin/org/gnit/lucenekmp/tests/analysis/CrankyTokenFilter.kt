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
package org.gnit.lucenekmp.tests.analysis

import kotlin.random.Random
import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Throws IOException from random Tokenstream methods.
 *
 * <p>This can be used to simulate a buggy analyzer in IndexWriter, where we must delete the
 * document but not abort everything in the buffer.
 */
class CrankyTokenFilter(input: TokenStream, val random: Random) : TokenFilter(input) {
    var thingToDo: Int = 0

    /** Creates a new CrankyTokenFilter */
    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (thingToDo == 0 && random.nextBoolean()) {
            throw IOException("Fake IOException from TokenStream.incrementToken()")
        }
        return input.incrementToken()
    }

    @Throws(IOException::class)
    override fun end() {
        super.end()
        if (thingToDo == 1 && random.nextBoolean()) {
            throw IOException("Fake IOException from TokenStream.end()")
        }
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        thingToDo = random.nextInt(100)
        if (thingToDo == 2 && random.nextBoolean()) {
            throw IOException("Fake IOException from TokenStream.reset()")
        }
    }

    override fun close() {
        super.close()
        if (thingToDo == 3 && random.nextBoolean()) {
            throw IOException("Fake IOException from TokenStream.close()")
        }
    }
}
