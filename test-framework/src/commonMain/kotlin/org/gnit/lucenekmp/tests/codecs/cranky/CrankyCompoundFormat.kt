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
package org.gnit.lucenekmp.tests.codecs.cranky

import kotlin.random.Random
import okio.IOException
import org.gnit.lucenekmp.codecs.CompoundDirectory
import org.gnit.lucenekmp.codecs.CompoundFormat
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext

class CrankyCompoundFormat(var delegate: CompoundFormat, var random: Random) : CompoundFormat() {
    @Throws(IOException::class)
    override fun getCompoundReader(dir: Directory, si: SegmentInfo): CompoundDirectory {
        return delegate.getCompoundReader(dir, si)
    }

    @Throws(IOException::class)
    override fun write(dir: Directory, si: SegmentInfo, context: IOContext) {
        if (random.nextInt(100) == 0) {
            throw IOException("Fake IOException from CompoundFormat.write()")
        }
        delegate.write(dir, si, context)
    }
}
