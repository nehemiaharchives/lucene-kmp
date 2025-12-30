package org.gnit.lucenekmp.analysis.ko.dict

import okio.Path
import okio.Path.Companion.toPath
import org.gnit.lucenekmp.analysis.ko.POS
import org.gnit.lucenekmp.analysis.morph.BinaryDictionary
import org.gnit.lucenekmp.jdkport.BufferedInputStream
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.jdkport.OutputStreamWriter
import org.gnit.lucenekmp.jdkport.PrintWriter
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.store.InputStreamDataInput
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.UnicodeUtil
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.util.fst.IntsRefFSTEnum
import org.gnit.lucenekmp.util.fst.PositiveIntOutputs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestTokenInfoDictionary : LuceneTestCase() {

    @Test
    fun testPut() {
        val dict = newDictionary(
            "명사,1,1,2,NNG,*,*,*,*,*,*,*",
            "일반,5000,5000,3,NNG,*,*,*,*,*,*,*"
        )
        val wordIdRef = IntsRefBuilder().get()

        dict.lookupWordIds(0, wordIdRef)
        var wordId = wordIdRef.ints[wordIdRef.offset]
        assertEquals(1, dict.getLeftId(wordId))
        assertEquals(1, dict.getRightId(wordId))
        assertEquals(2, dict.getWordCost(wordId))

        dict.lookupWordIds(1, wordIdRef)
        wordId = wordIdRef.ints[wordIdRef.offset]
        assertEquals(5000, dict.getLeftId(wordId))
        assertEquals(5000, dict.getRightId(wordId))
        assertEquals(3, dict.getWordCost(wordId))
    }

    private fun newDictionary(vararg entries: String): TokenInfoDictionary {
        val dir = createTempDir("tokenInfoDict")
        Files.newOutputStream(dir.resolve("test.csv")).use { out ->
            PrintWriter(OutputStreamWriter(out, StandardCharsets.UTF_8)).use { printer ->
                entries.forEach { printer.println(it) }
            }
        }
        Files.createFile(dir.resolve("unk.def"))
        Files.createFile(dir.resolve("char.def"))
        Files.newOutputStream(dir.resolve("matrix.def")).use { out ->
            PrintWriter(OutputStreamWriter(out, StandardCharsets.UTF_8)).use { printer ->
                printer.println("1 1")
            }
        }
        DictionaryBuilder.build(dir, dir, "utf-8", true)
        val dictionaryPath = TokenInfoDictionary::class.qualifiedName!!.replace('.', '/')
        return TokenInfoDictionary(
            dir.resolve(dictionaryPath + BinaryDictionary.TARGETMAP_FILENAME_SUFFIX),
            dir.resolve(dictionaryPath + BinaryDictionary.POSDICT_FILENAME_SUFFIX),
            dir.resolve(dictionaryPath + BinaryDictionary.DICT_FILENAME_SUFFIX),
            dir.resolve(dictionaryPath + TokenInfoDictionary.FST_FILENAME_SUFFIX)
        )
    }

    @Test
    fun testPutException() {
        expectThrows(IllegalArgumentException::class) {
            newDictionary("HANGUL,1,1,1,NNG,*,*,*,*,*")
        }
        expectThrows(IllegalArgumentException::class) {
            newDictionary("HANGUL,8192,8192,1,NNG,*,*,*,*,*,*,*")
        }
    }

    @Test
    fun testEnumerateAll() {
        var numTerms = 0
        var numWords = 0
        var lastWordId = -1
        var lastSourceId = -1
        val charDef = CharacterDefinition.getInstance()
        val tid = TokenInfoDictionary.getInstance()
        val matrix = ConnectionCosts.getInstance()
        val fst = tid.getFST().getInternalFST()
        val fstEnum = IntsRefFSTEnum(fst)
        var mapping: IntsRefFSTEnum.InputOutput<Long>? = fstEnum.next()
        val scratch = IntsRef()
        while (mapping != null) {
            numTerms++
            val input = mapping.input ?: break
            val chars = CharArray(input.length)
            for (i in 0 until input.length) {
                chars[i] = input.ints[input.offset + i].toChar()
            }
            val surfaceForm = chars.concatToString()
            assertFalse(surfaceForm.isEmpty())
            assertEquals(surfaceForm.trim(), surfaceForm)
            assertTrue(UnicodeUtil.validUTF16String(surfaceForm))

            val output = mapping.output ?: break
            val sourceId = output.toInt()
            assertTrue(sourceId > lastSourceId)
            lastSourceId = sourceId
            tid.lookupWordIds(sourceId, scratch)
            for (i in 0 until scratch.length) {
                numWords++
                val wordId = scratch.ints[scratch.offset + i]
                assertTrue(wordId > lastWordId)
                lastWordId = wordId

                val leftId = tid.getLeftId(wordId)
                val rightId = tid.getRightId(wordId)
                matrix.get(rightId, leftId)
                tid.getWordCost(wordId)

                val type = tid.getMorphAttributes().getPOSType(wordId)
                val leftPOS = tid.getMorphAttributes().getLeftPOS(wordId)
                val rightPOS = tid.getMorphAttributes().getRightPOS(wordId)

                if (type == POS.Type.MORPHEME) {
                    assertSame(leftPOS, rightPOS)
                    val reading = tid.getMorphAttributes().getReading(wordId)
                    val isHanja = charDef.isHanja(surfaceForm[0])
                    if (isHanja) {
                        assertNotNull(reading)
                        for (j in reading.indices) {
                            assertTrue(charDef.isHangul(reading[j]))
                        }
                    }
                    if (reading != null) {
                        assertTrue(UnicodeUtil.validUTF16String(reading))
                    }
                } else {
                    if (type == POS.Type.COMPOUND) {
                        assertSame(leftPOS, rightPOS)
                        assertTrue(leftPOS == POS.Tag.NNG || rightPOS == POS.Tag.NNP)
                    }
                    val decompound =
                        tid.getMorphAttributes().getMorphemes(wordId, chars, 0, chars.size)
                    if (decompound != null) {
                        var offset = 0
                        for (morph in decompound) {
                            assertTrue(UnicodeUtil.validUTF16String(morph.surfaceForm))
                            assertFalse(morph.surfaceForm.isEmpty())
                            assertEquals(morph.surfaceForm.trim(), morph.surfaceForm)
                            if (type != POS.Type.INFLECT) {
                                assertEquals(
                                    morph.surfaceForm,
                                    surfaceForm.substring(offset, offset + morph.surfaceForm.length)
                                )
                                offset += morph.surfaceForm.length
                            }
                        }
                        assertTrue(offset <= surfaceForm.length)
                    }
                }
            }
            mapping = fstEnum.next()
        }
        if (VERBOSE) {
            println("checked $numTerms terms, $numWords words.")
        }
    }

    @Test
    fun testBinaryFSTIsLatestFormat() {
        val stream: InputStream =
            BufferedInputStream(TokenInfoDictionary.getClassResource(TokenInfoDictionary.FST_FILENAME_SUFFIX))
        stream.use { input ->
            val actualVersion =
                FST.readMetadata(InputStreamDataInput(input), PositiveIntOutputs.singleton).version
            assertEquals(
                FST.VERSION_CURRENT,
                actualVersion,
                message = "TokenInfoDictionary's FST is not the latest version: expected "
                        + FST.VERSION_CURRENT
                        + " but got: "
                        + actualVersion
                        + "; regenerate this FST"
            )
        }
    }

    private fun createTempDir(prefix: String): Path {
        val path = ("build/tmp/nori/$prefix-" + Random.nextInt()).toPath()
        Files.createDirectories(path)
        return path
    }
}
