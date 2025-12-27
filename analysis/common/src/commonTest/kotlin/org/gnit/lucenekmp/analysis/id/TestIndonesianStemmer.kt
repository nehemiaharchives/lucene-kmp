package org.gnit.lucenekmp.analysis.id

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Tests [IndonesianStemmer]. */
class TestIndonesianStemmer : BaseTokenStreamTestCase() {
    private lateinit var a: Analyzer
    private lateinit var b: Analyzer

    @BeforeTest
    fun setUp() {
        a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.KEYWORD, false)
                return TokenStreamComponents(tokenizer, IndonesianStemFilter(tokenizer))
            }
        }
        b = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.KEYWORD, false)
                return TokenStreamComponents(tokenizer, IndonesianStemFilter(tokenizer, false))
            }
        }
    }

    @AfterTest
    fun tearDown() {
        IOUtils.close(a, b)
    }

    /** Some examples from the paper. */
    @Test
    @Throws(IOException::class)
    fun testExamples() {
        checkOneTerm(a, "bukukah", "buku")
        checkOneTerm(a, "adalah", "ada")
        checkOneTerm(a, "bukupun", "buku")
        checkOneTerm(a, "bukuku", "buku")
        checkOneTerm(a, "bukumu", "buku")
        checkOneTerm(a, "bukunya", "buku")
        checkOneTerm(a, "mengukur", "ukur")
        checkOneTerm(a, "menyapu", "sapu")
        checkOneTerm(a, "menduga", "duga")
        checkOneTerm(a, "menuduh", "uduh")
        checkOneTerm(a, "membaca", "baca")
        checkOneTerm(a, "merusak", "rusak")
        checkOneTerm(a, "pengukur", "ukur")
        checkOneTerm(a, "penyapu", "sapu")
        checkOneTerm(a, "penduga", "duga")
        checkOneTerm(a, "pembaca", "baca")
        checkOneTerm(a, "diukur", "ukur")
        checkOneTerm(a, "tersapu", "sapu")
        checkOneTerm(a, "kekasih", "kasih")
        checkOneTerm(a, "berlari", "lari")
        checkOneTerm(a, "belajar", "ajar")
        checkOneTerm(a, "bekerja", "kerja")
        checkOneTerm(a, "perjelas", "jelas")
        checkOneTerm(a, "pelajar", "ajar")
        checkOneTerm(a, "pekerja", "kerja")
        checkOneTerm(a, "tarikkan", "tarik")
        checkOneTerm(a, "ambilkan", "ambil")
        checkOneTerm(a, "mengambilkan", "ambil")
        checkOneTerm(a, "makanan", "makan")
        checkOneTerm(a, "janjian", "janji")
        checkOneTerm(a, "perjanjian", "janji")
        checkOneTerm(a, "tandai", "tanda")
        checkOneTerm(a, "dapati", "dapat")
        checkOneTerm(a, "mendapati", "dapat")
        checkOneTerm(a, "pantai", "panta")
    }

    /** Some detailed analysis examples (that might not be the best). */
    @Test
    @Throws(IOException::class)
    fun testIRExamples() {
        checkOneTerm(a, "penyalahgunaan", "salahguna")
        checkOneTerm(a, "menyalahgunakan", "salahguna")
        checkOneTerm(a, "disalahgunakan", "salahguna")

        checkOneTerm(a, "pertanggungjawaban", "tanggungjawab")
        checkOneTerm(a, "mempertanggungjawabkan", "tanggungjawab")
        checkOneTerm(a, "dipertanggungjawabkan", "tanggungjawab")

        checkOneTerm(a, "pelaksanaan", "laksana")
        checkOneTerm(a, "pelaksana", "laksana")
        checkOneTerm(a, "melaksanakan", "laksana")
        checkOneTerm(a, "dilaksanakan", "laksana")

        checkOneTerm(a, "melibatkan", "libat")
        checkOneTerm(a, "terlibat", "libat")

        checkOneTerm(a, "penculikan", "culik")
        checkOneTerm(a, "menculik", "culik")
        checkOneTerm(a, "diculik", "culik")
        checkOneTerm(a, "penculik", "culik")

        checkOneTerm(a, "perubahan", "ubah")
        checkOneTerm(a, "peledakan", "ledak")
        checkOneTerm(a, "penanganan", "tangan")
        checkOneTerm(a, "kepolisian", "polisi")
        checkOneTerm(a, "kenaikan", "naik")
        checkOneTerm(a, "bersenjata", "senjata")
        checkOneTerm(a, "penyelewengan", "seleweng")
        checkOneTerm(a, "kecelakaan", "celaka")
    }

    /** Test stemming only inflectional suffixes. */
    @Test
    @Throws(IOException::class)
    fun testInflectionalOnly() {
        checkOneTerm(b, "bukunya", "buku")
        checkOneTerm(b, "bukukah", "buku")
        checkOneTerm(b, "bukunyakah", "buku")
        checkOneTerm(b, "dibukukannya", "dibukukan")
    }

    @Test
    @Throws(IOException::class)
    fun testShouldntStem() {
        checkOneTerm(a, "bersenjata", "senjata")
        checkOneTerm(a, "bukukah", "buku")
        checkOneTerm(a, "gigi", "gigi")
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, IndonesianStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
