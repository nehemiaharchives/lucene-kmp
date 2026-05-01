package org.gnit.lucenekmp.jdkport

import org.gnit.lucenekmp.util.configureTestLogging
import org.gnit.lucenekmp.util.getLogger
import kotlin.test.BeforeTest
import kotlin.test.Test

class LoggerTest {

    private val logger = getLogger()

    @BeforeTest
    fun setUp() {
        configureTestLogging()
    }

    @Test
    fun testLogging() {
        logger.debug { "DEBUG logging should be printed out on console" }
        logger.debug { "the logg output should start from org.gnit.lucenekmp.jdkport.LoggerTest" }
    }
}
