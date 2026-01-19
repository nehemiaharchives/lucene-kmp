package org.gnit.lucenekmp.jdkport

import kotlin.native.concurrent.Worker

actual fun currentThreadId(): Long = Worker.current.id.toLong()
