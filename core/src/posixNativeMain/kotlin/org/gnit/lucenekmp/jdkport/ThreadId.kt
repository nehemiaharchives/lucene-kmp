package org.gnit.lucenekmp.jdkport

import platform.posix.pthread_self

actual fun currentThreadId(): Long = Thread.currentThreadOrNull()?.threadId ?: pthread_self().hashCode().toLong()
