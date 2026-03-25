package org.gnit.lucenekmp.jdkport

expect fun conditionAwaitBlocking(condition: Condition, time: Long, unit: TimeUnit): Boolean

expect fun conditionSignalAllBlocking(condition: Condition)
