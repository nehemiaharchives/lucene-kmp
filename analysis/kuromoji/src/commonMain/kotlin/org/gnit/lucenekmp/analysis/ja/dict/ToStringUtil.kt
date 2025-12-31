package org.gnit.lucenekmp.analysis.ja.dict

import okio.IOException

/** Utility class for english translations of morphological data, used only for debugging. */
object ToStringUtil {
    private val posTranslations: Map<String, String> = mapOf(
        "名詞" to "noun",
        "名詞-一般" to "noun-common",
        "名詞-固有名詞" to "noun-proper",
        "名詞-固有名詞-一般" to "noun-proper-misc",
        "名詞-固有名詞-人名" to "noun-proper-person",
        "名詞-固有名詞-人名-一般" to "noun-proper-person-misc",
        "名詞-固有名詞-人名-姓" to "noun-proper-person-surname",
        "名詞-固有名詞-人名-名" to "noun-proper-person-given_name",
        "名詞-固有名詞-組織" to "noun-proper-organization",
        "名詞-固有名詞-地域" to "noun-proper-place",
        "名詞-固有名詞-地域-一般" to "noun-proper-place-misc",
        "名詞-固有名詞-地域-国" to "noun-proper-place-country",
        "名詞-代名詞" to "noun-pronoun",
        "名詞-代名詞-一般" to "noun-pronoun-misc",
        "名詞-代名詞-縮約" to "noun-pronoun-contraction",
        "名詞-副詞可能" to "noun-adverbial",
        "名詞-サ変接続" to "noun-verbal",
        "名詞-形容動詞語幹" to "noun-adjective-base",
        "名詞-数" to "noun-numeric",
        "名詞-非自立" to "noun-affix",
        "名詞-非自立-一般" to "noun-affix-misc",
        "名詞-非自立-副詞可能" to "noun-affix-adverbial",
        "名詞-非自立-助動詞語幹" to "noun-affix-aux",
        "名詞-非自立-形容動詞語幹" to "noun-affix-adjective-base",
        "名詞-特殊" to "noun-special",
        "名詞-特殊-助動詞語幹" to "noun-special-aux",
        "名詞-接尾" to "noun-suffix",
        "名詞-接尾-一般" to "noun-suffix-misc",
        "名詞-接尾-人名" to "noun-suffix-person",
        "名詞-接尾-地域" to "noun-suffix-place",
        "名詞-接尾-サ変接続" to "noun-suffix-verbal",
        "名詞-接尾-助動詞語幹" to "noun-suffix-aux",
        "名詞-接尾-形容動詞語幹" to "noun-suffix-adjective-base",
        "名詞-接尾-副詞可能" to "noun-suffix-adverbial",
        "名詞-接尾-助数詞" to "noun-suffix-classifier",
        "名詞-接尾-特殊" to "noun-suffix-special",
        "名詞-接続詞的" to "noun-suffix-conjunctive",
        "名詞-動詞非自立的" to "noun-verbal_aux",
        "名詞-引用文字列" to "noun-quotation",
        "名詞-ナイ形容詞語幹" to "noun-nai_adjective",
        "接頭詞" to "prefix",
        "接頭詞-名詞接続" to "prefix-nominal",
        "接頭詞-動詞接続" to "prefix-verbal",
        "接頭詞-形容詞接続" to "prefix-adjectival",
        "接頭詞-数接続" to "prefix-numerical",
        "動詞" to "verb",
        "動詞-自立" to "verb-main",
        "動詞-非自立" to "verb-auxiliary",
        "動詞-接尾" to "verb-suffix",
        "形容詞" to "adjective",
        "形容詞-自立" to "adjective-main",
        "形容詞-非自立" to "adjective-auxiliary",
        "形容詞-接尾" to "adjective-suffix",
        "副詞" to "adverb",
        "副詞-一般" to "adverb-misc",
        "副詞-助詞類接続" to "adverb-particle_conjunction",
        "連体詞" to "adnominal",
        "接続詞" to "conjunction",
        "助詞" to "particle",
        "助詞-格助詞" to "particle-case",
        "助詞-格助詞-一般" to "particle-case-misc",
        "助詞-格助詞-引用" to "particle-case-quote",
        "助詞-格助詞-連語" to "particle-case-compound",
        "助詞-接続助詞" to "particle-conjunctive",
        "助詞-係助詞" to "particle-dependency",
        "助詞-副助詞" to "particle-adverbial",
        "助詞-間投助詞" to "particle-interjective",
        "助詞-並立助詞" to "particle-coordinate",
        "助詞-終助詞" to "particle-final",
        "助詞-副助詞／並立助詞／終助詞" to "particle-adverbial/conjunctive/final",
        "助詞-連体化" to "particle-adnominalizer",
        "助詞-副詞化" to "particle-adnominalizer",
        "助詞-特殊" to "particle-special",
        "助動詞" to "auxiliary-verb",
        "感動詞" to "interjection",
        "記号" to "symbol",
        "記号-一般" to "symbol-misc",
        "記号-句点" to "symbol-period",
        "記号-読点" to "symbol-comma",
        "記号-空白" to "symbol-space",
        "記号-括弧開" to "symbol-open_bracket",
        "記号-括弧閉" to "symbol-close_bracket",
        "記号-アルファベット" to "symbol-alphabetic",
        "その他" to "other",
        "その他-間投" to "other-interjection",
        "フィラー" to "filler",
        "非言語音" to "non-verbal",
        "語断片" to "fragment",
        "未知語" to "unknown"
    )

