package org.gnit.lucenekmp.tests.util

import dev.scottpierce.envvar.EnvVar


/** *
 * ported from package com.carrotsearch.randomizedtesting.RandomizedTest
 */
class RandomizedTest {
    companion object {

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
    }
}
