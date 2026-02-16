package org.gnit.lucenekmp.tests.junitport

import org.gnit.lucenekmp.jdkport.Ported
import org.gnit.lucenekmp.jdkport.System

@Ported(from = "org.hamcrest.MatcherAssert")
object MatcherAssert {
    fun <T> assertThat(actual: T, matcher: Matcher<in T>) {
        assertThat<T>("", actual, matcher)
    }

    fun <T> assertThat(reason: String, actual: T, matcher: Matcher<in T>) {
        if (!matcher.matches(actual as Any)) {
            val description: Description = StringDescription()
            description.appendText(reason)
                .appendText(System.lineSeparator())
                .appendText("Expected: ")
                .appendDescriptionOf(matcher)
                .appendText(System.lineSeparator())
                .appendText("     but: ")
            matcher.describeMismatch(actual, description)

            throw AssertionError(description.toString())
        }
    }

    fun assertThat(reason: String, assertion: Boolean) {
        if (!assertion) {
            throw AssertionError(reason)
        }
    }
}
