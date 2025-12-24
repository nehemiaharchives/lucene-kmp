package org.gnit.lucenekmp.jdkport

@PublishedApi
internal actual fun exitProcess(exitCode: Int) {
    kotlin.system.exitProcess(exitCode)
}