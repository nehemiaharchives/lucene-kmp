/*
 * Copyright (c) 1996, 2025, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package org.gnit.lucenekmp.jdkport

/** Kotlin common subset of `java.text.FieldPosition` used by format implementations. */
@Ported(from = "java.text.FieldPosition")
class FieldPosition(val field: Int = 0) {
    var beginIndex: Int = 0
    var endIndex: Int = 0
}
