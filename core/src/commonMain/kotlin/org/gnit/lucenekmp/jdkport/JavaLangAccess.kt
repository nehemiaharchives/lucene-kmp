package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Job

object JavaLangAccess {
    /**
     * Decodes ASCII from the source byte array into the destination
     * char array.
     *
     * @return the number of bytes successfully decoded, at most len
     */
    fun decodeASCII(src: ByteArray, srcOff: Int, dst: CharArray?, dstOff: Int, len: Int): Int {
        if (dst == null) return 0
        val max = minOf(len, src.size - srcOff, dst.size - dstOff)
        for (i in 0 until max) {
            val b = src[srcOff + i]
            if (b < 0) break // Non-ASCII byte
            dst[dstOff + i] = b.toInt().toChar()
        }
        // Return the number of bytes successfully decoded as ASCII
        var count = 0
        for (i in 0 until max) {
            if (src[srcOff + i] < 0) break
            count++
        }
        return count
    }

    fun encodeASCII(src: CharArray, srcOff: Int, dst: ByteArray?, dstOff: Int, len: Int): Int {
        if (dst == null) return 0
        val max = minOf(len, src.size - srcOff, dst.size - dstOff)
        for (i in 0 until max) {
            val c = src[srcOff + i]
            if (c.code > 0x7F) break // Non-ASCII char
            dst[dstOff + i] = c.code.toByte()
        }
        // Return the number of chars successfully encoded as ASCII
        var count = 0
        for (i in 0 until max) {
            if (src[srcOff + i].code > 0x7F) break
            count++
        }
        return count
    }

    /**
     * Inflated copy from byte[] to char[], as defined by StringLatin1.inflate
     */
    fun inflateBytesToChars(src: ByteArray?, srcOff: Int, dst: CharArray?, dstOff: Int, len: Int){
        if (src == null || dst == null) return
        val max = minOf(len, src.size - srcOff, dst.size - dstOff)
        for (i in 0 until max) {
            val b = src[srcOff + i]
            dst[dstOff + i] = b.toInt().toChar()
        }
    }

    var headStackableScope: StackableScope? = null

    fun headStackableScope(job: Job?): StackableScope? {
        // Imitate "top of stackable scope stack" by getting StackableScope from coroutine context
        TODO("Not yet implemented")
    }

    /**
     * Returns the ThreadContainer for a thread, may be null.
     */
    fun threadContainer(thread: Job): ThreadContainer? {
        TODO("Not yet implemented")
    }

    /**
     * Starts a thread in the given ThreadContainer.
     */
    fun start(thread: Job, container: ThreadContainer){
        TODO("Not yet implemented")
    }

    private val jobRegistry: MutableSet<Job> = mutableSetOf()

    fun registerJob(job: Job) {
        jobRegistry.add(job)
    }

    fun unregisterJob(job: Job) {
        jobRegistry.remove(job)
    }

    val allThreads: Array<Job>
        get() = jobRegistry.filter { it.isActive }.toTypedArray()


}
