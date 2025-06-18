package org.gnit.lucenekmp.jdkport

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
@PublishedApi
internal actual inline fun assert(
    condition: Boolean,
    lazyMessage: () -> Any
) {
    kotlin.assert(condition, lazyMessage)
}
