package org.gnit.lucenekmp.analysis.ko.dict

import okio.Path
import okio.Path.Companion.toPath
import org.gnit.lucenekmp.analysis.morph.BinaryDictionary
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestExternalDictionary : LuceneTestCase() {

    private lateinit var dir: Path

    @BeforeTest
    fun setUp() {
        dir = createTempDir("systemDict")
        Files.newBufferedWriter(dir.resolve("unk.def"), StandardCharsets.UTF_8).use { writer ->
            writer.write("DEFAULT,1798,3559,3677,SY,*,*,*,*,*,*,*")
            writer.newLine()
            writer.write("SPACE,1795,3556,1065,SP,*,*,*,*,*,*,*")
            writer.newLine()
        }
        Files.newBufferedWriter(dir.resolve("char.def"), StandardCharsets.UTF_8).use { writer ->
            writer.write("0x0021..0x002F SYMBOL")
            writer.newLine()
            writer.write("0x0030..0x0039 NUMERIC")
            writer.newLine()
        }
        Files.newBufferedWriter(dir.resolve("matrix.def"), StandardCharsets.UTF_8).use { writer ->
            writer.write("3 3")
            writer.newLine()
            writer.write("1 1 0")
            writer.newLine()
            writer.write("1 2 0")
            writer.newLine()
        }
        Files.newBufferedWriter(dir.resolve("noun.csv"), StandardCharsets.UTF_8).use { writer ->
            writer.write("명사,1,1,2,NNG,*,*,*,*,*,*,*")
            writer.newLine()
            writer.write("일반,5000,5000,3,NNG,*,*,*,*,*,*,*")
            writer.newLine()
        }
        DictionaryBuilder.build(dir, dir, "utf-8", true)
    }

    @Test
    fun testLoadExternalTokenInfoDictionary() {
        val dictionaryPath = TokenInfoDictionary::class.qualifiedName!!.replace('.', '/')
        val dict = TokenInfoDictionary(
            dir.resolve(dictionaryPath + BinaryDictionary.TARGETMAP_FILENAME_SUFFIX),
            dir.resolve(dictionaryPath + BinaryDictionary.POSDICT_FILENAME_SUFFIX),
            dir.resolve(dictionaryPath + BinaryDictionary.DICT_FILENAME_SUFFIX),
            dir.resolve(dictionaryPath + TokenInfoDictionary.FST_FILENAME_SUFFIX)
        )
        assertNotNull(dict.getFST())
    }

    @Test
    fun testLoadExternalUnknownDictionary() {
        val dictionaryPath = UnknownDictionary::class.qualifiedName!!.replace('.', '/')
        val dict = UnknownDictionary(
            dir.resolve(dictionaryPath + BinaryDictionary.TARGETMAP_FILENAME_SUFFIX),
            dir.resolve(dictionaryPath + BinaryDictionary.POSDICT_FILENAME_SUFFIX),
            dir.resolve(dictionaryPath + BinaryDictionary.DICT_FILENAME_SUFFIX)
        )
        assertNotNull(dict.getCharacterDefinition())
    }

    @Test
    fun testLoadExternalConnectionCosts() {
        val dictionaryPath = ConnectionCosts::class.qualifiedName!!.replace('.', '/')
        val cc = ConnectionCosts(dir.resolve(dictionaryPath + ConnectionCosts.FILENAME_SUFFIX))
        assertEquals(0, cc.get(1, 1))
    }

    @Ignore
    @Test
    fun testLoadExternalUrlTokenInfoDictionary() {
        // URL constructors are not available in common code.
    }

    @Ignore
    @Test
    fun testLoadExternalUrlUnknownDictionary() {
        // URL constructors are not available in common code.
    }

    @Ignore
    @Test
    fun testLoadExternalUrlConnectionCosts() {
        // URL constructors are not available in common code.
    }

    private fun createTempDir(prefix: String): Path {
        val path = ("build/tmp/nori/$prefix-" + Random.nextInt()).toPath()
        Files.createDirectories(path)
        return path
    }
}
