package org.gnit.lucenekmp.analysis.ms

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.StopwordAnalyzerBase
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.analysis.core.DecimalDigitFilter
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException

/** Analyzer for Malay. */
class MalayAnalyzer : StopwordAnalyzerBase {
    private val stemExclusionSet: CharArraySet

    /** Builds an analyzer with the given stop words. */
    constructor(stopwords: CharArraySet, stemExclusionSet: CharArraySet) : super(stopwords) {
        this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet))
    }

    /** Builds an analyzer with the given stop words. */
    constructor(stopwords: CharArraySet) : this(stopwords, CharArraySet.EMPTY_SET)

    /** Builds an analyzer with the default stop words: [DEFAULT_STOPWORD_FILE]. */
    constructor() : this(DefaultSetHolder.DEFAULT_STOP_SET)

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val source: Tokenizer = StandardTokenizer()
        var result: TokenStream = LowerCaseFilter(source)
        result = DecimalDigitFilter(result)
        if (!stemExclusionSet.isEmpty()) {
            result = SetKeywordMarkerFilter(result, stemExclusionSet)
        }
        result = MalayNormalizationFilter(result)
        result = StopFilter(result, stopwords)
        result = MalayStemFilter(result)
        return TokenStreamComponents(source, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        var result: TokenStream = LowerCaseFilter(`in`)
        result = DecimalDigitFilter(result)
        result = MalayNormalizationFilter(result)
        return result
    }

    companion object {
        /** File containing default Malay stopwords. */
        const val DEFAULT_STOPWORD_FILE: String = "stopwords.txt"

        private const val STOPWORDS_COMMENT = "#"

        /**
         * Returns an unmodifiable instance of the default stop-words set.
         */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private const val DEFAULT_STOPWORD_DATA: String = """
# Source: stopwords-iso (ms) local clone: ../stopwords-ms/stopwords-ms.txt
abdul
abdullah
acara
ada
adalah
ahmad
air
akan
akhbar
akhir
aktiviti
alam
amat
amerika
anak
anggota
antara
antarabangsa
apa
apabila
april
as
asas
asean
asia
asing
atas
atau
australia
awal
awam
bagaimanapun
bagi
bahagian
bahan
baharu
bahawa
baik
bandar
bank
banyak
barangan
baru
baru-baru
bawah
beberapa
bekas
beliau
belum
berada
berakhir
berbanding
berdasarkan
berharap
berikutan
berjaya
berjumlah
berkaitan
berkata
berkenaan
berlaku
bermula
bernama
bernilai
bersama
berubah
besar
bhd
bidang
bilion
bn
boleh
bukan
bulan
bursa
cadangan
china
dagangan
dalam
dan
dana
dapat
dari
daripada
dasar
datang
datuk
demikian
dengan
depan
derivatives
dewan
di
diadakan
dibuka
dicatatkan
dijangka
diniagakan
dis
disember
ditutup
dolar
dr
dua
dunia
ekonomi
eksekutif
eksport
empat
enam
faedah
feb
global
hadapan
hanya
harga
hari
hasil
hingga
hubungan
ia
iaitu
ialah
indeks
india
indonesia
industri
ini
islam
isnin
isu
itu
jabatan
jalan
jan
jawatan
jawatankuasa
jepun
jika
jualan
juga
julai
jumaat
jumlah
jun
juta
kadar
kalangan
kali
kami
kata
katanya
kaunter
kawasan
ke
keadaan
kecil
kedua
kedua-dua
kedudukan
kekal
kementerian
kemudahan
kenaikan
kenyataan
kepada
kepentingan
keputusan
kerajaan
kerana
kereta
kerja
kerjasama
kes
keselamatan
keseluruhan
kesihatan
ketika
ketua
keuntungan
kewangan
khamis
kini
kira-kira
kita
klci
klibor
komposit
kontrak
kos
kuala
kuasa
kukuh
kumpulan
lagi
lain
langkah
laporan
lebih
lepas
lima
lot
luar
lumpur
mac
mahkamah
mahu
majlis
makanan
maklumat
malam
malaysia
mana
manakala
masa
masalah
masih
masing-masing
masyarakat
mata
media
mei
melalui
melihat
memandangkan
memastikan
membantu
membawa
memberi
memberikan
membolehkan
membuat
mempunyai
menambah
menarik
menawarkan
mencapai
mencatatkan
mendapat
mendapatkan
menerima
menerusi
mengadakan
mengambil
mengenai
menggalakkan
menggunakan
mengikut
mengumumkan
mengurangkan
meningkat
meningkatkan
menjadi
menjelang
menokok
menteri
menunjukkan
menurut
menyaksikan
menyediakan
mereka
merosot
merupakan
mesyuarat
minat
minggu
minyak
modal
mohd
mudah
mungkin
naik
najib
nasional
negara
negara-negara
negeri
niaga
nilai
nov
ogos
okt
oleh
operasi
orang
pada
pagi
paling
pameran
papan
para
paras
parlimen
parti
pasaran
pasukan
pegawai
pejabat
pekerja
pelabur
pelaburan
pelancongan
pelanggan
pelbagai
peluang
pembangunan
pemberita
pembinaan
pemimpin
pendapatan
pendidikan
penduduk
penerbangan
pengarah
pengeluaran
pengerusi
pengguna
pengurusan
peniaga
peningkatan
penting
peratus
perdagangan
perdana
peringkat
perjanjian
perkara
perkhidmatan
perladangan
perlu
permintaan
perniagaan
persekutuan
persidangan
pertama
pertubuhan
pertumbuhan
perusahaan
peserta
petang
pihak
pilihan
pinjaman
polis
politik
presiden
prestasi
produk
program
projek
proses
proton
pukul
pula
pusat
rabu
rakan
rakyat
ramai
rantau
raya
rendah
ringgit
rumah
sabah
sahaja
saham
sama
sarawak
satu
sawit
saya
sdn
sebagai
sebahagian
sebanyak
sebarang
sebelum
sebelumnya
sebuah
secara
sedang
segi
sehingga
sejak
sekarang
sektor
sekuriti
selain
selama
selasa
selatan
selepas
seluruh
semakin
semalam
semasa
sementara
semua
semula
sen
sendiri
seorang
sepanjang
seperti
sept
september
serantau
seri
serta
sesi
setiap
setiausaha
sidang
singapura
sini
sistem
sokongan
sri
sudah
sukan
suku
sumber
supaya
susut
syarikat
syed
tahap
tahun
tan
tanah
tanpa
tawaran
teknologi
telah
tempat
tempatan
tempoh
tenaga
tengah
tentang
terbaik
terbang
terbesar
terbuka
terdapat
terhadap
termasuk
tersebut
terus
tetapi
thailand
tiada
tidak
tiga
timbalan
timur
tindakan
tinggi
tun
tunai
turun
turut
umno
unit
untuk
untung
urus
usaha
utama
walaupun
wang
wanita
wilayah
yang
"""

        private object DefaultSetHolder {
            val DEFAULT_STOP_SET: CharArraySet

            init {
                try {
                    DEFAULT_STOP_SET = WordlistLoader.getWordSet(StringReader(DEFAULT_STOPWORD_DATA), STOPWORDS_COMMENT)
                } catch (ex: IOException) {
                    throw UncheckedIOException("Unable to load default stopword set", ex)
                }
            }
        }
    }
}
