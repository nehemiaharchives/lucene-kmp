package org.gnit.lucenekmp.tests.util


/**
 * A [TestRule] that guarantees the execution of [.after] even if an exception has been
 * thrown from delegate [Statement]. This is much like [AfterClass] or [After]
 * annotations but can be used with [RuleChain] to guarantee the order of execution.
 */
abstract class AbstractBeforeAfterRule /*: org.junit.rules.TestRule*/ {

    //TODO noop for now
    /*override fun apply(
        s: org.junit.runners.model.Statement,
        d: org.junit.runner.Description?
    ): org.junit.runners.model.Statement {
        return object : org.junit.runners.model.Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                val errors: java.util.ArrayList<Throwable?> = java.util.ArrayList<Throwable?>()

                try {
                    before()
                    s.evaluate()
                } catch (t: Throwable) {
                    errors.add(t)
                }

                try {
                    after()
                } catch (t: Throwable) {
                    errors.add(t)
                }

                org.junit.runners.model.MultipleFailureException.assertEmpty(errors)
            }
        }
    }*/

    @Throws(Exception::class)
    protected open fun before() {
    }

    @Throws(Exception::class)
    protected open fun after() {
    }
}
