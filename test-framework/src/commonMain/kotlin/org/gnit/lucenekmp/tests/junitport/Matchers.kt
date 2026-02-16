package org.gnit.lucenekmp.tests.junitport

import org.gnit.lucenekmp.jdkport.Ported

@Ported(from = "Matcher")
object Matchers {

    /**
     * Creates a matcher that matches if the examined object matches **ANY** of the specified matchers.
     * For example:
     * <pre>assertThat("myValue", anyOf(startsWith("foo"), containsString("Val")))</pre>
     */
    fun <T> anyOf(first: Matcher<in T>, second: Matcher<in T>): AnyOf<T> {
        return AnyOf.anyOf<T>(first, second)
    }

    /**
     * Creates a matcher that matches if the examined [String] contains the specified
     * [String] anywhere.
     * For example:
     * <pre>assertThat("myStringOfNote", containsString("ring"))</pre>
     *
     * @param substring
     * the substring that the returned matcher will expect to find within any examined string
     */
    fun containsString(substring: String): Matcher<String> {
        return object : BaseMatcher<String>() {
            override fun matches(actual: Any): Boolean {
                return actual is String && actual.contains(substring)
            }

            override fun describeTo(description: Description) {
                description.appendText("a string containing ").appendValue(substring)
            }
        }
    }


    /**
     * Creates a matcher that matches when the examined object is logically equal to the specified
     * `operand`, as determined by calling the [java.lang.Object.equals] method on
     * the **examined** object.
     *
     *
     * If the specified operand is `null` then the created matcher will only match if
     * the examined object's `equals` method returns `true` when passed a
     * `null` (which would be a violation of the `equals` contract), unless the
     * examined object itself is `null`, in which case the matcher will return a positive
     * match.
     *
     *
     * The created matcher provides a special behaviour when examining `Array`s, whereby
     * it will match if both the operand and the examined object are arrays of the same length and
     * contain items that are equal to each other (according to the above rules) **in the same
     * indexes**.
     * For example:
     * <pre>
     * assertThat("foo", equalTo("foo"));
     * assertThat(new String[] {"foo", "bar"}, equalTo(new String[] {"foo", "bar"}));
    </pre> *
     */
    fun <T> equalTo(operand: T): Matcher<T> {
        return object : BaseMatcher<T>() {
            override fun matches(actual: Any): Boolean {
                return actual == operand
            }

            override fun describeTo(description: Description) {
                if (operand == null) {
                    description.appendText("null")
                } else {
                    description.appendValue(operand)
                }
            }
        }
    }

}
