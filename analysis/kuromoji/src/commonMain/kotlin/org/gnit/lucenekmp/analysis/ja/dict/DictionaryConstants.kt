package org.gnit.lucenekmp.analysis.ja.dict

/** Dictionary constants */
internal object DictionaryConstants {
    /** Codec header of the dictionary file. */
    const val DICT_HEADER: String = "kuromoji_dict"

    /** Codec header of the dictionary mapping file. */
    const val TARGETMAP_HEADER: String = "kuromoji_dict_map"

    /** Codec header of the POS dictionary file. */
    const val POSDICT_HEADER: String = "kuromoji_dict_pos"

    /** Codec header of the connection costs. */
    const val CONN_COSTS_HEADER: String = "kuromoji_cc"

    /** Codec header of the character definition file. */
    const val CHARDEF_HEADER: String = "kuromoji_cd"

    /** Codec version of the binary dictionary */
    const val VERSION: Int = 1
}
