package org.gnit.lucenekmp.jdkport

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
@PublishedApi
internal actual fun exitProcess(exitCode: Int) {
    kotlin.system.exitProcess(exitCode)
}