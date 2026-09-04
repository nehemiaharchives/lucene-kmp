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
package org.gnit.lucenekmp.queryparser.flexible.spans

import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldableNode
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector

/**
 * This attribute is used by the [UniqueFieldQueryNodeProcessor] processor. It holds a value
 * that defines which is the unique field name that should be set in every [FieldableNode].
 *
 * @see UniqueFieldQueryNodeProcessor
 */
class UniqueFieldAttributeImpl : AttributeImpl(), UniqueFieldAttribute {

    override var uniqueField: CharSequence = ""

    init {
        clear()
    }

    override fun clear() {
        this.uniqueField = ""
    }

    override fun copyTo(target: AttributeImpl) {

        if (target !is UniqueFieldAttributeImpl) {
            throw IllegalArgumentException(
                "cannot copy the values from attribute UniqueFieldAttribute to an instance of " +
                    target::class.qualifiedName
            )
        }

        val uniqueFieldAttr = target
        uniqueFieldAttr.uniqueField = uniqueField.toString()
    }

    override fun equals(other: Any?): Boolean {

        if (other is UniqueFieldAttributeImpl) {

            return other.uniqueField == this.uniqueField
        }

        return false
    }

    override fun hashCode(): Int {
        return this.uniqueField.hashCode()
    }

    override fun reflectWith(reflector: AttributeReflector) {
        reflector.reflect(UniqueFieldAttribute::class, "uniqueField", uniqueField)
    }

    override fun newInstance(): AttributeImpl {
        return UniqueFieldAttributeImpl()
    }
}
