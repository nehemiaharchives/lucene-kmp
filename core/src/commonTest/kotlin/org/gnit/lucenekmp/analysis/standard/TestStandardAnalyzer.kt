package org.gnit.lucenekmp.analysis.standard

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockGraphTokenFilter
import org.gnit.lucenekmp.tests.analysis.standard.EmojiTokenizationTestUnicode_12_1
import org.gnit.lucenekmp.tests.analysis.standard.WordBreakTestUnicode_12_1_0
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestStandardAnalyzer : BaseTokenStreamTestCase() {

    // LUCENE-5897: slow tokenization of strings of the form
    // (\p{WB:ExtendNumLet}[\p{WB:Format}\p{WB:Extend}]*)+
    @Test
    @Throws(Exception::class)
    fun testLargePartiallyMatchingToken() {
        // TODO: get these lists of chars matching a property from ICU4J
        // http://www.unicode.org/Public/6.3.0/ucd/auxiliary/WordBreakProperty.txt
        val WordBreak_ExtendNumLet_chars =
            "_\u203f\u2040\u2054\ufe33\ufe34\ufe4d\ufe4e\ufe4f\uff3f".toCharArray()

        // http://www.unicode.org/Public/6.3.0/ucd/auxiliary/WordBreakProperty.txt
        val WordBreak_Format_chars = intArrayOf( // only the first char in ranges
            0xAD, 0x600, 0x61C, 0x6DD, 0x70F, 0x180E, 0x200E, 0x202A, 0x2060, 0x2066, 0xFEFF, 0xFFF9,
            0x110BD, 0x1D173, 0xE0001, 0xE0020
        )

        // http://www.unicode.org/Public/6.3.0/ucd/auxiliary/WordBreakProperty.txt
        val WordBreak_Extend_chars = intArrayOf( // only the first char in ranges
            0x300, 0x483, 0x591, 0x5bf, 0x5c1, 0x5c4, 0x5c7, 0x610, 0x64b, 0x670, 0x6d6, 0x6df, 0x6e7,
            0x6ea, 0x711, 0x730, 0x7a6, 0x7eb, 0x816, 0x81b, 0x825, 0x829, 0x859, 0x8e4, 0x900, 0x93a,
            0x93e, 0x951, 0x962, 0x981, 0x9bc, 0x9be, 0x9c7, 0x9cb, 0x9d7, 0x9e2, 0xa01, 0xa3c, 0xa3e,
            0xa47, 0xa4b, 0xa51, 0xa70, 0xa75, 0xa81, 0xabc, 0xabe, 0xac7, 0xacb, 0xae2, 0xb01, 0xb3c,
            0xb3e, 0xb47, 0xb4b, 0xb56, 0xb62, 0xb82, 0xbbe, 0xbc6, 0xbca, 0xbd7, 0xc01, 0xc3e, 0xc46,
            0xc4a, 0xc55, 0xc62, 0xc82, 0xcbc, 0xcbe, 0xcc6, 0xcca, 0xcd5, 0xce2, 0xd02, 0xd3e, 0xd46,
            0xd4a, 0xd57, 0xd62, 0xd82, 0xdca, 0xdcf, 0xdd6, 0xdd8, 0xdf2, 0xe31, 0xe34, 0xe47, 0xeb1,
            0xebb, 0xec8, 0xf18, 0xf35, 0xf37, 0xf39, 0xf3e, 0xf71, 0xf86, 0xf8d, 0xf99, 0xfc6, 0x102b,
            0x1056, 0x105e, 0x1062, 0x1067, 0x1071, 0x1082, 0x108f, 0x109a, 0x135d, 0x1712, 0x1732,
            0x1752, 0x1772, 0x17b4, 0x17dd, 0x180b, 0x18a9, 0x1920, 0x1930, 0x19b0, 0x19c8, 0x1a17,
            0x1a55, 0x1a60, 0x1a7f, 0x1b00, 0x1b34, 0x1b6b, 0x1b80, 0x1ba1, 0x1be6, 0x1c24, 0x1cd0,
            0x1cd4, 0x1ced, 0x1cf2, 0x1dc0, 0x1dfc, 0x200c, 0x20d0, 0x2cef, 0x2d7f, 0x2de0, 0x302a,
            0x3099, 0xa66f, 0xa674, 0xa69f, 0xa6f0, 0xa802, 0xa806, 0xa80b, 0xa823, 0xa880, 0xa8b4,
            0xa8e0, 0xa926, 0xa947, 0xa980, 0xa9b3, 0xaa29, 0xaa43, 0xaa4c, 0xaa7b, 0xaab0, 0xaab2,
            0xaab7, 0xaabe, 0xaac1, 0xaaeb, 0xaaf5, 0xabe3, 0xabec, 0xfb1e, 0xfe00, 0xfe20, 0xff9e,
            0x101fd, 0x10a01, 0x10a05, 0x10a0C, 0x10a38, 0x10a3F, 0x11000, 0x11001, 0x11038, 0x11080,
            0x11082, 0x110b0, 0x110b3, 0x110b7, 0x110b9, 0x11100, 0x11127, 0x1112c, 0x11180, 0x11182,
            0x111b3, 0x111b6, 0x111bF, 0x116ab, 0x116ac, 0x116b0, 0x116b6, 0x16f51, 0x16f8f, 0x1d165,
            0x1d167, 0x1d16d, 0x1d17b, 0x1d185, 0x1d1aa, 0x1d242, 0xe0100
        )

        val builder = StringBuilder()
        val numChars = TestUtil.nextInt(random(), 100 * 1024, 1024 * 1024)
        var i = 0
        while (i < numChars) {
            builder.append(WordBreak_ExtendNumLet_chars[random().nextInt(WordBreak_ExtendNumLet_chars.size)])
            ++i
            if (random().nextBoolean()) {
                val numFormatExtendChars = TestUtil.nextInt(random(), 1, 8)
                for (j in 0 until numFormatExtendChars) {
                    val codepoint = if (random().nextBoolean()) {
                        WordBreak_Format_chars[random().nextInt(WordBreak_Format_chars.size)]
                    } else {
                        WordBreak_Extend_chars[random().nextInt(WordBreak_Extend_chars.size)]
                    }
                    val chars = CharArray(2)
                    val count = Character.toChars(codepoint, chars, 0)
                    builder.append(chars.concatToString(0, count))
                    i += count
                }
            }
        }
        val ts = StandardTokenizer()
        ts.setReader(StringReader(builder.toString()))
        ts.reset()
        while (ts.incrementToken()) {
        }
        ts.end()
        ts.close()

        val newBufferSize = TestUtil.nextInt(random(), 200, 8192)
        ts.setMaxTokenLength(newBufferSize) // try a different buffer size
        ts.setReader(StringReader(builder.toString()))
        ts.reset()
        while (ts.incrementToken()) {
        }
        ts.end()
        ts.close()
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
        val tokenizer = StandardTokenizer()
        tokenizer.setReader(StringReader(input))
        assertTokenStreamContents(tokenizer, arrayOf("testing", "1234"))
    }

    private var a: Analyzer = createAnalyzer()

    private fun createAnalyzer(): Analyzer {
        return object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = StandardTokenizer(newAttributeFactory())
                return TokenStreamComponents(tokenizer)
            }
        }
    }

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        a = createAnalyzer()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testArmenian() {
        assertAnalyzesTo(
            a,
            "Վիքիպեդիայի 13 միլիոն հոդվածները (4,600` հայերեն վիքիպեդիայում) գրվել են կամավորների կողմից ու համարյա բոլոր հոդվածները կարող է խմբագրել ցանկաց մարդ ով կարող է բացել Վիքիպեդիայի կայքը։",
            arrayOf(
                "Վիքիպեդիայի", "13", "միլիոն", "հոդվածները", "4,600", "հայերեն", "վիքիպեդիայում", "գրվել", "են", "կամավորների", "կողմից", "ու", "համարյա", "բոլոր", "հոդվածները", "կարող", "է", "խմբագրել", "ցանկաց", "մարդ", "ով", "կարող", "է", "բացել", "Վիքիպեդիայի", "կայքը"
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
    fun testArabic() {
        assertAnalyzesTo(
            a,
            "الفيلم الوثائقي الأول عن ويكيبيديا يسمى \"الحقيقة بالأرقام: قصة ويكيبيديا\" (بالإنجليزية: Truth in Numbers: The Wikipedia Story)، سيتم إطلاقه في 2008.",
            arrayOf("الفيلم", "الوثائقي", "الأول", "عن", "ويكيبيديا", "يسمى", "الحقيقة", "بالأرقام", "قصة", "ويكيبيديا", "بالإنجليزية", "Truth", "in", "Numbers", "The", "Wikipedia", "Story", "سيتم", "إطلاقه", "في", "2008")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testAramaic() {
        assertAnalyzesTo(
            a,
            "ܘܝܩܝܦܕܝܐ (ܐܢܓܠܝܐ: Wikipedia) ܗܘ ܐܝܢܣܩܠܘܦܕܝܐ ܚܐܪܬܐ ܕܐܢܛܪܢܛ ܒܠܫܢ̈ܐ ܣܓܝܐ̈ܐ܂ ܫܡܗ ܐܬܐ ܡܢ ܡ̈ܠܬܐ ܕ\"ܘܝܩܝ\" ܘ\"ܐܝܢܣܩܠܘܦܕܝܐ\"܀",
            arrayOf("ܘܝܩܝܦܕܝܐ", "ܐܢܓܠܝܐ", "Wikipedia", "ܗܘ", "ܐܝܢܣܩܠܘܦܕܝܐ", "ܚܐܪܬܐ", "ܕܐܢܛܪܢܛ", "ܒܠܫܢ̈ܐ", "ܣܓܝܐ̈ܐ", "ܫܡܗ", "ܐܬܐ", "ܡܢ", "ܡ̈ܠܬܐ", "ܕ", "ܘܝܩܝ", "ܘ", "ܐܝܢܣܩܠܘܦܕܝܐ")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testBengali() {
        assertAnalyzesTo(
            a,
            "এই বিশ্বকোষ পরিচালনা করে উইকিমিডিয়া ফাউন্ডেশন (একটি অলাভজনক সংস্থা)। উইকিপিডিয়ার শুরু ১৫ জানুয়ারি, ২০০১ সালে। এখন পর্যন্ত ২০০টিরও বেশী ভাষায় উইকিপিডিয়া রয়েছে।",
            arrayOf("এই", "বিশ্বকোষ", "পরিচালনা", "করে", "উইকিমিডিয়া", "ফাউন্ডেশন", "একটি", "অলাভজনক", "সংস্থা", "উইকিপিডিয়ার", "শুরু", "১৫", "জানুয়ারি", "২০০১", "সালে", "এখন", "পর্যন্ত", "২০০টিরও", "বেশী", "ভাষায়", "উইকিপিডিয়া", "রয়েছে")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testFarsi() {
        assertAnalyzesTo(
            a,
            "ویکی پدیای انگلیسی در تاریخ ۲۵ دی ۱۳۷۹ به صورت مکملی برای دانشنامهٔ تخصصی نوپدیا نوشته شد.",
            arrayOf("ویکی", "پدیای", "انگلیسی", "در", "تاریخ", "۲۵", "دی", "۱۳۷۹", "به", "صورت", "مکملی", "برای", "دانشنامهٔ", "تخصصی", "نوپدیا", "نوشته", "شد")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testGreek() {
        assertAnalyzesTo(
            a,
            "Γράφεται σε συνεργασία από εθελοντές με το λογισμικό wiki, κάτι που σημαίνει ότι άρθρα μπορεί να προστεθούν ή να αλλάξουν από τον καθένα.",
            arrayOf("Γράφεται", "σε", "συνεργασία", "από", "εθελοντές", "με", "το", "λογισμικό", "wiki", "κάτι", "που", "σημαίνει", "ότι", "άρθρα", "μπορεί", "να", "προστεθούν", "ή", "να", "αλλάξουν", "από", "τον", "καθένα")
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
        assertAnalyzesTo(a, "སྣོན་མཛོད་དང་ལས་འདིས་བོད་ཡིག་མི་ཉམས་གོང་འཕེལ་དུ་གཏོང་བར་ཧ་ཅང་དགེ་མཚན་མཆིས་སོ། །", arrayOf("སྣོན", "མཛོད", "དང", "ལས", "འདིས", "བོད", "ཡིག", "མི", "ཉམས", "གོང", "འཕེལ", "དུ", "གཏོང", "བར", "ཧ", "ཅང", "དགེ", "མཚན", "མཆིས", "སོ"))
    }

    /*
     * For chinese, tokenize as char (these can later form bigrams or whatever)
     */
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
        assertAnalyzesTo(a, "David has 5000 bones", arrayOf("David", "has", "5000", "bones"), intArrayOf(0, 6, 10, 15), intArrayOf(5, 9, 14, 20))
    }

    @Test
    @Throws(Exception::class)
    fun testTypes() {
        assertAnalyzesTo(a, "David has 5000 bones", arrayOf("David", "has", "5000", "bones"), types = arrayOf("<ALPHANUM>", "<ALPHANUM>", "<NUM>", "<ALPHANUM>"))
    }

    @Test
    @Throws(Exception::class)
    fun testUnicodeWordBreaks() {
        WordBreakTestUnicode_12_1_0.test(a)
    }

    @Test
    @Throws(Exception::class)
    fun testSupplementary() {
        assertAnalyzesTo(a, "𩬅艱鍟䇹愯瀛", arrayOf("𩬅", "艱", "鍟", "䇹", "愯", "瀛"), types = arrayOf("<IDEOGRAPHIC>", "<IDEOGRAPHIC>", "<IDEOGRAPHIC>", "<IDEOGRAPHIC>", "<IDEOGRAPHIC>", "<IDEOGRAPHIC>"))
    }

    @Test
    @Throws(Exception::class)
    fun testKorean() {
        assertAnalyzesTo(a, "훈민정음", arrayOf("훈민정음"), types = arrayOf("<HANGUL>"))
    }

    @Test
    @Throws(Exception::class)
    fun testJapanese() {
        assertAnalyzesTo(a, "仮名遣い カタカナ", arrayOf("仮", "名", "遣", "い", "カタカナ"), types = arrayOf("<IDEOGRAPHIC>", "<IDEOGRAPHIC>", "<IDEOGRAPHIC>", "<HIRAGANA>", "<KATAKANA>"))
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
     * Multiple consecutive chars in \p{WB:MidLetter}, \p{WB:MidNumLet}, and/or \p{MidNum} should
     * trigger a token split.
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
        assertAnalyzesTo(a, "💩 💩💩", arrayOf("💩", "💩", "💩"), types = arrayOf("<EMOJI>", "<EMOJI>", "<EMOJI>"))
    }

    /** emoji zwj sequence */
    @Test
    @Throws(Exception::class)
    fun testEmojiSequence() {
        assertAnalyzesTo(a, "👩‍❤️‍👩", arrayOf("👩‍❤️‍👩"), types = arrayOf("<EMOJI>"))
    }

    /** emoji zwj sequence with fitzpatrick modifier */
    @Test
    @Throws(Exception::class)
    fun testEmojiSequenceWithModifier() {
        assertAnalyzesTo(a, "👨🏼‍⚕️", arrayOf("👨🏼‍⚕️"), types = arrayOf("<EMOJI>"))
    }

    /** regional indicator */
    @Test
    @Throws(Exception::class)
    fun testEmojiRegionalIndicator() {
        assertAnalyzesTo(a, "🇺🇸🇺🇸", arrayOf("🇺🇸", "🇺🇸"), types = arrayOf("<EMOJI>", "<EMOJI>"))
    }

    /** variation sequence */
    @Test
    @Throws(Exception::class)
    fun testEmojiVariationSequence() {
        assertAnalyzesTo(a, "#️⃣", arrayOf("#️⃣"), types = arrayOf("<EMOJI>"))
        assertAnalyzesTo(a, "3️⃣", arrayOf("3️⃣"), types = arrayOf("<EMOJI>"))

        // text presentation sequences
        assertAnalyzesTo(a, "#\uFE0E", emptyArray(), emptyArray())
        assertAnalyzesTo(a, "3\uFE0E", arrayOf("3\uFE0E"), types = arrayOf("<NUM>"))
        assertAnalyzesTo(a, "\u2B55\uFE0E", arrayOf("\u2B55"), types = arrayOf("<EMOJI>"))
        assertAnalyzesTo(a, "\u2B55\uFE0E\u200D\u2B55\uFE0E", arrayOf("\u2B55", "\u200D\u2B55"), types = arrayOf("<EMOJI>", "<EMOJI>"))
    }

    @Test
    @Throws(Exception::class)
    fun testEmojiTagSequence() {
        assertAnalyzesTo(a, "🏴", arrayOf("🏴"), types = arrayOf("<EMOJI>"))
    }

    @Test
    @Throws(Exception::class)
    fun testEmojiTokenization() {
        // simple emoji around latin
        assertAnalyzesTo(a, "poo💩poo", arrayOf("poo", "💩", "poo"), types = arrayOf("<ALPHANUM>", "<EMOJI>", "<ALPHANUM>"))
        // simple emoji around non-latin
        assertAnalyzesTo(a, "💩中國💩", arrayOf("💩", "中", "國", "💩"), types = arrayOf("<EMOJI>", "<IDEOGRAPHIC>", "<IDEOGRAPHIC>", "<EMOJI>"))
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
        val analyzer: Analyzer = StandardAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }

    /** blast some random large strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomHugeStrings() {
        val analyzer: Analyzer = StandardAnalyzer()
        checkRandomData(random(), analyzer, 20 * RANDOM_MULTIPLIER, 8192)
        analyzer.close()
    }

    // Adds random graph after:
    @Test
    @Throws(Exception::class)
    fun testRandomHugeStringsGraphAfter() {
        val random = random()
        val analyzer: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = StandardTokenizer(newAttributeFactory())
                val tokenStream: TokenStream = MockGraphTokenFilter(random(), tokenizer)
                return TokenStreamComponents(tokenizer, tokenStream)
            }
        }
        checkRandomData(random, analyzer, 20 * RANDOM_MULTIPLIER, 8192)
        analyzer.close()
    }

    @Test
    fun testNormalize() {
        val a: Analyzer = StandardAnalyzer()
        assertEquals(BytesRef("\"\\à3[]()! cz@"), a.normalize("dummy", "\"\\À3[]()! Cz@"))
    }

    @Test
    @Throws(Exception::class)
    fun testMaxTokenLengthDefault() {
        val a = StandardAnalyzer()

        // exact max length:
        val bString = "b".repeat(StandardAnalyzer.DEFAULT_MAX_TOKEN_LENGTH)
        // first bString is exact max default length; next one is 1 too long
        val input = "x $bString ${bString}b"
        assertAnalyzesTo(a, input, arrayOf("x", bString, bString, "b"))
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMaxTokenLengthNonDefault() {
        val a = StandardAnalyzer()
        a.maxTokenLength = 5
        assertAnalyzesTo(a, "ab cd toolong xy z", arrayOf("ab", "cd", "toolo", "ng", "xy", "z"))
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSplitSurrogatePairWithSpoonFeedReader() {
        val text = "12345678\ud800\udf00" // U+D800 U+DF00 = U+10300 = 𐌀 (OLD ITALIC LETTER A)

        // Collect tokens with normal reader
        val a = StandardAnalyzer()
        var ts: TokenStream = a.tokenStream("dummy", text)
        val tokens: MutableList<String> = mutableListOf()
        var termAtt: CharTermAttribute = ts.addAttribute(CharTermAttribute::class)
        ts.reset()
        while (ts.incrementToken()) {
            tokens.add(termAtt.toString())
        }
        ts.end()
        ts.close()

        // Tokens from a spoon-feed reader should be the same as from a normal reader
        // The 9th char is a high surrogate, so the 9-max-chars spoon-feed reader will split the
        // surrogate pair at a read boundary
        val reader: Reader = SpoonFeedMaxCharsReaderWrapper(9, StringReader(text))
        ts = a.tokenStream("dummy", reader)
        termAtt = ts.addAttribute(CharTermAttribute::class)
        ts.reset()
        var tokenNum = 0
        while (ts.incrementToken()) {
            assertEquals(tokens[tokenNum], termAtt.toString(), "token #$tokenNum mismatch: ")
            ++tokenNum
        }
        ts.end()
        ts.close()
    }
}

class SpoonFeedMaxCharsReaderWrapper(private val maxChars: Int, private val `in`: Reader) : Reader() {

    override fun close() {
        `in`.close()
    }

    /** Returns the configured number of chars if available */
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        return `in`.read(cbuf, off, minOf(maxChars, len))
    }
}
