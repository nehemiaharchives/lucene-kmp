package org.gnit.lucenekmp.jdkport

import kotlin.native.concurrent.Worker

actual fun currentThreadId(): Long = Thread.currentThreadOrNull()?.threadId ?: Worker.current.id.toLong()
