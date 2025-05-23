package org.gnit.lucenekmp.tests.util

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.systemPropertyAsBoolean
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.systemPropertyAsInt
import org.gnit.lucenekmp.util.BytesRef
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.test.*

open class LuceneTestCase {


    //↓ line 2860 of LuceneTestCase.java
    /** A runnable that can throw any checked exception.  */
    fun interface ThrowingRunnable {
        @Throws(Throwable::class)
        fun run()
    }

    /** A function Consumer that can throw any checked exception.  */
    fun interface ThrowingConsumer<T> {
        @Throws(Exception::class)
        fun accept(t: T)
    }
    //↑ line 2870 of LuceneTestCase.java

    companion object {

        // ↓ line 277 of LuceneTestCase.java
        // --------------------------------------------------------------------
        // Test groups, system properties and other annotations modifying tests
        // --------------------------------------------------------------------
        const val SYSPROP_NIGHTLY: String = "tests.nightly"
        const val SYSPROP_WEEKLY: String = "tests.weekly"
        const val SYSPROP_MONSTER: String = "tests.monster"
        const val SYSPROP_AWAITSFIX: String = "tests.awaitsfix"

        /**
         * @see .ignoreAfterMaxFailures
         */
        const val SYSPROP_MAXFAILURES: String = "tests.maxfailures"

        /**
         * @see .ignoreAfterMaxFailures
         */
        const val SYSPROP_FAILFAST: String = "tests.failfast"

        /** Annotation for tests that should only be run during nightly builds.  */
        @MustBeDocumented
        // @java.lang.annotation.Inherited // TODO not possible with kotlin common code, need to walk around
        @Retention(AnnotationRetention.RUNTIME)
        @TestGroup(enabled = false, sysProperty = SYSPROP_NIGHTLY)
        annotation class Nightly
        // ↑ line 294 of LuceneTestCase.java


        // line 457
        /** Whether or not [Nightly] tests should run.  */
        val TEST_NIGHTLY: Boolean = systemPropertyAsBoolean(
            SYSPROP_NIGHTLY,
            /*Nightly::class.java.getAnnotation<A>(TestGroup::class.java)
                .enabled()*/ false
        )


        // line 408
        // -----------------------------------------------------------------
        // Truly immutable fields and constants, initialized once and valid
        // for all suites ever since.
        // -----------------------------------------------------------------
        /**
         * True if and only if tests are run in verbose mode. If this flag is false tests are not expected
         * to print any messages. Enforced with [TestRuleLimitSysouts].
         */
        val VERBOSE: Boolean = systemPropertyAsBoolean("tests.verbose", false)


        // line 484
        /**
         * A random multiplier which you should use when writing random tests: multiply it by the number
         * of iterations to scale your tests (for nightly builds).
         */
        val RANDOM_MULTIPLIER: Int = systemPropertyAsInt("tests.multiplier", defaultRandomMultiplier())

        /** Compute the default value of the random multiplier (based on [.TEST_NIGHTLY]).  */
        fun defaultRandomMultiplier(): Int {
            return if (TEST_NIGHTLY) 2 else 1
        }
        // line 490


        // line 618 of LuceneTestCase.java
        /** Returns random string, including full unicode range.  */
        fun randomUnicodeString(r: Random): String {
            return randomUnicodeString(r, 20)
        }

        /** Returns a random string up to a certain length.  */
        fun randomUnicodeString(r: Random, maxLength: Int): String {
            val end = TestUtil.nextInt(r, 0, maxLength)
            if (end == 0) {
                // allow 0 length
                return ""
            }
            val buffer = CharArray(end)
            randomFixedLengthUnicodeString(r, buffer, 0, buffer.size)
            return buffer.concatToString(0, 0 + end)
        }

        /** Fills provided char[] with valid random unicode code unit sequence.  */
        fun randomFixedLengthUnicodeString(
            random: Random, chars: CharArray, offset: Int, length: Int
        ) {
            var i = offset
            val end = offset + length
            while (i < end) {
                val t: Int = random.nextInt(5)
                if (0 == t && i < length - 1) {
                    // Make a surrogate pair
                    // High surrogate
                    chars[i++] = TestUtil.nextInt(random, 0xd800, 0xdbff).toChar()
                    // Low surrogate
                    chars[i++] = TestUtil.nextInt(random, 0xdc00, 0xdfff).toChar()
                } else if (t <= 1) {
                    chars[i++] = random.nextInt(0x80).toChar()
                } else if (2 == t) {
                    chars[i++] = TestUtil.nextInt(random, 0x80, 0x7ff).toChar()
                } else if (3 == t) {
                    chars[i++] = TestUtil.nextInt(random, 0x800, 0xd7ff).toChar()
                } else if (4 == t) {
                    chars[i++] = TestUtil.nextInt(random, 0xe000, 0xffff).toChar()
                }
            }
        }
        // line 657

        // line 725

        // -----------------------------------------------------------------
        // Test facilities and facades for subclasses.
        // -----------------------------------------------------------------

        /**
         * Access to the current {@link RandomizedContext}'s Random instance. It is safe to use this
         * method from multiple threads, etc., but it should be called while within a runner's scope (so
         * no static initializers). The returned {@link Random} instance will be <b>different</b> when
         * this method is called inside a {@link BeforeClass} hook (static suite scope) and within {@link
         * Before}/ {@link After} hooks or test methods.
         *
         * <p>The returned instance must not be shared with other threads or cross a single scope's
         * boundary. For example, a {@link Random} acquired within a test method shouldn't be reused for
         * another test case.
         *
         * <p>There is an overhead connected with getting the {@link Random} for a particular context and
         * thread. It is better to cache the {@link Random} locally if tight loops with multiple
         * invocations are present or create a derivative local {@link Random} for millions of calls like
         * this:
         *
         * <pre>
         * Random random = new Random(random().nextLong());
         * // tight loop with many invocations.
         * </pre>
         */
        fun random(): Random {
            return Random
        }

        // line 820 of LuceneTestCase.java
        /**
         * Returns a number of at least `i`
         *
         *
         * The actual number returned will be influenced by whether [.TEST_NIGHTLY] is active and
         * [.RANDOM_MULTIPLIER], but also with some random fudge.
         */
        fun atLeast(random: Random, i: Int): Int {
            val min: Int = i * RANDOM_MULTIPLIER
            val max = min + (min / 2)
            return TestUtil.nextInt(random, min, max)
        }

        fun atLeast(i: Int): Int {
            return atLeast(Random, i)
        }


        //↓ line 2872 of LuceneTestCase.java
        /** Checks a specific exception class is thrown by the given runnable, and returns it.  */
        fun <T : Throwable> expectThrows(
            expectedType: KClass<T>, runnable: ThrowingRunnable
        ): T? {
            return expectThrows<T>(
                expectedType,
                "Expected exception " + expectedType.simpleName + " but no exception was thrown",
                runnable
            )
        }

        /** Checks a specific exception class is thrown by the given runnable, and returns it.  */
        fun <T : Throwable> expectThrows(
            expectedType: KClass<T>,
            noExceptionMessage: String?,
            runnable: ThrowingRunnable
        ): T? {
            val thrown: Throwable? =
                _expectThrows(mutableListOf(expectedType), runnable)
            if (expectedType.isInstance(thrown)) {
                return expectedType.cast(thrown)
            }
            if (null == thrown) {
                throw AssertionError(noExceptionMessage)
            }

            fail(
                message = "Unexpected exception type, expected "
                        + expectedType.simpleName
                        + " but got: "
                        + thrown,
                cause = thrown
            )
        }

        /** Checks a specific exception class is thrown by the given runnable, and returns it.  */
        fun <T : Throwable> expectThrowsAnyOf(
            expectedTypes: MutableList<KClass<out T>>,
            runnable: ThrowingRunnable
        ): T? {
            if (expectedTypes.isEmpty()) {
                throw AssertionError("At least one expected exception type is required?")
            }

            val thrown: Throwable? = _expectThrows(expectedTypes, runnable)
            if (null != thrown) {
                for (expectedType in expectedTypes) {
                    if (expectedType.isInstance(thrown)) {
                        return expectedType.cast(thrown)
                    }
                }
            }

            val exceptionTypes =
                expectedTypes.map { obj: KClass<out T> -> obj.simpleName }

            if (thrown != null) {
                fail(
                    message = "Unexpected exception type, expected any of "
                            + exceptionTypes
                            + " but got: "
                            + thrown,
                    cause = thrown
                )
            } else {
                throw AssertionError(
                    ("Expected any of the following exception types: "
                            + exceptionTypes
                            + " but no exception was thrown.")
                )
            }
        }

        /**
         * Checks that specific wrapped and outer exception classes are thrown by the given runnable, and
         * returns the wrapped exception.
         */
        fun <TO : Throwable, TW : Throwable> expectThrows(
            expectedOuterType: KClass<TO>,
            expectedWrappedType: KClass<TW>,
            runnable: ThrowingRunnable
        ): TW? {
            val thrown: Throwable? =
                _expectThrows(mutableListOf<KClass<TO>>(expectedOuterType), runnable)
            if (null == thrown) {
                fail(
                    message = "Expected outer exception "
                            + expectedOuterType.simpleName
                            + " but no exception was thrown."
                )
            }
            if (expectedOuterType.isInstance(thrown)) {
                val cause = thrown.cause
                if (expectedWrappedType.isInstance(cause)) {
                    return expectedWrappedType.cast(cause)
                } else {
                    fail(
                        message = "Unexpected wrapped exception type, expected "
                                + expectedWrappedType.simpleName
                                + " but got: "
                                + cause,
                        cause = thrown
                    )
                }
            }

            fail(
                message = "Unexpected outer exception type, expected "
                        + expectedOuterType.simpleName
                        + " but got: "
                        + thrown,
                cause = thrown
            )
        }


        /**
         * Checks that one of the specified wrapped and outer exception classes are thrown by the given
         * runnable, and returns the outer exception.
         *
         *
         * This method accepts outer exceptions with no wrapped exception; an empty list of expected
         * wrapped exception types indicates no wrapped exception.
         */
        fun <TO : Throwable, TW : Throwable> expectThrowsAnyOf(
            expectedOuterToWrappedTypes: Map<KClass<out TO>, MutableList<KClass<out TW>>>,
            runnable: ThrowingRunnable
        ): TO? {
            val outerClasses: MutableList<KClass<out TO>> = expectedOuterToWrappedTypes.keys.toMutableList()
            val thrown: Throwable? = _expectThrows(outerClasses, runnable)

            if (null == thrown) {
                val outerTypes =
                    outerClasses.map { obj: KClass<out TO> -> obj.simpleName }
                throw AssertionError(
                    ("Expected any of the following outer exception types: "
                            + outerTypes
                            + " but no exception was thrown.")
                )
            }
            for (entry in expectedOuterToWrappedTypes.entries) {
                val expectedOuterType: KClass<out TO> = entry.key
                val expectedWrappedTypes: MutableList<KClass<out TW>> = entry.value
                val cause = thrown.cause
                if (expectedOuterType.isInstance(thrown)) {
                    if (expectedWrappedTypes.isEmpty()) {
                        return null // no wrapped exception
                    } else {
                        for (expectedWrappedType in expectedWrappedTypes) {
                            if (expectedWrappedType.isInstance(cause)) {
                                return expectedOuterType.cast(thrown)
                            }
                        }
                        val wrappedTypes =
                            expectedWrappedTypes.map { obj: KClass<out TW> -> obj.simpleName }

                        fail(
                            message = "Unexpected wrapped exception type, expected one of $wrappedTypes but got: $cause",
                            cause = thrown
                        )
                    }
                }
            }
            val outerTypes =
                outerClasses.map { obj: KClass<out TO> -> obj.simpleName }

            fail(
                message = "Unexpected outer exception type, expected one of $outerTypes but got: $thrown",
                cause = thrown
            )
        }

        /**
         * Helper method for [.expectThrows] and [.expectThrowsAnyOf] that takes care of
         * propagating any [AssertionError] or [AssumptionViolatedException] instances thrown
         * if and only if they are super classes of the `expectedTypes`. Otherwise simply
         * returns any [Throwable] thrown, regardless of type, or null if the `runnable`
         * completed w/o error.
         */
        private fun _expectThrows(
            expectedTypes: MutableList<out KClass<*>>,
            runnable: ThrowingRunnable
        ): Throwable? {
            try {
                runnable.run()
            } catch (ae: AssertionError) {
                for (expectedType in expectedTypes) {
                    if (expectedType.isInstance(ae)) { // user is expecting this type explicitly
                        return ae
                    }
                }
                throw ae
                /*} catch (ae: AssumptionViolatedException) { // TODO this exception is junit specific and incompatible with kotlin multiplatform common code. need walk around.
                    for (expectedType in expectedTypes) {
                        if (expectedType.isInstance(ae)) {
                            return ae
                        }
                    }
                    throw ae*/
            } catch (e: Throwable) {
                return e
            }
            return null
        }
        //↑ line 3056 of LuceneTestCase.java


        //TODO: implement the collate() and others of LuceneTestCase.java

        //↓ line 3202 of LuceneTestCase.java
        /**
         * Creates a [BytesRef] holding UTF-8 bytes for the incoming String, that sometimes uses a
         * non-zero `offset`, and non-zero end-padding, to tickle latent bugs that fail to look at
         * `BytesRef.offset`.
         */
        fun newBytesRef(s: String): BytesRef {
            return newBytesRef(s.encodeToByteArray())
        }

        /**
         * Creates a copy of the incoming [BytesRef] that sometimes uses a non-zero `offset`,
         * and non-zero end-padding, to tickle latent bugs that fail to look at `BytesRef.offset`.
         */
        fun newBytesRef(b: BytesRef): BytesRef {
            assertTrue(b.isValid())
            return newBytesRef(b.bytes, b.offset, b.length)
        }

        /**
         * Creates a random BytesRef from the incoming bytes that sometimes uses a non-zero `offset`, and non-zero end-padding, to tickle latent bugs that fail to look at `BytesRef.offset`.
         */
        fun newBytesRef(b: ByteArray): BytesRef {
            return newBytesRef(b, 0, b.size)
        }

        /**
         * Creates a random empty BytesRef that sometimes uses a non-zero `offset`, and non-zero
         * end-padding, to tickle latent bugs that fail to look at `BytesRef.offset`.
         */
        fun newBytesRef(): BytesRef {
            return newBytesRef(ByteArray(0), 0, 0)
        }

        /**
         * Creates a random empty BytesRef, with at least the requested length of bytes free, that
         * sometimes uses a non-zero `offset`, and non-zero end-padding, to tickle latent bugs that
         * fail to look at `BytesRef.offset`.
         */
        fun newBytesRef(byteLength: Int): BytesRef {
            return newBytesRef(ByteArray(byteLength), 0, byteLength)
        }

        /**
         * Creates a copy of the incoming bytes slice that sometimes uses a non-zero `offset`, and
         * non-zero end-padding, to tickle latent bugs that fail to look at `BytesRef.offset`.
         */
        fun newBytesRef(bytesIn: ByteArray, offset: Int, length: Int): BytesRef {
            val logger = KotlinLogging.logger {}

            logger.debug { "LTC.newBytesRef!  bytesIn.length=" + bytesIn.size + " offset=" + offset + " length=" + length }

            assertTrue(
                "got offset=" + offset + " length=" + length + " bytesIn.length=" + bytesIn.size
            ) { bytesIn.size >= offset + length }

            // randomly set a non-zero offset
            val startOffset = /*if (Random.nextBoolean()) {
                Random.nextInt(1, 20)
            } else {
                0
            }*/
                Random.nextInt(1, 20)

            // also randomly set an end padding:
            val endPadding = /*if (Random.nextBoolean()) {
                Random.nextInt(1, 20)
            } else {
                0
            }*/
                Random.nextInt(1, 20)

            val bytes = ByteArray(startOffset + length + endPadding)

            bytesIn.copyInto(bytes, startOffset, offset, offset + length)

            logger.debug { "LTC:  return bytes.length=" + bytes.size + " startOffset=" + startOffset + " length=" + length }
            val it: BytesRef = BytesRef(bytes, startOffset, length)
            assertTrue(it.isValid())

            if (Random.nextInt(1, 17) == 7) {
                // try to ferret out bugs in this method too!
                return newBytesRef(it.bytes, it.offset, it.length)
            }

            return it
        }
        //↑ line 3288 of LuceneTestCase.java

    }
}
