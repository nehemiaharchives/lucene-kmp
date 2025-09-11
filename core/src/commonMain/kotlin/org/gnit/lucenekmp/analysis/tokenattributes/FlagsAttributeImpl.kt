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
package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector

/** Default implementation of [FlagsAttribute]. */
class FlagsAttributeImpl : AttributeImpl(), FlagsAttribute {
    override var flags: Int = 0

    override fun clear() {
        flags = 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is FlagsAttributeImpl && other.flags == flags
    }

    override fun hashCode(): Int {
        return flags
    }

    override fun copyTo(target: AttributeImpl) {
        (target as FlagsAttribute).flags = flags
    }

    override fun reflectWith(reflector: AttributeReflector) {
        reflector.reflect(FlagsAttribute::class, "flags", flags)
    }

    override fun newInstance(): AttributeImpl {
        throw UnsupportedOperationException(
            "FlagsAttributeImpl cannot be instantiated directly, use init() instead"
        )
    }
}

