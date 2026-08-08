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
package org.gnit.lucenekmp.queryparser.flexible.core

import org.gnit.lucenekmp.queryparser.flexible.messages.Message
import org.gnit.lucenekmp.queryparser.flexible.messages.NLSException

/**
 * Error class with NLS support
 *
 * @see org.gnit.lucenekmp.queryparser.flexible.messages.NLS
 * @see org.gnit.lucenekmp.queryparser.flexible.messages.Message
 */
class QueryNodeError : Error, NLSException {
    override val messageObject: Message?

    /**
     * @param message - NLS Message Object
     */
    constructor(message: Message) : super(message.key) {
        this.messageObject = message
    }

    /**
     * @param throwable - @see java.lang.Error
     */
    constructor(throwable: Throwable) : super(throwable) {
        this.messageObject = null
    }

    /**
     * @param message - NLS Message Object
     * @param throwable - @see java.lang.Error
     */
    constructor(message: Message, throwable: Throwable) : super(message.key, throwable) {
        this.messageObject = message
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.lucene.messages.NLSException#getMessageObject()
     */
}
