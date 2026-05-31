/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.analysis.compound.hyphenation

import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import org.gnit.lucenekmp.jdkport.InputSource
import org.gnit.lucenekmp.jdkport.InputStream

/** A SAX document handler to read and parse hyphenation patterns from a XML file. */
class PatternParser {
    private var consumer: PatternConsumer? = null

    var token: StringBuilder = StringBuilder()
    var currElement: Int = 0
    var exception: ArrayList<Any> = ArrayList()
    var hyphenChar: Char = '-'

    constructor(consumer: PatternConsumer) {
        this.consumer = consumer
    }

    /**
     * Parses a hyphenation pattern file.
     *
     * @param filename the filename
     */
    @Throws(IOException::class)
    fun parse(filename: String) {
        parse(InputSource(filename))
    }

    /**
     * Parses a hyphenation pattern file.
     *
     * @param source the InputSource for the file
     */
    @Throws(IOException::class)
    fun parse(source: InputSource) {
        parseXml(readSource(source))
    }

    private fun parseXml(xml: String) {
        val withoutComments = xml.replace(Regex("(?s)<!--.*?-->"), " ")
        Regex("<hyphen-char\\s+[^>]*value\\s*=\\s*\"([^\"]*)\"[^>]*/?>")
            .find(withoutComments)?.groupValues?.get(1)?.firstOrNull()?.let { hyphenChar = it }

        parseClasses(extract(withoutComments, "classes"))
        parseExceptions(extract(withoutComments, "exceptions"))
        parsePatterns(extract(withoutComments, "patterns"))
    }

    private fun parseClasses(text: String?) {
        if (text == null) return
        val tk = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (word in tk) {
            consumer!!.addClass(word)
        }
    }

    private fun parseExceptions(text: String?) {
        if (text == null) return
        val tk = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (word in tk) {
            val ex = normalizeException(word)
            consumer!!.addException(getExceptionWord(ex), ex)
        }
    }

    private fun parsePatterns(text: String?) {
        if (text == null) return
        val tk = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (word in tk) {
            consumer!!.addPattern(getPattern(word), getInterletterValues(word))
        }
    }

    protected fun getPattern(word: String): String {
        val pat = StringBuilder()
        for (i in 0..<word.length) {
            val c = word[i]
            if (!c.isDigit()) {
                pat.append(c)
            }
        }
        return pat.toString()
    }

    protected fun getInterletterValues(pat: String): String {
        val il = StringBuilder()
        var word = pat
        if (!word[0].isDigit()) {
            word = "0$word"
        }
        for (i in 0..<word.length) {
            val c = word[i]
            if (c.isDigit()) {
                il.append(c)
                val next = i + 1
                if (next < word.length && word[next].isDigit()) {
                    il.append('0')
                }
            }
        }
        if (!word[word.length - 1].isDigit()) {
            il.append('0')
        }
        return il.toString()
    }

    protected fun normalizeException(ex: String): ArrayList<Any> {
        val res = ArrayList<Any>()
        val sb = StringBuilder()
        for (i in 0..<ex.length) {
            val c = ex[i]
            if (c == hyphenChar) {
                if (sb.isNotEmpty()) {
                    res.add(sb.toString())
                    sb.setLength(0)
                }
                res.add(Hyphen("-"))
            } else {
                sb.append(c)
            }
        }
        if (sb.isNotEmpty()) {
            res.add(sb.toString())
        }
        return res
    }

    protected fun getExceptionWord(ex: ArrayList<Any>): String {
        val res = StringBuilder()
        for (item in ex) {
            if (item is String) {
                res.append(item)
            } else if (item is Hyphen && item.noBreak != null) {
                res.append(item.noBreak)
            }
        }
        return res.toString()
    }

    private fun extract(xml: String, tag: String): String? {
        return Regex("(?s)<$tag(?:\\s+[^>]*)?>(.*?)</$tag>").find(xml)?.groupValues?.get(1)
    }

    @Throws(IOException::class)
    private fun readSource(source: InputSource): String {
        val stream = source.byteStream
        if (stream != null) {
            return readAll(stream)
        }
        val systemId = source.systemId ?: throw IOException("InputSource has no byte stream or systemId")
        val path = systemId.toPath()
        val bufferedSource = FileSystem.SYSTEM.source(path).buffer()
        return try {
            bufferedSource.readUtf8()
        } finally {
            bufferedSource.close()
        }
    }

    @Throws(IOException::class)
    private fun readAll(stream: InputStream): String {
        val bytes = ArrayList<Byte>()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count == -1) break
            for (i in 0..<count) {
                bytes.add(buffer[i])
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    companion object {
        private const val ELEM_CLASSES = 1
        private const val ELEM_EXCEPTIONS = 2
        private const val ELEM_PATTERNS = 3
        private const val ELEM_HYPHEN = 4
    }
}
