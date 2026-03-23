package org.gnit.lucenekmp.jdkport

@Ported(from = "java.lang.OutOfMemoryError")
open class OutOfMemoryError : Error {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(cause: Throwable?) : super(cause)
}
