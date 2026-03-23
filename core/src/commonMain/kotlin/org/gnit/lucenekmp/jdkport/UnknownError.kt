package org.gnit.lucenekmp.jdkport

@Ported(from = "java.lang.UnknownError")
open class UnknownError : Error {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(cause: Throwable?) : super(cause)
}
