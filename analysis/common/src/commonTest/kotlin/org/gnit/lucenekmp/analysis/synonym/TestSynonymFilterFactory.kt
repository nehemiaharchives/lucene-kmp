package org.gnit.lucenekmp.analysis.synonym

import okio.IOException
import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.cjk.CJKAnalyzer
import org.gnit.lucenekmp.analysis.pattern.PatternTokenizerFactory
import org.gnit.lucenekmp.jdkport.ByteArrayInputStream
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.util.StringMockResourceLoader
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.Version
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Deprecated("Tests deprecated SynonymFilterFactory")
class TestSynonymFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    private class SynonymResourceLoader(private val resources: Map<String, String>) : ResourceLoader {
        override fun <T> findClass(cname: String, expectedType: KClass<*>): KClass<*> {
            return when (cname) {
                PatternTokenizerFactory::class.qualifiedName,
                "org.apache.lucene.analysis.pattern.PatternTokenizerFactory" -> PatternTokenizerFactory::class
                CJKAnalyzer::class.qualifiedName,
                "org.apache.lucene.analysis.cjk.CJKAnalyzer" -> CJKAnalyzer::class
                SolrSynonymParser::class.qualifiedName,
                "org.apache.lucene.analysis.synonym.SolrSynonymParser" -> SolrSynonymParser::class
                WordnetSynonymParser::class.qualifiedName,
                "org.apache.lucene.analysis.synonym.WordnetSynonymParser" -> WordnetSynonymParser::class
                else -> throw RuntimeException("Cannot load class: $cname")
            }
        }

