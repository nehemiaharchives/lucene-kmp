package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.numberOfLeadingZeros
import org.gnit.lucenekmp.jdkport.numberOfTrailingZeros


/**
 * An approximate priority queue, which attempts to poll items by decreasing log of the weight,
 * though exact ordering is not guaranteed. This class doesn't support null elements.
 */
internal class ApproximatePriorityQueue<T> {
    // Indexes between 0 and 63 are sparsely populated, and indexes that are
    // greater than or equal to 64 are densely populated
    // Items close to the beginning of this list are more likely to have a
    // higher weight.
    private val slots: MutableList<T?> = ArrayList<T?>(Long.SIZE_BITS)

    // A bitset where ones indicate that the corresponding index in `slots` is taken.
    private var usedSlots = 0L

    init {
        for (i in 0..<Long.SIZE_BITS) {
            slots.add(null)
        }
    }

    /** Add an entry to this queue that has the provided weight.  */
    fun add(entry: T?, weight: Long) {
        checkNotNull(entry)

        // The expected slot of an item is the number of leading zeros of its weight,
        // ie. the larger the weight, the closer an item is to the start of the array.
        val expectedSlot: Int = Long.numberOfLeadingZeros(weight)

        // If the slot is already taken, we look for the next one that is free.
        // The above bitwise operation is equivalent to looping over slots until finding one that is
        // free.
        val freeSlots = usedSlots.inv()
        val destinationSlot: Int =
            expectedSlot + Long.numberOfTrailingZeros(freeSlots ushr expectedSlot)
        assert(destinationSlot >= expectedSlot)
        if (destinationSlot < Long.SIZE_BITS) {
            usedSlots = usedSlots or (1L shl destinationSlot)
            val previous = slots.set(destinationSlot, entry)
            assert(previous == null)
        } else {
            slots.add(entry)
        }
    }

    /**
     * Return an entry matching the predicate. This will usually be one of the available entries that
     * have the highest weight, though this is not guaranteed. This method returns `null` if no
     * free entries are available.
     */
    fun poll(predicate: (T?) -> Boolean /*java.util.function.Predicate<T?>*/): T? {
        // Look at indexes 0..63 first, which are sparsely populated.
        var nextSlot = 0
        do {
            val nextUsedSlot: Int = nextSlot + Long.numberOfTrailingZeros(usedSlots ushr nextSlot)
            if (nextUsedSlot >= Long.SIZE_BITS) {
                break
            }
            val entry = slots[nextUsedSlot]
            if (predicate(entry)) {
                usedSlots = usedSlots and (1L shl nextUsedSlot).inv()
                slots[nextUsedSlot] = null
                return entry
            } else {
                nextSlot = nextUsedSlot + 1
            }
        } while (nextSlot < Long.SIZE_BITS)

        // Then look at indexes 64.. which are densely populated.
        // Poll in descending order so that if the number of indexing threads
        // decreases, we keep using the same entry over and over again.
        // Resizing operations are also less costly on lists when items are closer
        // to the end of the list.
        val lit = slots.listIterator(slots.size)
        while (lit.previousIndex() >= Long.SIZE_BITS) {
            val entry = lit.previous()
            if (predicate(entry)) {
                lit.remove()
                return entry
            }
        }

        // No entry matching the predicate was found.
        return null
    }

    // Only used for assertions
    fun contains(o: T?): Boolean {
        if (o == null) {
            throw NullPointerException()
        }
        return slots.contains(o)
    }

    val isEmpty: Boolean
        get() = usedSlots == 0L && slots.size == Long.SIZE_BITS

    fun remove(o: T?): Boolean {
        if (o == null) {
            throw NullPointerException()
        }
        val index = slots.indexOf(o)
        if (index == -1) {
            return false
        }
        if (index >= Long.SIZE_BITS) {
            slots.removeAt(index)
        } else {
            usedSlots = usedSlots and (1L shl index).inv()
            slots[index] = null
        }
        return true
    }
}