    /** Get the english form of a POS tag */
    fun getPOSTranslation(s: String): String? = posTranslations[s]

    private val inflTypeTranslations: Map<String, String> = mapOf(
        "*" to "*",
        "形容詞・アウオ段" to "adj-group-a-o-u",
        "形容詞・イ段" to "adj-group-i",
        "形容詞・イイ" to "adj-group-ii",
        "不変化型" to "non-inflectional",
        "特殊・タ" to "special-da",
        "特殊・ダ" to "special-ta",
        "文語・ゴトシ" to "classical-gotoshi",
        "特殊・ジャ" to "special-ja",
        "特殊・ナイ" to "special-nai",
        "五段・ラ行特殊" to "5-row-cons-r-special",
        "特殊・ヌ" to "special-nu",
        "文語・キ" to "classical-ki",
        "特殊・タイ" to "special-tai",
        "文語・ベシ" to "classical-beshi",
        "特殊・ヤ" to "special-ya",
        "文語・マジ" to "classical-maji",
        "下二・タ行" to "2-row-lower-cons-t",
        "特殊・デス" to "special-desu",
        "特殊・マス" to "special-masu",
        "五段・ラ行アル" to "5-row-aru",
        "文語・ナリ" to "classical-nari",
        "文語・リ" to "classical-ri",
        "文語・ケリ" to "classical-keri",
        "文語・ル" to "classical-ru",
        "五段・カ行イ音便" to "5-row-cons-k-i-onbin",
        "五段・サ行" to "5-row-cons-s",
        "一段" to "1-row",
        "五段・ワ行促音便" to "5-row-cons-w-cons-onbin",
        "五段・マ行" to "5-row-cons-m",
        "五段・タ行" to "5-row-cons-t",
        "五段・ラ行" to "5-row-cons-r",
        "サ変・−スル" to "irregular-suffix-suru",
        "五段・ガ行" to "5-row-cons-g",
        "サ変・−ズル" to "irregular-suffix-zuru",
        "五段・バ行" to "5-row-cons-b",
        "五段・ワ行ウ音便" to "5-row-cons-w-u-onbin",
        "下二・ダ行" to "2-row-lower-cons-d",
        "五段・カ行促音便ユク" to "5-row-cons-k-cons-onbin-yuku",
        "上二・ダ行" to "2-row-upper-cons-d",
        "五段・カ行促音便" to "5-row-cons-k-cons-onbin",
        "一段・得ル" to "1-row-eru",
        "四段・タ行" to "4-row-cons-t",
        "五段・ナ行" to "5-row-cons-n",
        "下二・ハ行" to "2-row-lower-cons-h",
        "四段・ハ行" to "4-row-cons-h",
        "四段・バ行" to "4-row-cons-b",
        "サ変・スル" to "irregular-suru",
        "上二・ハ行" to "2-row-upper-cons-h",
        "下二・マ行" to "2-row-lower-cons-m",
        "四段・サ行" to "4-row-cons-s",
        "下二・ガ行" to "2-row-lower-cons-g",
        "カ変・来ル" to "kuru-kanji",
        "一段・クレル" to "1-row-kureru",
        "下二・得" to "2-row-lower-u",
        "カ変・クル" to "kuru-kana",
        "ラ変" to "irregular-cons-r",
        "下二・カ行" to "2-row-lower-cons-k"
    )

