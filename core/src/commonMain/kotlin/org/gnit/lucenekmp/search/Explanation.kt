package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.jdkport.Objects


/** Expert: Describes the score computation for document and query.  */
class Explanation private constructor(
    /** Indicates whether or not this Explanation models a match.  */
    val isMatch: Boolean, // whether the document matched
    value: Number, description: String, details: MutableCollection<Explanation>
) {
    /** The value assigned to this explanation node.  */
    val value: Number // the value of this node

    /** A description of this explanation node.  */
    val description: String // what it represents
    private val details: MutableList<Explanation> // sub-explanations

    /** Create a new explanation  */
    init {
        this.value = requireNotNull<Number>(value)
        this.description = requireNotNull<String>(description)
        this.details = ArrayList<Explanation>(details)
        for (detail in details) {
            requireNotNull<Explanation>(detail)
        }
    }

    private val summary: String
        get() = this.value.toString() + " = " + this.description

    /** The sub-nodes of this explanation node.  */
    fun getDetails(): Array<Explanation> {
        return details.toTypedArray<Explanation>()
    }

    /** Render an explanation as text.  */
    override fun toString(): String {
        return toString(0)
    }

    private fun toString(depth: Int): String {
        val buffer = StringBuilder()
        for (i in 0..<depth) {
            buffer.append("  ")
        }
        buffer.append(this.summary)
        buffer.append("\n")

        val details = getDetails()
        for (i in details.indices) {
            buffer.append(details[i]!!.toString(depth + 1))
        }

        return buffer.toString()
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || this::class != o::class) return false
        val that = o as Explanation
        return this.isMatch == that.isMatch && value == that.value
                && description == that.description
                && details == that.details
    }

    override fun hashCode(): Int {
        return Objects.hash(this.isMatch, value, description, details)
    }

    companion object {
        /**
         * Create a new explanation for a match.
         *
         * @param value the contribution to the score of the document
         * @param description how `value` was computed
         * @param details sub explanations that contributed to this explanation
         */
        fun match(
            value: Number, description: String, details: MutableCollection<Explanation>
        ): Explanation {
            return Explanation(true, value, description, details)
        }

        /**
         * Create a new explanation for a match.
         *
         * @param value the contribution to the score of the document
         * @param description how `value` was computed
         * @param details sub explanations that contributed to this explanation
         */
        fun match(value: Number, description: String, vararg details: Explanation): Explanation {
            return Explanation(true, value, description, mutableListOf(*details))
        }

        /** Create a new explanation for a document which does not match.  */
        fun noMatch(description: String, details: MutableCollection<Explanation>): Explanation {
            return Explanation(false, 0f, description, details)
        }

        /** Create a new explanation for a document which does not match.  */
        fun noMatch(description: String, vararg details: Explanation): Explanation {
            return Explanation(false, 0f, description, mutableListOf(*details))
        }
    }
}
