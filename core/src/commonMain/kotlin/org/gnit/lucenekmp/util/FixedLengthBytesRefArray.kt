package org.gnit.lucenekmp.util

/**
 * Just like [BytesRefArray] except all values have the same length.
 *
 * **Note: This class is not Thread-Safe!**
 *
 * @lucene.internal
 * @lucene.experimental
 */
internal class FixedLengthBytesRefArray(private val valueLength: Int) : SortableBytesRefArray {
    private val valuesPerBlock: Int

    /** How many values have been appended */
    private var size = 0

    /** How many blocks are used */
    private var currentBlock = -1

    private var nextEntry: Int

    private var blocks: Array<ByteArray?>

    /** Creates a new [BytesRefArray] with a counter to track allocated bytes */
    init {
        // ~32K per page, unless each value is > 32K:
        valuesPerBlock = kotlin.math.max(1, 32768 / valueLength)
        nextEntry = valuesPerBlock
        blocks = arrayOf()
    }

    /** Clears this [BytesRefArray] */
    override fun clear() {
        size = 0
        blocks = arrayOf()
        currentBlock = -1
        nextEntry = valuesPerBlock
    }

    /**
     * Appends a copy of the given [BytesRef] to this [BytesRefArray].
     *
     * @param bytes the bytes to append
     * @return the index of the appended bytes
     */
    override fun append(bytes: BytesRef): Int {
        require(bytes.length == valueLength) {
            "value length is ${bytes.length} but is supposed to always be $valueLength"
        }
        if (nextEntry == valuesPerBlock) {
            currentBlock++
            if (currentBlock == blocks.size) {
                val size = ArrayUtil.oversize(currentBlock + 1, RamUsageEstimator.NUM_BYTES_OBJECT_REF)
                val next = arrayOfNulls<ByteArray>(size)
                blocks.copyInto(next, 0, 0, blocks.size)
                blocks = next
            }
            blocks[currentBlock] = ByteArray(valuesPerBlock * valueLength)
            nextEntry = 0
        }

        bytes.bytes.copyInto(
            destination = blocks[currentBlock]!!,
            destinationOffset = nextEntry * valueLength,
            startIndex = bytes.offset,
            endIndex = bytes.offset + valueLength
        )
        nextEntry++

        return size++
    }

    /**
     * Returns the current size of this [FixedLengthBytesRefArray]
     *
     * @return the current size of this [FixedLengthBytesRefArray]
     */
    override fun size(): Int {
        return size
    }

    private fun sort(comp: Comparator<BytesRef>): IntArray {
        val orderedEntries = IntArray(size())
        for (i in orderedEntries.indices) {
            orderedEntries[i] = i
        }

        object : StringSorter(comp) {

            init {
                scratchBytes1.length = valueLength
                scratchBytes2.length = valueLength
                pivot.length = valueLength
            }

            override fun get(builder: BytesRefBuilder, result: BytesRef, i: Int) {
                val index = orderedEntries[i]
                result.bytes = blocks[index / valuesPerBlock]!!
                result.offset = (index % valuesPerBlock) * valueLength
            }

            override fun swap(i: Int, j: Int) {
                val o = orderedEntries[i]
                orderedEntries[i] = orderedEntries[j]
                orderedEntries[j] = o
            }
        }.sort(0, size())
        return orderedEntries
    }

    /**
     * Returns a [BytesRefIterator] with point in time semantics. The iterator provides access
     * to all so far appended [BytesRef] instances.
     *
     * The iterator will iterate the byte values in the order specified by the comparator.
     *
     * This is a non-destructive operation.
     */
    override fun iterator(comp: Comparator<BytesRef>): BytesRefIterator {
        val result = BytesRef()
        result.length = valueLength
        val size = size()
        val indices = sort(comp)
        return object : BytesRefIterator {
            var pos = 0

            override fun next(): BytesRef? {
                if (pos < size) {
                    val index = indices[pos]
                    pos++
                    result.bytes = blocks[index / valuesPerBlock]!!
                    result.offset = (index % valuesPerBlock) * valueLength
                    return result
                }
                return null
            }
        }
    }
}
