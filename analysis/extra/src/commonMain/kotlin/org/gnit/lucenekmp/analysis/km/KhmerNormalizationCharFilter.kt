package org.gnit.lucenekmp.analysis.km

import org.gnit.lucenekmp.analysis.charfilter.MappingCharFilter
import org.gnit.lucenekmp.analysis.charfilter.NormalizeCharMap
import org.gnit.lucenekmp.jdkport.Reader

/**
 * Applies character normalization for Khmer text. Wraps [MappingCharFilter] and applies
 * a set of confusable-character mappings that vary by normalization level.
 *
 * - Level 1: formally confusable characters (always applied)
 * - Level 2: additionally informally confusable characters
 * - Level 3: additionally digit mapping and further equivalences
 */
class KhmerNormalizationCharFilter(input: Reader, level: Int = 1) :
    MappingCharFilter(getTibNormalizeCharMapMemoized(level), input) {

    constructor(input: Reader) : this(input, 1)

    companion object {
        private val cache = mutableMapOf<Int, NormalizeCharMap>()

        /** Returns a memoized [NormalizeCharMap] for the given normalization level. */
        fun getTibNormalizeCharMapMemoized(level: Int): NormalizeCharMap {
            return cache.getOrPut(level) { getTibNormalizeCharMap(level) }
        }

        /** Builds a [NormalizeCharMap] for the given normalization level (1, 2, or 3). */
        fun getTibNormalizeCharMap(level: Int): NormalizeCharMap {
            val builder = NormalizeCharMap.Builder()
            // same display ("formally confusable")
            builder.add("\u17C1\u17B8", "\u17BE") // កេី=កើ
            builder.add("\u17C1\u17B6", "\u17C4") // កេា=កោ
            builder.add("\u17D2\u179A\u17D2", "\u17D2\u179A") // coeng rho must be the last coeng
            // TODO: check
            //builder.add("\u17BB\u17D0", "\u17C9\u17D0") // samyok sannya causes downshifting so cannot occur with -u (មុ័=ម៉័)
            builder.add("\u17B4", "") // invisible, discouraged
            builder.add("\u17B5", "") // invisible, discouraged
            builder.add("\u17E2\u17D3", "\u19E0") // deprecated
            builder.add("\u17A3", "\u17A2") // deprecated
            builder.add("\u17A4", "\u17A2\u17B6") // deprecated
            builder.add("\u17A8", "\u17A7\u1780") // deprecated
            builder.add("\u17D8", "\u17D4\u179B\u17D4") // deprecated
            if (level < 2) return builder.build()
            // similar display ("informally confusable")
            builder.add("\u1791\u17D2\u1794", "\u17A1") // ទ្យ ~ ឡ
            builder.add("\u17BE\u17D2\u1799", "\u17BF") // កើ្យ ~ កឿ
            builder.add("\u17C1\u17B8\u17D2\u1799", "\u17BF") // កេី្យ ~ កឿ
            builder.add("\u17D2\u1799\u17BE", "\u17BF") // ក្យើ ~ កឿ
            builder.add("\u17C1\u17D2\u1799", "\u17C0") // កេ្យ ~ កៀ
            builder.add("\u1794\u17D2\u1789", "\u17AB") // ប្ញ ~ ឫ
            builder.add("\u17AD\u17B6", "\u1789") // ឭា ~ ញ
            builder.add("\u17AE\u17B6", "\u1789") // ឮា ~ ញ
            builder.add("\u1796\u17D2\u1789", "\u17AD") // ព្ញ ~ ឭ
            builder.add("\u1796\u17B6\u17D2\u1789", "\u1789") // ពា្ញ ~ ញ
            builder.add("\u1796\u17D2\u178B", "\u17D2\u1792") // ព្ឋ ~ ឰ
            builder.add("\u178A\u17D2\u178B", "\u178A\u17D2\u1792") // ដ្ឋ ~ ដ្ធ
            builder.add("\u1791\u17D2\u178B", "\u1791\u17D2\u1792") // ទ្ឋ ~ ទ្ធ
            builder.add("\u1796\u17D0\u1793\u17D2\u178B", "\u17D2\u1792") // ព័ន្ឋ ~ ព័ន្ធ
            builder.add("\u1796\u1793\u17D2\u178B", "\u17D2\u1792") // ពន្ឋ ~ ពន្ធ
            builder.add("\u17AA\u17D2\u1799", "\u17B1\u17D2\u1799") // ឪ្យ ~ ឱ្យ
            builder.add("\u17B3\u17D2\u1799", "\u17B1\u17D2\u1799") // ឳ្យ ~ ឱ្យ
            builder.add("\u17A7\u17B7", "\u17B1") // ឧិ ~ ឱ
            builder.add("\u17A7\u17CC", "\u17B1") // ឧ៌ ~ ឱ
            builder.add("\u17A7\u17CD", "\u17B1") // ឧ៍ ~ ឱ
            builder.add("\u178A\u17D2\u1792", "\u178A\u17D2\u178B") // ដ្ធ ~ ដ្ឋ
            builder.add("\u1789\u17D2\u179C", "\u1796\u17D2\u179C\u17B6") // ញ្វ ~ ព្វា
            builder.add(":", "\u17C8") // common confusion
            builder.add("\u17D2\u178A", "\u17D2\u178F") // Coeng Da / Coeng Ta
            if (level < 3) return builder.build()
            builder.add("\u17DD", "\u17D1")
            builder.add("\u17B2", "\u17B1")
            builder.add("\u17D3", "\u17D6")
            builder.add("\u17E0", "0")
            builder.add("\u17E1", "1")
            builder.add("\u17E2", "2")
            builder.add("\u17E3", "3")
            builder.add("\u17E4", "4")
            builder.add("\u17E5", "5")
            builder.add("\u17E6", "6")
            builder.add("\u17E7", "7")
            builder.add("\u17E8", "8")
            builder.add("\u17E9", "9")
            builder.add("\u17C9", "\u17CA") // from examples in the document, ex: ស៉ី / ស៊ី or ស៉ើប / ស៊ើប
            builder.add("\u17A7\u17CA", "\u17A8") // ឧ៊ ~ ឨ
            return builder.build()
        }
    }
}
