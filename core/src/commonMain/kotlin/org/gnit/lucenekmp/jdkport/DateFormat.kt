/*
 * Copyright (c) 1996, 2025, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package org.gnit.lucenekmp.jdkport

/** Kotlin common subset of `java.text.DateFormat` used by Lucene's flexible query parser. */
@Ported(from = "java.text.DateFormat")
abstract class DateFormat {
    abstract fun format(
        date: Date,
        toAppendTo: StringBuilder,
        pos: FieldPosition
    ): StringBuilder

    abstract fun parse(source: String, parsePosition: ParsePosition): Date?

    open fun format(
        obj: Any,
        toAppendTo: StringBuilder,
        pos: FieldPosition
    ): StringBuilder {
        return format(obj as Date, toAppendTo, pos)
    }
}
