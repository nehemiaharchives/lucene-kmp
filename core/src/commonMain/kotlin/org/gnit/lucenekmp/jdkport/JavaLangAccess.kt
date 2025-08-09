package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking

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

    // Maintain a per-Job head of the StackableScope stack.
    private val stackHeads: MutableMap<Job, StackableScope?> = mutableMapOf()

    // Gets/sets the head for the current coroutine Job.
    var headStackableScope: StackableScope?
        get() {
            val job = runBlocking { currentCoroutineContext()[Job] }
            return if (job != null) stackHeads[job] else null
        }
        set(value) {
            val job = runBlocking { currentCoroutineContext()[Job] }
            if (job != null) {
                if (value == null) stackHeads.remove(job) else stackHeads[job] = value
            }
        }

    fun headStackableScope(job: Job?): StackableScope? {
    if (job == null) return null
    return stackHeads[job]
    }

    /**
     * Returns the ThreadContainer for a thread, may be null.
     */
    private val threadContainerMap: MutableMap<Job, ThreadContainer> = mutableMapOf()

    fun threadContainer(thread: Job): ThreadContainer? {
    return threadContainerMap[thread]
    }

    /**
     * Starts a thread in the given ThreadContainer.
     */
    fun start(thread: Job, container: ThreadContainer){
    // Record the container for this thread and register it for discovery.
    threadContainerMap[thread] = container
    registerJob(thread)
    // Notify the container that the thread is starting.
    container.onStart(thread)
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
