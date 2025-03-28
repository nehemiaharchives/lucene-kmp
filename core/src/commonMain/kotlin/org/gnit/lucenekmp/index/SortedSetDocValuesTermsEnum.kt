package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder


/** Implements a [TermsEnum] wrapping a provided [SortedSetDocValues].  */
internal class SortedSetDocValuesTermsEnum(private val values: SortedSetDocValues) : BaseTermsEnum() {
    private var currentOrd: Long = -1
    private val scratch: BytesRefBuilder

    /** Creates a new TermsEnum over the provided values  */
    init {
        scratch = BytesRefBuilder()
    }

    @Throws(IOException::class)
    override fun seekCeil(text: BytesRef): SeekStatus {
        val ord = values.lookupTerm(text)
        if (ord >= 0) {
            currentOrd = ord
            scratch.copyBytes(text)
            return SeekStatus.FOUND
        } else {
            currentOrd = -ord - 1
            if (currentOrd == values.valueCount) {
                return SeekStatus.END
            } else {
                // TODO: hmm can we avoid this "extra" lookup:
                scratch.copyBytes(values.lookupOrd(currentOrd))
                return SeekStatus.NOT_FOUND
            }
        }
    }

    @Throws(IOException::class)
    override fun seekExact(text: BytesRef): Boolean {
        val ord = values.lookupTerm(text)
        if (ord >= 0) {
            currentOrd = ord
            scratch.copyBytes(text)
            return true
        } else {
            return false
        }
    }

    @Throws(IOException::class)
    override fun seekExact(ord: Long) {
        require(ord >= 0 && ord < values.valueCount)
        currentOrd = ord.toInt().toLong()
        scratch.copyBytes(values.lookupOrd(currentOrd))
    }

    @Throws(IOException::class)
    override fun next(): BytesRef? {
        currentOrd++
        if (currentOrd >= values.valueCount) {
            return null
        }
        scratch.copyBytes(values.lookupOrd(currentOrd))
        return scratch.get()
    }

    @Throws(IOException::class)
    override fun term(): BytesRef {
        return scratch.get()
    }

    @Throws(IOException::class)
    override fun ord(): Long {
        return currentOrd
    }

    override fun docFreq(): Int {
        throw UnsupportedOperationException()
    }

    override fun totalTermFreq(): Long {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun postings(reuse: PostingsEnum, flags: Int): PostingsEnum {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun impacts(flags: Int): ImpactsEnum {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun seekExact(term: BytesRef, state: TermState) {
        require(state != null && state is OrdTermState)
        this.seekExact((state as OrdTermState).ord)
    }

    @Throws(IOException::class)
    override fun termState(): TermState {
        val state = OrdTermState()
        state.ord = currentOrd
        return state
    }
}
