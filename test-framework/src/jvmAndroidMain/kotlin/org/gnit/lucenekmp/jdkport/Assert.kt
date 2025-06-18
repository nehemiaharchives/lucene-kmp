package org.gnit.lucenekmp.jdkport

internal actual inline fun assert(
    condition: Boolean,
    lazyMessage: () -> Any
){
    kotlin.assert(condition, lazyMessage)
}
