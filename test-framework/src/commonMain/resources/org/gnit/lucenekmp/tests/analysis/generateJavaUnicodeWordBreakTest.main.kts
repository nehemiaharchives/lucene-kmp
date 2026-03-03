#!/usr/bin/env -S kotlin -J--enable-native-access=ALL-UNNAMED -J--sun-misc-unsafe-memory-access=allow

import java.io.File
import java.net.URL

fun usageAndExit(scriptName: String, badVersion: String? = null): Nothing {
    System.err.println("Usage: $scriptName -v <version>")
    if (!badVersion.isNullOrEmpty()) {
        System.err.println("\tversion must be of the form X.Y, e.g. 5.2")
    }
    kotlin.system.exitProcess(1)
}

fun parseVersion(args: Array<String>, scriptName: String): String {
    var version: String? = null
    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "-v", "--version" -> {
                if (i + 1 >= args.size) usageAndExit(scriptName)
                version = args[i + 1]
                i += 2
            }
            else -> {
                if (arg.startsWith("--version=")) {
                    version = arg.substringAfter('=')
                    i += 1
                } else {
                    usageAndExit(scriptName)
                }
            }
        }
    }

    val resolved = version ?: usageAndExit(scriptName)
    if (!Regex("\\d+\\.\\d+").matches(resolved)) {
        usageAndExit(scriptName, resolved)
    }
    return resolved
}

fun aboveBmpCharToSurrogates(hexCodePoint: String): List<String> {
    val ch = hexCodePoint.toInt(16)
    val highSurrogate = 0xD800 + ((ch - 0x10000) shr 10)
    val lowSurrogate = 0xDC00 + (ch and 0x3FF)
    return listOf("%04X".format(highSurrogate), "%04X".format(lowSurrogate))
}

fun getUrlContent(url: String): String {
    System.err.print("Retrieving '$url'...")
    return try {
        val content = URL(url).readText()
        System.err.println("done.")
        content
    } catch (e: Exception) {
        System.err.println("Failed to download '$url':\n\t${e.message}")
        kotlin.system.exitProcess(1)
    }
}

fun parseUnicodeDataFile(url: String, target: MutableSet<Int>, wantedPropertyValues: Set<String>) {
    val content = getUrlContent(url)
    System.err.print("Parsing '$url'...")

    for (rawLine in content.split(Regex("\\r?\\n"))) {
        var line = rawLine.replace(Regex("\\s*#.*"), "").trimEnd()
        if (line.isBlank()) continue

        val mSingle = Regex("^([0-9A-F]{4,6})\\s*;\\s*(.+)$", RegexOption.IGNORE_CASE).find(line)
        val mRange = Regex("^([0-9A-F]{4,6})\\.\\.([0-9A-F]{4,6})\\s*;\\s*(.+)$", RegexOption.IGNORE_CASE).find(line)

        val start: Int
        val end: Int
        val propertyValue: String

        if (mSingle != null) {
            start = mSingle.groupValues[1].toInt(16)
            end = start
            propertyValue = mSingle.groupValues[2].trim().lowercase()
        } else if (mRange != null) {
            start = mRange.groupValues[1].toInt(16)
            end = mRange.groupValues[2].toInt(16)
            propertyValue = mRange.groupValues[3].trim().lowercase()
        } else {
            continue
        }

        if (wantedPropertyValues.contains(propertyValue)) {
            for (cp in start..end) {
                target.add(cp)
            }
        }
    }

    System.err.println("done.")
}

fun toEscapedUnicode(hexChars: List<String>): String {
    return hexChars.joinToString("") { hex ->
        if (hex == "0022") "\\\"" else "\\u$hex"
    }
}

val scriptName = File(System.getProperty("kotlin.script.file") ?: "generateJavaUnicodeWordBreakTest.main.kts").name
val version = parseVersion(args, scriptName)

val urlPrefix = "http://www.unicode.org/Public/${version}.0/ucd"
val scriptsUrl = "$urlPrefix/Scripts.txt"
val lineBreakUrl = "$urlPrefix/LineBreak.txt"
val wordBreakUrl = "$urlPrefix/auxiliary/WordBreakProperty.txt"
val wordBreakTestUrl = "$urlPrefix/auxiliary/WordBreakTest.txt"
val emojiPrefix = "http://www.unicode.org/Public/emoji/$version"
val emojiUrl = "$emojiPrefix/emoji-data.txt"

val underscoreVersion = "${version}.0".replace('.', '_')
val className = "WordBreakTestUnicode_$underscoreVersion"
val outputFilename = "$className.kt"

