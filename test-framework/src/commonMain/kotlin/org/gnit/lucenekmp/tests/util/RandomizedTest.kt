package org.gnit.lucenekmp.tests.util

import dev.scottpierce.envvar.EnvVar
import kotlin.random.Random


/** *
 * ported from package com.carrotsearch.randomizedtesting.RandomizedTest
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
            //return com.carrotsearch.randomizedtesting.RandomizedTest.getContext().getRandom()
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
