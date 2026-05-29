package org.gnit.lucenekmp.analysis.br

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.core.LetterTokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/**
 * Test the Brazilian Stem Filter, which only modifies the term text.
 *
 * It is very similar to the snowball portuguese algorithm but not exactly the same.
 */
class TestBrazilianAnalyzer : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testWithSnowballExamples() {
        check("boa", "boa")
        check("boainain", "boainain")
        check("boas", "boas")
        check("bôas", "boas")
        check("boassu", "boassu")
        check("boataria", "boat")
        check("boate", "boat")
        check("boates", "boat")
        check("boatos", "boat")
        check("bob", "bob")
        check("boba", "bob")
        check("bobagem", "bobag")
        check("bobagens", "bobagens")
        check("bobalhões", "bobalho")
        check("bobear", "bob")
        check("bobeira", "bobeir")
        check("bobinho", "bobinh")
        check("bobinhos", "bobinh")
        check("bobo", "bob")
        check("bobs", "bobs")
        check("boca", "boc")
        check("bocadas", "boc")
        check("bocadinho", "bocadinh")
        check("bocado", "boc")
        check("bocaiúva", "bocaiuv")
        check("boçal", "bocal")
        check("bocarra", "bocarr")
        check("bocas", "boc")
        check("bode", "bod")
        check("bodoque", "bodoqu")
        check("body", "body")
        check("boeing", "boeing")
        check("boem", "boem")
        check("boemia", "boem")
        check("boêmio", "boemi")
        check("bogotá", "bogot")
        check("boi", "boi")
        check("bóia", "boi")
        check("boiando", "boi")
        check("quiabo", "quiab")
        check("quicaram", "quic")
        check("quickly", "quickly")
        check("quieto", "quiet")
        check("quietos", "quiet")
        check("quilate", "quilat")
        check("quilates", "quilat")
        check("quilinhos", "quilinh")
        check("quilo", "quil")
        check("quilombo", "quilomb")
        check("quilométricas", "quilometr")
        check("quilométricos", "quilometr")
        check("quilômetro", "quilometr")
        check("quilômetros", "quilometr")
        check("quilos", "quil")
        check("quimica", "quimic")
        check("quilos", "quil")
        check("quimica", "quimic")
        check("quimicas", "quimic")
        check("quimico", "quimic")
        check("quimicos", "quimic")
        check("quimioterapia", "quimioterap")
        check("quimioterápicos", "quimioterap")
        check("quimono", "quimon")
        check("quincas", "quinc")
        check("quinhão", "quinha")
        check("quinhentos", "quinhent")
        check("quinn", "quinn")
        check("quino", "quin")
        check("quinta", "quint")
        check("quintal", "quintal")
        check("quintana", "quintan")
        check("quintanilha", "quintanilh")
        check("quintão", "quinta")
        check("quintessência", "quintessente")
        check("quintino", "quintin")
        check("quinto", "quint")
        check("quintos", "quint")
        check("quintuplicou", "quintuplic")
        check("quinze", "quinz")
        check("quinzena", "quinzen")
        check("quiosque", "quiosqu")
    }

    @Test
    @Throws(Exception::class)
    fun testNormalization() {
        check("Brasil", "brasil")
        check("Brasília", "brasil")
        check("quimio5terápicos", "quimio5terapicos")
        check("áá", "áá")
        check("ááá", "aaa")
    }

    @Test
    @Throws(Exception::class)
    fun testReusableTokenStream() {
        val a: Analyzer = BrazilianAnalyzer()
        checkReuse(a, "boa", "boa")
        checkReuse(a, "boainain", "boainain")
        checkReuse(a, "boas", "boas")
        checkReuse(a, "bôas", "boas")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testStemExclusionTable() {
        val stemExclusionSet = CharArraySet(1, false)
        stemExclusionSet.add("quintessência")
        val a = BrazilianAnalyzer(CharArraySet.EMPTY_SET, stemExclusionSet)
        checkReuse(a, "quintessência", "quintessência")
        a.close()
    }

    @Test
    @Throws(IOException::class)
    fun testWithKeywordAttribute() {
        val set = CharArraySet(1, true)
        set.add("Brasília")
        val tokenizer: Tokenizer = LetterTokenizer()
        tokenizer.setReader(StringReader("Brasília Brasilia"))
        val filter = BrazilianStemFilter(SetKeywordMarkerFilter(LowerCaseFilter(tokenizer), set))
        assertTokenStreamContents(filter, arrayOf("brasília", "brasil"))
    }

    private fun check(input: String, expected: String) {
        val a: Analyzer = BrazilianAnalyzer()
        checkOneTerm(a, input, expected)
        a.close()
    }

    private fun checkReuse(a: Analyzer, input: String, expected: String) {
        checkOneTerm(a, input, expected)
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a: Analyzer = BrazilianAnalyzer()
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, BrazilianStemFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
