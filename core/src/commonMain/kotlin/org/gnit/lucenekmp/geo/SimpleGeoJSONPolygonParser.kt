package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.jdkport.ParseException
import kotlin.math.min


/*
  We accept either a whole type: Feature, like this:

    { "type": "Feature",
      "geometry": {
         "type": "Polygon",
         "coordinates": [
           [ [100.0, 0.0], [101.0, 0.0], [101.0, 1.0],
             [100.0, 1.0], [100.0, 0.0] ]
           ]
       },
       "properties": {
         "prop0": "value0",
         "prop1": {"this": "that"}
         }
       }

   Or the inner object with type: Multi/Polygon.

   Or a type: FeatureCollection, if it has only one Feature which is a Polygon or MultiPolyon.

   type: MultiPolygon (union of polygons) is also accepted.
*/
/**
 * Does minimal parsing of a GeoJSON object, to extract either Polygon or MultiPolygon, either
 * directly as the top-level type, or if the top-level type is Feature, as the geometry of that
 * feature.
 */
internal class SimpleGeoJSONPolygonParser(val input: String) {
    private var upto = 0
    private var polyType: String? = null
    private var coordinates: MutableList<Any>? = null

    @Throws(ParseException::class)
    fun parse(): Array<Polygon> {
        // parse entire object
        parseObject("")

        // make sure there's nothing left:
        readEnd()

        // The order of JSON object keys (type, geometry, coordinates in our case) can be arbitrary, so
        // we wait until we are done parsing to
        // put the pieces together here:
        if (coordinates == null) {
            throw newParseException("did not see any polygon coordinates")
        }

        if (polyType == null) {
            throw newParseException("did not see type: Polygon or MultiPolygon")
        }

        if (polyType == "Polygon") {
            return arrayOf(parsePolygon(coordinates!!))
        } else {
            val polygons: MutableList<Polygon> =
                ArrayList()
            for (i in coordinates!!.indices) {
                val o = coordinates!![i]
                if (o is MutableList<*> == false) {
                    throw newParseException(
                        "elements of coordinates array should be an array, but got: " + o::class.qualifiedName
                    )
                }
                polygons.add(parsePolygon(o as MutableList<Any>))
            }

            return polygons.toTypedArray<Polygon>()
        }
    }

    /** path is the "address" by keys of where we are, e.g. geometry.coordinates  */
    @Throws(ParseException::class)
    private fun parseObject(path: String) {
        scan('{')
        var first = true
        while (true) {
            var ch = peek()
            if (ch == '}') {
                break
            } else if (first == false) {
                if (ch == ',') {
                    // ok
                    upto++
                    ch = peek()
                    if (ch == '}') {
                        break
                    }
                } else {
                    throw newParseException("expected , but got $ch")
                }
            }

            first = false

            var uptoStart = upto
            val key = parseString()

            if (path == "crs.properties" && key == "href") {
                upto = uptoStart
                throw newParseException("cannot handle linked crs")
            }

            scan(':')

            val o: Any?

            ch = peek()

            uptoStart = upto

            if (ch == '[') {
                val newPath: String = if (path.isEmpty()) {
                    key
                } else {
                    "$path.$key"
                }
                o = parseArray(newPath)
            } else if (ch == '{') {
                val newPath: String = if (path.isEmpty()) {
                    key
                } else {
                    "$path.$key"
                }
                parseObject(newPath)
                o = null
            } else if (ch == '"') {
                o = parseString()
            } else if (ch == 't') {
                scan("true")
                o = true /*java.lang.Boolean.TRUE*/
            } else if (ch == 'f') {
                o = false /*java.lang.Boolean.FALSE*/
                scan("false")
            } else if (ch == 'n') {
                scan("null")
                o = null
            } else if (ch == '-' || ch == '.' || (ch >= '0' && ch <= '9')) {
                o = parseNumber()
            } else if (ch == '}') {
                break
            } else {
                throw newParseException("expected array, object, string or literal value, but got: $ch")
            }

            if (path == "crs.properties" && key == "name") {
                if (o is String == false) {
                    upto = uptoStart
                    throw newParseException("crs.properties.name should be a string, but saw: $o")
                }
                val crs = o
                if (crs.startsWith("urn:ogc:def:crs:OGC") == false || crs.endsWith(":CRS84") == false) {
                    upto = uptoStart
                    throw newParseException("crs must be CRS84 from OGC, but saw: $o")
                }
            }

            if (key == "type" && path.startsWith("crs") == false) {
                if (o is String == false) {
                    upto = uptoStart
                    throw newParseException("type should be a string, but got: $o")
                }
                val type = o
                if (type == "Polygon" && isValidGeometryPath(path)) {
                    polyType = "Polygon"
                } else if (type == "MultiPolygon" && isValidGeometryPath(path)) {
                    polyType = "MultiPolygon"
                } else if ((type == "FeatureCollection" || type == "Feature")
                    && (path == "features.[]" || path.isEmpty())
                ) {
                    // OK, we recurse
                } else {
                    upto = uptoStart
                    throw newParseException(
                        "can only handle type FeatureCollection (if it has a single polygon geometry), Feature, Polygon or MultiPolygon, but got "
                                + type
                    )
                }
            } else if (key == "coordinates" && isValidGeometryPath(path)) {
                if (o is MutableList<*> == false) {
                    upto = uptoStart
                    throw newParseException("coordinates should be an array, but got: " + o!!::class.qualifiedName)
                }
                if (coordinates != null) {
                    upto = uptoStart
                    throw newParseException("only one Polygon or MultiPolygon is supported")
                }
                coordinates = o as MutableList<Any>
            }
        }

        scan('}')
    }

