package org.gnit.lucenekmp.jdkport

// Custom exceptions similar to the Java NIO ones.
@Ported(from = "java.nio.ReadOnlyBufferException")
class ReadOnlyBufferException(message: String? = null) : RuntimeException(message)