    /** Get the english form of inflection type */
    fun getInflectionTypeTranslation(s: String): String? = inflTypeTranslations[s]

    private val inflFormTranslations: Map<String, String> = mapOf(
        "*" to "*",
        "基本形" to "base",
        "文語基本形" to "classical-base",
        "未然ヌ接続" to "imperfective-nu-connection",
        "未然ウ接続" to "imperfective-u-connection",
        "連用タ接続" to "conjunctive-ta-connection",
        "連用テ接続" to "conjunctive-te-connection",
        "連用ゴザイ接続" to "conjunctive-gozai-connection",
        "体言接続" to "uninflected-connection",
        "仮定形" to "subjunctive",
        "命令ｅ" to "imperative-e",
        "仮定縮約１" to "conditional-contracted-1",
        "仮定縮約２" to "conditional-contracted-2",
        "ガル接続" to "garu-connection",
        "未然形" to "imperfective",
        "連用形" to "conjunctive",
        "音便基本形" to "onbin-base",
        "連用デ接続" to "conjunctive-de-connection",
        "未然特殊" to "imperfective-special",
        "命令ｉ" to "imperative-i",
        "連用ニ接続" to "conjunctive-ni-connection",
        "命令ｙｏ" to "imperative-yo",
        "体言接続特殊" to "adnominal-special",
        "命令ｒｏ" to "imperative-ro",
        "体言接続特殊２" to "uninflected-special-connection-2",
        "未然レル接続" to "imperfective-reru-connection",
        "現代基本形" to "modern-base",
        "基本形-促音便" to "base-onbin"
    )

    /** Get the english form of inflected form */
    fun getInflectedFormTranslation(s: String): String? = inflFormTranslations[s]

    /** Romanize katakana with modified hepburn */
    fun getRomanization(s: String): String {
        val out = StringBuilder()
        try {
            getRomanization(out, s)
        } catch (bogus: IOException) {
            throw RuntimeException(bogus)
        }
        return out.toString()
    }

