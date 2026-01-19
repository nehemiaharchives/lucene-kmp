package org.gnit.lucenekmp.jdkport

@Ported(from = "java.lang.IllegalMonitorStateException")
class IllegalMonitorStateException(message: String? = null) : IllegalStateException(message)
