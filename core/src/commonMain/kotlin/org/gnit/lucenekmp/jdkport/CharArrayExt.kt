package org.gnit.lucenekmp.jdkport

fun CharArray.toCodeString() = this.joinToString(
    separator = ",",
    prefix = "{",
    postfix = "}"
) { it.code.toString() }
