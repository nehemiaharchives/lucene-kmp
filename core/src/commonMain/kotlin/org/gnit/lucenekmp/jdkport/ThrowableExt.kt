package org.gnit.lucenekmp.jdkport

fun Throwable.printStackTrace(s: PrintStream) {
    // Guard against malicious overrides of Throwable.equals by
    // using a Set with identity equality semantics.
    val dejaVu: MutableSet<Throwable> = mutableSetOf()
    dejaVu.add(this)

    // Print our stack trace
    s.println(this.stackTraceToString())
}
