package org.gnit.lucenekmp.tests.util

import dev.scottpierce.envvar.EnvVar
import kotlin.random.Random


/** *
 * ported from package RandomizedTest
 */
open class RandomizedTest {
    companion object {

        // ↓ line 83 of RandomizedTest.java
        /**
         * Shortcut for [RandomizedContext.getRandom]. Even though this method
         * is static, it returns per-thread [Random] instance, so no race conditions
         * can occur.
         *
         *
         * It is recommended that specific methods are used to pick random values.
         */
        fun random(): Random {
            //return RandomizedTest.getContext().getRandom()
            return Random
        }

        //
        // Random value pickers. Shortcuts to methods in {@link #getRandom()} mostly.
        //
        fun randomBoolean(): Boolean {
            return random().nextBoolean()
        }

        fun randomByte(): Byte {
            return random().nextInt().toByte()
        }

        fun randomShort(): Short {
            return random().nextInt().toShort()
        }

        fun randomInt(): Int {
            return random().nextInt()
        }

        fun randomFloat(): Float {
            return random().nextFloat()
        }

        fun randomDouble(): Double {
            return random().nextDouble()
        }

        fun randomLong(): Long {
            return random().nextLong()
        }

        // ↓ line 153
        //
        // Delegates to RandomNumbers.
        //
        /**
         * A random integer from 0..max (inclusive).
         */
        @Deprecated("")
        fun randomInt(max: Int): Int {
            return RandomNumbers.randomIntBetween(
                Random,
                0,
                max
            )
        }

        /**
         * A random long from 0..max (inclusive).
         */
        @Deprecated("")
        fun randomLong(max: Long): Long {
            return RandomNumbers.randomLongBetween(
                random(),
                0,
                max
            )
        }

        /**
         * A random integer from `min` to `max` (inclusive).
         *
         * @see .scaledRandomIntBetween
         */
        fun randomIntBetween(min: Int, max: Int): Int {
            return RandomNumbers.randomIntBetween(
                random(),
                min,
                max
            )
        }

        /**
         * An alias for [.randomIntBetween].
         *
         * @see .scaledRandomIntBetween
         */
        fun between(min: Int, max: Int): Int {
            return randomIntBetween(min, max)
        }

        /**
         * A random long from `min` to `max` (inclusive).
         */
        fun randomLongBetween(min: Long, max: Long): Long {
            return RandomNumbers.randomLongBetween(
                random(),
                min,
                max
            )
        }

        /**
         * An alias for [.randomLongBetween].
         */
        fun between(min: Long, max: Long): Long {
            return randomLongBetween(min, max)
        }
        // line 201




        //↓ line 236
        /**
         * Rarely returns `true` in about 10% of all calls (regardless of the
         * [.isNightly] mode).
         */
        fun rarely(): Boolean {
            return randomInt(100) >= 90
        }

        /**
         * The exact opposite of [.rarely].
         */
        fun frequently(): Boolean {
            return !rarely()
        }
        //↑ line 245


        //↓ line
        //↑ line


        //↓ line 742
        /**
         * @param condition
         * If `false` an [AssumptionViolatedException] is
         * thrown by this method and the test case (should be) ignored (or
         * rather technically, flagged as a failure not passing a certain
         * assumption). Tests that are assumption-failures do not break
         * builds (again: typically).
         * @param message
         * Message to be included in the exception's string.
         */
        fun assumeTrue(message: String, condition: Boolean) {
            if (!condition) {
                // @see {@link Rants#RANT_2}.
                throw /*AssumptionViolated*/Exception(message)
            }
        }

        /**
         * Reverse of [.assumeTrue].
         */
        fun assumeFalse(message: String, condition: Boolean) {
            assumeTrue(message, !condition)
        }

        /**
         * Assume `t` is `null`.
         */
        fun assumeNoException(msg: String, t: Throwable?) {
            if (t != null) {
                // This does chain the exception as the cause.
                throw /*AssumptionViolated*/Exception(msg, t)
            }
        }
        //↑ line 764


        //↓ line
        //↑ line


        //↓ line 814 of RandomizedTest.java
        fun systemPropertyAsInt(propertyName: String, defaultValue: Int): Int {
            val v: String? =
                EnvVar[propertyName] /*changed from java implementation which is getProperty(propertyName)*/
            return if (v != null && v.trim { it <= ' ' }.isNotEmpty()) {
                try {
                    v.trim { it <= ' ' }.toInt()
                } catch (e: NumberFormatException) {
                    throw IllegalArgumentException(
                        "Integer value expected for property " +
                                propertyName + ": " + v, e
                    )
                }
            } else {
                defaultValue
            }
        }

        //↓ line 847 of RandomizedTest.java
        private val BOOLEANS: Map<String, Boolean> = mapOf(
            "true" to true,
            "false" to false,
            "on" to true,
            "off" to false,
            "yes" to true,
            "no" to false,
            "enabled" to true,
            "disabled" to false,
        )

        fun systemPropertyAsBoolean(propertyName: String, defaultValue: Boolean): Boolean {
            var v: String? =
                EnvVar[propertyName] /*changed from java implementation which is getProperty(propertyName)*/

            return if (v != null && v.trim { it <= ' ' }.isNotEmpty()) {
                v = v.trim { it <= ' ' }
                val result: Boolean? = BOOLEANS[v]
                result
                    ?: throw IllegalArgumentException(
                        "Boolean value expected for property " +
                                propertyName + " " +
                                "(true/false, on/off, enabled/disabled, yes/no): " + v
                    )
            } else {
                defaultValue
            }
        }
        //↑ line 878 of RandomizedTest.java

    }
}
