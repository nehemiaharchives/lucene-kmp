package org.gnit.lucenekmp.analysis.ja.dict

import okio.Path
import org.gnit.lucenekmp.analysis.ja.dict.TokenInfoDictionary.Companion.FST_FILENAME_SUFFIX
import org.gnit.lucenekmp.analysis.morph.BinaryDictionary
import org.gnit.lucenekmp.analysis.morph.BinaryDictionary.Companion.DICT_FILENAME_SUFFIX
import org.gnit.lucenekmp.analysis.morph.BinaryDictionary.Companion.POSDICT_FILENAME_SUFFIX
import org.gnit.lucenekmp.analysis.morph.BinaryDictionary.Companion.TARGETMAP_FILENAME_SUFFIX
import org.gnit.lucenekmp.jdkport.BufferedInputStream
import org.gnit.lucenekmp.jdkport.Files
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/** Tests of TokenInfoDictionary build tools; run using ant test-tools  */
class TestTokenInfoDictionary : LuceneTestCase() {
    @Throws(Exception::class)
    fun testPut() {
        val dict: TokenInfoDictionary =
            newDictionary(
                "名詞,1,1,2,名詞,一般,*,*,*,*,*,*,*",  // "large" id
                "一般,5000,5000,3,名詞,一般,*,*,*,*,*,*,*"
            )
        val wordIdRef: IntsRef =
            IntsRefBuilder().get()

        dict.lookupWordIds(0, wordIdRef)
        var wordId: Int = wordIdRef.ints[wordIdRef.offset]
        assertEquals(5000, dict.getLeftId(wordId).toLong())
        assertEquals(5000, dict.getRightId(wordId).toLong())
        assertEquals(3, dict.getWordCost(wordId).toLong())

        dict.lookupWordIds(1, wordIdRef)
        wordId = wordIdRef.ints[wordIdRef.offset]
        assertEquals(1, dict.getLeftId(wordId).toLong())
        assertEquals(1, dict.getRightId(wordId).toLong())
        assertEquals(2, dict.getWordCost(wordId).toLong())
    }

    @Throws(Exception::class)
    private fun newDictionary(vararg entries: String): TokenInfoDictionary {
        val dir: Path = /*LuceneTestCase.createTempDir()*/ TODO("replace path and file acccess to inlined String val")
        Files.newOutputStream(dir.resolve("test.csv")).use { out ->
            PrintWriter(
                OutputStreamWriter(
                    out,
                    StandardCharsets.UTF_8
                )
            ).use { printer ->
                for (entry in entries) {
                    printer.println(entry)
                }
            }
        }
        Files.createFile(dir.resolve("unk.def"))
        Files.createFile(dir.resolve("char.def"))
        Files.newOutputStream(dir.resolve("matrix.def")).use { out ->
            PrintWriter(
                OutputStreamWriter(
                    out,
                    StandardCharsets.UTF_8
                )
            ).use { printer ->
                printer.println("1 1")
            }
        }
        DictionaryBuilder.build(
            DictionaryBuilder.DictionaryFormat.IPADIC,
            dir,
            dir,
            "utf-8",
            true
        )
        val dictionaryPath =
            TokenInfoDictionary::class.simpleName!!.replace('.', '/')
        // We must also load the other files (in BinaryDictionary) from the correct path
        return TokenInfoDictionary(
            dir.resolve(dictionaryPath + TARGETMAP_FILENAME_SUFFIX),
            dir.resolve(dictionaryPath + POSDICT_FILENAME_SUFFIX),
            dir.resolve(dictionaryPath + DICT_FILENAME_SUFFIX),
            dir.resolve(dictionaryPath + FST_FILENAME_SUFFIX)
        )
    }

    fun testPutException() {
        // too few columns
        LuceneTestCase.expectThrows<IllegalArgumentException>(
            IllegalArgumentException::class,
            LuceneTestCase.ThrowingRunnable { newDictionary("KANJI,1,1,1,名詞,一般,*,*,*,*,*") })
        // left id != right id
        LuceneTestCase.expectThrows<IllegalArgumentException>(
            IllegalArgumentException::class,
            LuceneTestCase.ThrowingRunnable { newDictionary("KANJI,1285,1,1,名詞,一般,*,*,*,*,*,*,*") })
        // left id != right id
        LuceneTestCase.expectThrows<IllegalArgumentException>(
            IllegalArgumentException::class,
            LuceneTestCase.ThrowingRunnable { newDictionary("KANJI,1285,1,1,名詞,一般,*,*,*,*,*,*,*") })
        // id too large
        LuceneTestCase.expectThrows<IllegalArgumentException>(
            IllegalArgumentException::class,
            LuceneTestCase.ThrowingRunnable { newDictionary("KANJI,8192,8192,1,名詞,一般,*,*,*,*,*,*,*") })
    }

