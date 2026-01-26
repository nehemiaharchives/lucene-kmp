package org.gnit.lucenekmp.jdkport

/**
 * Checked exception received by a thread when another thread interrupts it
 * while it is blocked in an I/O operation upon a channel. Before this
 * exception is thrown the channel will have been closed and the interrupt
 * status of the previously-blocked thread will have been set.
 *
 * @since 1.4
 */
@Ported(from = "java.nio.channels.ClosedByInterruptException")
open class ClosedByInterruptException : AsynchronousCloseException()
