package org.gnit.lucenekmp.analysis.email

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.jdkport.BufferedReader
import org.gnit.lucenekmp.jdkport.InputStreamReader
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.standard.EmojiTokenizationTestUnicode_12_1
import org.gnit.lucenekmp.tests.analysis.standard.WordBreakTestUnicode_12_1_0
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ClasspathResourceLoader
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

class TestUAX29URLEmailTokenizer : BaseTokenStreamTestCase() {
    // LUCENE-5440: extremely slow tokenization of text matching email <local-part> (before the '@')
    @Ignore // passes on local dev machine but taking too long time to finish, so ignoring in CI
    @Test
    @Throws(Exception::class)
    fun testLongEMAILatomText() {
        // EMAILatomText = [A-Za-z0-9!#$%&'*+-/=?\^_`{|}~]
        val emailAtomChars =
            "!#$%&'*+,-./0123456789=?ABCDEFGHIJKLMNOPQRSTUVWXYZ^_`abcdefghijklmnopqrstuvwxyz{|}~".toCharArray()
        val builder = StringBuilder()
        val numChars = TestUtil.nextInt(random(), 100 * 1024, 3 * 1024 * 1024)
        for (i in 0..<numChars) {
            builder.append(emailAtomChars[random().nextInt(emailAtomChars.size)])
        }
        var tokenCount = 0
        val ts = UAX29URLEmailTokenizer()
        val text = builder.toString()
        ts.setReader(StringReader(text))
        ts.reset()
        while (ts.incrementToken()) {
            tokenCount++
        }
        ts.end()
        ts.close()
        assertTrue(tokenCount > 0)

        tokenCount = 0
        val newBufferSize = TestUtil.nextInt(random(), 200, 8192)
        ts.setMaxTokenLength(newBufferSize)
        ts.setReader(StringReader(text))
        ts.reset()
        while (ts.incrementToken()) {
            tokenCount++
        }
        ts.end()
        ts.close()
        assertTrue(tokenCount > 0)
    }

    @Test
    @Throws(IOException::class)
    fun testHugeDoc() {
        val sb = StringBuilder()
        val whitespace = CharArray(4094)
        whitespace.fill(' ')
        sb.append(whitespace.concatToString())
        sb.append("testing 1234")
        val input = sb.toString()
        val tokenizer = UAX29URLEmailTokenizer(newAttributeFactory())
        tokenizer.setReader(StringReader(input))
        assertTokenStreamContents(tokenizer, arrayOf("testing", "1234"))
    }

