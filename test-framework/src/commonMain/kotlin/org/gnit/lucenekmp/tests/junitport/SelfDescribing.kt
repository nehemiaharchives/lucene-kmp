package org.gnit.lucenekmp.tests.junitport

import org.gnit.lucenekmp.jdkport.Ported

/**
 * The ability of an object to describe itself.
 */
@Ported(from = "org.hamcrest.SelfDescribing")
interface SelfDescribing {
    /**
     * Generates a description of the object.  The description may be part of a
     * a description of a larger object of which this is just a component, so it
     * should be worded appropriately.
     *
     * @param description
     * The description to be built or appended to.
     */
    fun describeTo(description: Description)
}
