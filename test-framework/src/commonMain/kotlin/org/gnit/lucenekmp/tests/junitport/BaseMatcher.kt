package org.gnit.lucenekmp.tests.junitport

import org.gnit.lucenekmp.jdkport.Ported

/**
 * BaseClass for all Matcher implementations.
 *
 * @see Matcher
 */
@Ported(from = "org.hamcrest.BaseMatcher")
abstract class BaseMatcher<T> : Matcher<T> {
    /**
     * @see Matcher._dont_implement_Matcher___instead_extend_BaseMatcher_
     */
    @Deprecated("")
    override fun _dont_implement_Matcher___instead_extend_BaseMatcher_() {
        // See Matcher interface for an explanation of this method.
    }

    override fun describeMismatch(item: Any, description: Description) {
        description.appendText("was ").appendValue(item)
    }

    override fun toString(): String {
        return StringDescription.toString(this)
    }

    companion object {
        /**
         * Useful null-check method. Writes a mismatch description if the actual object is null
         * @param actual the object to check
         * @param mismatch where to write the mismatch description, if any
         * @return false iff the actual object is null
         */
        protected fun isNotNull(actual: Any, mismatch: Description): Boolean {
            if (actual == null) {
                mismatch.appendText("was null")
                return false
            }
            return true
        }
    }
}
