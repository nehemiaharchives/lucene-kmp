package org.gnit.lucenekmp.analysis.ko.dict

/** Dictionary constants */
internal object DictionaryConstants {
    /** Codec header of the dictionary file. */
    const val DICT_HEADER: String = "ko_dict"

    /** Codec header of the dictionary mapping file. */
    const val TARGETMAP_HEADER: String = "ko_dict_map"

    /** Codec header of the POS dictionary file. */
    const val POSDICT_HEADER: String = "ko_dict_pos"

    /** Codec header of the connection costs file. */
    const val CONN_COSTS_HEADER: String = "ko_cc"

    /** Codec header of the character definition file */
    const val CHARDEF_HEADER: String = "ko_cd"

    /** Codec version of the binary dictionary */
    const val VERSION: Int = 1
}
