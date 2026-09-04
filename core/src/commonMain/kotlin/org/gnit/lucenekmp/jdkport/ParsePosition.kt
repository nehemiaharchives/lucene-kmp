/*
 * Copyright (c) 1996, 2025, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package org.gnit.lucenekmp.jdkport

/** Kotlin common subset of `java.text.ParsePosition` used by format implementations. */
@Ported(from = "java.text.ParsePosition")
class ParsePosition(var index: Int) {
    var errorIndex: Int = -1
}
