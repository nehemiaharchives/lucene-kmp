package org.gnit.lucenekmp.jdkport

/**
 * A channel that can read and write bytes. This interface simply unifies
 * ReadableByteChannel and WritableByteChannel; it does not specify any new operations.
 *
 * @since 1.4
 */
@Ported(from = "java.nio.channels.ByteChannel")
interface ByteChannel : ReadableByteChannel, WritableByteChannel
