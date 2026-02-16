package org.gnit.lucenekmp.tests.junitport

import org.gnit.lucenekmp.jdkport.Ported

@Ported(from = "org.hamcrest.core.ShortcutCombination")
abstract class ShortcutCombination<T>(matchers: Iterable<Matcher<in T>>) : BaseMatcher<T>() {
    private val matchers: Iterable<Matcher<in T>>

    init {
        this.matchers = matchers
    }

    abstract override fun matches(o: Any): Boolean

    abstract override fun describeTo(description: Description)

    protected fun matches(o: Any, shortcut: Boolean): Boolean {
        for (matcher in matchers) {
            if (matcher.matches(o) == shortcut) {
                return shortcut
            }
        }
        return !shortcut
    }

    fun describeTo(description: Description, operator: String) {
        description.appendList("(", " $operator ", ")", matchers)
    }
}