    /** enumerates the entire FST/lookup data and just does basic sanity checks  */
    @Throws(Exception::class)
    fun testEnumerateAll() {
        // just for debugging
        var numTerms = 0
        var numWords = 0
        var lastWordId = -1
        var lastSourceId = -1
        val tid: TokenInfoDictionary =
            TokenInfoDictionary.getInstance()
        val matrix: ConnectionCosts =
            ConnectionCosts.getInstance()
        val fst: FST<Long> = tid.getFST().getInternalFST()
        val fstEnum: IntsRefFSTEnum<Long> =
            IntsRefFSTEnum(fst)
        var mapping: IntsRefFSTEnum.InputOutput<Long>
        val scratch = IntsRef()
        while ((fstEnum.next().also { mapping = it!! }) != null) {
            numTerms++
            val input: IntsRef = mapping.input!!
            val chars = CharArray(input.length)
            for (i in chars.indices) {
                chars[i] = input.ints[input.offset + i].toChar()
            }
            assertTrue(
                UnicodeUtil.validUTF16String(
                    chars.concatToString()
                )
            )

            val output: Long = mapping.output!!
            val sourceId = output.toInt()
            // we walk in order, terms, sourceIds, and wordIds should always be increasing
            assertTrue(sourceId > lastSourceId)
            lastSourceId = sourceId
            tid.lookupWordIds(sourceId, scratch)
            for (i in 0..<scratch.length) {
                numWords++
                val wordId: Int = scratch.ints[scratch.offset + i]
                assertTrue(wordId > lastWordId)
                lastWordId = wordId

                val baseForm: String? =
                    tid.getMorphAttributes().getBaseForm(wordId, chars, 0, chars.size)
                assertTrue(
                    baseForm == null || UnicodeUtil.validUTF16String(
                        baseForm
                    )
                )

                val inflectionForm: String? = tid.getMorphAttributes().getInflectionForm(wordId)
                assertTrue(
                    inflectionForm == null || UnicodeUtil.validUTF16String(
                        inflectionForm
                    )
                )
                if (inflectionForm != null) {
                    // check that it's actually an ipadic inflection form
                    assertNotNull(
                        ToStringUtil.getInflectedFormTranslation(
                            inflectionForm
                        )
                    )
                }

                val inflectionType: String? = tid.getMorphAttributes().getInflectionType(wordId)
                assertTrue(
                    inflectionType == null || UnicodeUtil.validUTF16String(
                        inflectionType
                    )
                )
                if (inflectionType != null) {
                    // check that it's actually an ipadic inflection type
                    assertNotNull(
                        ToStringUtil.getInflectionTypeTranslation(
                            inflectionType
                        )
                    )
                }

                val leftId: Int = tid.getLeftId(wordId)
                val rightId: Int = tid.getRightId(wordId)

                matrix.get(rightId, leftId)

                tid.getWordCost(wordId)

                val pos: String? = tid.getMorphAttributes().getPartOfSpeech(wordId)
                assertNotNull(pos)
                assertTrue(UnicodeUtil.validUTF16String(pos))
                // check that it's actually an ipadic pos tag
                assertNotNull(
                    ToStringUtil.getPOSTranslation(
                        pos
                    )
                )

                val pronunciation: String? =
                    tid.getMorphAttributes().getPronunciation(wordId, chars, 0, chars.size)
                assertNotNull(pronunciation)
                assertTrue(
                    UnicodeUtil.validUTF16String(
                        pronunciation
                    )
                )

                val reading: String? =
                    tid.getMorphAttributes().getReading(wordId, chars, 0, chars.size)
                assertNotNull(reading)
                assertTrue(
                    UnicodeUtil.validUTF16String(
                        reading
                    )
                )
            }
        }
        if (VERBOSE) {
            println("checked $numTerms terms, $numWords words.")
        }
    }

    // #12911: make sure our shipped binary FST for TokenInfoDictionary is the latest & greatest
    // format
    @Throws(Exception::class)
    fun testBinaryFSTIsLatestFormat() {
        BufferedInputStream(
            TokenInfoDictionary.getClassResource(FST_FILENAME_SUFFIX)
        ).use { `is` ->
            // we only need to load the FSTMetadata to check version:
            val actualVersion: Int =
                FST.readMetadata<Long>(
                    InputStreamDataInput(
                        `is`
                    ), PositiveIntOutputs.singleton
                ).version

            assertEquals(
                FST.VERSION_CURRENT.toLong(),
                actualVersion.toLong(),
                message = ("TokenInfoDictionary's FST is not the latest version: expected "
                        + FST.VERSION_CURRENT
                        + " but got: "
                        + actualVersion
                        + "; run \"./gradlew :lucene:analysis:kuromoji:regenerate\" to regenerate this FST")
            )
        }
    }
}
