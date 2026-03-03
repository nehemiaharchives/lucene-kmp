package org.gnit.lucenekmp.jdkport

@Ported(from = "java.net.InetSocketAddress")
class InetSocketAddress(val hostname: String, val port: Int) {
    override fun toString(): String {
        return "$hostname:$port"
    }
}
