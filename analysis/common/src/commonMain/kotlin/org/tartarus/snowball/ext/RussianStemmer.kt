package org.tartarus.snowball.ext

import org.tartarus.snowball.Among
import org.tartarus.snowball.SnowballStemmer

/**
 * This class implements the stemming algorithm defined by a snowball script.
 */
class RussianStemmer : SnowballStemmer() {
    private val a_0 = arrayOf(
        Among("\u0432", -1, 1),
        Among("\u0438\u0432", 0, 2),
        Among("\u044B\u0432", 0, 2),
        Among("\u0432\u0448\u0438", -1, 1),
        Among("\u0438\u0432\u0448\u0438", 3, 2),
        Among("\u044B\u0432\u0448\u0438", 3, 2),
        Among("\u0432\u0448\u0438\u0441\u044C", -1, 1),
        Among("\u0438\u0432\u0448\u0438\u0441\u044C", 6, 2),
        Among("\u044B\u0432\u0448\u0438\u0441\u044C", 6, 2)
    )

    private val a_1 = arrayOf(
        Among("\u0435\u0435", -1, 1),
        Among("\u0438\u0435", -1, 1),
        Among("\u043E\u0435", -1, 1),
        Among("\u044B\u0435", -1, 1),
        Among("\u0438\u043C\u0438", -1, 1),
        Among("\u044B\u043C\u0438", -1, 1),
        Among("\u0435\u0439", -1, 1),
        Among("\u0438\u0439", -1, 1),
        Among("\u043E\u0439", -1, 1),
        Among("\u044B\u0439", -1, 1),
        Among("\u0435\u043C", -1, 1),
        Among("\u0438\u043C", -1, 1),
        Among("\u043E\u043C", -1, 1),
        Among("\u044B\u043C", -1, 1),
        Among("\u0435\u0433\u043E", -1, 1),
        Among("\u043E\u0433\u043E", -1, 1),
        Among("\u0435\u043C\u0443", -1, 1),
        Among("\u043E\u043C\u0443", -1, 1),
        Among("\u0438\u0445", -1, 1),
        Among("\u044B\u0445", -1, 1),
        Among("\u0435\u044E", -1, 1),
        Among("\u043E\u044E", -1, 1),
        Among("\u0443\u044E", -1, 1),
        Among("\u044E\u044E", -1, 1),
        Among("\u0430\u044F", -1, 1),
        Among("\u044F\u044F", -1, 1)
    )

    private val a_2 = arrayOf(
        Among("\u0435\u043C", -1, 1),
        Among("\u043D\u043D", -1, 1),
        Among("\u0432\u0448", -1, 1),
        Among("\u0438\u0432\u0448", 2, 2),
        Among("\u044B\u0432\u0448", 2, 2),
        Among("\u0449", -1, 1),
        Among("\u044E\u0449", 5, 1),
        Among("\u0443\u044E\u0449", 6, 2)
    )

    private val a_3 = arrayOf(
        Among("\u0441\u044C", -1, 1),
        Among("\u0441\u044F", -1, 1)
    )

    private val a_4 = arrayOf(
        Among("\u043B\u0430", -1, 1),
        Among("\u0438\u043B\u0430", 0, 2),
        Among("\u044B\u043B\u0430", 0, 2),
        Among("\u043D\u0430", -1, 1),
        Among("\u0435\u043D\u0430", 3, 2),
        Among("\u0435\u0442\u0435", -1, 1),
        Among("\u0438\u0442\u0435", -1, 2),
        Among("\u0439\u0442\u0435", -1, 1),
        Among("\u0435\u0439\u0442\u0435", 7, 2),
        Among("\u0443\u0439\u0442\u0435", 7, 2),
        Among("\u043B\u0438", -1, 1),
        Among("\u0438\u043B\u0438", 10, 2),
        Among("\u044B\u043B\u0438", 10, 2),
        Among("\u0439", -1, 1),
        Among("\u0435\u0439", 13, 2),
        Among("\u0443\u0439", 13, 2),
        Among("\u043B", -1, 1),
        Among("\u0438\u043B", 16, 2),
        Among("\u044B\u043B", 16, 2),
        Among("\u0435\u043C", -1, 1),
        Among("\u0438\u043C", -1, 2),
        Among("\u044B\u043C", -1, 2),
        Among("\u043D", -1, 1),
        Among("\u0435\u043D", 22, 2),
        Among("\u043B\u043E", -1, 1),
        Among("\u0438\u043B\u043E", 24, 2),
        Among("\u044B\u043B\u043E", 24, 2),
        Among("\u043D\u043E", -1, 1),
        Among("\u0435\u043D\u043E", 27, 2),
        Among("\u043D\u043D\u043E", 27, 1),
        Among("\u0435\u0442", -1, 1),
        Among("\u0443\u0435\u0442", 30, 2),
        Among("\u0438\u0442", -1, 2),
        Among("\u044B\u0442", -1, 2),
        Among("\u044E\u0442", -1, 1),
        Among("\u0443\u044E\u0442", 34, 2),
        Among("\u044F\u0442", -1, 2),
        Among("\u043D\u044B", -1, 1),
        Among("\u0435\u043D\u044B", 37, 2),
        Among("\u0442\u044C", -1, 1),
        Among("\u0438\u0442\u044C", 39, 2),
        Among("\u044B\u0442\u044C", 39, 2),
        Among("\u0435\u0448\u044C", -1, 1),
        Among("\u0438\u0448\u044C", -1, 2),
        Among("\u044E", -1, 2),
        Among("\u0443\u044E", 44, 2)
    )

