package org.gnit.lucenekmp.jdkport

/**
 * minimum port of java.io.PrintWriter just to make it work in lucene-kmp
*/
@Ported(from = "java.io.PrintWriter")
class PrintWriter(val out: Writer): Writer() {

    /**
     * Writes a string.  This method cannot be inherited from the Writer class
     * because it must suppress I/O exceptions.
     * @param str String to be written
     */
    override fun write(str: String) {
        write(str, 0, str.length)
    }

    override fun write(cbuf: CharArray, off: Int, len: Int) {
        out.write(cbuf, off, len)
    }

    /**
     * Prints a string.  If the argument is `null` then the string
     * `"null"` is printed.  Otherwise, the string's characters are
     * converted into bytes according to the default charset,
     * and these bytes are written in exactly the manner of the
     * [.write] method.
     *
     * @param      s   The `String` to be printed
     * @see Charset.defaultCharset
     */
    fun print(s: String) {
        write(s)
    }

    /**
     * Prints a String and then terminates the line.  This method behaves as
     * though it invokes [.print] and then
     * [.println].
     *
     * @param x the `String` value to be printed
     */
    fun println(x: String?) {
        print(x)
        println()
    }

    /* Methods that do terminate lines */
    /**
     * Terminates the current line by writing the line separator string.  The
     * line separator is [System.lineSeparator] and is not necessarily
     * a single newline character (`'\n'`).
     */
    fun println() {
        newLine()
    }

    private fun newLine() {
        out.write(/*java.lang.System.lineSeparator()*/"\n")
    }

    override fun flush() {
        out.flush()
    }

    override fun close() {
        out.close()
    }
}