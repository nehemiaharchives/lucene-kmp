package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

class TestCapitalizationFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    @Test
    fun testCapitalization() {
        val reader = StringReader("kiTTEN")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("Capitalization", "keep", "and the it BIG", "onlyFirstWord", "true").create(stream)
        assertTokenStreamContents(stream, arrayOf("Kitten"))
    }

    @Test
    fun testCapitalization2() {
        val reader = StringReader("and")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "Capitalization",
                "keep",
                "and the it BIG",
                "onlyFirstWord",
                "true",
                "forceFirstLetter",
                "true"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("And"))
    }

    /** first is forced, but it's not a keep word, either */
    @Test
    fun testCapitalization3() {
        val reader = StringReader("AnD")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "Capitalization",
                "keep",
                "and the it BIG",
                "onlyFirstWord",
                "true",
                "forceFirstLetter",
                "true"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("And"))
    }

    @Test
    fun testCapitalization4() {
        val reader = StringReader("AnD")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "Capitalization",
                "keep",
                "and the it BIG",
                "onlyFirstWord",
                "true",
                "forceFirstLetter",
                "false"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("And"))
    }

    @Test
    fun testCapitalization5() {
        val reader = StringReader("big")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "Capitalization",
                "keep",
                "and the it BIG",
                "onlyFirstWord",
                "true",
                "forceFirstLetter",
                "true"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("Big"))
    }

    @Test
    fun testCapitalization6() {
        val reader = StringReader("BIG")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "Capitalization",
                "keep",
                "and the it BIG",
                "onlyFirstWord",
                "true",
                "forceFirstLetter",
                "true"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("BIG"))
    }

    @Test
    fun testCapitalization7() {
        val reader = StringReader("Hello thEre my Name is Ryan")
        var stream: TokenStream = keywordMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "Capitalization",
                "keep",
                "and the it BIG",
                "onlyFirstWord",
                "true",
                "forceFirstLetter",
                "true"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("Hello there my name is ryan"))
    }

    @Test
    fun testCapitalization8() {
        val reader = StringReader("Hello thEre my Name is Ryan")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "Capitalization",
                "keep",
                "and the it BIG",
                "onlyFirstWord",
                "false",
                "forceFirstLetter",
                "true"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("Hello", "There", "My", "Name", "Is", "Ryan"))
    }

    @Test
    fun testCapitalization9() {
        val reader = StringReader("Hello thEre my Name is Ryan")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "Capitalization",
                "keep",
                "and the it BIG",
                "onlyFirstWord",
                "false",
                "minWordLength",
                "3",
                "forceFirstLetter",
                "true"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("Hello", "There", "my", "Name", "is", "Ryan"))
    }

    @Test
    fun testCapitalization10() {
        val reader = StringReader("McKinley")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "Capitalization",
                "keep",
                "and the it BIG",
                "onlyFirstWord",
                "false",
                "minWordLength",
                "3",
                "forceFirstLetter",
                "true"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("Mckinley"))
    }

    /** using "McK" as okPrefix */
    @Test
    fun testCapitalization11() {
        val reader = StringReader("McKinley")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "Capitalization",
                "keep",
                "and the it BIG",
                "onlyFirstWord",
                "false",
                "minWordLength",
                "3",
                "okPrefix",
                "McK",
                "forceFirstLetter",
                "true"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("McKinley"))
    }

    /** test with numbers */
    @Test
    fun testCapitalization12() {
        val reader = StringReader("1st 2nd third")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "Capitalization",
                "keep",
                "and the it BIG",
                "onlyFirstWord",
                "false",
                "minWordLength",
                "3",
                "okPrefix",
                "McK",
                "forceFirstLetter",
                "false"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("1st", "2nd", "Third"))
    }

    @Test
    fun testCapitalization13() {
        val reader = StringReader("the The the")
        var stream: TokenStream = keywordMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "Capitalization",
                "keep",
                "and the it BIG",
                "onlyFirstWord",
                "false",
                "minWordLength",
                "3",
                "okPrefix",
                "McK",
                "forceFirstLetter",
                "true"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("The The the"))
    }

    @Test
    fun testKeepIgnoreCase() {
        val reader = StringReader("kiTTEN")
        var stream: TokenStream = keywordMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "Capitalization",
                "keep",
                "kitten",
                "keepIgnoreCase",
                "true",
                "onlyFirstWord",
                "true",
                "forceFirstLetter",
                "true"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("KiTTEN"))
    }

    @Test
    fun testKeepIgnoreCase2() {
        val reader = StringReader("kiTTEN")
        var stream: TokenStream = keywordMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "Capitalization",
                "keep",
                "kitten",
                "keepIgnoreCase",
                "true",
                "onlyFirstWord",
                "true",
                "forceFirstLetter",
                "false"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("kiTTEN"))
    }

    @Test
    fun testKeepIgnoreCase3() {
        val reader = StringReader("kiTTEN")
        var stream: TokenStream = keywordMockTokenizer(reader)
        stream =
            tokenFilterFactory(
                "Capitalization",
                "keepIgnoreCase",
                "true",
                "onlyFirstWord",
                "true",
                "forceFirstLetter",
                "false"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf("Kitten"))
    }

    /**
     * Test CapitalizationFilterFactory's minWordLength option.
     *
     * This is very weird when combined with ONLY_FIRST_WORD!!!
     */
    @Test
    fun testMinWordLength() {
        val reader = StringReader("helo testing")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("Capitalization", "onlyFirstWord", "true", "minWordLength", "5").create(stream)
        assertTokenStreamContents(stream, arrayOf("helo", "Testing"))
    }

    /**
     * Test CapitalizationFilterFactory's maxWordCount option with only words of 1 in each token (it
     * should do nothing)
     */
    @Test
    fun testMaxWordCount() {
        val reader = StringReader("one two three four")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("Capitalization", "maxWordCount", "2").create(stream)
        assertTokenStreamContents(stream, arrayOf("One", "Two", "Three", "Four"))
    }

    /** Test CapitalizationFilterFactory's maxWordCount option when exceeded */
    @Test
    fun testMaxWordCount2() {
        val reader = StringReader("one two three four")
        var stream: TokenStream = keywordMockTokenizer(reader)
        stream = tokenFilterFactory("Capitalization", "maxWordCount", "2").create(stream)
        assertTokenStreamContents(stream, arrayOf("one two three four"))
    }

    /**
     * Test CapitalizationFilterFactory's maxTokenLength option when exceeded
     *
     * This is weird, it is not really a max, but inclusive (look at 'is')
     */
    @Test
    fun testMaxTokenLength() {
        val reader = StringReader("this is a test")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("Capitalization", "maxTokenLength", "2").create(stream)
        assertTokenStreamContents(stream, arrayOf("this", "is", "A", "test"))
    }

    /** Test CapitalizationFilterFactory's forceFirstLetter option */
    @Test
    fun testForceFirstLetterWithKeep() {
        val reader = StringReader("kitten")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("Capitalization", "keep", "kitten", "forceFirstLetter", "true").create(stream)
        assertTokenStreamContents(stream, arrayOf("Kitten"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("Capitalization", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }

    /** Test that invalid arguments result in exception */
    @Test
    fun testInvalidArguments() {
        for (arg in arrayOf("minWordLength", "maxTokenLength", "maxWordCount")) {
            val expected = expectThrows(IllegalArgumentException::class) {
                val reader = StringReader("foo foobar super-duper-trooper")
                val stream: TokenStream = whitespaceMockTokenizer(reader)
                tokenFilterFactory(
                    "Capitalization",
                    "keep",
                    "and the it BIG",
                    "onlyFirstWord",
                    "false",
                    arg,
                    "-3",
                    "okPrefix",
                    "McK",
                    "forceFirstLetter",
                    "true"
                ).create(stream)
            }
            assertTrue(
                expected.message!!.contains("$arg must be greater than or equal to zero") ||
                    expected.message!!.contains("$arg must be greater than zero")
            )
        }
    }
}