    private val a_5 = arrayOf(
        Among("\u0430", -1, 1),
        Among("\u0435\u0432", -1, 1),
        Among("\u043E\u0432", -1, 1),
        Among("\u0435", -1, 1),
        Among("\u0438\u0435", 3, 1),
        Among("\u044C\u0435", 3, 1),
        Among("\u0438", -1, 1),
        Among("\u0435\u0438", 6, 1),
        Among("\u0438\u0438", 6, 1),
        Among("\u0430\u043C\u0438", 6, 1),
        Among("\u044F\u043C\u0438", 6, 1),
        Among("\u0438\u044F\u043C\u0438", 10, 1),
        Among("\u0439", -1, 1),
        Among("\u0435\u0439", 12, 1),
        Among("\u0438\u0435\u0439", 13, 1),
        Among("\u0438\u0439", 12, 1),
        Among("\u043E\u0439", 12, 1),
        Among("\u0430\u043C", -1, 1),
        Among("\u0435\u043C", -1, 1),
        Among("\u0438\u0435\u043C", 18, 1),
        Among("\u043E\u043C", -1, 1),
        Among("\u044F\u043C", -1, 1),
        Among("\u0438\u044F\u043C", 21, 1),
        Among("\u043E", -1, 1),
        Among("\u0443", -1, 1),
        Among("\u0430\u0445", -1, 1),
        Among("\u044F\u0445", -1, 1),
        Among("\u0438\u044F\u0445", 26, 1),
        Among("\u044B", -1, 1),
        Among("\u044C", -1, 1),
        Among("\u044E", -1, 1),
        Among("\u0438\u044E", 30, 1),
        Among("\u044C\u044E", 30, 1),
        Among("\u044F", -1, 1),
        Among("\u0438\u044F", 33, 1),
        Among("\u044C\u044F", 33, 1)
    )

    private val a_6 = arrayOf(
        Among("\u043E\u0441\u0442", -1, 1),
        Among("\u043E\u0441\u0442\u044C", -1, 1)
    )

    private val a_7 = arrayOf(
        Among("\u0435\u0439\u0448\u0435", -1, 1),
        Among("\u043D", -1, 2),
        Among("\u0435\u0439\u0448", -1, 1),
        Among("\u044C", -1, 3)
    )

    private val g_v = charArrayOf(33.toChar(), 65.toChar(), 8.toChar(), 232.toChar())

    private var I_p2 = 0
    private var I_pV = 0

    private fun r_mark_regions(): Boolean {
        I_pV = limit
        I_p2 = limit
        val v_1 = cursor
        run {
            while (true) {
                if (in_grouping(g_v, 1072, 1103)) {
                    break
                }
                if (cursor >= limit) {
                    return@run
                }
                cursor++
            }
            I_pV = cursor
            while (true) {
                if (out_grouping(g_v, 1072, 1103)) {
                    break
                }
                if (cursor >= limit) {
                    return@run
                }
                cursor++
            }
            while (true) {
                if (in_grouping(g_v, 1072, 1103)) {
                    break
                }
                if (cursor >= limit) {
                    return@run
                }
                cursor++
            }
            while (true) {
                if (out_grouping(g_v, 1072, 1103)) {
                    break
                }
                if (cursor >= limit) {
                    return@run
                }
                cursor++
            }
            I_p2 = cursor
        }
        cursor = v_1
        return true
    }

    private fun r_R2(): Boolean {
        return I_p2 <= cursor
    }

    private fun r_perfective_gerund(): Boolean {
        val among_var: Int
        ket = cursor
        among_var = find_among_b(a_0)
        if (among_var == 0) {
            return false
        }
        bra = cursor
        when (among_var) {
            1 -> {
                val v_1 = limit - cursor
                if (!eq_s_b("\u0430")) {
                    cursor = limit - v_1
                    if (!eq_s_b("\u044F")) {
                        return false
                    }
                }
                slice_del()
            }
            2 -> slice_del()
        }
        return true
    }

