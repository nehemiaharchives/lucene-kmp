package org.gnit.lucenekmp.analysis.custom

import okio.IOException
import okio.Path.Companion.toPath
import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.CharFilter
import org.gnit.lucenekmp.analysis.CharFilterFactory
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.AnalysisSPIRegistry
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.analysis.charfilter.HTMLStripCharFilterFactory
import org.gnit.lucenekmp.analysis.charfilter.MappingCharFilterFactory
import org.gnit.lucenekmp.analysis.classic.ClassicTokenizerFactory
import org.gnit.lucenekmp.analysis.core.LetterTokenizer
import org.gnit.lucenekmp.analysis.core.LowerCaseFilterFactory
import org.gnit.lucenekmp.analysis.core.StopFilterFactory
import org.gnit.lucenekmp.analysis.core.UpperCaseFilterFactory
import org.gnit.lucenekmp.analysis.core.WhitespaceTokenizerFactory
import org.gnit.lucenekmp.analysis.miscellaneous.ASCIIFoldingFilterFactory
import org.gnit.lucenekmp.analysis.reverse.ReverseStringFilterFactory
import org.gnit.lucenekmp.analysis.standard.StandardTokenizerFactory
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.util.AttributeFactory
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.SetOnce
import org.gnit.lucenekmp.util.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestCustomAnalyzer : BaseTokenStreamTestCase() {
    @Suppress("DEPRECATION")
    private val LUCENE_10_0_0: Version = Version.LUCENE_10_0_0

    init {
        AnalysisCommonFactories.ensureInitialized()
        AnalysisSPIRegistry.register(
            TokenizerFactory::class,
            WhitespaceTokenizerFactory.NAME,
            WhitespaceTokenizerFactory::class,
            ::WhitespaceTokenizerFactory
        )
        AnalysisSPIRegistry.register(
            TokenizerFactory::class,
            ClassicTokenizerFactory.NAME,
            ClassicTokenizerFactory::class,
            ::ClassicTokenizerFactory
        )
        AnalysisSPIRegistry.register(
            TokenizerFactory::class,
            StandardTokenizerFactory.NAME,
            StandardTokenizerFactory::class,
            ::StandardTokenizerFactory
        )
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            StopFilterFactory.NAME,
            StopFilterFactory::class,
            ::StopFilterFactory
        )
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            UpperCaseFilterFactory.NAME,
            UpperCaseFilterFactory::class,
            ::UpperCaseFilterFactory
        )
        AnalysisSPIRegistry.register(
            TokenizerFactory::class,
            "dummyTokenizer",
            DummyTokenizerFactory::class,
            ::DummyTokenizerFactory
        )
        AnalysisSPIRegistry.register(
            CharFilterFactory::class,
            "dummyCharFilter",
            DummyCharFilterFactory::class,
            ::DummyCharFilterFactory
        )
        AnalysisSPIRegistry.register(
            CharFilterFactory::class,
            "dummyMultiTermAwareCharFilter",
            DummyMultiTermAwareCharFilterFactory::class,
            ::DummyMultiTermAwareCharFilterFactory
        )
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            "dummyTokenFilter",
            DummyTokenFilterFactory::class,
            ::DummyTokenFilterFactory
        )
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            "dummyMultiTermAwareTokenFilter",
            DummyMultiTermAwareTokenFilterFactory::class,
            ::DummyMultiTermAwareTokenFilterFactory
        )
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            "dummyVersionAwareTokenFilter",
            DummyVersionAwareTokenFilterFactory::class,
            ::DummyVersionAwareTokenFilterFactory
        )
    }

    @Test
    fun testWhitespaceFactoryWithFolding() {
        val a =
            CustomAnalyzer.builder()
                .withTokenizer(WhitespaceTokenizerFactory::class)
                .addTokenFilter(ASCIIFoldingFilterFactory::class, "preserveOriginal", "true")
                .addTokenFilter(LowerCaseFilterFactory::class)
                .build()

        assertEquals(WhitespaceTokenizerFactory::class, a.tokenizerFactory::class)
        assertEquals(emptyList(), a.charFilterFactories)
        val tokenFilters = a.tokenFilterFactories
        assertEquals(2, tokenFilters.size)
        assertEquals(ASCIIFoldingFilterFactory::class, tokenFilters[0]::class)
        assertEquals(LowerCaseFilterFactory::class, tokenFilters[1]::class)
        assertEquals(0, a.getPositionIncrementGap("dummy"))
        assertEquals(1, a.getOffsetGap("dummy"))

        assertAnalyzesTo(
            a,
            "foo bar FOO BAR",
            arrayOf("foo", "bar", "foo", "bar"),
            intArrayOf(1, 1, 1, 1)
        )
        assertAnalyzesTo(
            a,
            "föó bär FÖÖ BAR",
            arrayOf("foo", "föó", "bar", "bär", "foo", "föö", "bar"),
            intArrayOf(1, 0, 1, 0, 1, 0, 1)
        )
        a.close()
    }

    @Test
    fun testWhitespaceWithFolding() {
        val a =
            CustomAnalyzer.builder()
                .withTokenizer("whitespace")
                .addTokenFilter("asciifolding", "preserveOriginal", "true")
                .addTokenFilter("lowercase")
                .build()

        assertEquals(WhitespaceTokenizerFactory::class, a.tokenizerFactory::class)
        assertEquals(emptyList(), a.charFilterFactories)
        val tokenFilters = a.tokenFilterFactories
        assertEquals(2, tokenFilters.size)
        assertEquals(ASCIIFoldingFilterFactory::class, tokenFilters[0]::class)
        assertEquals(LowerCaseFilterFactory::class, tokenFilters[1]::class)
        assertEquals(0, a.getPositionIncrementGap("dummy"))
        assertEquals(1, a.getOffsetGap("dummy"))

        assertAnalyzesTo(
            a,
            "foo bar FOO BAR",
            arrayOf("foo", "bar", "foo", "bar"),
            intArrayOf(1, 1, 1, 1)
        )
        assertAnalyzesTo(
            a,
            "föó bär FÖÖ BAR",
            arrayOf("foo", "föó", "bar", "bär", "foo", "föö", "bar"),
            intArrayOf(1, 0, 1, 0, 1, 0, 1)
        )
        a.close()
    }

    @Test
    fun testVersionAwareFilter() {
        val a =
            CustomAnalyzer.builder()
                .withDefaultMatchVersion(LUCENE_10_0_0)
                .withTokenizer(StandardTokenizerFactory::class)
                .addTokenFilter(DummyVersionAwareTokenFilterFactory::class)
                .build()
        assertAnalyzesTo(a, "HELLO WORLD", arrayOf("HELLO", "WORLD"))

        val b =
            CustomAnalyzer.builder()
                .withTokenizer(StandardTokenizerFactory::class)
                .addTokenFilter(DummyVersionAwareTokenFilterFactory::class)
                .build()
        assertAnalyzesTo(b, "HELLO WORLD", arrayOf("hello", "world"))
    }

    @Test
    fun testFactoryHtmlStripClassicFolding() {
        val a =
            CustomAnalyzer.builder()
                .withDefaultMatchVersion(LUCENE_10_0_0)
                .addCharFilter(HTMLStripCharFilterFactory::class)
                .withTokenizer(ClassicTokenizerFactory::class)
                .addTokenFilter(ASCIIFoldingFilterFactory::class, "preserveOriginal", "true")
                .addTokenFilter(LowerCaseFilterFactory::class)
                .withPositionIncrementGap(100)
                .withOffsetGap(1000)
                .build()

        assertEquals(ClassicTokenizerFactory::class, a.tokenizerFactory::class)
        val charFilters = a.charFilterFactories
        assertEquals(1, charFilters.size)
        assertEquals(HTMLStripCharFilterFactory::class, charFilters[0]::class)
        val tokenFilters = a.tokenFilterFactories
        assertEquals(2, tokenFilters.size)
        assertEquals(ASCIIFoldingFilterFactory::class, tokenFilters[0]::class)
        assertEquals(LowerCaseFilterFactory::class, tokenFilters[1]::class)
        assertEquals(100, a.getPositionIncrementGap("dummy"))
        assertEquals(1000, a.getOffsetGap("dummy"))

        assertAnalyzesTo(
            a,
            "<p>foo bar</p> FOO BAR",
            arrayOf("foo", "bar", "foo", "bar"),
            intArrayOf(1, 1, 1, 1)
        )
        assertAnalyzesTo(
            a,
            "<p><b>föó</b> bär     FÖÖ BAR</p>",
            arrayOf("foo", "föó", "bar", "bär", "foo", "föö", "bar"),
            intArrayOf(1, 0, 1, 0, 1, 0, 1)
        )
        a.close()
    }

    @Test
    fun testHtmlStripClassicFolding() {
        val a =
            CustomAnalyzer.builder()
                .withDefaultMatchVersion(LUCENE_10_0_0)
                .addCharFilter("htmlstrip")
                .withTokenizer("classic")
                .addTokenFilter("asciifolding", "preserveOriginal", "true")
                .addTokenFilter("lowercase")
                .withPositionIncrementGap(100)
                .withOffsetGap(1000)
                .build()

        assertEquals(ClassicTokenizerFactory::class, a.tokenizerFactory::class)
        val charFilters = a.charFilterFactories
        assertEquals(1, charFilters.size)
        assertEquals(HTMLStripCharFilterFactory::class, charFilters[0]::class)
        val tokenFilters = a.tokenFilterFactories
        assertEquals(2, tokenFilters.size)
        assertEquals(ASCIIFoldingFilterFactory::class, tokenFilters[0]::class)
        assertEquals(LowerCaseFilterFactory::class, tokenFilters[1]::class)
        assertEquals(100, a.getPositionIncrementGap("dummy"))
        assertEquals(1000, a.getOffsetGap("dummy"))

        assertAnalyzesTo(
            a,
            "<p>foo bar</p> FOO BAR",
            arrayOf("foo", "bar", "foo", "bar"),
            intArrayOf(1, 1, 1, 1)
        )
        assertAnalyzesTo(
            a,
            "<p><b>föó</b> bär     FÖÖ BAR</p>",
            arrayOf("foo", "föó", "bar", "bär", "foo", "föö", "bar"),
            intArrayOf(1, 0, 1, 0, 1, 0, 1)
        )
        a.close()
    }

    @Test
    fun testStopWordsFromClasspath() {
        val a =
            CustomAnalyzer.builder()
                .withTokenizer(WhitespaceTokenizerFactory::class)
                .addTokenFilter(
                    "stop",
                    "ignoreCase",
                    "true",
                    "words",
                    "org/gnit/lucenekmp/analysis/custom/teststop.txt",
                    "format",
                    "wordset"
                )
                .build()

        assertEquals(WhitespaceTokenizerFactory::class, a.tokenizerFactory::class)
        assertEquals(emptyList(), a.charFilterFactories)
        val tokenFilters = a.tokenFilterFactories
        assertEquals(1, tokenFilters.size)
        assertEquals(StopFilterFactory::class, tokenFilters[0]::class)
        assertEquals(0, a.getPositionIncrementGap("dummy"))
        assertEquals(1, a.getOffsetGap("dummy"))

        assertAnalyzesTo(a, "foo Foo Bar", emptyArray())
        a.close()
    }

    @Test
    fun testStopWordsFromClasspathWithMap() {
        val stopConfig1 = mutableMapOf<String, String>()
        stopConfig1["ignoreCase"] = "true"
        stopConfig1["words"] = "org/gnit/lucenekmp/analysis/custom/teststop.txt"
        stopConfig1["format"] = "wordset"

        val stopConfig2 = LinkedHashMap(stopConfig1)
        val stopConfigImmutable = UnmodifiableMutableMap(LinkedHashMap(stopConfig1))

        var a =
            CustomAnalyzer.builder()
                .withTokenizer("whitespace")
                .addTokenFilter("stop", stopConfig1)
                .build()
        assertTrue(stopConfig1.isEmpty())
        assertAnalyzesTo(a, "foo Foo Bar", emptyArray())

        a =
            CustomAnalyzer.builder()
                .withTokenizer(WhitespaceTokenizerFactory::class)
                .addTokenFilter(StopFilterFactory::class, stopConfig2)
                .build()
        assertTrue(stopConfig2.isEmpty())
        assertAnalyzesTo(a, "foo Foo Bar", emptyArray())

        expectThrows(UnsupportedOperationException::class) {
            CustomAnalyzer.builder()
                .withTokenizer("whitespace")
                .addTokenFilter("stop", stopConfigImmutable)
                .build()
        }
        a.close()
    }

    @Test
    fun testStopWordsFromFile() {
        val configDir = createTempDir("customAnalyzerConfig")
        val stopFile = configDir.resolve("teststop.txt")
        Files.newBufferedWriter(stopFile, StandardCharsets.UTF_8).use { writer ->
            writer.write("foo\nbar\n")
        }

        val a =
            CustomAnalyzer.builder(configDir)
                .withTokenizer("whitespace")
                .addTokenFilter(
                    "stop",
                    "ignoreCase",
                    "true",
                    "words",
                    stopFile.toString(),
                    "format",
                    "wordset"
                )
                .build()
        assertAnalyzesTo(a, "foo Foo Bar", emptyArray())
        a.close()
    }

    @Test
    fun testStopWordsFromFileAbsolute() {
        val configDir = createTempDir("customAnalyzerConfigAbs")
        val stopFile = configDir.resolve("teststop.txt")
        Files.newBufferedWriter(stopFile, StandardCharsets.UTF_8).use { writer ->
            writer.write("foo\nbar\n")
        }

        val a =
            CustomAnalyzer.builder(".".toPath())
                .withTokenizer("whitespace")
                .addTokenFilter(
                    "stop",
                    "ignoreCase",
                    "true",
                    "words",
                    stopFile.toString(),
                    "format",
                    "wordset"
                )
                .build()
        assertAnalyzesTo(a, "foo Foo Bar", emptyArray())
        a.close()
    }

    @Test
    fun testIncorrectOrder() {
        expectThrows(IllegalArgumentException::class) {
            CustomAnalyzer.builder()
                .addCharFilter("htmlstrip")
                .withDefaultMatchVersion(Version.LATEST)
                .withTokenizer("whitespace")
                .build()
        }
    }

    @Test
    fun testMissingSPI() {
        val expected = expectThrows(IllegalArgumentException::class) {
            CustomAnalyzer.builder().withTokenizer("foobar_nonexistent").build()
        }
        assertTrue(expected.message!!.contains("SPI"))
        assertTrue(expected.message!!.contains("does not exist"))
    }

    @Test
    fun testSetTokenizerTwice() {
        expectThrows(SetOnce.AlreadySetException::class) {
            CustomAnalyzer.builder()
                .withTokenizer("whitespace")
                .withTokenizer(StandardTokenizerFactory::class)
                .build()
        }
    }

    @Test
    fun testSetMatchVersionTwice() {
        expectThrows(SetOnce.AlreadySetException::class) {
            CustomAnalyzer.builder()
                .withDefaultMatchVersion(Version.LATEST)
                .withDefaultMatchVersion(Version.LATEST)
                .withTokenizer("standard")
                .build()
        }
    }

    @Test
    fun testSetPosIncTwice() {
        expectThrows(SetOnce.AlreadySetException::class) {
            CustomAnalyzer.builder()
                .withPositionIncrementGap(2)
                .withPositionIncrementGap(3)
                .withTokenizer("standard")
                .build()
        }
    }

    @Test
    fun testSetOfsGapTwice() {
        expectThrows(SetOnce.AlreadySetException::class) {
            CustomAnalyzer.builder()
                .withOffsetGap(2)
                .withOffsetGap(3)
                .withTokenizer("standard")
                .build()
        }
    }

    @Test
    fun testNoTokenizer() {
        expectThrows(IllegalStateException::class) {
            CustomAnalyzer.builder().build()
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun testNullTokenizer() {
        expectThrows(NullPointerException::class) {
            CustomAnalyzer.builder().withTokenizer(null as String)
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun testNullTokenizerFactory() {
        expectThrows(NullPointerException::class) {
            CustomAnalyzer.builder().withTokenizer(null as kotlin.reflect.KClass<out TokenizerFactory>)
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun testNullParamKey() {
        expectThrows(IllegalArgumentException::class) {
            val params = arrayOfNulls<String>(2)
            params[1] = "foo"
            CustomAnalyzer.builder().withTokenizer("whitespace", *(params as Array<String>)).build()
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun testNullMatchVersion() {
        expectThrows(NullPointerException::class) {
            CustomAnalyzer.builder()
                .withDefaultMatchVersion(null as Version)
                .withTokenizer("whitespace")
                .build()
        }
    }

    private class DummyCharFilter(input: Reader, private val match: Char, private val repl: Char) : CharFilter(input) {
        override fun correct(currentOff: Int): Int {
            return currentOff
        }

        @Throws(IOException::class)
        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            val read = input.read(cbuf, off, len)
            if (read <= 0) {
                return read
            }
            for (i in 0 until read) {
                if (cbuf[off + i] == match) {
                    cbuf[off + i] = repl
                }
            }
            return read
        }
    }

    open class DummyCharFilterFactory(args: MutableMap<String, String>) : CharFilterFactory(args) {
        private var match: Char = '0'
        private var repl: Char = '1'

        constructor(args: MutableMap<String, String>, match: Char, repl: Char) : this(args) {
            this.match = match
            this.repl = repl
        }

        override fun create(input: Reader): Reader {
            return DummyCharFilter(input, match, repl)
        }
    }

    class DummyMultiTermAwareCharFilterFactory(args: MutableMap<String, String>) : DummyCharFilterFactory(args) {
        override fun normalize(input: Reader): Reader {
            return create(input)
        }
    }

    class DummyTokenizerFactory(args: MutableMap<String, String>) : TokenizerFactory(args) {
        override fun create(factory: AttributeFactory): Tokenizer {
            return LetterTokenizer(factory)
        }
    }

    open class DummyTokenFilterFactory(args: MutableMap<String, String>) : TokenFilterFactory(args) {
        override fun create(input: TokenStream): TokenStream {
            return input
        }
    }

    class DummyMultiTermAwareTokenFilterFactory(args: MutableMap<String, String>) : DummyTokenFilterFactory(args) {
        override fun normalize(input: TokenStream): TokenStream {
            return ASCIIFoldingFilterFactory(mutableMapOf()).normalize(input)
        }
    }

    class DummyVersionAwareTokenFilterFactory(args: MutableMap<String, String>) : TokenFilterFactory(args) {
        override fun create(input: TokenStream): TokenStream {
            if (luceneMatchVersion == Version.LUCENE_10_0_0) {
                return input
            }
            return LowerCaseFilter(input)
        }
    }

    @Test
    fun testNormalization() {
        val analyzer1 =
            CustomAnalyzer.builder()
                .withTokenizer(DummyTokenizerFactory::class, mutableMapOf())
                .addCharFilter(DummyCharFilterFactory::class, mutableMapOf())
                .addTokenFilter(DummyTokenFilterFactory::class, mutableMapOf())
                .build()
        assertEquals(BytesRef("0À"), analyzer1.normalize("dummy", "0À"))

        val analyzer2 =
            CustomAnalyzer.builder()
                .withTokenizer(DummyTokenizerFactory::class, mutableMapOf())
                .addCharFilter(DummyMultiTermAwareCharFilterFactory::class, mutableMapOf())
                .addTokenFilter(DummyMultiTermAwareTokenFilterFactory::class, mutableMapOf())
                .build()
        assertEquals(BytesRef("1A"), analyzer2.normalize("dummy", "0À"))
    }

    @Test
    fun testNormalizationWithMultipleTokenFilters() {
        val analyzer =
            CustomAnalyzer.builder()
                .withTokenizer(WhitespaceTokenizerFactory::class, mutableMapOf())
                .addTokenFilter(LowerCaseFilterFactory::class, mutableMapOf())
                .addTokenFilter(ASCIIFoldingFilterFactory::class, mutableMapOf())
                .build()
        assertEquals(BytesRef("a b e"), analyzer.normalize("dummy", "À B é"))
    }

    @Test
    fun testNormalizationWithMultiplCharFilters() {
        val analyzer =
            CustomAnalyzer.builder()
                .withTokenizer(WhitespaceTokenizerFactory::class, mutableMapOf())
                .addCharFilter(
                    MappingCharFilterFactory::class,
                    mutableMapOf("mapping" to "org/gnit/lucenekmp/analysis/custom/mapping1.txt")
                )
                .addCharFilter(
                    MappingCharFilterFactory::class,
                    mutableMapOf("mapping" to "org/gnit/lucenekmp/analysis/custom/mapping2.txt")
                )
                .build()
        assertEquals(BytesRef("e f c"), analyzer.normalize("dummy", "a b c"))
    }

    @Test
    fun testConditions() {
        val analyzer =
            CustomAnalyzer.builder()
                .withTokenizer("whitespace")
                .addTokenFilter("lowercase")
                .whenTerm { t -> t.toString().contains("o") }
                .addTokenFilter("uppercase")
                .addTokenFilter(ReverseStringFilterFactory::class)
                .endwhen()
                .addTokenFilter("asciifolding")
                .build()

        assertAnalyzesTo(
            analyzer,
            "Héllo world whaT's hãppening",
            arrayOf("OLLEH", "DLROW", "what's", "happening")
        )
    }

    @Test
    fun testConditionsWithResourceLoader() {
        val analyzer =
            CustomAnalyzer.builder()
                .withTokenizer("whitespace")
                .addTokenFilter("lowercase")
                .`when`("protectedterm", "protected", "org/gnit/lucenekmp/analysis/custom/teststop.txt")
                .addTokenFilter("reversestring")
                .endwhen()
                .build()

        assertAnalyzesTo(analyzer, "FOO BAR BAZ", arrayOf("foo", "bar", "zab"))
    }

    @Test
    fun testConditionsWithWrappedResourceLoader() {
        val analyzer =
            CustomAnalyzer.builder()
                .withTokenizer("whitespace")
                .addTokenFilter("lowercase")
                .whenTerm { t -> !t.toString().contains("o") }
                .addTokenFilter(
                    "stop",
                    "ignoreCase",
                    "true",
                    "words",
                    "org/gnit/lucenekmp/analysis/custom/teststop.txt",
                    "format",
                    "wordset"
                )
                .endwhen()
                .build()

        assertAnalyzesTo(analyzer, "foo bar baz", arrayOf("foo", "baz"))
    }

    private class UnmodifiableMutableMap<K, V>(private val delegate: Map<K, V>) : MutableMap<K, V> {
        override val size: Int
            get() = delegate.size
        override fun containsKey(key: K): Boolean = delegate.containsKey(key)
        override fun containsValue(value: V): Boolean = delegate.containsValue(value)
        override fun get(key: K): V? = delegate[key]
        override fun isEmpty(): Boolean = delegate.isEmpty()
        override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
            get() = throw UnsupportedOperationException()
        override val keys: MutableSet<K>
            get() = throw UnsupportedOperationException()
        override val values: MutableCollection<V>
            get() = throw UnsupportedOperationException()
        override fun clear() = throw UnsupportedOperationException()
        override fun put(key: K, value: V): V? = throw UnsupportedOperationException()
        override fun putAll(from: Map<out K, V>) = throw UnsupportedOperationException()
        override fun remove(key: K): V? = throw UnsupportedOperationException()
    }
}
