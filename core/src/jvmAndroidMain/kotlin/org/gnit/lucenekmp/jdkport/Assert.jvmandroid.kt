package org.gnit.lucenekmp.jdkport

//@PublishedApi
actual inline fun assert(
    condition: Boolean,
    lazyMessage: () -> Any
){
    kotlin.assert(condition, lazyMessage)
}
