package org.gnit.lucenekmp.analysis.ja.dict


import okio.Path
import org.gnit.lucenekmp.analysis.ja.dict.TokenInfoDictionary.Companion.FST_FILENAME_SUFFIX
import org.gnit.lucenekmp.analysis.morph.BinaryDictionary.Companion.DICT_FILENAME_SUFFIX
import org.gnit.lucenekmp.analysis.morph.BinaryDictionary.Companion.POSDICT_FILENAME_SUFFIX
import org.gnit.lucenekmp.analysis.morph.BinaryDictionary.Companion.TARGETMAP_FILENAME_SUFFIX
import org.gnit.lucenekmp.jdkport.ClassLoader
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestExternalDictionary : LuceneTestCase() {
    private var dir: Path? = null
    private val loader: ClassLoader = /*javaClass.getClassLoader()*/ TODO("replace loader with in line text val")

    @BeforeTest
    @Throws(Exception::class)
    /*override*/ fun setUp() {
        //super.setUp()
        dir = /*LuceneTestCase.createTempDir("systemDict")*/ TODO()
        Files.newBufferedWriter(
            dir!!.resolve("unk.def"),
            StandardCharsets.UTF_8
        ).use { writer ->
            writer.write("DEFAULT,5,5,4769,記号,一般,*,*,*,*,*")
            writer.newLine()
            writer.write("SPACE,9,9,8903,記号,空白,*,*,*,*,*")
            writer.newLine()
        }
        Files.newBufferedWriter(
            dir!!.resolve("char.def"),
            StandardCharsets.UTF_8
        ).use { writer ->
            writer.write("0x0021..0x002F SYMBOL")
            writer.newLine()
            writer.write("0x0030..0x0039 NUMERIC")
            writer.newLine()
        }
        Files.newBufferedWriter(
            dir!!.resolve("matrix.def"),
            StandardCharsets.UTF_8
        ).use { writer ->
            writer.write("3 3")
            writer.newLine()
            writer.write("0 1 1")
            writer.newLine()
            writer.write("0 2 -1630")
            writer.newLine()
        }
        Files.newBufferedWriter(
            dir!!.resolve("noun.csv"),
            StandardCharsets.UTF_8
        ).use { writer ->
            writer.write("白昼夢,1285,1285,5622,名詞,一般,*,*,*,*,白昼夢,ハクチュウム,ハクチューム")
            writer.newLine()
            writer.write("デバッギング,1285,1285,3657,名詞,一般,*,*,*,*,デバッギング,デバッギング,デバッギング")
            writer.newLine()
        }
        DictionaryBuilder.build(
            DictionaryBuilder.DictionaryFormat.IPADIC,
            dir!!,
            dir!!,
            "utf-8",
            true
        )
    }

    @Test
    @Throws(Exception::class)
    fun testLoadExternalTokenInfoDictionary() {
        val dictionaryPath =
            TokenInfoDictionary::class.simpleName.toString()
                .replace('.', '/')
        val dict: TokenInfoDictionary =
            TokenInfoDictionary(
                dir!!.resolve(dictionaryPath + TARGETMAP_FILENAME_SUFFIX),
                dir!!.resolve(dictionaryPath + POSDICT_FILENAME_SUFFIX),
                dir!!.resolve(dictionaryPath + DICT_FILENAME_SUFFIX),
                dir!!.resolve(dictionaryPath + FST_FILENAME_SUFFIX)
            )
        assertNotNull(dict.getFST())
    }

    @Test
    @Throws(Exception::class)
    fun testLoadExternalUnknownDictionary() {
        val dictionaryPath =
            UnknownDictionary::class.simpleName.toString()
                .replace('.', '/')
        val dict: UnknownDictionary =
            UnknownDictionary(
                dir!!.resolve(dictionaryPath + TARGETMAP_FILENAME_SUFFIX),
                dir!!.resolve(dictionaryPath + POSDICT_FILENAME_SUFFIX),
                dir!!.resolve(dictionaryPath + DICT_FILENAME_SUFFIX)
            )
        assertNotNull(dict.getCharacterDefinition())
    }

    @Test
    @Throws(Exception::class)
    fun testLoadExternalConnectionCosts() {
        val dictionaryPath =
            ConnectionCosts::class.simpleName.toString()
                .replace('.', '/')
        val cc: ConnectionCosts =
            ConnectionCosts(dir!!.resolve(dictionaryPath + ConnectionCosts.FILENAME_SUFFIX))
        assertEquals(1, cc.get(0, 1).toLong())
    }

    @Test
    @Throws(Exception::class)
    fun testLoadExternalUrlTokenInfoDictionary() {
        val dictionaryPath =
            TokenInfoDictionary::class.simpleName.toString()
                .replace('.', '/')
        val dict: TokenInfoDictionary = TODO()
            /*TokenInfoDictionary(
                loader.getResource(dictionaryPath + TARGETMAP_FILENAME_SUFFIX),
                loader.getResource(dictionaryPath + POSDICT_FILENAME_SUFFIX),
                loader.getResource(dictionaryPath + DICT_FILENAME_SUFFIX),
                loader.getResource(dictionaryPath + FST_FILENAME_SUFFIX)
            )*/
        assertNotNull(dict.getFST())
    }

    @Test
    @Throws(Exception::class)
    fun testLoadExternalUrlUnknownDictionary() {
        val dictionaryPath =
            UnknownDictionary::class.simpleName.toString()
                .replace('.', '/')
        val dict: UnknownDictionary = TODO()
            /*UnknownDictionary(
                loader.getResource(dictionaryPath + TARGETMAP_FILENAME_SUFFIX),
                loader.getResource(dictionaryPath + POSDICT_FILENAME_SUFFIX),
                loader.getResource(dictionaryPath + DICT_FILENAME_SUFFIX)
            )*/
        assertNotNull(dict.getCharacterDefinition())
    }

    @Test
    @Throws(Exception::class)
    fun testLoadExternalUrlConnectionCosts() {
        val dictionaryPath =
            ConnectionCosts::class.simpleName.toString()
                .replace('.', '/')
        val cc: ConnectionCosts = TODO()
            /*ConnectionCosts(loader.getResource(dictionaryPath + ConnectionCosts.FILENAME_SUFFIX))*/
        assertEquals(1, cc.get(0, 1).toLong())
    }
}
