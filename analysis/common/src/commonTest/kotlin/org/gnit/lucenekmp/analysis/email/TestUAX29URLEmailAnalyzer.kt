package org.gnit.lucenekmp.analysis.email

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class TestUAX29URLEmailAnalyzer : BaseTokenStreamTestCase() {
    private lateinit var a: Analyzer

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        a = UAX29URLEmailAnalyzer()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        a.close()
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
        assertAnalyzesTo(a, input, arrayOf("testing", "1234"))
    }

    @Test
    @Throws(Exception::class)
    fun testArmenian() {
        assertAnalyzesTo(
            a,
            "Վիքիպեդիայի 13 միլիոն հոդվածները (4,600` հայերեն վիքիպեդիայում) գրվել են կամավորների կողմից ու համարյա բոլոր հոդվածները կարող է խմբագրել ցանկաց մարդ ով կարող է բացել Վիքիպեդիայի կայքը։",
            arrayOf(
                "վիքիպեդիայի", "13", "միլիոն", "հոդվածները", "4,600", "հայերեն",
                "վիքիպեդիայում", "գրվել", "են", "կամավորների", "կողմից", "ու",
                "համարյա", "բոլոր", "հոդվածները", "կարող", "է", "խմբագրել", "ցանկաց",
                "մարդ", "ով", "կարող", "է", "բացել", "վիքիպեդիայի", "կայքը"
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
            arrayOf(
                "الفيلم", "الوثائقي", "الأول", "عن", "ويكيبيديا", "يسمى", "الحقيقة",
                "بالأرقام", "قصة", "ويكيبيديا", "بالإنجليزية", "truth", "numbers",
                "wikipedia", "story", "سيتم", "إطلاقه", "في", "2008"
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testAramaic() {
        assertAnalyzesTo(
            a,
            "ܘܝܩܝܦܕܝܐ (ܐܢܓܠܝܐ: Wikipedia) ܗܘ ܐܝܢܣܩܠܘܦܕܝܐ ܚܐܪܬܐ ܕܐܢܛܪܢܛ ܒܠܫܢ̈ܐ ܣܓܝܐ̈ܐ܂ ܫܡܗ ܐܬܐ ܡܢ ܡ̈ܠܬܐ ܕ\"ܘܝܩܝ\" ܘ\"ܐܝܢܣܩܠܘܦܕܝܐ\"܀",
            arrayOf(
                "ܘܝܩܝܦܕܝܐ", "ܐܢܓܠܝܐ", "wikipedia", "ܗܘ", "ܐܝܢܣܩܠܘܦܕܝܐ",
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
                "γράφεται", "σε", "συνεργασία", "από", "εθελοντές", "με", "το",
                "λογισμικό", "wiki", "κάτι", "που", "σημαίνει", "ότι", "άρθρα",
                "μπορεί", "να", "προστεθούν", "ή", "να", "αλλάξουν", "από", "τον", "καθένα"
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testThai() {
        assertAnalyzesTo(
            a,
            "การที่ได้ต้องแสดงว่างานดี. แล้วเธอจะไปไหน? ๑๒๓๔",
            arrayOf("การที่ได้ต้องแสดงว่างานดี", "แล้วเธอจะไปไหน", "๑๒๓๔")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testLao() {
        assertAnalyzesTo(
            a,
            "ສາທາລະນະລັດ ປະຊາທິປະໄຕ ປະຊາຊົນລາວ",
            arrayOf("ສາທາລະນະລັດ", "ປະຊາທິປະໄຕ", "ປະຊາຊົນລາວ")
        )
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
        assertAnalyzesTo(a, "我是中国人。 １２３４ Ｔｅｓｔｓ ", arrayOf("我", "是", "中", "国", "人", "１２３４", "ｔｅｓｔｓ"))
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
         * Standard analyzer does not correctly tokenize combining character U+0364 COMBINING LATIN SMALL LETTER E.
         * The word "moͤchte" is incorrectly tokenized into "mo" "chte", the combining character is lost.
         * Expected result is only one token "moͤchte".
         */
        assertAnalyzesTo(a, "moͤchte", arrayOf("moͤchte"))
    }

    /* Tests from StandardAnalyzer, just to show behavior is similar */
    @Test
    @Throws(Exception::class)
    fun testAlphanumericSA() {
        // alphanumeric tokens
        assertAnalyzesTo(a, "B2B", arrayOf("b2b"))
        assertAnalyzesTo(a, "2B", arrayOf("2b"))
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
        assertAnalyzesTo(a, "O'Reilly", arrayOf("o'reilly"))
        assertAnalyzesTo(a, "you're", arrayOf("you're"))
        assertAnalyzesTo(a, "she's", arrayOf("she's"))
        assertAnalyzesTo(a, "Jim's", arrayOf("jim's"))
        assertAnalyzesTo(a, "don't", arrayOf("don't"))
        assertAnalyzesTo(a, "O'Reilly's", arrayOf("o'reilly's"))
    }

    @Test
    @Throws(Exception::class)
    fun testNumericSA() {
        // floating point, serial, model numbers, ip addresses, etc.
        assertAnalyzesTo(a, "21.35", arrayOf("21.35"))
        assertAnalyzesTo(a, "R2D2 C3PO", arrayOf("r2d2", "c3po"))
        assertAnalyzesTo(a, "216.239.63.104", arrayOf("216.239.63.104"))
    }

    @Test
    @Throws(Exception::class)
    fun testTextWithNumbersSA() {
        // numbers
        assertAnalyzesTo(a, "David has 5000 bones", arrayOf("david", "has", "5000", "bones"))
    }

    @Test
    @Throws(Exception::class)
    fun testVariousTextSA() {
        // various
        assertAnalyzesTo(a, "C embedded developers wanted", arrayOf("c", "embedded", "developers", "wanted"))
        assertAnalyzesTo(a, "foo bar FOO BAR", arrayOf("foo", "bar", "foo", "bar"))
        assertAnalyzesTo(a, "foo      bar .  FOO <> BAR", arrayOf("foo", "bar", "foo", "bar"))
        assertAnalyzesTo(a, "\"QUOTED\" word", arrayOf("quoted", "word"))
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
            arrayOf("david", "has", "5000", "bones"),
            intArrayOf(0, 6, 10, 15),
            intArrayOf(5, 9, 14, 20)
        )
    }

    @Test
    @Throws(Exception::class)
    fun testTypes() {
        assertAnalyzesTo(
            a,
            "david has 5000 bones",
            arrayOf("david", "has", "5000", "bones"),
            arrayOf("<ALPHANUM>", "<ALPHANUM>", "<NUM>", "<ALPHANUM>")
        )
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

    @Test
    @Throws(Exception::class)
    fun testBasicEmails() {
        assertAnalyzesTo(
            a,
            "one test@example.com two three [A@example.CO.UK] \"ArakaBanassaMassanaBakarA\" <info@Info.info>",
            arrayOf("one", "test@example.com", "two", "three", "a@example.co.uk", "arakabanassamassanabakara", "info@info.info"),
            arrayOf("<ALPHANUM>", "<EMAIL>", "<ALPHANUM>", "<ALPHANUM>", "<EMAIL>", "<ALPHANUM>", "<EMAIL>")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testMailtoSchemeEmails() {
        // See LUCENE-3880
        assertAnalyzesTo(
            a,
            "MAILTO:Test@Example.ORG",
            arrayOf("mailto", "test@example.org"),
            arrayOf("<ALPHANUM>", "<EMAIL>")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testBasicURLs() {
        assertAnalyzesTo(
            a,
            "a <HTTPs://example.net/omg/isnt/that/NICE?no=its&n%30t#mntl-E>b-D ftp://www.example.com/ABC.txt file:///C:/path/to/a/FILE.txt C",
            arrayOf(
                "https://example.net/omg/isnt/that/nice?no=its&n%30t#mntl-e",
                "b",
                "d",
                "ftp://www.example.com/abc.txt",
                "file:///c:/path/to/a/file.txt",
                "c"
            ),
            arrayOf("<URL>", "<ALPHANUM>", "<ALPHANUM>", "<URL>", "<URL>", "<ALPHANUM>")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testNoSchemeURLs() {
        // ".ph" is a Top Level Domain
        assertAnalyzesTo(a, "<index.ph>", arrayOf("index.ph"), arrayOf("<URL>"))
        assertAnalyzesTo(a, "index.ph", arrayOf("index.ph"), arrayOf("<URL>"))
        assertAnalyzesTo(a, "index.php", arrayOf("index.php"), arrayOf("<ALPHANUM>"))
        assertAnalyzesTo(a, "index.phα", arrayOf("index.phα"), arrayOf("<ALPHANUM>"))
        assertAnalyzesTo(a, "index-h.php", arrayOf("index", "h.php"), arrayOf("<ALPHANUM>", "<ALPHANUM>"))
        assertAnalyzesTo(a, "index2.php", arrayOf("index2", "php"), arrayOf("<ALPHANUM>", "<ALPHANUM>"))
        assertAnalyzesTo(a, "index2.ph９,", arrayOf("index2", "ph９"), arrayOf("<ALPHANUM>", "<ALPHANUM>"))
        assertAnalyzesTo(
            a,
            "example.com,example.ph,index.php,index2.php,example2.ph",
            arrayOf("example.com", "example.ph", "index.php", "index2", "php", "example2.ph"),
            arrayOf("<URL>", "<URL>", "<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>", "<URL>")
        )
        assertAnalyzesTo(
            a,
            "example.com:8080 example.com/path/here example.com?query=something example.com#fragment",
            arrayOf("example.com:8080", "example.com/path/here", "example.com?query=something", "example.com#fragment"),
            arrayOf("<URL>", "<URL>", "<URL>", "<URL>")
        )
        assertAnalyzesTo(
            a,
            "example.com:8080/path/here?query=something#fragment",
            arrayOf("example.com:8080/path/here?query=something#fragment"),
            arrayOf("<URL>")
        )
        assertAnalyzesTo(
            a,
            "example.com/path/here?query=something",
            arrayOf("example.com/path/here?query=something"),
            arrayOf("<URL>")
        )
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
    }

    @Test
    @Throws(Exception::class)
    fun testMaxTokenLengthDefault() {
        val bToken = StringBuilder()
        // exact max length:
        for (i in 0..<UAX29URLEmailAnalyzer.DEFAULT_MAX_TOKEN_LENGTH) {
            bToken.append('b')
        }

        val bString = bToken.toString()
        // first bString is exact max default length; next one is 1 too long
        val input = "x $bString ${bString}b"
        assertAnalyzesTo(a, input, arrayOf("x", bString, bString, "b"))
    }

    @Test
    @Throws(Exception::class)
    fun testMaxTokenLengthNonDefault() {
        val a = UAX29URLEmailAnalyzer()
        a.setMaxTokenLength(5)
        assertAnalyzesTo(a, "ab cd toolong xy z", arrayOf("ab", "cd", "toolo", "ng", "xy", "z"))
        a.close()
    }
}
