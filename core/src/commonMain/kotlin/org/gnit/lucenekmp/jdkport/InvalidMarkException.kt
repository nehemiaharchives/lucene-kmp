package org.gnit.lucenekmp.jdkport


/**
 * Unchecked exception thrown when an attempt is made to reset a buffer
 * when its mark is not defined.
 *
 * @since 1.4
 */
@Ported(from = "java.nio.InvalidMarkException")
class InvalidMarkException: IllegalStateException()
