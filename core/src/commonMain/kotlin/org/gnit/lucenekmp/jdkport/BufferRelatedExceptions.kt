package org.gnit.lucenekmp.jdkport


@Ported(from = "java.nio.BufferOverflowException")
class BufferOverflowException(message: String = "") : RuntimeException(message)

@Ported(from = "java.nio.BufferUnderflowException")
class BufferUnderflowException(message: String = "") : RuntimeException(message)
