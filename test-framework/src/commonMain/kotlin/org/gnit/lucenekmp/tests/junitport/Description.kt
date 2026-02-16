package org.gnit.lucenekmp.tests.junitport

import org.gnit.lucenekmp.jdkport.Ported


/**
 * A description of a Matcher. A Matcher will describe itself to a description
 * which can later be used for reporting.
 *
 * @see Matcher.describeTo
 */
@Ported(from = "org.hamcrest.Description")
interface Description {
    /**
     * Appends some plain text to the description.
     */
    fun appendText(text: String): Description

    /**
     * Appends the description of a [SelfDescribing] value to this description.
     */
    fun appendDescriptionOf(value: SelfDescribing): Description

    /**
     * Appends an arbitrary value to the description.
     */
    fun appendValue(value: Any?): Description

    /**
     * Appends a list of values to the description.
     */
    fun <T> appendValueList(
        start: String, separator: String, end: String,
        vararg values: T
    ): Description

    /**
     * Appends a list of values to the description.
     */
    fun <T> appendValueList(
        start: String, separator: String, end: String,
        values: Iterable<T>
    ): Description

    /**
     * Appends a list of [SelfDescribing] objects
     * to the description.
     */
    fun appendList(
        start: String, separator: String, end: String,
        values: Iterable<out SelfDescribing>
    ): Description


    class NullDescription : Description {
        override fun appendDescriptionOf(value: SelfDescribing): Description {
            return this
        }

        override fun appendList(
            start: String, separator: String,
            end: String, values: Iterable<out SelfDescribing>
        ): Description {
            return this
        }

        override fun appendText(text: String): Description {
            return this
        }

        override fun appendValue(value: Any?): Description {
            return this
        }

        override fun <T> appendValueList(
            start: String, separator: String,
            end: String, vararg values: T
        ): Description {
            return this
        }

        override fun <T> appendValueList(
            start: String, separator: String,
            end: String, values: Iterable<T>
        ): Description {
            return this
        }

        override fun toString(): String {
            return ""
        }
    }

    companion object {
        /**
         * A description that consumes input but does nothing.
         */
        val NONE: Description = NullDescription()
    }
}
