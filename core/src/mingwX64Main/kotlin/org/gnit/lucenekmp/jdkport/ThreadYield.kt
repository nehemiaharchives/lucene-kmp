package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.yield
import kotlinx.coroutines.runBlocking

internal actual fun platformThreadYield() {
    runBlocking {
        yield()
    }
}
