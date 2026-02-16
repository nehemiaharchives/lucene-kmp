package org.gnit.lucenekmp.tests.junitport

import org.gnit.lucenekmp.jdkport.Ported


/**
 * Calculates the logical disjunction of multiple matchers. Evaluation is shortcut, so
 * subsequent matchers are not called if an earlier matcher returns `true`.
 */
@Ported(from = "org.hamcrest.core.AnyOf")
class AnyOf<T>(matchers: Iterable<Matcher<in T>>) : ShortcutCombination<T>(matchers) {

    // TODO implement if needed
    //constructor(vararg matchers: Matcher<in T>) : this(java.util.Arrays.asList<Matcher<in T>>(*matchers))

    override fun matches(o: Any): Boolean {
        return matches(o, true)
    }

    override fun describeTo(description: Description) {
        describeTo(description, "or")
    }

    companion object {
        /**
         * Creates a matcher that matches if the examined object matches **ANY** of the specified matchers.
         * For example:
         * <pre>assertThat("myValue", anyOf(startsWith("foo"), containsString("Val")))</pre>
         */
        fun <T> anyOf(matchers: Iterable<Matcher<in T>>): AnyOf<T> {
            return AnyOf<T>(matchers)
        }

        /**
         * Creates a matcher that matches if the examined object matches **ANY** of the specified matchers.
         * For example:
         * <pre>assertThat("myValue", anyOf(startsWith("foo"), containsString("Val")))</pre>
         */

        fun <T> anyOf(vararg matchers: Matcher<in T>): AnyOf<T> {
            return Companion.anyOf(matchers.asList())
        }
    }
}
