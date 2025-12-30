package org.gnit.lucenekmp.analysis.ko

/**
 * Part of speech classification for Korean based on Sejong corpus classification.
 */
class POS {
    /** The type of the token. */
    enum class Type {
        /** A simple morpheme. */
        MORPHEME,

        /** Compound noun. */
        COMPOUND,

        /** Inflected token. */
        INFLECT,

        /** Pre-analysis token. */
        PREANALYSIS,
    }

    /** Part of speech tag for Korean based on Sejong corpus classification. */
    enum class Tag(val code: Int, private val desc: String) {
        /** Pre-final ending */
        EP(100, "Pre-final ending"),
        /** Sentence-closing ending */
        EF(101, "Sentence-closing ending"),
        /** Connective ending */
        EC(102, "Connective ending"),
        /** Nominal transformative ending */
        ETN(103, "Nominal transformative ending"),
        /** Adnominal form transformative ending */
        ETM(104, "Adnominal form transformative ending"),
        /** Interjection */
        IC(110, "Interjection"),
        /** Subject case marker */
        JKS(120, "Subject case marker"),
        /** Complement case marker */
        JKC(121, "Complement case marker"),
        /** Adnominal case marker */
        JKG(122, "Adnominal case marker"),
        /** Object case marker */
        JKO(123, "Object case marker"),
        /** Adverbial case marker */
        JKB(124, "Adverbial case marker"),
        /** Vocative case marker */
        JKV(125, "Vocative case marker"),
        /** Quotative case marker */
        JKQ(126, "Quotative case marker"),
        /** Auxiliary postpositional particle */
        JX(127, "Auxiliary postpositional particle"),
        /** Conjunctive postpositional particle */
        JC(128, "Conjunctive postpositional particle"),
        /** General Adverb */
        MAG(130, "General Adverb"),
        /** Conjunctive adverb */
        MAJ(131, "Conjunctive adverb"),
        /** Determiner */
        MM(140, "Modifier"),
        /** General Noun */
        NNG(150, "General Noun"),
        /** Proper Noun */
        NNP(151, "Proper Noun"),
        /** Dependent noun (following nouns) */
        NNB(152, "Dependent noun"),
        /** Dependent noun */
        NNBC(153, "Dependent noun"),
        /** Pronoun */
        NP(154, "Pronoun"),
        /** Numeral */
        NR(155, "Numeral"),
        /** Terminal punctuation (? ! .) */
        SF(160, "Terminal punctuation"),
        /** Chinese character */
        SH(161, "Chinese Characeter"),
        /** Foreign language */
        SL(162, "Foreign language"),
        /** Number */
        SN(163, "Number"),
        /** Space */
        SP(164, "Space"),
        /** Closing brackets */
        SSC(165, "Closing brackets"),
        /** Opening brackets */
        SSO(166, "Opening brackets"),
        /** Separator (· / :) */
        SC(167, "Separator"),
        /** Other symbol */
        SY(168, "Other symbol"),
        /** Ellipsis */
        SE(169, "Ellipsis"),
        /** Adjective */
        VA(170, "Adjective"),
        /** Negative designator */
        VCN(171, "Negative designator"),
        /** Positive designator */
        VCP(172, "Positive designator"),
        /** Verb */
        VV(173, "Verb"),
        /** Auxiliary Verb or Adjective */
        VX(174, "Auxiliary Verb or Adjective"),
        /** Prefix */
        XPN(181, "Prefix"),
        /** Root */
        XR(182, "Root"),
        /** Adjective Suffix */
        XSA(183, "Adjective Suffix"),
        /** Noun Suffix */
        XSN(184, "Noun Suffix"),
        /** Verb Suffix */
        XSV(185, "Verb Suffix"),
        /** Unknown */
        UNKNOWN(999, "Unknown"),
        /** Unknown */
        UNA(-1, "Unknown"),
        /** Unknown */
        NA(-1, "Unknown"),
        /** Unknown */
        VSV(-1, "Unknown");

        /** Returns the description associated with the tag. */
        fun description(): String = desc
    }

    companion object {
        /** Returns the [Tag] of the provided name. */
        fun resolveTag(name: String): Tag = Tag.valueOf(name.uppercase())

        /** Returns the [Tag] of the provided tag. */
        fun resolveTag(tag: Byte): Tag {
            require(tag < Tag.values().size) { "Tag out of range: $tag" }
            return Tag.values()[tag.toInt()]
        }

        /** Returns the [Type] of the provided name. */
        fun resolveType(name: String): Type {
            if (name == "*") {
                return Type.MORPHEME
            }
            return Type.valueOf(name.uppercase())
        }

        /** Returns the [Type] of the provided type. */
        fun resolveType(type: Byte): Type {
            require(type < Type.values().size) { "Type out of range: $type" }
            return Type.values()[type.toInt()]
        }
    }
}
