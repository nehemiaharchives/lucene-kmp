package org.gnit.lucenekmp.analysis.ja.dict


import org.gnit.lucenekmp.jdkport.ByteArrayInputStream
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestExternalDictionary : LuceneTestCase() {
    private fun stream(bytes: ByteArray) = ByteArrayInputStream(bytes)

    @Test
    @Throws(Exception::class)
    fun testLoadExternalTokenInfoDictionary() {
        val dict = TokenInfoDictionary(
            { stream(JapaneseDictionaryData.tokenInfoTargetMap) },
            { stream(JapaneseDictionaryData.tokenInfoPosDict) },
            { stream(JapaneseDictionaryData.tokenInfoDict) },
            { stream(JapaneseDictionaryData.tokenInfoFst) }
        )
        assertNotNull(dict.getFST())
    }

    @Test
    @Throws(Exception::class)
    fun testLoadExternalUnknownDictionary() {
        val dict = UnknownDictionary(
            { stream(JapaneseDictionaryData.unknownTargetMap) },
            { stream(JapaneseDictionaryData.unknownPosDict) },
            { stream(JapaneseDictionaryData.unknownDict) }
        )
        assertNotNull(dict.getCharacterDefinition())
    }

    @Test
    @Throws(Exception::class)
    fun testLoadExternalConnectionCosts() {
        val cc = ConnectionCosts { stream(JapaneseDictionaryData.connectionCosts) }
        assertTrue(cc.get(0, 1) >= Int.MIN_VALUE)
    }

    @Test
    @Throws(Exception::class)
    fun testLoadExternalUrlTokenInfoDictionary() {
        val dict = TokenInfoDictionary(
            { stream(JapaneseDictionaryData.tokenInfoTargetMap) },
            { stream(JapaneseDictionaryData.tokenInfoPosDict) },
            { stream(JapaneseDictionaryData.tokenInfoDict) },
            { stream(JapaneseDictionaryData.tokenInfoFst) }
        )
        assertNotNull(dict.getFST())
    }

    @Test
    @Throws(Exception::class)
    fun testLoadExternalUrlUnknownDictionary() {
        val dict = UnknownDictionary(
            { stream(JapaneseDictionaryData.unknownTargetMap) },
            { stream(JapaneseDictionaryData.unknownPosDict) },
            { stream(JapaneseDictionaryData.unknownDict) }
        )
        assertNotNull(dict.getCharacterDefinition())
    }

    @Test
    @Throws(Exception::class)
    fun testLoadExternalUrlConnectionCosts() {
        val cc = ConnectionCosts { stream(JapaneseDictionaryData.connectionCosts) }
        assertTrue(cc.get(0, 1) >= Int.MIN_VALUE)
    }
}