    private fun r_adjective(): Boolean {
        ket = cursor
        if (find_among_b(a_1) == 0) {
            return false
        }
        bra = cursor
        slice_del()
        return true
    }

    private fun r_adjectival(): Boolean {
        val among_var: Int
        if (!r_adjective()) {
            return false
        }
        val v_1 = limit - cursor
        run {
            ket = cursor
            among_var = find_among_b(a_2)
            if (among_var == 0) {
                cursor = limit - v_1
                return@run
            }
            bra = cursor
            when (among_var) {
                1 -> {
                    val v_2 = limit - cursor
                    if (!eq_s_b("\u0430")) {
                        cursor = limit - v_2
                        if (!eq_s_b("\u044F")) {
                            cursor = limit - v_1
                            return@run
                        }
                    }
                    slice_del()
                }
                2 -> slice_del()
            }
        }
        return true
    }

    private fun r_reflexive(): Boolean {
        ket = cursor
        if (find_among_b(a_3) == 0) {
            return false
        }
        bra = cursor
        slice_del()
        return true
    }

    private fun r_verb(): Boolean {
        val among_var: Int
        ket = cursor
        among_var = find_among_b(a_4)
        if (among_var == 0) {
            return false
        }
        bra = cursor
        when (among_var) {
            1 -> {
                val v_1 = limit - cursor
                if (!eq_s_b("\u0430")) {
                    cursor = limit - v_1
                    if (!eq_s_b("\u044F")) {
                        return false
                    }
                }
                slice_del()
            }
            2 -> slice_del()
        }
        return true
    }

    private fun r_noun(): Boolean {
        ket = cursor
        if (find_among_b(a_5) == 0) {
            return false
        }
        bra = cursor
        slice_del()
        return true
    }

    private fun r_derivational(): Boolean {
        ket = cursor
        if (find_among_b(a_6) == 0) {
            return false
        }
        bra = cursor
        if (!r_R2()) {
            return false
        }
        slice_del()
        return true
    }

    private fun r_tidy_up(): Boolean {
        val among_var: Int
        ket = cursor
        among_var = find_among_b(a_7)
        if (among_var == 0) {
            return false
        }
        bra = cursor
        when (among_var) {
            1 -> {
                slice_del()
                ket = cursor
                if (!eq_s_b("\u043D")) {
                    return false
                }
                bra = cursor
                if (!eq_s_b("\u043D")) {
                    return false
                }
                slice_del()
            }
            2 -> {
                if (!eq_s_b("\u043D")) {
                    return false
                }
                slice_del()
            }
            3 -> slice_del()
        }
        return true
    }

    override fun stem(): Boolean {
        val v_1 = cursor
        run {
            while (true) {
                val v_2 = cursor
                while (true) {
                    val v_3 = cursor
                    bra = cursor
                    if (eq_s("\u0451")) {
                        ket = cursor
                        cursor = v_3
                        break
                    }
                    cursor = v_3
                    if (cursor >= limit) {
                        cursor = v_2
                        return@run
                    }
                    cursor++
                }
                slice_from("\u0435")
            }
        }
        cursor = v_1
        r_mark_regions()
        limit_backward = cursor
        cursor = limit
        if (cursor < I_pV) {
            return false
        }
        val v_6 = limit_backward
        limit_backward = I_pV
        val v_7 = limit - cursor
        run {
            run {
                val v_8 = limit - cursor
                if (r_perfective_gerund()) {
                    return@run
                }
                cursor = limit - v_8
                val v_9 = limit - cursor
                if (!r_reflexive()) {
                    cursor = limit - v_9
                }
                run {
                    val v_10 = limit - cursor
                    if (r_adjectival()) {
                        return@run
                    }
                    cursor = limit - v_10
                    if (r_verb()) {
                        return@run
                    }
                    cursor = limit - v_10
                    if (!r_noun()) {
                        return@run
                    }
                }
            }
        }
        cursor = limit - v_7
        val v_11 = limit - cursor
        run {
            ket = cursor
            if (!eq_s_b("\u0438")) {
                cursor = limit - v_11
                return@run
            }
            bra = cursor
            slice_del()
        }
        val v_12 = limit - cursor
        r_derivational()
        cursor = limit - v_12
        val v_13 = limit - cursor
        r_tidy_up()
        cursor = limit - v_13
        limit_backward = v_6
        cursor = limit_backward
        return true
    }

    override fun equals(other: Any?): Boolean {
        return other is RussianStemmer
    }

    override fun hashCode(): Int {
        return "org.tartarus.snowball.ext.RussianStemmer".hashCode()
    }
}
