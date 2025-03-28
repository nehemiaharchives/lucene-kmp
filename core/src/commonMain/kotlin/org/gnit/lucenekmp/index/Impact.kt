package org.gnit.lucenekmp.index

/** Per-document scoring factors.  */
class Impact
/** Constructor.  */(
    /** Term frequency of the term in the document.  */
    var freq: Int,
    /** Norm factor of the document.  */
    var norm: Long
) {
    override fun toString(): String {
        return "{freq=$freq,norm=$norm}"
    }

    override fun hashCode(): Int {
        var h = freq
        h = 31 * h + norm.hashCode()
        return h
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null || this::class != obj::class) return false
        val other = obj as Impact
        return freq == other.freq && norm == other.norm
    }
}
