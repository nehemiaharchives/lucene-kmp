package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.runBlocking

actual fun conditionAwaitBlocking(condition: Condition, time: Long, unit: TimeUnit): Boolean =
    runBlocking { condition.await(time, unit) }

actual fun conditionSignalAllBlocking(condition: Condition) {
    runBlocking { condition.signalAll() }
}
