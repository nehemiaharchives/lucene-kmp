package org.gnit.lucenekmp.internal.vectorization

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
internal actual fun currentStackTraceHasClassMethodInternal(className: String, methodName: String): Boolean {
    val stack = Throwable().getStackTrace()
    return stack.any { frame ->
        val parsed = parseNativeStackFrame(frame) ?: return@any false
        parsed.first == className && parsed.second == methodName
    }
}

@OptIn(ExperimentalNativeApi::class)
internal actual fun currentStackTraceHasAnyMethodInternal(methodNames: Set<String>): Boolean {
    val stack = Throwable().getStackTrace()
    return stack.any { frame ->
        val parsed = parseNativeStackFrame(frame) ?: return@any false
        parsed.second in methodNames
    }
}

@OptIn(ExperimentalNativeApi::class)
internal actual fun currentStackTraceHasClassInternal(className: String): Boolean {
    val stack = Throwable().getStackTrace()
    return stack.any { frame ->
        val parsed = parseNativeStackFrame(frame) ?: return@any false
        parsed.first == className
    }
}

@OptIn(ExperimentalNativeApi::class)
internal actual fun hasValidVectorizationCallerPlatform(validCallers: Set<String>): Boolean {
    val stack = Throwable().getStackTrace()
    return stack.any { frame ->
        validCallers.any { frame.contains(it) }
    }
}

private fun parseNativeStackFrame(frame: String): Pair<String, String>? {
    val kfunPrefix = "kfun:"
    val kfunStart = frame.indexOf(kfunPrefix)
    if (kfunStart == -1) {
        return null
    }
    var signature = frame.substring(kfunStart + kfunPrefix.length)
    signature = signature.substringBefore(" + ")
    signature = signature.substringBefore(" (")
    signature = signature.removeSuffix("-trampoline")
    if (signature.endsWith("#internal")) {
        signature = signature.removeSuffix("#internal")
    } else if (signature.endsWith("#external")) {
        signature = signature.removeSuffix("#external")
    }
    val methodSeparator = signature.lastIndexOf('#')
    if (methodSeparator <= 0 || methodSeparator == signature.length - 1) {
        return null
    }
    val className = signature.substring(0, methodSeparator)
    val methodName = signature.substring(methodSeparator + 1).substringBefore('(')
    if (methodName.isEmpty()) {
        return null
    }
    return className to methodName
}
