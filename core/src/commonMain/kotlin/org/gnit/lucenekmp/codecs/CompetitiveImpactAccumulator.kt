package org.gnit.lucenekmp.codecs

import org.gnit.lucenekmp.index.Impact
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.TreeSet
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.jdkport.compareUnsigned
import org.gnit.lucenekmp.jdkport.toUnsignedInt
import kotlin.math.max

/** This class accumulates the (freq, norm) pairs that may produce competitive scores.  */
class CompetitiveImpactAccumulator {
    // We speed up accumulation for common norm values with this array that maps
    // norm values in -128..127 to the maximum frequency observed for these norm
    // values
    private val maxFreqs: IntArray = IntArray(256)

    // This TreeSet stores competitive (freq,norm) pairs for norm values that fall
    // outside of -128..127. It is always empty with the default similarity, which
    // encodes norms as bytes.
    private val otherFreqNormPairs: TreeSet<Impact> = TreeSet { o1: Impact, o2: Impact ->
        // greater freqs compare greater
        var cmp: Int = Int.compare(o1.freq, o2.freq)
        if (cmp == 0) {
            // greater norms compare lower
            cmp = Long.compareUnsigned(o2.norm, o1.norm)
        }
        cmp
    }

    /** Reset to the same state it was in after creation.  */
    fun clear() {
        Arrays.fill(maxFreqs, 0)
        otherFreqNormPairs.clear()
        require(assertConsistent())
    }

    /**
     * Accumulate a (freq,norm) pair, updating this structure if there is no equivalent or more
     * competitive entry already.
     */
    fun add(freq: Int, norm: Long) {
        if (norm >= Byte.Companion.MIN_VALUE && norm <= Byte.Companion.MAX_VALUE) {
            val index: Int = Byte.toUnsignedInt(norm.toByte())
            maxFreqs[index] = max(maxFreqs[index], freq)
        } else {
            add(Impact(freq, norm), otherFreqNormPairs)
        }
        require(assertConsistent())
    }

    /** Merge `acc` into this.  */
    fun addAll(acc: CompetitiveImpactAccumulator) {
        val maxFreqs = this.maxFreqs
        val otherMaxFreqs = acc.maxFreqs
        for (i in maxFreqs.indices) {
            maxFreqs[i] = max(maxFreqs[i], otherMaxFreqs[i])
        }

        for (entry in acc.otherFreqNormPairs) {
            add(entry, otherFreqNormPairs)
        }

        require(assertConsistent())
    }

    /** Replace the content of this `acc` with the provided `acc`.  */
    fun copy(acc: CompetitiveImpactAccumulator) {
        val maxFreqs = this.maxFreqs
        val otherMaxFreqs = acc.maxFreqs

        System.arraycopy(otherMaxFreqs, 0, maxFreqs, 0, maxFreqs.size)
        otherFreqNormPairs.clear()
        otherFreqNormPairs.addAll(acc.otherFreqNormPairs)

        require(assertConsistent())
    }

    /** Get the set of competitive freq and norm pairs, ordered by increasing freq and norm.  */
    fun getCompetitiveFreqNormPairs(): MutableList<Impact> {
        val impacts: MutableList<Impact> = ArrayList()
        var maxFreqForLowerNorms = 0
        for (i in maxFreqs.indices) {
            val maxFreq = maxFreqs[i]
            if (maxFreq > maxFreqForLowerNorms) {
                impacts.add(Impact(maxFreq, i.toLong()))
                maxFreqForLowerNorms = maxFreq
            }
        }

        if (otherFreqNormPairs.isEmpty()) {
            // Common case: all norms are bytes
            return impacts
        }

        val freqNormPairs: TreeSet<Impact> = TreeSet<Impact>(this.otherFreqNormPairs)
        for (impact in impacts) {
            add(impact, freqNormPairs)
        }
        return freqNormPairs.toMutableList()
    }

    private fun add(newEntry: Impact, freqNormPairs: TreeSet<Impact>) {
        val next: Impact? = freqNormPairs.ceiling(newEntry)
        if (next == null) {
            // nothing is more competitive
            freqNormPairs.add(newEntry)
        } else if (Long.compareUnsigned(next.norm, newEntry.norm) <= 0) {
            // we already have this entry or more competitive entries in the tree
            return
        } else {
            // some entries have a greater freq but a less competitive norm, so we
            // don't know which one will trigger greater scores, still add to the tree
            freqNormPairs.add(newEntry)
        }

        val it: MutableIterator<Impact> = freqNormPairs.headSet(newEntry, false).descendingIterator()
        while (it.hasNext()) {
            val entry: Impact = it.next()
            if (Long.compareUnsigned(entry.norm, newEntry.norm) >= 0) {
                // less competitive
                it.remove()
            } else {
                // lesser freq but better norm, further entries are not comparable
                break
            }
        }
    }

    override fun toString(): String {
        return ArrayList(this.getCompetitiveFreqNormPairs()).toString()
    }

    // Only called by assertions
    private fun assertConsistent(): Boolean {
        var previousFreq = 0
        var previousNorm: Long = 0
        for (impact in otherFreqNormPairs) {
            require(impact.norm < Byte.Companion.MIN_VALUE || impact.norm > Byte.Companion.MAX_VALUE)
            require(previousFreq < impact.freq)
            require(Long.compareUnsigned(previousNorm, impact.norm) < 0)
            previousFreq = impact.freq
            previousNorm = impact.norm
        }
        return true
    }
}
