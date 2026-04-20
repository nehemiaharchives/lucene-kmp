package org.gnit.lucenekmp.jdkport

import platform.windows.GetCurrentThreadId

actual fun currentThreadId(): Long = GetCurrentThreadId().toLong()
