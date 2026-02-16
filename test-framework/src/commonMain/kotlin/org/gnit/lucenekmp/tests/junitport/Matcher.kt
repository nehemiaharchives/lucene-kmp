package org.gnit.lucenekmp.tests.junitport

import org.gnit.lucenekmp.jdkport.Ported

/**
 *
 *
 * A matcher over acceptable values.
 * A matcher is able to describe itself to give feedback when it fails.
 *
 *
 *
 * Matcher implementations should **NOT directly implement this interface**.
 * Instead, **extend** the [BaseMatcher] abstract class,
 * which will ensure that the Matcher API can grow to support
 * new features and remain compatible with all Matcher implementations.
 *
 *
 *
 * When using Hamcrest, there is no guarantee as to how often `matches()` or
 * `describeMismatch()` will be called, so the objects passed as
 * `actual` arguments should not change when referenced. If you're testing a
 * stream, a good practice is to collect the contents of the stream before matching.
 *
 *
 *
 * N.B. Well designed matchers should be immutable.
 *
 *
 * @see BaseMatcher
 */
@Ported(from = "org.hamcrest.SelfDescribing")
interface Matcher<T> : SelfDescribing {
    /**
     * Evaluates the matcher for argument <var>item</var>.
     *
     * This method matches against Object, instead of the generic type T. This is
     * because the caller of the Matcher does not know at runtime what the type is
     * (because of type erasure with Java generics). It is down to the implementations
     * to check the correct type.
     *
     * @param actual the object against which the matcher is evaluated.
     * @return `true` if <var>item</var> matches, otherwise `false`.
     *
     * @see BaseMatcher
     */
    fun matches(actual: Any): Boolean

    /**
     * Generate a description of why the matcher has not accepted the item.
     * The description will be part of a larger description of why a matching
     * failed, so it should be concise.
     * This method assumes that `matches(item)` is false, but
     * will not check this.
     *
     * @param actual The item that the Matcher has rejected.
     * @param mismatchDescription
     * The description to be built or appended to.
     */
    fun describeMismatch(actual: Any, mismatchDescription: Description)

    /**
     * This method simply acts a friendly reminder not to implement Matcher directly and
     * instead extend BaseMatcher. It's easy to ignore JavaDoc, but a bit harder to ignore
     * compile errors .
     *
     * @see Matcher for reasons why.
     *
     * @see BaseMatcher
     *
     */
    @Deprecated("to make")
    fun _dont_implement_Matcher___instead_extend_BaseMatcher_()
}