    /** Romanize katakana with modified hepburn */
    @Throws(IOException::class)
    fun getRomanization(builder: Appendable, s: CharSequence) {
        val len = s.length
        var i = 0
        while (i < len) {
            val ch = s[i]
            val ch2 = if (i < len - 1) s[i + 1] else 0.toChar()
            val ch3 = if (i < len - 2) s[i + 2] else 0.toChar()

            when (ch) {
                'ッ' -> {
                    when (ch2) {
                        'カ', 'キ', 'ク', 'ケ', 'コ' -> builder.append('k')
                        'サ', 'シ', 'ス', 'セ', 'ソ' -> builder.append('s')
                        'タ', 'チ', 'ツ', 'テ', 'ト' -> builder.append('t')
                        'パ', 'ピ', 'プ', 'ペ', 'ポ' -> builder.append('p')
                    }
                }
                'ア' -> builder.append('a')
                'イ' -> {
                    if (ch2 == 'ィ') {
                        builder.append("yi")
                        i++
                    } else if (ch2 == 'ェ') {
                        builder.append("ye")
                        i++
                    } else {
                        builder.append('i')
                    }
                }
                'ウ' -> {
                    when (ch2) {
                        'ァ' -> { builder.append("wa"); i++ }
                        'ィ' -> { builder.append("wi"); i++ }
                        'ゥ' -> { builder.append("wu"); i++ }
                        'ェ' -> { builder.append("we"); i++ }
                        'ォ' -> { builder.append("wo"); i++ }
                        'ュ' -> { builder.append("wyu"); i++ }
                        else -> builder.append('u')
                    }
                }
                'エ' -> builder.append('e')
                'オ' -> {
                    if (ch2 == 'ウ') { builder.append('ō'); i++ } else builder.append('o')
                }
                'カ' -> builder.append("ka")
                'キ' -> {
                    if (ch2 == 'ョ' && ch3 == 'ウ') { builder.append("kyō"); i += 2 }
                    else if (ch2 == 'ュ' && ch3 == 'ウ') { builder.append("kyū"); i += 2 }
                    else if (ch2 == 'ャ') { builder.append("kya"); i++ }
                    else if (ch2 == 'ョ') { builder.append("kyo"); i++ }
                    else if (ch2 == 'ュ') { builder.append("kyu"); i++ }
                    else if (ch2 == 'ェ') { builder.append("kye"); i++ }
                    else builder.append("ki")
                }
                'ク' -> {
                    when (ch2) {
                        'ァ' -> { builder.append("kwa"); i++ }
                        'ィ' -> { builder.append("kwi"); i++ }
                        'ェ' -> { builder.append("kwe"); i++ }
                        'ォ' -> { builder.append("kwo"); i++ }
                        'ヮ' -> { builder.append("kwa"); i++ }
                        else -> builder.append("ku")
                    }
                }
                'ケ' -> builder.append("ke")
                'コ' -> {
                    if (ch2 == 'ウ') { builder.append("kō"); i++ } else builder.append("ko")
                }
                'サ' -> builder.append("sa")
                'シ' -> {
                    if (ch2 == 'ョ' && ch3 == 'ウ') { builder.append("shō"); i += 2 }
                    else if (ch2 == 'ュ' && ch3 == 'ウ') { builder.append("shū"); i += 2 }
                    else if (ch2 == 'ャ') { builder.append("sha"); i++ }
                    else if (ch2 == 'ョ') { builder.append("sho"); i++ }
                    else if (ch2 == 'ュ') { builder.append("shu"); i++ }
                    else if (ch2 == 'ェ') { builder.append("she"); i++ }
                    else builder.append("shi")
                }
                'ス' -> {
                    if (ch2 == 'ィ') { builder.append("si"); i++ } else builder.append("su")
                }
                'セ' -> builder.append("se")
                'ソ' -> {
                    if (ch2 == 'ウ') { builder.append("sō"); i++ } else builder.append("so")
                }
                'タ' -> builder.append("ta")
                'チ' -> {
                    if (ch2 == 'ョ' && ch3 == 'ウ') { builder.append("chō"); i += 2 }
                    else if (ch2 == 'ュ' && ch3 == 'ウ') { builder.append("chū"); i += 2 }
                    else if (ch2 == 'ャ') { builder.append("cha"); i++ }
                    else if (ch2 == 'ョ') { builder.append("cho"); i++ }
                    else if (ch2 == 'ュ') { builder.append("chu"); i++ }
                    else if (ch2 == 'ェ') { builder.append("che"); i++ }
                    else builder.append("chi")
                }
                'ツ' -> {
                    if (ch2 == 'ァ') { builder.append("tsa"); i++ }
                    else if (ch2 == 'ィ') { builder.append("tsi"); i++ }
                    else if (ch2 == 'ェ') { builder.append("tse"); i++ }
                    else if (ch2 == 'ォ') { builder.append("tso"); i++ }
                    else if (ch2 == 'ュ') { builder.append("tsyu"); i++ }
                    else builder.append("tsu")
                }
                'テ' -> {
                    if (ch2 == 'ィ') { builder.append("ti"); i++ }
                    else if (ch2 == 'ゥ') { builder.append("tu"); i++ }
                    else if (ch2 == 'ュ') { builder.append("tyu"); i++ }
                    else builder.append("te")
                }
                'ト' -> {
                    if (ch2 == 'ウ') { builder.append("tō"); i++ }
                    else if (ch2 == 'ゥ') { builder.append("tu"); i++ }
                    else builder.append("to")
                }
                'ナ' -> builder.append("na")
                'ニ' -> {
                    if (ch2 == 'ョ' && ch3 == 'ウ') { builder.append("nyō"); i += 2 }
                    else if (ch2 == 'ュ' && ch3 == 'ウ') { builder.append("nyū"); i += 2 }
                    else if (ch2 == 'ャ') { builder.append("nya"); i++ }
                    else if (ch2 == 'ョ') { builder.append("nyo"); i++ }
                    else if (ch2 == 'ュ') { builder.append("nyu"); i++ }
                    else if (ch2 == 'ェ') { builder.append("nye"); i++ }
                    else builder.append("ni")
                }
                'ヌ' -> builder.append("nu")
                'ネ' -> builder.append("ne")
                'ノ' -> {
                    if (ch2 == 'ウ') { builder.append("nō"); i++ } else builder.append("no")
                }
                'ハ' -> builder.append("ha")
                'ヒ' -> {
                    if (ch2 == 'ョ' && ch3 == 'ウ') { builder.append("hyō"); i += 2 }
                    else if (ch2 == 'ュ' && ch3 == 'ウ') { builder.append("hyū"); i += 2 }
                    else if (ch2 == 'ャ') { builder.append("hya"); i++ }
                    else if (ch2 == 'ョ') { builder.append("hyo"); i++ }
                    else if (ch2 == 'ュ') { builder.append("hyu"); i++ }
                    else if (ch2 == 'ェ') { builder.append("hye"); i++ }
                    else builder.append("hi")
                }
                'フ' -> {
                    if (ch2 == 'ャ') { builder.append("fya"); i++ }
                    else if (ch2 == 'ュ') { builder.append("fyu"); i++ }
                    else if (ch2 == 'ィ' && ch3 == 'ェ') { builder.append("fye"); i += 2 }
                    else if (ch2 == 'ョ') { builder.append("fyo"); i++ }
                    else if (ch2 == 'ァ') { builder.append("fa"); i++ }
                    else if (ch2 == 'ィ') { builder.append("fi"); i++ }
                    else if (ch2 == 'ェ') { builder.append("fe"); i++ }
                    else if (ch2 == 'ォ') { builder.append("fo"); i++ }
                    else builder.append("fu")
                }
                'ヘ' -> builder.append("he")
                'ホ' -> {
                    if (ch2 == 'ウ') { builder.append("hō"); i++ }
                    else if (ch2 == 'ゥ') { builder.append("hu"); i++ }
                    else builder.append("ho")
                }
                'マ' -> builder.append("ma")
                'ミ' -> {
                    if (ch2 == 'ョ' && ch3 == 'ウ') { builder.append("myō"); i += 2 }
                    else if (ch2 == 'ュ' && ch3 == 'ウ') { builder.append("myū"); i += 2 }
                    else if (ch2 == 'ャ') { builder.append("mya"); i++ }
                    else if (ch2 == 'ョ') { builder.append("myo"); i++ }
                    else if (ch2 == 'ュ') { builder.append("myu"); i++ }
                    else if (ch2 == 'ェ') { builder.append("mye"); i++ }
                    else builder.append("mi")
                }
                'ム' -> builder.append("mu")
                'メ' -> builder.append("me")
                'モ' -> {
                    if (ch2 == 'ウ') { builder.append("mō"); i++ } else builder.append("mo")
                }
                'ヤ' -> builder.append("ya")
                'ユ' -> builder.append("yu")
                'ヨ' -> {
                    if (ch2 == 'ウ') { builder.append("yō"); i++ } else builder.append("yo")
                }
                'ラ' -> {
                    if (ch2 == '゜') { builder.append("la"); i++ } else builder.append("ra")
                }
                'リ' -> {
                    if (ch2 == 'ョ' && ch3 == 'ウ') { builder.append("ryō"); i += 2 }
                    else if (ch2 == 'ュ' && ch3 == 'ウ') { builder.append("ryū"); i += 2 }
                    else if (ch2 == 'ャ') { builder.append("rya"); i++ }
                    else if (ch2 == 'ョ') { builder.append("ryo"); i++ }
                    else if (ch2 == 'ュ') { builder.append("ryu"); i++ }
                    else if (ch2 == 'ェ') { builder.append("rye"); i++ }
                    else if (ch2 == '゜') { builder.append("li"); i++ }
                    else builder.append("ri")
                }
                'ル' -> {
                    if (ch2 == '゜') { builder.append("lu"); i++ } else builder.append("ru")
                }
                'レ' -> {
                    if (ch2 == '゜') { builder.append("le"); i++ } else builder.append("re")
                }
                'ロ' -> {
                    if (ch2 == 'ウ') { builder.append("rō"); i++ }
                    else if (ch2 == '゜') { builder.append("lo"); i++ }
                    else builder.append("ro")
                }
                'ワ' -> builder.append("wa")
                'ヰ' -> builder.append("i")
                'ヱ' -> builder.append("e")
                'ヲ' -> builder.append("o")
                'ン' -> {
                    when (ch2) {
                        'バ', 'ビ', 'ブ', 'ベ', 'ボ', 'パ', 'ピ', 'プ', 'ペ', 'ポ', 'マ', 'ミ', 'ム', 'メ', 'モ' ->
                            builder.append('m')
                        'ヤ', 'ユ', 'ヨ', 'ア', 'イ', 'ウ', 'エ', 'オ' ->
                            builder.append("n'")
                        else -> builder.append("n")
                    }
                }
                'ガ' -> builder.append("ga")
                'ギ' -> {
                    if (ch2 == 'ョ' && ch3 == 'ウ') { builder.append("gyō"); i += 2 }
                    else if (ch2 == 'ュ' && ch3 == 'ウ') { builder.append("gyū"); i += 2 }
                    else if (ch2 == 'ャ') { builder.append("gya"); i++ }
                    else if (ch2 == 'ョ') { builder.append("gyo"); i++ }
                    else if (ch2 == 'ュ') { builder.append("gyu"); i++ }
                    else if (ch2 == 'ェ') { builder.append("gye"); i++ }
                    else builder.append("gi")
                }
                'グ' -> {
                    when (ch2) {
                        'ァ' -> { builder.append("gwa"); i++ }
                        'ィ' -> { builder.append("gwi"); i++ }
                        'ェ' -> { builder.append("gwe"); i++ }
                        'ォ' -> { builder.append("gwo"); i++ }
                        'ヮ' -> { builder.append("gwa"); i++ }
                        else -> builder.append("gu")
                    }
                }
                'ゲ' -> builder.append("ge")
                'ゴ' -> {
                    if (ch2 == 'ウ') { builder.append("gō"); i++ } else builder.append("go")
                }
                'ザ' -> builder.append("za")
                'ジ' -> {
                    if (ch2 == 'ョ' && ch3 == 'ウ') { builder.append("jō"); i += 2 }
                    else if (ch2 == 'ュ' && ch3 == 'ウ') { builder.append("jū"); i += 2 }
                    else if (ch2 == 'ャ') { builder.append("ja"); i++ }
                    else if (ch2 == 'ョ') { builder.append("jo"); i++ }
                    else if (ch2 == 'ュ') { builder.append("ju"); i++ }
                    else if (ch2 == 'ェ') { builder.append("je"); i++ }
                    else builder.append("ji")
                }
                'ズ' -> {
                    if (ch2 == 'ィ') { builder.append("zi"); i++ } else builder.append("zu")
                }
                'ゼ' -> builder.append("ze")
                'ゾ' -> {
                    if (ch2 == 'ウ') { builder.append("zō"); i++ } else builder.append("zo")
                }
                'ダ' -> builder.append("da")
                'ヂ' -> {
                    if (ch2 == 'ョ' && ch3 == 'ウ') { builder.append("jō"); i += 2 }
                    else if (ch2 == 'ュ' && ch3 == 'ウ') { builder.append("jū"); i += 2 }
                    else if (ch2 == 'ャ') { builder.append("ja"); i++ }
                    else if (ch2 == 'ョ') { builder.append("jo"); i++ }
                    else if (ch2 == 'ュ') { builder.append("ju"); i++ }
                    else if (ch2 == 'ェ') { builder.append("je"); i++ }
                    else builder.append("ji")
                }
                'ヅ' -> builder.append("zu")
                'デ' -> {
                    if (ch2 == 'ィ') { builder.append("di"); i++ }
                    else if (ch2 == 'ュ') { builder.append("dyu"); i++ }
                    else builder.append("de")
                }
                'ド' -> {
                    if (ch2 == 'ウ') { builder.append("dō"); i++ }
                    else if (ch2 == 'ゥ') { builder.append("du"); i++ }
                    else builder.append("do")
                }
                'バ' -> builder.append("ba")
                'ビ' -> {
                    if (ch2 == 'ョ' && ch3 == 'ウ') { builder.append("byō"); i += 2 }
                    else if (ch2 == 'ュ' && ch3 == 'ウ') { builder.append("byū"); i += 2 }
                    else if (ch2 == 'ャ') { builder.append("bya"); i++ }
                    else if (ch2 == 'ョ') { builder.append("byo"); i++ }
                    else if (ch2 == 'ュ') { builder.append("byu"); i++ }
                    else if (ch2 == 'ェ') { builder.append("bye"); i++ }
                    else builder.append("bi")
                }
                'ブ' -> builder.append("bu")
                'ベ' -> builder.append("be")
                'ボ' -> {
                    if (ch2 == 'ウ') { builder.append("bō"); i++ } else builder.append("bo")
                }
                'パ' -> builder.append("pa")
                'ピ' -> {
                    if (ch2 == 'ョ' && ch3 == 'ウ') { builder.append("pyō"); i += 2 }
                    else if (ch2 == 'ュ' && ch3 == 'ウ') { builder.append("pyū"); i += 2 }
                    else if (ch2 == 'ャ') { builder.append("pya"); i++ }
                    else if (ch2 == 'ョ') { builder.append("pyo"); i++ }
                    else if (ch2 == 'ュ') { builder.append("pyu"); i++ }
                    else if (ch2 == 'ェ') { builder.append("pye"); i++ }
                    else builder.append("pi")
                }
                'プ' -> builder.append("pu")
                'ペ' -> builder.append("pe")
                'ポ' -> {
                    if (ch2 == 'ウ') { builder.append("pō"); i++ } else builder.append("po")
                }
                'ヷ' -> builder.append("va")
                'ヸ' -> builder.append("vi")
                'ヹ' -> builder.append("ve")
                'ヺ' -> builder.append("vo")
                'ヴ' -> {
                    if (ch2 == 'ィ' && ch3 == 'ェ') { builder.append("vye"); i += 2 }
                    else builder.append('v')
                }
                'ァ' -> builder.append('a')
                'ィ' -> builder.append('i')
                'ゥ' -> builder.append('u')
                'ェ' -> builder.append('e')
                'ォ' -> builder.append('o')
                'ヮ' -> builder.append("wa")
                'ャ' -> builder.append("ya")
                'ュ' -> builder.append("yu")
                'ョ' -> builder.append("yo")
                'ー' -> { }
                else -> builder.append(ch)
            }
            i++
        }
    }

}