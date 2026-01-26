package org.gnit.lucenekmp.jdkport

/**
 * Checked exception received by a thread when another thread closes the
 * channel or the part of the channel upon which it is blocked in an I/O
 * operation.
 *
 * @since 1.4
 */
@Ported(from = "java.nio.channels.AsynchronousCloseException")
open class AsynchronousCloseException : ClosedChannelException()