    /** Returns true if the object path is a valid location to see a Multi/Polygon geometry  */
    private fun isValidGeometryPath(path: String): Boolean {
        return path.isEmpty() || path == "geometry" || path == "features.[].geometry"
    }

    @Throws(ParseException::class)
    private fun parsePolygon(coordinates: MutableList<Any>): Polygon {
        val holes: MutableList<Polygon> = ArrayList<Polygon>()
        var o: Any = coordinates[0]
        if (o is MutableList<*> == false) {
            throw newParseException(
                "first element of polygon array must be an array [[lat, lon], [lat, lon] ...] but got: "
                        + o
            )
        }
        val polyPoints = parsePoints(o as MutableList<Any>)
        for (i in 1..<coordinates.size) {
            o = coordinates[i]
            if (o is MutableList<*> == false) {
                throw newParseException(
                    "elements of coordinates array must be an array [[lat, lon], [lat, lon] ...] but got: "
                            + o
                )
            }
            val holePoints = parsePoints(o as MutableList<Any>)
            holes.add(Polygon(holePoints[0], holePoints[1]))
        }
        return Polygon(
            polyPoints[0],
            polyPoints[1],
            *holes.toTypedArray<Polygon>()
        )
    }

    /** Parses [[lat, lon], [lat, lon] ...] into 2d double array  */
    @Throws(ParseException::class)
    private fun parsePoints(o: MutableList<Any>): Array<DoubleArray> {
        val lats = DoubleArray(o.size)
        val lons = DoubleArray(o.size)
        for (i in o.indices) {
            val point = o[i]
            if (point is MutableList<*> == false) {
                throw newParseException(
                    "elements of coordinates array must [lat, lon] array, but got: $point"
                )
            }
            val pointList = point as MutableList<Any>
            if (pointList.size != 2) {
                throw newParseException(
                    "elements of coordinates array must [lat, lon] array, but got wrong element count: "
                            + pointList
                )
            }
            if (pointList[0] is Double == false) {
                throw newParseException(
                    "elements of coordinates array must [lat, lon] array, but first element is not a Double: "
                            + pointList[0]
                )
            }
            if (pointList[1] is Double == false) {
                throw newParseException(
                    "elements of coordinates array must [lat, lon] array, but second element is not a Double: "
                            + pointList[1]
                )
            }

            // lon, lat ordering in GeoJSON!
            lons[i] = (pointList[0] as Double)
            lats[i] = (pointList[1] as Double)
        }

        return arrayOf(lats, lons)
    }