val header = """
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

package org.gnit.lucenekmp.tests.analysis.standard

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase

/**
 * This class was automatically generated by $scriptName
 * from: $urlPrefix/auxiliary/WordBreakTest.txt
 *
 * WordBreakTest.txt indicates the points in the provided character sequences
 * at which conforming implementations must and must not break words.  This
 * class tests for expected token extraction from each of the test sequences
 * in WordBreakTest.txt, where the expected tokens are those character
 * sequences bounded by word breaks and containing at least one character
 * from one of the following character sets:
 *
 *    \\p{Script = Han}                (From $scriptsUrl)
 *    \\p{Script = Hiragana}
 *    \\p{LineBreak = Complex_Context} (From $lineBreakUrl)
 *    \\p{WordBreak = ALetter}         (From $wordBreakUrl)
 *    \\p{WordBreak = Hebrew_Letter}
 *    \\p{WordBreak = Katakana}
 *    \\p{WordBreak = Numeric}
 *    \\p{Extended_Pictographic}       (From $emojiUrl)
 */
object $className {

    @Throws(Exception::class)
    fun test(analyzer: Analyzer) {
""".trimIndent()

val codepoints = mutableSetOf<Int>()
val regionalIndicatorCodepoints = mutableSetOf<Int>()

parseUnicodeDataFile(lineBreakUrl, codepoints, setOf("sa"))
parseUnicodeDataFile(scriptsUrl, codepoints, setOf("han", "hiragana"))
parseUnicodeDataFile(wordBreakUrl, codepoints, setOf("aletter", "hebrew_letter", "katakana", "numeric"))
parseUnicodeDataFile(wordBreakUrl, regionalIndicatorCodepoints, setOf("regional_indicator"))
parseUnicodeDataFile(emojiUrl, codepoints, setOf("extended_pictographic"))

val tests = getUrlContent(wordBreakTestUrl).split(Regex("\\r?\\n"))

val scriptDir = File(System.getProperty("kotlin.script.file") ?: ".").absoluteFile.parentFile
val outputDir = scriptDir.resolve("../../../../../../kotlin/org/gnit/lucenekmp/tests/analysis/standard").canonicalFile
val outputPath = outputDir.resolve(outputFilename)

System.err.print("Writing '${outputPath.absolutePath}'...")

outputPath.bufferedWriter().use { out ->
    out.write(header)

    for (rawLine in tests) {
        if (rawLine.isBlank() || rawLine.trimStart().startsWith("#")) {
            continue
        }

        val sequence = rawLine.substringBefore('#').trimEnd()
        val lineNoTabs = rawLine.replace("\t", "  ")
        out.write("        // $lineNoTabs\n")

        var trimmedSequence = sequence.replace(Regex("\\s*÷\\s*$"), "")

        var testString = trimmedSequence
            .replace(Regex("\\s*÷\\s*"), "\\\\u")
            .replace(Regex("\\s*×\\s*"), "\\\\u")

        testString = Regex("\\\\u([0-9A-F]{5,})").replace(testString) { m ->
            aboveBmpCharToSurrogates(m.groupValues[1]).joinToString("") { "\\u$it" }
        }
        testString = testString
            .replace("\\u000A", "\\n")
            .replace("\\u000D", "\\r")
            .replace("\\u0022", "\\\"")

        trimmedSequence = trimmedSequence.replace(Regex("^\\s*÷\\s*"), "")

        if (Regex("^0061\\s*×\\s*200D\\s*×\\s*1F6D1$").matches(trimmedSequence)) {
            out.write("        // Skipping this test because it conflicts with TR#51 v11.0 rules.\n\n")
            continue
        }
        if (Regex("^0061\\s*×\\s*200D\\s*×\\s*2701$").matches(trimmedSequence)) {
            out.write("        // Skipping this test because it conflicts with TR#51 v11.0 rules.\n\n")
            continue
        }

        val tokens = mutableListOf<String>()

        for (candidate in trimmedSequence.split(Regex("\\s*÷\\s*"))) {
            val hexMatches = Regex("([0-9A-F]+)", RegexOption.IGNORE_CASE).findAll(candidate)
            val chars = mutableListOf<String>()
            var hasWantedChars = false
            var prevCharRegionalIndicator = false

            for (match in hexMatches) {
                val hexChar = match.groupValues[1].uppercase()
                if (hexChar.length == 4) {
                    chars.add(hexChar)
                } else {
                    chars.addAll(aboveBmpCharToSurrogates(hexChar))
                }

                if (!hasWantedChars) {
                    val codepoint = hexChar.toInt(16)
                    when {
                        codepoints.contains(codepoint) -> hasWantedChars = true
                        regionalIndicatorCodepoints.contains(codepoint) -> {
                            if (prevCharRegionalIndicator) {
                                hasWantedChars = true
                            } else {
                                prevCharRegionalIndicator = true
                            }
                        }
                    }
                }
            }

            if (hasWantedChars) {
                tokens.add("\"${toEscapedUnicode(chars)}\"")
            }
        }

        out.write("        BaseTokenStreamTestCase.assertAnalyzesTo(analyzer, \"$testString\",\n")
        out.write("            arrayOf(${tokens.joinToString(", ")}))\n\n")
    }

    out.write("    }\n}\n")
}

System.err.println("done.")