    private lateinit var a: Analyzer
    private lateinit var urlAnalyzer: Analyzer
    private lateinit var emailAnalyzer: Analyzer

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = UAX29URLEmailTokenizer(newAttributeFactory())
                    return TokenStreamComponents(tokenizer)
                }
            }
        urlAnalyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer = UAX29URLEmailTokenizer(newAttributeFactory())
                    tokenizer.setMaxTokenLength(
                        UAX29URLEmailTokenizer.MAX_TOKEN_LENGTH_LIMIT
                    ) // Tokenize arbitrary length URLs
                    val filter: TokenFilter = URLFilter(tokenizer)
                    return TokenStreamComponents(tokenizer, filter)
                }
            }
        emailAnalyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer = UAX29URLEmailTokenizer(newAttributeFactory())
                    val filter: TokenFilter = EmailFilter(tokenizer)
                    return TokenStreamComponents(tokenizer, filter)
                }
            }
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        a.close()
        urlAnalyzer.close()
        emailAnalyzer.close()
    }

    /** Passes through tokens with type "<URL>" and blocks all other types. */
    private class URLFilter(`in`: TokenStream) : TokenFilter(`in`) {
        private val typeAtt: TypeAttribute = addAttribute(TypeAttribute::class)

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            var isTokenAvailable = false
            while (input.incrementToken()) {
                if (typeAtt.type() == UAX29URLEmailTokenizer.TOKEN_TYPES[UAX29URLEmailTokenizer.URL]) {
                    isTokenAvailable = true
                    break
                }
            }
            return isTokenAvailable
        }
    }

    /** Passes through tokens with type "<EMAIL>" and blocks all other types. */
    private class EmailFilter(`in`: TokenStream) : TokenFilter(`in`) {
        private val typeAtt: TypeAttribute = addAttribute(TypeAttribute::class)

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            var isTokenAvailable = false
            while (input.incrementToken()) {
                if (typeAtt.type() == UAX29URLEmailTokenizer.TOKEN_TYPES[UAX29URLEmailTokenizer.EMAIL]) {
                    isTokenAvailable = true
                    break
                }
            }
            return isTokenAvailable
        }
    }

    @Test
    @Throws(Exception::class)
    fun testArabic() {
        assertAnalyzesTo(
            a,
            "الفيلم الوثائقي الأول عن ويكيبيديا يسمى \"الحقيقة بالأرقام: قصة ويكيبيديا\" (بالإنجليزية: Truth in Numbers: The Wikipedia Story)، سيتم إطلاقه في 2008.",
            arrayOf(
                "الفيلم", "الوثائقي", "الأول", "عن", "ويكيبيديا", "يسمى", "الحقيقة",
                "بالأرقام", "قصة", "ويكيبيديا", "بالإنجليزية", "Truth", "in", "Numbers",
                "The", "Wikipedia", "Story", "سيتم", "إطلاقه", "في", "2008"
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testArmenian() {
        assertAnalyzesTo(
            a,
            "Վիքիպեդիայի 13 միլիոն հոդվածները (4,600` հայերեն վիքիպեդիայում) գրվել են կամավորների կողմից ու համարյա բոլոր հոդվածները կարող է խմբագրել ցանկաց մարդ ով կարող է բացել Վիքիպեդիայի կայքը։",
            arrayOf(
                "Վիքիպեդիայի", "13", "միլիոն", "հոդվածները", "4,600", "հայերեն",
                "վիքիպեդիայում", "գրվել", "են", "կամավորների", "կողմից", "ու",
                "համարյա", "բոլոր", "հոդվածները", "կարող", "է", "խմբագրել", "ցանկաց",
                "մարդ", "ով", "կարող", "է", "բացել", "Վիքիպեդիայի", "կայքը"
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testAmharic() {
        assertAnalyzesTo(
            a,
            "ዊኪፔድያ የባለ ብዙ ቋንቋ የተሟላ ትክክለኛና ነጻ መዝገበ ዕውቀት (ኢንሳይክሎፒዲያ) ነው። ማንኛውም",
            arrayOf("ዊኪፔድያ", "የባለ", "ብዙ", "ቋንቋ", "የተሟላ", "ትክክለኛና", "ነጻ", "መዝገበ", "ዕውቀት", "ኢንሳይክሎፒዲያ", "ነው", "ማንኛውም")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testAramaic() {
        assertAnalyzesTo(
            a,
            "ܘܝܩܝܦܕܝܐ (ܐܢܓܠܝܐ: Wikipedia) ܗܘ ܐܝܢܣܩܠܘܦܕܝܐ ܚܐܪܬܐ ܕܐܢܛܪܢܛ ܒܠܫܢ̈ܐ ܣܓܝܐ̈ܐ܂ ܫܡܗ ܐܬܐ ܡܢ ܡ̈ܠܬܐ ܕ\"ܘܝܩܝ\" ܘ\"ܐܝܢܣܩܠܘܦܕܝܐ\"܀",
            arrayOf(
                "ܘܝܩܝܦܕܝܐ", "ܐܢܓܠܝܐ", "Wikipedia", "ܗܘ", "ܐܝܢܣܩܠܘܦܕܝܐ",
                "ܚܐܪܬܐ", "ܕܐܢܛܪܢܛ", "ܒܠܫܢ̈ܐ", "ܣܓܝܐ̈ܐ", "ܫܡܗ", "ܐܬܐ", "ܡܢ",
                "ܡ̈ܠܬܐ", "ܕ", "ܘܝܩܝ", "ܘ", "ܐܝܢܣܩܠܘܦܕܝܐ"
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testBengali() {
        assertAnalyzesTo(
            a,
            "এই বিশ্বকোষ পরিচালনা করে উইকিমিডিয়া ফাউন্ডেশন (একটি অলাভজনক সংস্থা)। উইকিপিডিয়ার শুরু ১৫ জানুয়ারি, ২০০১ সালে। এখন পর্যন্ত ২০০টিরও বেশী ভাষায় উইকিপিডিয়া রয়েছে।",
            arrayOf(
                "এই", "বিশ্বকোষ", "পরিচালনা", "করে", "উইকিমিডিয়া", "ফাউন্ডেশন",
                "একটি", "অলাভজনক", "সংস্থা", "উইকিপিডিয়ার", "শুরু", "১৫",
                "জানুয়ারি", "২০০১", "সালে", "এখন", "পর্যন্ত", "২০০টিরও", "বেশী",
                "ভাষায়", "উইকিপিডিয়া", "রয়েছে"
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testFarsi() {
        assertAnalyzesTo(
            a,
            "ویکی پدیای انگلیسی در تاریخ ۲۵ دی ۱۳۷۹ به صورت مکملی برای دانشنامهٔ تخصصی نوپدیا نوشته شد.",
            arrayOf(
                "ویکی", "پدیای", "انگلیسی", "در", "تاریخ", "۲۵", "دی", "۱۳۷۹",
                "به", "صورت", "مکملی", "برای", "دانشنامهٔ", "تخصصی", "نوپدیا", "نوشته", "شد"
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testGreek() {
        assertAnalyzesTo(
            a,
            "Γράφεται σε συνεργασία από εθελοντές με το λογισμικό wiki, κάτι που σημαίνει ότι άρθρα μπορεί να προστεθούν ή να αλλάξουν από τον καθένα.",
            arrayOf(
                "Γράφεται", "σε", "συνεργασία", "από", "εθελοντές", "με", "το",
                "λογισμικό", "wiki", "κάτι", "που", "σημαίνει", "ότι", "άρθρα",
                "μπορεί", "να", "προστεθούν", "ή", "να", "αλλάξουν", "από", "τον", "καθένα"
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testThai() {
        assertAnalyzesTo(a, "การที่ได้ต้องแสดงว่างานดี. แล้วเธอจะไปไหน? ๑๒๓๔", arrayOf("การที่ได้ต้องแสดงว่างานดี", "แล้วเธอจะไปไหน", "๑๒๓๔"))
    }

    @Test
    @Throws(Exception::class)
    fun testLao() {
        assertAnalyzesTo(a, "ສາທາລະນະລັດ ປະຊາທິປະໄຕ ປະຊາຊົນລາວ", arrayOf("ສາທາລະນະລັດ", "ປະຊາທິປະໄຕ", "ປະຊາຊົນລາວ"))
    }

    @Test
    @Throws(Exception::class)
    fun testTibetan() {
        assertAnalyzesTo(
            a,
            "སྣོན་མཛོད་དང་ལས་འདིས་བོད་ཡིག་མི་ཉམས་གོང་འཕེལ་དུ་གཏོང་བར་ཧ་ཅང་དགེ་མཚན་མཆིས་སོ། །",
            arrayOf(
                "སྣོན", "མཛོད", "དང", "ལས", "འདིས", "བོད", "ཡིག",
                "མི", "ཉམས", "གོང", "འཕེལ", "དུ", "གཏོང", "བར",
                "ཧ", "ཅང", "དགེ", "མཚན", "མཆིས", "སོ"
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testChinese() {
        assertAnalyzesTo(a, "我是中国人。 １２３４ Ｔｅｓｔｓ ", arrayOf("我", "是", "中", "国", "人", "１２３４", "Ｔｅｓｔｓ"))
    }

    @Test
    @Throws(Exception::class)
    fun testEmpty() {
        assertAnalyzesTo(a, "", emptyArray())
        assertAnalyzesTo(a, ".", emptyArray())
        assertAnalyzesTo(a, " ", emptyArray())
    }

    /* test various jira issues this analyzer is related to */

    @Test
    @Throws(Exception::class)
    fun testLUCENE1545() {
        /*
         * Standard analyzer does not correctly tokenize combining character U+0364 COMBINING LATIN SMALL LETTRE E.
         * The word "moͤchte" is incorrectly tokenized into "mo" "chte", the combining character is lost.
         * Expected result is only on token "moͤchte".
         */
        assertAnalyzesTo(a, "moͤchte", arrayOf("moͤchte"))
    }

    /* Tests from StandardAnalyzer, just to show behavior is similar */
    @Test
    @Throws(Exception::class)
    fun testAlphanumericSA() {
        // alphanumeric tokens
        assertAnalyzesTo(a, "B2B", arrayOf("B2B"))
        assertAnalyzesTo(a, "2B", arrayOf("2B"))
    }

    @Test
    @Throws(Exception::class)
    fun testDelimitersSA() {
        // other delimiters: "-", "/", ","
        assertAnalyzesTo(a, "some-dashed-phrase", arrayOf("some", "dashed", "phrase"))
        assertAnalyzesTo(a, "dogs,chase,cats", arrayOf("dogs", "chase", "cats"))
        assertAnalyzesTo(a, "ac/dc", arrayOf("ac", "dc"))
    }

    @Test
    @Throws(Exception::class)
    fun testApostrophesSA() {
        // internal apostrophes: O'Reilly, you're, O'Reilly's
        assertAnalyzesTo(a, "O'Reilly", arrayOf("O'Reilly"))
        assertAnalyzesTo(a, "you're", arrayOf("you're"))
        assertAnalyzesTo(a, "she's", arrayOf("she's"))
        assertAnalyzesTo(a, "Jim's", arrayOf("Jim's"))
        assertAnalyzesTo(a, "don't", arrayOf("don't"))
        assertAnalyzesTo(a, "O'Reilly's", arrayOf("O'Reilly's"))
    }

    @Test
    @Throws(Exception::class)
    fun testNumericSA() {
        // floating point, serial, model numbers, ip addresses, etc.
        assertAnalyzesTo(a, "21.35", arrayOf("21.35"))
        assertAnalyzesTo(a, "R2D2 C3PO", arrayOf("R2D2", "C3PO"))
        assertAnalyzesTo(a, "216.239.63.104", arrayOf("216.239.63.104"))
    }

    @Test
    @Throws(Exception::class)
    fun testTextWithNumbersSA() {
        // numbers
        assertAnalyzesTo(a, "David has 5000 bones", arrayOf("David", "has", "5000", "bones"))
    }

    @Test
    @Throws(Exception::class)
    fun testVariousTextSA() {
        // various
        assertAnalyzesTo(a, "C embedded developers wanted", arrayOf("C", "embedded", "developers", "wanted"))
        assertAnalyzesTo(a, "foo bar FOO BAR", arrayOf("foo", "bar", "FOO", "BAR"))
        assertAnalyzesTo(a, "foo      bar .  FOO <> BAR", arrayOf("foo", "bar", "FOO", "BAR"))
        assertAnalyzesTo(a, "\"QUOTED\" word", arrayOf("QUOTED", "word"))
    }

    @Test
    @Throws(Exception::class)
    fun testKoreanSA() {
        // Korean words
        assertAnalyzesTo(a, "안녕하세요 한글입니다", arrayOf("안녕하세요", "한글입니다"))
    }

    @Test
    @Throws(Exception::class)
    fun testOffsets() {
        assertAnalyzesTo(
            a,
            "David has 5000 bones",
            arrayOf("David", "has", "5000", "bones"),
            intArrayOf(0, 6, 10, 15),
            intArrayOf(5, 9, 14, 20)
        )
    }

    @Test
    @Throws(Exception::class)
    fun testTypes() {
        assertAnalyzesTo(
            a,
            "David has 5000 bones",
            arrayOf("David", "has", "5000", "bones"),
            arrayOf("<ALPHANUM>", "<ALPHANUM>", "<NUM>", "<ALPHANUM>")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testWikiURLs() {
        val luceneResourcesWikiPage = readResource("LuceneResourcesWikiPage.html")
        assertTrue(luceneResourcesWikiPage.isNotEmpty())
        val urls = readResourceLines("LuceneResourcesWikiPageURLs.txt")
        assertTrue(urls.isNotEmpty())
        assertAnalyzesTo(urlAnalyzer, luceneResourcesWikiPage, urls.toTypedArray())
    }

    @Test
    @Throws(Exception::class)
    fun testEmails() {
        val randomTextWithEmails = readResource("random.text.with.email.addresses.txt")
        assertTrue(randomTextWithEmails.isNotEmpty())
        val emails = readResourceLines("email.addresses.from.random.text.with.email.addresses.txt")
        assertTrue(emails.isNotEmpty())
        assertAnalyzesTo(emailAnalyzer, randomTextWithEmails, emails.toTypedArray())
    }

    @Test
    @Throws(Exception::class)
    fun testMailtoSchemeEmails() {
        // See LUCENE-3880
        assertAnalyzesTo(
            a,
            "mailto:test@example.org",
            arrayOf("mailto", "test@example.org"),
            arrayOf("<ALPHANUM>", "<EMAIL>")
        )

        // TODO: Support full mailto: scheme URIs. See RFC 6068: http://tools.ietf.org/html/rfc6068
        assertAnalyzesTo(
            a,
            "mailto:personA@example.com,personB@example.com?cc=personC@example.com" +
                "&subject=Subjectivity&body=Corpusivity%20or%20something%20like%20that",
            arrayOf(
                "mailto",
                "personA@example.com",
                // TODO: recognize ',' address delimiter. Also, see examples of ';' delimiter use at:
                // http://www.mailto.co.uk/
                ",personB@example.com",
                "?cc=personC@example.com", // TODO: split field keys/values
                "subject",
                "Subjectivity",
                "body",
                "Corpusivity",
                "20or",
                "20something",
                "20like",
                "20that"
            ), // TODO: Hex decoding + re-tokenization
            arrayOf(
                "<ALPHANUM>",
                "<EMAIL>",
                "<EMAIL>",
                "<EMAIL>",
                "<ALPHANUM>",
                "<ALPHANUM>",
                "<ALPHANUM>",
                "<ALPHANUM>",
                "<ALPHANUM>",
                "<ALPHANUM>",
                "<ALPHANUM>",
                "<ALPHANUM>"
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testURLs() {
        val randomTextWithURLs = readResource("random.text.with.urls.txt")
        assertTrue(randomTextWithURLs.isNotEmpty())
        val urls = readResourceLines("urls.from.random.text.with.urls.txt")
        assertTrue(urls.isNotEmpty())
        assertAnalyzesTo(urlAnalyzer, randomTextWithURLs, urls.toTypedArray())
    }

    @Test
    @Throws(Exception::class)
    fun testUnicodeWordBreaks() {
        WordBreakTestUnicode_12_1_0.test(a)
    }

    @Test
    @Throws(Exception::class)
    fun testSupplementary() {
        assertAnalyzesTo(
            a,
            "𩬅艱鍟䇹愯瀛",
            arrayOf("𩬅", "艱", "鍟", "䇹", "愯", "瀛"),
            arrayOf("<IDEOGRAPHIC>", "<IDEOGRAPHIC>", "<IDEOGRAPHIC>", "<IDEOGRAPHIC>", "<IDEOGRAPHIC>", "<IDEOGRAPHIC>")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testKorean() {
        assertAnalyzesTo(a, "훈민정음", arrayOf("훈민정음"), arrayOf("<HANGUL>"))
    }

    @Test
    @Throws(Exception::class)
    fun testJapanese() {
        assertAnalyzesTo(
            a,
            "仮名遣い カタカナ",
            arrayOf("仮", "名", "遣", "い", "カタカナ"),
            arrayOf("<IDEOGRAPHIC>", "<IDEOGRAPHIC>", "<IDEOGRAPHIC>", "<HIRAGANA>", "<KATAKANA>")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testCombiningMarks() {
        checkOneTerm(a, "ざ", "ざ") // hiragana
        checkOneTerm(a, "ザ", "ザ") // katakana
        checkOneTerm(a, "壹゙", "壹゙") // ideographic
        checkOneTerm(a, "아゙", "아゙") // hangul
    }

    /**
     * Multiple consecutive chars in \p{Word_Break = MidLetter}, \p{Word_Break = MidNumLet}, and/or
     * \p{Word_Break = MidNum} should trigger a token split.
     */
    @Test
    @Throws(Exception::class)
    fun testMid() {
        // ':' is in \p{WB:MidLetter}, which should trigger a split unless there is a Letter char on
        // both sides
        assertAnalyzesTo(a, "A:B", arrayOf("A:B"))
        assertAnalyzesTo(a, "A::B", arrayOf("A", "B"))

        // '.' is in \p{WB:MidNumLet}, which should trigger a split unless there is a Letter or Numeric
        // char on both sides
        assertAnalyzesTo(a, "1.2", arrayOf("1.2"))
        assertAnalyzesTo(a, "A.B", arrayOf("A.B"))
        assertAnalyzesTo(a, "1..2", arrayOf("1", "2"))
        assertAnalyzesTo(a, "A..B", arrayOf("A", "B"))

        // ',' is in \p{WB:MidNum}, which should trigger a split unless there is a Numeric char on both
        // sides
        assertAnalyzesTo(a, "1,2", arrayOf("1,2"))
        assertAnalyzesTo(a, "1,,2", arrayOf("1", "2"))

        // Mixed consecutive \p{WB:MidLetter} and \p{WB:MidNumLet} should trigger a split
        assertAnalyzesTo(a, "A.:B", arrayOf("A", "B"))
        assertAnalyzesTo(a, "A:.B", arrayOf("A", "B"))

        // Mixed consecutive \p{WB:MidNum} and \p{WB:MidNumLet} should trigger a split
        assertAnalyzesTo(a, "1,.2", arrayOf("1", "2"))
        assertAnalyzesTo(a, "1.,2", arrayOf("1", "2"))

        // '_' is in \p{WB:ExtendNumLet}

        assertAnalyzesTo(a, "A:B_A:B", arrayOf("A:B_A:B"))
        assertAnalyzesTo(a, "A:B_A::B", arrayOf("A:B_A", "B"))

        assertAnalyzesTo(a, "1.2_1.2", arrayOf("1.2_1.2"))
        assertAnalyzesTo(a, "A.B_A.B", arrayOf("A.B_A.B"))
        assertAnalyzesTo(a, "1.2_1..2", arrayOf("1.2_1", "2"))
        assertAnalyzesTo(a, "A.B_A..B", arrayOf("A.B_A", "B"))

        assertAnalyzesTo(a, "1,2_1,2", arrayOf("1,2_1,2"))
        assertAnalyzesTo(a, "1,2_1,,2", arrayOf("1,2_1", "2"))

        assertAnalyzesTo(a, "C_A.:B", arrayOf("C_A", "B"))
        assertAnalyzesTo(a, "C_A:.B", arrayOf("C_A", "B"))

        assertAnalyzesTo(a, "3_1,.2", arrayOf("3_1", "2"))
        assertAnalyzesTo(a, "3_1.,2", arrayOf("3_1", "2"))
    }

    /** simple emoji */
    @Test
    @Throws(Exception::class)
    fun testEmoji() {
        assertAnalyzesTo(
            a,
            "💩 💩💩",
            arrayOf("💩", "💩", "💩"),
            arrayOf("<EMOJI>", "<EMOJI>", "<EMOJI>")
        )
    }

    /** emoji zwj sequence */
    @Test
    @Throws(Exception::class)
    fun testEmojiSequence() {
        assertAnalyzesTo(a, "👩‍❤️‍👩", arrayOf("👩‍❤️‍👩"), arrayOf("<EMOJI>"))
    }

    /** emoji zwj sequence with fitzpatrick modifier */
    @Test
    @Throws(Exception::class)
    fun testEmojiSequenceWithModifier() {
        assertAnalyzesTo(a, "👨🏼‍⚕️", arrayOf("👨🏼‍⚕️"), arrayOf("<EMOJI>"))
    }

    /** regional indicator */
    @Test
    @Throws(Exception::class)
    fun testEmojiRegionalIndicator() {
        assertAnalyzesTo(a, "🇺🇸🇺🇸", arrayOf("🇺🇸", "🇺🇸"), arrayOf("<EMOJI>", "<EMOJI>"))
    }

    /** variation sequence */
    @Test
    @Throws(Exception::class)
    fun testEmojiVariationSequence() {
        assertAnalyzesTo(a, "#️⃣", arrayOf("#️⃣"), arrayOf("<EMOJI>"))
        assertAnalyzesTo(a, "3️⃣", arrayOf("3️⃣"), arrayOf("<EMOJI>"))

        // text presentation sequences
        assertAnalyzesTo(a, "#\uFE0E", emptyArray(), emptyArray())
        assertAnalyzesTo(
            a,
            "3\uFE0E", // \uFE0E is included in \p{WB:Extend}
            arrayOf("3\uFE0E"),
            arrayOf("<NUM>")
        )
        assertAnalyzesTo(
            a,
            "\u2B55\uFE0E", // \u2B55 = HEAVY BLACK CIRCLE
            arrayOf("\u2B55"),
            arrayOf("<EMOJI>")
        )
        assertAnalyzesTo(
            a,
            "\u2B55\uFE0E\u200D\u2B55\uFE0E",
            arrayOf("\u2B55", "\u200D\u2B55"),
            arrayOf("<EMOJI>", "<EMOJI>")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testEmojiTagSequence() {
        assertAnalyzesTo(a, "🏴", arrayOf("🏴"), arrayOf("<EMOJI>"))
    }

    @Test
    @Throws(Exception::class)
    fun testEmojiTokenization() {
        // simple emoji around latin
        assertAnalyzesTo(
            a,
            "poo💩poo",
            arrayOf("poo", "💩", "poo"),
            arrayOf("<ALPHANUM>", "<EMOJI>", "<ALPHANUM>")
        )
        // simple emoji around non-latin
        assertAnalyzesTo(
            a,
            "💩中國💩",
            arrayOf("💩", "中", "國", "💩"),
            arrayOf("<EMOJI>", "<IDEOGRAPHIC>", "<IDEOGRAPHIC>", "<EMOJI>")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testUnicodeEmojiTests() {
        EmojiTokenizationTestUnicode_12_1.test(a)
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
    }

    /** blast some random large strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomHugeStrings() {
        val random = random()
        checkRandomData(random, a, 3 * RANDOM_MULTIPLIER, 8192)
    }

    @Test
    @Throws(Exception::class)
    fun testExampleURLs() {
        val tlds = readResourceLines("TLDs.txt")
            .filter { line -> !line.trim().startsWith("#") && line.isNotBlank() }

        val analyzer: Analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    return TokenStreamComponents(UAX29URLEmailTokenizer(newAttributeFactory()))
                }
            }

        for (tld in tlds) {
            val url = "example.$tld"
            assertAnalyzesTo(analyzer, url, arrayOf(url), arrayOf("<URL>"))
        }
        analyzer.close()
    }

    private fun readResource(name: String): String {
        val loader = ClasspathResourceLoader(this::class)
        val stream = loader.openResource(name)
        InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
            val builder = StringBuilder()
            val buffer = CharArray(1024)
            while (true) {
                val numCharsRead = reader.read(buffer, 0, buffer.size)
                if (numCharsRead == -1) {
                    break
                }
                builder.append(buffer.concatToString(0, numCharsRead))
            }
            return builder.toString()
        }
    }

    private fun readResourceLines(name: String): List<String> {
        val loader = ClasspathResourceLoader(this::class)
        val stream = loader.openResource(name)
        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            val lines = mutableListOf<String>()
            while (true) {
                val line = reader.readLine() ?: break
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    lines.add(trimmed)
                }
            }
            return lines
        }
    }
}
