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
package org.gnit.lucenekmp.queries.function.valuesource

import org.gnit.lucenekmp.queries.function.ValueSource

/**
 * A base class for ValueSource implementations that retrieve values for a single field from
 * DocValues.
 */
abstract class FieldCacheSource(@kotlin.jvm.JvmField protected val field: String) : ValueSource() {
  open fun getField(): String {
    return field
  }

  override fun description(): String {
    return field
  }

  override fun equals(o: Any?): Boolean {
    if (o !is FieldCacheSource) return false
    val other = o
    return this.field.equals(other.field)
  }

  override fun hashCode(): Int {
    return field.hashCode()
  }
}
