package org.gnit.lucenekmp.jdkport

internal actual fun platformThreadYield() {
    java.lang.Thread.yield()
}
