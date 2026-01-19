package org.gnit.lucenekmp.jdkport

actual fun currentThreadId(): Long = java.lang.Thread.currentThread().id
