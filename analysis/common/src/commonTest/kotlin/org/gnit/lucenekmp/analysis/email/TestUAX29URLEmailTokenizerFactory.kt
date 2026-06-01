package org.gnit.lucenekmp.analysis.email

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A few tests based on org.apache.lucene.analysis.TestUAX29URLEmailTokenizer */
class TestUAX29URLEmailTokenizerFactory : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testUAX29URLEmailTokenizer() {
        val reader = StringReader("Wha\u0301t's this thing do?")
        val stream: Tokenizer = tokenizerFactory("UAX29URLEmail").create(newAttributeFactory())
        stream.setReader(reader)
        assertTokenStreamContents(stream, arrayOf("Wha\u0301t's", "this", "thing", "do"))
    }

    @Test
    @Throws(Exception::class)
    fun testArabic() {
        val reader =
            StringReader(
                "الفيلم الوثائقي الأول عن ويكيبيديا يسمى \"الحقيقة بالأرقام: قصة ويكيبيديا\" (بالإنجليزية: Truth in Numbers: The Wikipedia Story)، سيتم إطلاقه في 2008."
            )
        val stream: Tokenizer = tokenizerFactory("UAX29URLEmail").create(newAttributeFactory())
        stream.setReader(reader)
        assertTokenStreamContents(
            stream,
            arrayOf(
                "الفيلم",
                "الوثائقي",
                "الأول",
                "عن",
                "ويكيبيديا",
                "يسمى",
                "الحقيقة",
                "بالأرقام",
                "قصة",
                "ويكيبيديا",
                "بالإنجليزية",
                "Truth",
                "in",
                "Numbers",
                "The",
                "Wikipedia",
                "Story",
                "سيتم",
                "إطلاقه",
                "في",
                "2008"
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testChinese() {
        val reader = StringReader("我是中国人。 １２３４ Ｔｅｓｔｓ ")
        val stream: Tokenizer = tokenizerFactory("UAX29URLEmail").create(newAttributeFactory())
        stream.setReader(reader)
        assertTokenStreamContents(stream, arrayOf("我", "是", "中", "国", "人", "１２３４", "Ｔｅｓｔｓ"))
    }

    @Test
    @Throws(Exception::class)
    fun testKorean() {
        val reader = StringReader("안녕하세요 한글입니다")
        val stream: Tokenizer = tokenizerFactory("UAX29URLEmail").create(newAttributeFactory())
        stream.setReader(reader)
        assertTokenStreamContents(stream, arrayOf("안녕하세요", "한글입니다"))
    }

    @Test
    @Throws(Exception::class)
    fun testHyphen() {
        val reader = StringReader("some-dashed-phrase")
        val stream: Tokenizer = tokenizerFactory("UAX29URLEmail").create(newAttributeFactory())
        stream.setReader(reader)
        assertTokenStreamContents(stream, arrayOf("some", "dashed", "phrase"))
    }

    // Test with some URLs from TestUAX29URLEmailTokenizer's
    // urls.from.random.text.with.urls.txt
    @Test
    @Throws(Exception::class)
    fun testURLs() {
        val textWithURLs =
            "http://johno.jsmf.net/knowhow/ngrams/index.php?table=en-dickens-word-2gram&paragraphs=50&length=200&no-ads=on\n" +
                " some extra\nWords thrown in here. " +
                "http://c5-3486.bisynxu.FR/aI.YnNms/" +
                " samba Halta gamba " +
                "ftp://119.220.152.185/JgJgdZ/31aW5c/viWlfQSTs5/1c8U5T/ih5rXx/YfUJ/xBW1uHrQo6.R\n" +
                "M19nq.0URV4A.Me.CC/mj0kgt6hue/dRXv8YVLOw9v/CIOqb\n" +
                "Https://yu7v33rbt.vC6U3.XN--KPRW13D/y%4fMSzkGFlm/wbDF4m" +
                " inter Locutio " +
                "[c2d4::]/%471j5l/j3KFN%AAAn/Fip-NisKH/\n" +
                "file:///aXvSZS34is/eIgM8s~U5dU4Ifd%c7" +
                " blah Sirrah woof " +
                "http://[a42:a7b6::]/qSmxSUU4z/%52qVl4"
        val reader = StringReader(textWithURLs)
        val stream: Tokenizer = tokenizerFactory("UAX29URLEmail").create(newAttributeFactory())
        stream.setReader(reader)
        assertTokenStreamContents(
            stream,
            arrayOf(
                "http://johno.jsmf.net/knowhow/ngrams/index.php?table=en-dickens-word-2gram&paragraphs=50&length=200&no-ads=on",
                "some",
                "extra",
                "Words",
                "thrown",
                "in",
                "here",
                "http://c5-3486.bisynxu.FR/aI.YnNms/",
                "samba",
                "Halta",
                "gamba",
                "ftp://119.220.152.185/JgJgdZ/31aW5c/viWlfQSTs5/1c8U5T/ih5rXx/YfUJ/xBW1uHrQo6.R",
                "M19nq.0URV4A.Me.CC/mj0kgt6hue/dRXv8YVLOw9v/CIOqb",
                "Https://yu7v33rbt.vC6U3.XN--KPRW13D/y%4fMSzkGFlm/wbDF4m",
                "inter",
                "Locutio",
                "[c2d4::]/%471j5l/j3KFN%AAAn/Fip-NisKH/",
                "file:///aXvSZS34is/eIgM8s~U5dU4Ifd%c7",
                "blah",
                "Sirrah",
                "woof",
                "http://[a42:a7b6::]/qSmxSUU4z/%52qVl4"
            )
        )
    }

    // Test with some emails from TestUAX29URLEmailTokenizer's
    // email.addresses.from.random.text.with.email.addresses.txt
    @Test
    @Throws(Exception::class)
    fun testEmails() {
        val textWithEmails =
            " some extra\nWords thrown in here. " +
                "dJ8ngFi@avz13m.CC\n" +
                "kU-l6DS@[082.015.228.189]\n" +
                "\"%U\u0012@?\\B\"@Fl2d.md" +
                " samba Halta gamba " +
                "Bvd#@tupjv.sn\n" +
                "SBMm0Nm.oyk70.rMNdd8k.#ru3LI.gMMLBI.0dZRD4d.RVK2nY@au58t.B13albgy4u.mt\n" +
                "~+Kdz@3mousnl.SE\n" +
                " inter Locutio " +
                "C'ts`@Vh4zk.uoafcft-dr753x4odt04q.UY\n" +
                "}0tzWYDBuy@cSRQAABB9B.7c8xawf75-cyo.PM" +
                " blah Sirrah woof " +
                "lMahAA.j/5.RqUjS745.DtkcYdi@d2-4gb-l6.ae\n" +
                "lv'p@tqk.vj5s0tgl.0dlu7su3iyiaz.dqso.494.3hb76.XN--MGBAAM7A8H"
        val reader = StringReader(textWithEmails)
        val stream: Tokenizer = tokenizerFactory("UAX29URLEmail").create(newAttributeFactory())
        stream.setReader(reader)
        assertTokenStreamContents(
            stream,
            arrayOf(
                "some",
                "extra",
                "Words",
                "thrown",
                "in",
                "here",
                "dJ8ngFi@avz13m.CC",
                "kU-l6DS@[082.015.228.189]",
                "\"%U\u0012@?\\B\"@Fl2d.md",
                "samba",
                "Halta",
                "gamba",
                "Bvd#@tupjv.sn",
                "SBMm0Nm.oyk70.rMNdd8k.#ru3LI.gMMLBI.0dZRD4d.RVK2nY@au58t.B13albgy4u.mt",
                "~+Kdz@3mousnl.SE",
                "inter",
                "Locutio",
                "C'ts`@Vh4zk.uoafcft-dr753x4odt04q.UY",
                "}0tzWYDBuy@cSRQAABB9B.7c8xawf75-cyo.PM",
                "blah",
                "Sirrah",
                "woof",
                "lMahAA.j/5.RqUjS745.DtkcYdi@d2-4gb-l6.ae",
                "lv'p@tqk.vj5s0tgl.0dlu7su3iyiaz.dqso.494.3hb76.XN--MGBAAM7A8H"
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testMaxTokenLength() {
        val builder = StringBuilder()
        for (i in 0..<100) {
            builder.append("abcdefg") // 7 * 100 = 700 char "word"
        }
        val longWord = builder.toString()
        val content = "one two three $longWord four five six"
        val reader = StringReader(content)
        val stream = tokenizerFactory("UAX29URLEmail", "maxTokenLength", "1000").create(newAttributeFactory())
        stream.setReader(reader)
        assertTokenStreamContents(stream, arrayOf("one", "two", "three", longWord, "four", "five", "six"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            tokenizerFactory("UAX29URLEmail", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)
    }

    @Test
    @Throws(Exception::class)
    fun testIllegalArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            tokenizerFactory("UAX29URLEmail", "maxTokenLength", "-1").create()
        }
        assertTrue(expected.message?.contains("maxTokenLength must be greater than zero") == true)
    }
}
