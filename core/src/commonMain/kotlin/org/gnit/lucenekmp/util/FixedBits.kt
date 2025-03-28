package org.gnit.lucenekmp.util


/** Immutable twin of FixedBitSet.  */
internal class FixedBits(bits: LongArray, length: Int) : Bits {
    val bitSet: FixedBitSet = FixedBitSet(bits, length)

    override fun get(index: Int): Boolean {
        return bitSet.get(index)
    }

    override fun applyMask(dest: FixedBitSet, offset: Int) {
        bitSet.applyMask(dest, offset)
    }

    override fun length(): Int {
        return bitSet.length()
    }
}
