/*
 * Copyright (c) 1994, 2025, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package org.gnit.lucenekmp.jdkport

/** Kotlin common subset of `java.util.Date` used by Lucene's flexible query parser. */
@Ported(from = "java.util.Date")
data class Date(val time: Long)
