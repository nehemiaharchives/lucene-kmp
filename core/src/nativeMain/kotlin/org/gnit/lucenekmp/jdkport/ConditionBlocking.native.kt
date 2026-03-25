package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.runBlocking

internal interface BlockingCondition : Condition {
    fun awaitBlocking(time: Long, unit: TimeUnit): Boolean

    fun signalAllBlocking()
}

actual fun conditionAwaitBlocking(condition: Condition, time: Long, unit: TimeUnit): Boolean =
    (condition as? BlockingCondition)?.awaitBlocking(time, unit) ?: runBlocking { condition.await(time, unit) }

actual fun conditionSignalAllBlocking(condition: Condition) {
    (condition as? BlockingCondition)?.signalAllBlocking() ?: runBlocking { condition.signalAll() }
}
