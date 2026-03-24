package org.gnit.lucenekmp.jdkport

/**
 * Port of `java.util.concurrent.BrokenBarrierException`.
 */
@Ported(from = "java.util.concurrent.BrokenBarrierException")
open class BrokenBarrierException : Exception {
    constructor() : super()

    constructor(message: String) : super(message)
}
