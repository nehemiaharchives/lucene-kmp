package org.gnit.lucenekmp.jdkport

@Ported(from = "java.lang.LinkageError")
open class LinkageError : Error {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(cause: Throwable?) : super(cause)
}
