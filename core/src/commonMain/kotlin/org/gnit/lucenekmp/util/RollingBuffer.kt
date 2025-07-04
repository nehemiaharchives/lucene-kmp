package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.assert

/**
 * Simplified port of Lucene's RollingBuffer.
 * Acts like a growable T[] with reuse of instances.
 */
abstract class RollingBuffer<T : RollingBuffer.Resettable> {
    interface Resettable { fun reset() }

    @Suppress("UNCHECKED_CAST")
    private var buffer = arrayOfNulls<Resettable>(8) as Array<T?>
    private var nextWrite = 0
    private var nextPos = 0
    private var count = 0

    init {
        for (i in buffer.indices) {
            buffer[i] = newInstance()
        }
    }

    protected abstract fun newInstance(): T

    fun reset() {
        var idx = nextWrite - 1
        while (count > 0) {
            if (idx == -1) idx = buffer.size - 1
            buffer[idx]!!.reset()
            idx--
            count--
        }
        nextWrite = 0
        nextPos = 0
        count = 0
    }

    // For assert:
    private fun inBounds(pos: Int): Boolean {
        return pos < nextPos && pos >= nextPos - count
    }

    private fun getIndex(pos: Int): Int {
        var index = nextWrite - (nextPos - pos)
        if (index < 0) index += buffer.size
        return index
    }

    /** Get instance for this absolute position */
    fun get(pos: Int): T {
        while (pos >= nextPos) {
            if (count == buffer.size) {
                val newBuffer = arrayOfNulls<Resettable>(buffer.size * 2) as Array<T?>
                buffer.copyInto(newBuffer, 0, nextWrite, buffer.size)
                buffer.copyInto(newBuffer, buffer.size - nextWrite, 0, nextWrite)
                for (i in buffer.size until newBuffer.size) {
                    newBuffer[i] = newInstance()
                }
                nextWrite = buffer.size
                buffer = newBuffer
            }
            if (nextWrite == buffer.size) nextWrite = 0
            nextWrite++
            nextPos++
            count++
        }
        assert(inBounds(pos))
        val index = getIndex(pos)
        return buffer[index]!!
    }

    fun getMaxPos(): Int = nextPos - 1

    fun getBufferSize(): Int = count

    fun freeBefore(pos: Int) {
        val toFree = count - (nextPos - pos)
        var index = nextWrite - count
        if (index < 0) index += buffer.size
        repeat(toFree) {
            if (index == buffer.size) index = 0
            buffer[index]!!.reset()
            index++
        }
        count -= toFree
    }
}
