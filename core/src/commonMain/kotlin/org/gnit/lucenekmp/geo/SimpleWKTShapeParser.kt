package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.jdkport.ParseException

/** Minimal parser for WKT Polygon strings used in tests. */
object SimpleWKTShapeParser {
    @Throws(ParseException::class)
    fun parse(wkt: String): Polygon {
        val trimmed = wkt.trim()
        if (!trimmed.startsWith("POLYGON")) {
            throw ParseException("Only POLYGON WKT is supported", 0)
        }
        val start = trimmed.indexOf("(")
        val end = trimmed.lastIndexOf(")")
        if (start == -1 || end == -1 || end <= start) {
            throw ParseException("Invalid POLYGON WKT", 0)
        }
        val body = trimmed.substring(start + 1, end)
        val rings = mutableListOf<Pair<DoubleArray, DoubleArray>>()
        for (ringStr in splitRings(body)) {
            val coords = ringStr.trim().trimStart('(').trimEnd(')')
            val pts = coords.split(Regex("\\s*,\\s*"))
            val lats = DoubleArray(pts.size)
            val lons = DoubleArray(pts.size)
            for (i in pts.indices) {
                val parts = pts[i].trim().split(Regex("\\s+"))
                if (parts.size != 2) {
                    throw ParseException("Invalid coordinate: ${pts[i]}", 0)
                }
                lons[i] = parts[0].toDouble()
                lats[i] = parts[1].toDouble()
            }
            rings.add(Pair(lats, lons))
        }
        val holes = rings.drop(1).map { Polygon(it.first, it.second) }.toTypedArray()
        val outer = rings.firstOrNull() ?: throw ParseException("No coordinates", 0)
        return Polygon(outer.first, outer.second, *holes)
    }

    private fun splitRings(body: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var depth = 0
        for (ch in body) {
            when (ch) {
                '(' -> {
                    depth++
                    sb.append(ch)
                }
                ')' -> {
                    depth--
                    sb.append(ch)
                }
                ',' -> {
                    if (depth == 0) {
                        result.add(sb.toString())
                        sb.setLength(0)
                        continue
                    }
                    sb.append(ch)
                }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) result.add(sb.toString())
        return result
    }
}
