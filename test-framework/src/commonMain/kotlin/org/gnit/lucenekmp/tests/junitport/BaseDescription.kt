package org.gnit.lucenekmp.tests.junitport

import org.gnit.lucenekmp.jdkport.Ported
import org.gnit.lucenekmp.jdkport.toHexString

/**
 * A [Description] that is stored as a string.
 */
@Ported(from = "org.hamcrest.BaseDescription")
abstract class BaseDescription : Description {
    override fun appendText(text: String): Description {
        append(text)
        return this
    }

    override fun appendDescriptionOf(value: SelfDescribing): Description {
        value.describeTo(this)
        return this
    }

    override fun appendValue(value: Any?): Description {
        if (value == null) {
            append("null")
        } else if (value is String) {
            toJavaSyntax(value)
        } else if (value is Char) {
            append('"')
            toJavaSyntax(value)
            append('"')
        } else if (value is Byte) {
            append('<')
            append(descriptionOf(value))
            append("b>")
        } else if (value is Short) {
            append('<')
            append(descriptionOf(value))
            append("s>")
        } else if (value is Long) {
            append('<')
            append(descriptionOf(value))
            append("L>")
        } else if (value is Float) {
            append('<')
            append(descriptionOf(value))
            append("F>")
        } else if (value is Array<*>) {
            appendValueList("[", ", ", "]", value.asList())
        } else {
            append('<')
            append(descriptionOf(value))
            append('>')
        }
        return this
    }

    private fun descriptionOf(value: Any?): String {
        try {
            return value.toString()
        } catch (e: Exception) {
            return value?.let { it::class.qualifiedName + "@" + Int.toHexString(it.hashCode()) } ?: "null"
        }
    }

    override fun <T> appendValueList(start: String, separator: String, end: String, vararg values: T): Description {
        return appendValueList(start, separator, end, values.asList())
    }

    override fun <T> appendValueList(start: String, separator: String, end: String, values: Iterable<T>): Description {
        return appendValueList(start, separator, end, values.iterator())
    }

    private fun <T> appendValueList(start: String, separator: String, end: String, values: Iterator<T>): Description {
        val describingValues = values.asSequence().map { value ->
            object : SelfDescribing {
                override fun describeTo(description: Description) {
                    description.appendValue(value)
                }
            }
        }.asIterable()
        return appendList(start, separator, end, describingValues)
    }

    override fun appendList(start: String, separator: String, end: String, values: Iterable<out SelfDescribing>): Description {
        return appendList(start, separator, end, values.iterator())
    }

    private fun appendList(start: String, separator: String, end: String, i: Iterator<out SelfDescribing>): Description {
        var separate = false

        append(start)
        while (i.hasNext()) {
            if (separate) append(separator)
            appendDescriptionOf(i.next())
            separate = true
        }
        append(end)

        return this
    }

    /**
     * Append the String <var>str</var> to the description.
     * The default implementation passes every character to [.append].
     * Override in subclasses to provide an efficient implementation.
     */
    protected open fun append(str: String) {
        for (i in 0..<str.length) {
            append(str.get(i))
        }
    }

    /**
     * Append the char <var>c</var> to the description.
     */
    protected abstract fun append(c: Char)

    private fun toJavaSyntax(unformatted: String) {
        append('"')
        for (i in 0..<unformatted.length) {
            toJavaSyntax(unformatted.get(i))
        }
        append('"')
    }

    private fun toJavaSyntax(ch: Char) {
        when (ch) {
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\\' -> append("\\\\")
            else -> append(ch)
        }
    }
}
