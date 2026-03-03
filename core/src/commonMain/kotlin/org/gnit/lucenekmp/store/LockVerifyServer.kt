package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.jdkport.InetSocketAddress

/**
 * Simple standalone server that must be running when you use [VerifyingLockFactory]. This
 * server simply verifies at most one process holds the lock at a time. Run without any args to see
 * usage.
 *
 * @see VerifyingLockFactory
 * @see LockStressTest
 */
class LockVerifyServer {
    companion object {
        const val START_GUN_SIGNAL = 43

        // method pkg-private for tests
        @Throws(Exception::class)
        fun run(hostname: String, maxClients: Int, startClients: (InetSocketAddress) -> Unit) {
            throw UnsupportedOperationException(
                "LockVerifyServer is not supported in commonMain (network sockets are platform-specific). hostname=$hostname, maxClients=$maxClients, startClients=$startClients"
            )
        }

        @Throws(Exception::class)
        fun main(args: Array<String>) {
            if (args.size != 2) {
                println("Usage: java org.apache.lucene.store.LockVerifyServer bindToIp clients")
                throw IllegalArgumentException("Expected 2 args, got ${args.size}")
            }

            run(args[0], args[1].toInt()) { _ -> }
        }
    }
}
