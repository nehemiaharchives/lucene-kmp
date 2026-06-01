package org.gnit.lucenekmp.analysis.synonym

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.jdkport.BufferedReader
import org.gnit.lucenekmp.jdkport.LineNumberReader
import org.gnit.lucenekmp.jdkport.ParseException
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.util.CharsRefBuilder

/**
 * Parser for the Solr synonyms format.
 *
 * <ol>
 *   <li>Blank lines and lines starting with '#' are comments.
 *   <li>Explicit mappings match any token sequence on the LHS of "=&gt;" and replace with all
 *       alternatives on the RHS. These types of mappings ignore the expand parameter in the
 *       constructor.
 *   <li>Equivalent synonyms may be separated with commas and give no explicit mapping. In this case
 *       the mapping behavior will be taken from the expand parameter in the constructor.
 *   <li>Multiple synonym mapping entries are merged.
 * </ol>
 *
 * @lucene.experimental
 */
class SolrSynonymParser(
    dedup: Boolean,
    private val expand: Boolean,
    analyzer: Analyzer
) : SynonymMap.Parser(dedup, analyzer) {
    @Throws(IOException::class)
    override fun parse(input: Reader) {
        val br = LineNumberReader(input)
        try {
            addInternal(br)
        } catch (e: IllegalArgumentException) {
            val ex = ParseException("Invalid synonym rule at line ${br.lineNumber}", 0)
            ex.initCause(e)
            throw ex
        } finally {
            br.close()
        }
    }

    @Throws(IOException::class)
    private fun addInternal(input: BufferedReader) {
        var line: String?
        while (input.readLine().also { line = it } != null) {
            val lineValue = line!!
            if (lineValue.isEmpty() || lineValue[0] == '#') {
                continue
            }

            val sides = split(lineValue, "=>")
            if (sides.size > 1) {
                require(sides.size == 2) { "more than one explicit mapping specified on the same line" }
                val inputStrings = split(sides[0], ",")
                val inputs = Array(inputStrings.size) { idx ->
                    analyze(unescape(inputStrings[idx]).trim(), CharsRefBuilder())
                }

                val outputStrings = split(sides[1], ",")
                val outputs = Array(outputStrings.size) { idx ->
                    analyze(unescape(outputStrings[idx]).trim(), CharsRefBuilder())
                }

                for (i in inputs.indices) {
                    for (j in outputs.indices) {
                        add(inputs[i], outputs[j], false)
                    }
                }
            } else {
                val inputStrings = split(lineValue, ",")
                val inputs = Array(inputStrings.size) { idx ->
                    analyze(unescape(inputStrings[idx]).trim(), CharsRefBuilder())
                }
                if (expand) {
                    for (i in inputs.indices) {
                        for (j in inputs.indices) {
                            if (i != j) {
                                add(inputs[i], inputs[j], true)
                            }
                        }
                    }
                } else {
                    for (i in inputs.indices) {
                        add(inputs[i], inputs[0], false)
                    }
                }
            }
        }
    }

    private fun split(s: String, separator: String): List<String> {
        val list = mutableListOf<String>()
        var sb = StringBuilder()
        var pos = 0
        val end = s.length
        while (pos < end) {
            if (s.startsWith(separator, pos)) {
                if (sb.isNotEmpty()) {
                    list.add(sb.toString())
                    sb = StringBuilder()
                }
                pos += separator.length
                continue
            }

            var ch = s[pos++]
            if (ch == '\\') {
                sb.append(ch)
                if (pos >= end) break
                ch = s[pos++]
            }
            sb.append(ch)
        }

        if (sb.isNotEmpty()) {
            list.add(sb.toString())
        }
        return list
    }

    private fun unescape(s: String): String {
        if (s.indexOf('\\') >= 0) {
            val sb = StringBuilder()
            var i = 0
            while (i < s.length) {
                val ch = s[i]
                if (ch == '\\' && i < s.length - 1) {
                    sb.append(s[++i])
                } else {
                    sb.append(ch)
                }
                i++
            }
            return sb.toString()
        }
        return s
    }
}
