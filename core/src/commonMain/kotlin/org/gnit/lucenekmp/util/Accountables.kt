package org.gnit.lucenekmp.util


/**
 * Helper methods for constructing nested resource descriptions and debugging RAM usage.
 *
 *
 * `toString(Accountable`} can be used to quickly debug the nested structure of any
 * Accountable.
 *
 *
 * The `namedAccountable` and `namedAccountables` methods return type-safe,
 * point-in-time snapshots of the provided resources.
 */
object Accountables {
    /**
     * Returns a String description of an Accountable and any nested resources. This is intended for
     * development and debugging.
     */
    fun toString(a: Accountable): String {
        val sb = StringBuilder()
        toString(sb, a, 0)
        return sb.toString()
    }

    private fun toString(dest: StringBuilder, a: Accountable, depth: Int): StringBuilder {
        for (i in 1..<depth) {
            dest.append("    ")
        }

        if (depth > 0) {
            dest.append("|-- ")
        }

        dest.append(a.toString())
        dest.append(": ")
        dest.append(RamUsageEstimator.humanReadableUnits(a.ramBytesUsed()))
        dest.appendLine()

        for (child in a.getChildResources()) {
            toString(dest, child, depth + 1)
        }

        return dest
    }

    /**
     * Augments an existing accountable with the provided description.
     *
     *
     * The resource description is constructed in this format: `description [toString()]`
     *
     *
     * This is a point-in-time type safe view: consumers will not be able to cast or manipulate the
     * resource in any way.
     */
    fun namedAccountable(description: String, `in`: Accountable): Accountable {
        return Accountables.namedAccountable(
            "$description [$`in`]", `in`.getChildResources(), `in`.ramBytesUsed()
        )
    }

    /** Returns an accountable with the provided description and bytes.  */
    fun namedAccountable(description: String, bytes: Long): Accountable {
        return namedAccountable(description, mutableListOf<Accountable>(), bytes)
    }

    /**
     * Converts a map of resources to a collection.
     *
     *
     * The resource descriptions are constructed in this format: `prefix 'key' [toString()]`
     *
     *
     * This is a point-in-time type safe view: consumers will not be able to cast or manipulate the
     * resources in any way.
     */
    fun namedAccountables(
        prefix: String, `in`: MutableMap<*, out Accountable>
    ): MutableCollection<Accountable> {
        val resources: MutableList<Accountable> = mutableListOf<Accountable>()
        for (kv in `in`.entries) {
            resources.add(namedAccountable(prefix + " '" + kv.key + "'", kv.value!!))
        }
        resources.sortWith { o1, o2 ->
            o1.toString().compareTo(o2.toString())
        }
        return resources
    }

    /**
     * Returns an accountable with the provided description, children and bytes.
     *
     *
     * The resource descriptions are constructed in this format: `description [toString()]`
     *
     *
     * This is a point-in-time type safe view: consumers will not be able to cast or manipulate the
     * resources in any way, provided that the passed in children Accountables (and all their
     * descendants) were created with one of the namedAccountable functions.
     */
    fun namedAccountable(
        description: String, children: MutableCollection<Accountable>, bytes: Long
    ): Accountable {
        return object : Accountable {
            override fun ramBytesUsed(): Long {
                return bytes
            }

            override fun getChildResources(): MutableCollection<Accountable> {
                return children
            }

            override fun toString(): String {
                return description
            }
        }
    }
}