    @Throws(ParseException::class)
    private fun parseArray(path: String): MutableList<Any?> {
        val result: MutableList<Any?> = ArrayList()
        scan('[')
        while (upto < input.length) {
            var ch = peek()
            if (ch == ']') {
                scan(']')
                return result
            }

            if (result.isNotEmpty()) {
                if (ch != ',') {
                    throw newParseException("expected ',' separating list items, but got '$ch'")
                }

                // skip the ,
                upto++

                if (upto == input.length) {
                    throw newParseException("hit EOF while parsing array")
                }
                ch = peek()
            }

            val o: Any?
            if (ch == '[') {
                o = parseArray("$path.[]")
            } else if (ch == '{') {
                // This is only used when parsing the "features" in type: FeatureCollection
                parseObject("$path.[]")
                o = null
            } else if (ch == '-' || ch == '.' || (ch >= '0' && ch <= '9')) {
                o = parseNumber()
            } else if (ch == '"') {
                o = parseString()
            } else {
                throw newParseException(
                    "expected another array or number while parsing array, not '$ch'"
                )
            }

            result.add(o)
        }

        throw newParseException("hit EOF while reading array")
    }

    @Throws(ParseException::class)
    private fun parseNumber(): Number {
        val b = StringBuilder()
        val uptoStart = upto
        while (upto < input.length) {
            val ch = input[upto]
            if (ch == '-' || ch == '.' || (ch >= '0' && ch <= '9') || ch == 'e' || ch == 'E') {
                upto++
                b.append(ch)
            } else {
                break
            }
        }

        // we only handle doubles
        try {
            return b.toString().toDouble()
        } catch (nfe: NumberFormatException) {
            upto = uptoStart
            throw newParseException("could not parse number as double")
        }
    }

    @Throws(ParseException::class)
    private fun parseString(): String {
        scan('"')
        val b = StringBuilder()
        while (upto < input.length) {
            var ch = input[upto]
            if (ch == '"') {
                upto++
                return b.toString()
            }
            if (ch == '\\') {
                // an escaped character
                upto++
                if (upto == input.length) {
                    throw newParseException("hit EOF inside string literal")
                }
                ch = input[upto]
                when (ch) {
                    'u' -> {
                        // 4 hex digit unicode BMP escape
                        upto++
                        if (upto + 4 > input.length) {
                            throw newParseException("hit EOF inside string literal")
                        }
                        b.append(input.substring(upto, upto + 4).toInt(16))
                    }
                    '\\' -> {
                        b.append('\\')
                        upto++
                    }
                    else -> {
                        // TODO: allow \n, \t, etc.
                        throw newParseException("unsupported string escape character \\" + ch)
                    }
                }
            } else {
                b.append(ch)
                upto++
            }
        }

        throw newParseException("hit EOF inside string literal")
    }

    @Throws(ParseException::class)
    private fun peek(): Char {
        while (upto < input.length) {
            val ch = input[upto]
            if (isJSONWhitespace(ch)) {
                upto++
                continue
            }
            return ch
        }

        throw newParseException("unexpected EOF")
    }

    /**
     * Scans across whitespace and consumes the expected character, or throws `ParseException`
     * if the character is wrong
     */
    @Throws(ParseException::class)
    private fun scan(expected: Char) {
        while (upto < input.length) {
            val ch = input[upto]
            if (isJSONWhitespace(ch)) {
                upto++
                continue
            }
            if (ch != expected) {
                throw newParseException("expected '$expected' but got '$ch'")
            }
            upto++
            return
        }
        throw newParseException("expected '$expected' but got EOF")
    }

    @Throws(ParseException::class)
    private fun readEnd() {
        while (upto < input.length) {
            val ch = input[upto]
            if (isJSONWhitespace(ch) == false) {
                throw newParseException("unexpected character '$ch' after end of GeoJSON object")
            }
            upto++
        }
    }

    /** Scans the expected string, or throws `ParseException`  */
    @Throws(ParseException::class)
    private fun scan(expected: String) {
        if (upto + expected.length > input.length) {
            throw newParseException("expected \"$expected\" but hit EOF")
        }
        val subString = input.substring(upto, upto + expected.length)
        if (subString == expected == false) {
            throw newParseException("expected \"$expected\" but got \"$subString\"")
        }
        upto += expected.length
    }

    /** When calling this, upto should be at the position of the incorrect character!  */
    @Throws(ParseException::class)
    private fun newParseException(details: String): ParseException {
        val end = min(input.length, upto + 1)
        val fragment: String = if (upto < 50) {
            input.substring(0, end)
        } else {
            "..." + input.substring(upto - 50, end)
        }
        return ParseException(
            "$details at character offset $upto; fragment leading to this:\n$fragment",
            upto
        )
    }

    companion object {
        private fun isJSONWhitespace(ch: Char): Boolean {
            // JSON doesn't accept allow unicode whitespace
            return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r'
        }
    }
}
