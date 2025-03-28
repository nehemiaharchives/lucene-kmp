package org.gnit.lucenekmp.internal.hppc

/**
 * BufferAllocationException forked from HPPC.
 *
 * @lucene.internal
 */
class BufferAllocationException : RuntimeException {
    constructor(message: String?) : super(message)

    constructor(message: String, vararg args: Any?) : this(message, null, *args)

    constructor(message: String, t: Throwable?, vararg args: Any?) : super(formatMessage(message, t, *args), t)

    companion object {
        private fun formatMessage(message: String, t: Throwable?, vararg args: Any?): String {
            try {
                return message + args.joinToString()
            } catch (e: Exception) {
                val substitute =
                    BufferAllocationException(message + " [ILLEGAL FORMAT, ARGS SUPPRESSED]")
                if (t != null) {
                    substitute.addSuppressed(t)
                }
                substitute.addSuppressed(e)
                throw substitute
            }
        }
    }
}