        @Throws(IOException::class)
        override fun openResource(resource: String): InputStream {
            val text = resources[resource] ?: throw IOException("Resource not found: $resource")
            return ByteArrayInputStream(text.encodeToByteArray())
        }
    }

    private val synonymsTxt =
        """
#-----------------------------------------------------------------------
#some test synonym mappings unlikely to appear in real input text
aaa => aaaa
bbb => bbbb1 bbbb2
ccc => cccc1,cccc2
a\=>a => b\=>b
a\,a => b\,b
fooaaa,baraaa,bazaaa

# Some synonym groups specific to this example
GB,gib,gigabyte,gigabytes
MB,mib,megabyte,megabytes
Television, Televisions, TV, TVs
#notice we use "gib" instead of "GiB" so any WordDelimiterFilter coming
#after us won't split it into two words.

# Synonym mappings can be used for spelling correction too
pixima => pixma
""".trimIndent()

    private val synonymsWordnetTxt =
        """
s(100000001,1,'second',n,1,0).
s(100000001,2,'2nd',n,1,0).
s(100000001,3,'two',n,1,0).
""".trimIndent()

    private val synonyms2Txt = "蛙 => カエル\n"

    /** checks for synonyms of "GB" in synonyms.txt */
    private fun checkSolrSynonyms(factory: TokenFilterFactory) {
        val reader = StringReader("GB")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = factory.create(stream)
        assertTrue(stream is SynonymFilter)
        assertTokenStreamContents(
            stream,
            arrayOf("GB", "gib", "gigabyte", "gigabytes"),
            intArrayOf(1, 0, 0, 0)
        )
    }

    /** checks for synonyms of "second" in synonyms-wordnet.txt */
    private fun checkWordnetSynonyms(factory: TokenFilterFactory) {
        val reader = StringReader("second")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = factory.create(stream)
        assertTrue(stream is SynonymFilter)
        assertTokenStreamContents(stream, arrayOf("second", "2nd", "two"), intArrayOf(1, 0, 0))
    }

    /** test that we can parse and use the solr syn file */
    @Test
    fun testSynonyms() {
        val loader = SynonymResourceLoader(mapOf("synonyms.txt" to synonymsTxt))
        checkSolrSynonyms(tokenFilterFactory("Synonym", Version.LATEST, loader, "synonyms", "synonyms.txt"))
    }

    /** if the synonyms are completely empty, test that we still analyze correctly */
    @Test
    fun testEmptySynonyms() {
        val reader = StringReader("GB")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "Synonym",
                Version.LATEST,
                StringMockResourceLoader(""), // empty file!
                "synonyms",
                "synonyms.txt"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("GB"))
    }

    @Test
    fun testFormat() {
        val loader =
            SynonymResourceLoader(
                mapOf(
                    "synonyms.txt" to synonymsTxt,
                    "synonyms-wordnet.txt" to synonymsWordnetTxt
                )
            )
        checkSolrSynonyms(tokenFilterFactory("Synonym", Version.LATEST, loader, "synonyms", "synonyms.txt", "format", "solr"))
        checkWordnetSynonyms(
            tokenFilterFactory("Synonym", Version.LATEST, loader, "synonyms", "synonyms-wordnet.txt", "format", "wordnet")
        )
        // explicit class should work the same as the "solr" alias
        checkSolrSynonyms(
            tokenFilterFactory(
                "Synonym",
                Version.LATEST,
                loader,
                "synonyms",
                "synonyms.txt",
                "format",
                SolrSynonymParser::class.qualifiedName!!
            )
        )
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("Synonym", "synonyms", "synonyms.txt", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }

    /** Test that analyzer and tokenizerFactory is both specified */
    @Test
    fun testAnalyzer() {
        val analyzer = CJKAnalyzer::class.qualifiedName!!
        val tokenizerFactory = PatternTokenizerFactory::class.qualifiedName!!
        var factory: TokenFilterFactory?

        val loader = SynonymResourceLoader(mapOf("synonyms2.txt" to synonyms2Txt, "synonyms.txt" to synonymsTxt))

        factory =
            tokenFilterFactory("Synonym", Version.LATEST, loader, "synonyms", "synonyms2.txt", "analyzer", analyzer)
        assertNotNull(factory)

        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory(
                "Synonym",
                Version.LATEST,
                loader,
                "synonyms",
                "synonyms.txt",
                "analyzer",
                analyzer,
                "tokenizerFactory",
                tokenizerFactory
            )
        }
        assertTrue(expected.message!!.contains("Analyzer and TokenizerFactory can't be specified both"))
    }

    /** Test that we can parse TokenierFactory's arguments */
    @Test
    fun testTokenizerFactoryArguments() {
        val clazz = PatternTokenizerFactory::class.qualifiedName!!
        var factory: TokenFilterFactory?
        val loader = SynonymResourceLoader(mapOf("synonyms.txt" to synonymsTxt))

        // simple arg form
        factory =
            tokenFilterFactory(
                "Synonym",
                Version.LATEST,
                loader,
                "synonyms",
                "synonyms.txt",
                "tokenizerFactory",
                clazz,
                "pattern",
                "(.*)",
                "group",
                "0"
            )
        assertNotNull(factory)
        // prefix
        factory =
            tokenFilterFactory(
                "Synonym",
                Version.LATEST,
                loader,
                "synonyms",
                "synonyms.txt",
                "tokenizerFactory",
                clazz,
                "tokenizerFactory.pattern",
                "(.*)",
                "tokenizerFactory.group",
                "0"
            )
        assertNotNull(factory)

        // sanity check that sub-PatternTokenizerFactory fails w/o pattern
        expectThrows(Exception::class) {
            tokenFilterFactory("Synonym", Version.LATEST, loader, "synonyms", "synonyms.txt", "tokenizerFactory", clazz)
        }

        // sanity check that sub-PatternTokenizerFactory fails on unexpected
        expectThrows(Exception::class) {
            tokenFilterFactory(
                "Synonym",
                Version.LATEST,
                loader,
                "synonyms",
                "synonyms.txt",
                "tokenizerFactory",
                clazz,
                "tokenizerFactory.pattern",
                "(.*)",
                "tokenizerFactory.bogusbogusbogus",
                "bogus",
                "tokenizerFactory.group",
                "0"
            )
        }
    }
}
