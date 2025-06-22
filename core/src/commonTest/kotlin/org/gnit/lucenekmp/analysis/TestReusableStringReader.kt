package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.jdkport.CharBuffer
import kotlin.test.Test
import kotlin.test.assertEquals

class TestReusableStringReader : LuceneTestCase() {

    @Test
    fun testBasic() {
        val reader = ReusableStringReader()
        assertEquals(-1, reader.read())
        assertEquals(-1, reader.read(CharArray(1), 0, 1))
        assertEquals(-1, reader.read(CharArray(2), 1, 1))
        assertEquals(-1, reader.read(CharBuffer.wrap(CharArray(2))))

        reader.setValue("foobar")
        val buf = CharArray(4)
        assertEquals(4, reader.read(buf, 0, buf.size))
        assertEquals("foob", buf.concatToString())
        assertEquals(2, reader.read(buf, 0, buf.size))
        assertEquals("ar", buf.concatToString(0, 2))
        assertEquals(-1, reader.read(buf, 0, buf.size))
        reader.close()

        reader.setValue("foobar")
        assertEquals(0, reader.read(buf, 1, 0))
        assertEquals(3, reader.read(buf, 1, 3))
        assertEquals("foo", buf.concatToString(1, 4))
        assertEquals(2, reader.read(CharBuffer.wrap(buf, 2, 2)))
        assertEquals("ba", buf.concatToString(2, 4))
        assertEquals('r'.code, reader.read())
        assertEquals(-1, reader.read(buf, 0, buf.size))
        reader.close()

        reader.setValue("foobar")
        val sb = StringBuilder()
        var ch: Int
        while (reader.read().also { ch = it } != -1) {
            sb.append(ch.toChar())
        }
        reader.close()
        assertEquals("foobar", sb.toString())
    }
}

