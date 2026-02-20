package org.gnit.lucenekmp.geo

import okio.IOException
import org.gnit.lucenekmp.jdkport.ParseException
import org.gnit.lucenekmp.jdkport.StreamTokenizer
import org.gnit.lucenekmp.jdkport.StringReader

/**
 * Parses shape geometry represented in WKT format
 *
 *
 * complies with OGC® document: 12-063r5 and ISO/IEC 13249-3:2016 standard located at
 * http://docs.opengeospatial.org/is/12-063r5/12-063r5.html
 */
object SimpleWKTShapeParser {
    const val EMPTY: String = "EMPTY"
    const val SPACE: String = " "
    const val LPAREN: String = "("
    const val RPAREN: String = ")"
    const val COMMA: String = ","
    const val NAN: String = "NaN"

    private const val NUMBER = "<NUMBER>"
    private const val EOF = "END-OF-STREAM"
    private const val EOL = "END-OF-LINE"

    @Throws(IOException::class, ParseException::class)
    fun parse(wkt: String): Any? {
        return parseExpectedType(wkt, null)
    }

    @Throws(IOException::class, ParseException::class)
    fun parseExpectedType(wkt: String, shapeType: ShapeType?): Any? {
        StringReader(wkt).use { reader ->
            // set up the tokenizer; configured to read words w/o numbers
            val tokenizer = StreamTokenizer(reader)
            tokenizer.resetSyntax()
            tokenizer.wordChars('a'.code, 'z'.code)
            tokenizer.wordChars('A'.code, 'Z'.code)
            tokenizer.wordChars(128 + 32, 255)
            tokenizer.wordChars('0'.code, '9'.code)
            tokenizer.wordChars('-'.code, '-'.code)
            tokenizer.wordChars('+'.code, '+'.code)
            tokenizer.wordChars('.'.code, '.'.code)
            tokenizer.whitespaceChars(0, ' '.code)
            tokenizer.commentChar('#'.code)
            val geometry = parseGeometry(tokenizer, shapeType)
            checkEOF(tokenizer)
            return geometry
        }
    }

    /** parse geometry from the stream tokenizer  */
    @Throws(IOException::class, ParseException::class)
    private fun parseGeometry(stream: StreamTokenizer, shapeType: ShapeType?): Any? {
        val type = ShapeType.Companion.forName(nextWord(stream))
        if (shapeType != null && shapeType != ShapeType.GEOMETRYCOLLECTION) {
            if (type.wktName() != shapeType.wktName()) {
                throw ParseException(
                    "Expected geometry type: [$shapeType], but found: [$type]",
                    stream.lineno()
                )
            }
        }
        return when (type) {
            ShapeType.POINT -> parsePoint(stream)
            ShapeType.MULTIPOINT -> parseMultiPoint(stream)
            ShapeType.LINESTRING -> parseLine(stream)
            ShapeType.MULTILINESTRING -> parseMultiLine(stream)
            ShapeType.POLYGON -> parsePolygon(stream)
            ShapeType.MULTIPOLYGON -> parseMultiPolygon(stream)
            ShapeType.ENVELOPE -> parseBBox(stream)
            ShapeType.GEOMETRYCOLLECTION -> parseGeometryCollection(stream)
            //else -> throw IllegalArgumentException("Unknown geometry type: $type")
        }
    }

    /** Parses a point as a double array  */
    @Throws(IOException::class, ParseException::class)
    private fun parsePoint(stream: StreamTokenizer): DoubleArray? {
        if (nextEmptyOrOpen(stream) == EMPTY) {
            return null
        }
        val pt = doubleArrayOf(nextNumber(stream), nextNumber(stream))
        if (isNumberNext(stream)) {
            nextNumber(stream)
        }
        nextCloser(stream)
        return pt
    }

    /** Parses a list of points into latitude and longitude arraylists  */
    @Throws(IOException::class, ParseException::class)
    private fun parseCoordinates(
        stream: StreamTokenizer, lats: ArrayList<Double>, lons: ArrayList<Double>
    ) {
        var isOpenParen = false
        if (isNumberNext(stream) || ((nextWord(stream) == LPAREN).also { isOpenParen = it })) {
            parseCoordinate(stream, lats, lons)
        }

        while (nextCloserOrComma(stream) == COMMA) {
            isOpenParen = false
            if (isNumberNext(stream) || ((nextWord(stream) == LPAREN).also { isOpenParen = it })) {
                parseCoordinate(stream, lats, lons)
            }
            if (isOpenParen && nextCloser(stream) != RPAREN) {
                throw ParseException(
                    "expected: [" + RPAREN + "] but found: [" + tokenString(stream) + "]", stream.lineno()
                )
            }
        }

        if (isOpenParen && nextCloser(stream) != RPAREN) {
            throw ParseException(
                "expected: [" + RPAREN + "] but found: [" + tokenString(stream) + "]", stream.lineno()
            )
        }
    }

    /** parses a single coordinate, w/ optional 3rd dimension  */
    @Throws(IOException::class, ParseException::class)
    private fun parseCoordinate(
        stream: StreamTokenizer, lats: ArrayList<Double>, lons: ArrayList<Double>
    ) {
        lons.add(nextNumber(stream))
        lats.add(nextNumber(stream))
        if (isNumberNext(stream)) {
            nextNumber(stream)
        }
    }

    /** parses a MULTIPOINT type  */
    @Throws(IOException::class, ParseException::class)
    private fun parseMultiPoint(stream: StreamTokenizer): Array<DoubleArray>? {
        val token = nextEmptyOrOpen(stream)
        if (token == EMPTY) {
            return null
        }
        val lats: ArrayList<Double> = ArrayList()
        val lons: ArrayList<Double> = ArrayList()
        parseCoordinates(stream, lats, lons)
        val result = Array(lats.size) { DoubleArray(2) }
        for (i in lats.indices) {
            result[i] = doubleArrayOf(lons[i], lats[i])
        }
        return result
    }

    /** parses a LINESTRING  */
    @Throws(IOException::class, ParseException::class)
    private fun parseLine(stream: StreamTokenizer): Line? {
        val token = nextEmptyOrOpen(stream)
        if (token == EMPTY) {
            return null
        }
        val lats: ArrayList<Double> = ArrayList()
        val lons: ArrayList<Double> = ArrayList()
        parseCoordinates(stream, lats, lons)
        return Line(
            lats.toDoubleArray(),
            lons.toDoubleArray()
        )
    }

    /** parses a MULTILINESTRING  */
    @Throws(IOException::class, ParseException::class)
    private fun parseMultiLine(stream: StreamTokenizer): Array<Line>? {
        val token = nextEmptyOrOpen(stream)
        if (token == EMPTY) {
            return null
        }
        val lines: ArrayList<Line> = ArrayList()
        lines.add(parseLine(stream)!!)
        while (nextCloserOrComma(stream) == COMMA) {
            lines.add(parseLine(stream)!!)
        }
        return lines.toTypedArray<Line>()
    }

    /** parses the hole of a polygon  */
    @Throws(IOException::class, ParseException::class)
    private fun parsePolygonHole(stream: StreamTokenizer): Polygon {
        val lats: ArrayList<Double> = ArrayList()
        val lons: ArrayList<Double> = ArrayList()
        parseCoordinates(stream, lats, lons)
        return Polygon(
            lats.toDoubleArray(),
            lons.toDoubleArray()
        )
    }

    /** parses a POLYGON  */
    @Throws(IOException::class, ParseException::class)
    private fun parsePolygon(stream: StreamTokenizer): Polygon? {
        if (nextEmptyOrOpen(stream) == EMPTY) {
            return null
        }
        nextOpener(stream)
        val lats: ArrayList<Double> = ArrayList()
        val lons: ArrayList<Double> = ArrayList()
        parseCoordinates(stream, lats, lons)
        val holes: ArrayList<Polygon> = ArrayList()
        while (nextCloserOrComma(stream) == COMMA) {
            holes.add(parsePolygonHole(stream))
        }

        if (!holes.isEmpty()) {
            return Polygon(
                lats.toDoubleArray(),
                lons.toDoubleArray(),
                *holes.toTypedArray<Polygon>()
            )
        }
        return Polygon(
            lats.toDoubleArray(),
            lons.toDoubleArray()
        )
    }

    /** parses a MULTIPOLYGON  */
    @Throws(IOException::class, ParseException::class)
    private fun parseMultiPolygon(stream: StreamTokenizer): Array<Polygon>? {
        val token = nextEmptyOrOpen(stream)
        if (token == EMPTY) {
            return null
        }
        val polygons: ArrayList<Polygon> = ArrayList()
        polygons.add(parsePolygon(stream)!!)
        while (nextCloserOrComma(stream) == COMMA) {
            polygons.add(parsePolygon(stream)!!)
        }
        return polygons.toTypedArray<Polygon>()
    }

    /** parses an ENVELOPE  */
    @Throws(IOException::class, ParseException::class)
    private fun parseBBox(stream: StreamTokenizer): Rectangle? {
        if (nextEmptyOrOpen(stream) == EMPTY) {
            return null
        }
        val minLon = nextNumber(stream)
        nextComma(stream)
        val maxLon = nextNumber(stream)
        nextComma(stream)
        val maxLat = nextNumber(stream)
        nextComma(stream)
        val minLat = nextNumber(stream)
        nextCloser(stream)
        return Rectangle(minLat, maxLat, minLon, maxLon)
    }

    /** parses a GEOMETRYCOLLECTION  */
    @Throws(IOException::class, ParseException::class)
    private fun parseGeometryCollection(stream: StreamTokenizer): Array<Any?>? {
        if (nextEmptyOrOpen(stream) == EMPTY) {
            return null
        }
        val geometries: ArrayList<Any?> = ArrayList()
        geometries.add(parseGeometry(stream, ShapeType.GEOMETRYCOLLECTION))
        while (nextCloserOrComma(stream) == COMMA) {
            geometries.add(parseGeometry(stream, null))
        }
        return geometries.toTypedArray<Any?>()
    }

    /** next word in the stream  */
    @Throws(ParseException::class, IOException::class)
    private fun nextWord(stream: StreamTokenizer): String {
        when (stream.nextToken().toChar()) {
            StreamTokenizer.TT_WORD.toChar() -> {
                val word: String = stream.sval!!
                return if (word.equals(EMPTY, ignoreCase = true)) EMPTY else word
            }

            '(' -> return LPAREN
            ')' -> return RPAREN
            ',' -> return COMMA
        }
        throw ParseException("expected word but found: " + tokenString(stream), stream.lineno())
    }

    /** next number in the stream  */
    @Throws(IOException::class, ParseException::class)
    private fun nextNumber(stream: StreamTokenizer): Double {
        if (stream.nextToken() == StreamTokenizer.TT_WORD) {
            return if (stream.sval.equals(NAN, ignoreCase = true)) {
                Double.Companion.NaN
            } else {
                try {
                    stream.sval!!.toDouble()
                } catch (e: NumberFormatException) {
                    throw ParseException("invalid number found: " + stream.sval, stream.lineno())
                }
            }
        }
        throw ParseException("expected number but found: " + tokenString(stream), stream.lineno())
    }

    /** next token in the stream  */
    private fun tokenString(stream: StreamTokenizer): String {
        when (stream.ttype) {
            StreamTokenizer.TT_WORD -> return stream.sval!!
            StreamTokenizer.TT_EOF -> return EOF
            StreamTokenizer.TT_EOL -> return EOL
            StreamTokenizer.TT_NUMBER -> return NUMBER
        }
        return "'" + stream.ttype.toChar() + "'"
    }

    /** checks if the next token is a number  */
    @Throws(IOException::class)
    private fun isNumberNext(stream: StreamTokenizer): Boolean {
        val type: Int = stream.nextToken()
        stream.pushBack()
        return type == StreamTokenizer.TT_WORD
    }

    /** checks if next token is an EMPTY or open paren  */
    @Throws(IOException::class, ParseException::class)
    private fun nextEmptyOrOpen(stream: StreamTokenizer): String {
        val next = nextWord(stream)
        if (next == EMPTY || next == LPAREN) {
            return next
        }
        throw ParseException(
            "expected " + EMPTY + " or " + LPAREN + " but found: " + tokenString(stream),
            stream.lineno()
        )
    }

    /** checks if next token is a closing paren  */
    @Throws(IOException::class, ParseException::class)
    private fun nextCloser(stream: StreamTokenizer): String {
        if (nextWord(stream) == RPAREN) {
            return RPAREN
        }
        throw ParseException(
            "expected " + RPAREN + " but found: " + tokenString(stream), stream.lineno()
        )
    }

    /** expects a comma as next token  */
    @Throws(IOException::class, ParseException::class)
    private fun nextComma(stream: StreamTokenizer): String {
        if (nextWord(stream) == COMMA) {
            return COMMA
        }
        throw ParseException(
            "expected " + COMMA + " but found: " + tokenString(stream), stream.lineno()
        )
    }

    /** expects an open RPAREN as the next toke  */
    @Throws(IOException::class, ParseException::class)
    private fun nextOpener(stream: StreamTokenizer): String {
        if (nextWord(stream) == LPAREN) {
            return LPAREN
        }
        throw ParseException(
            "expected " + LPAREN + " but found: " + tokenString(stream), stream.lineno()
        )
    }

    /** expects either a closing LPAREN or comma as the next token  */
    @Throws(IOException::class, ParseException::class)
    private fun nextCloserOrComma(stream: StreamTokenizer): String {
        val token = nextWord(stream)
        if (token == COMMA || token == RPAREN) {
            return token
        }
        throw ParseException(
            "expected " + COMMA + " or " + RPAREN + " but found: " + tokenString(stream),
            stream.lineno()
        )
    }

    /** next word in the stream  */
    @Throws(ParseException::class, IOException::class)
    private fun checkEOF(stream: StreamTokenizer) {
        if (stream.nextToken() != StreamTokenizer.TT_EOF) {
            throw ParseException(
                "expected end of WKT string but found additional text: " + tokenString(stream),
                stream.lineno()
            )
        }
    }

    /** Enumerated type for Shapes  */
    enum class ShapeType(private val shapeName: String) {
        POINT("point"),
        MULTIPOINT("multipoint"),
        LINESTRING("linestring"),
        MULTILINESTRING("multilinestring"),
        POLYGON("polygon"),
        MULTIPOLYGON("multipolygon"),
        GEOMETRYCOLLECTION("geometrycollection"),
        ENVELOPE("envelope"); // not part of the actual WKB spec

        fun typename(): String {
            return shapeName
        }

        /** wkt shape name  */
        fun wktName(): String {
            return if (this == ENVELOPE) BBOX else this.shapeName
        }

        companion object {
            private val shapeTypeMap: MutableMap<String, ShapeType>
            private const val BBOX = "BBOX"

            init {
                val shapeTypes: MutableMap<String, ShapeType> = mutableMapOf()
                for (type in entries) {
                    shapeTypes.put(type.shapeName, type)
                }
                shapeTypes.put(ENVELOPE.wktName().lowercase(), ENVELOPE)
                shapeTypeMap = shapeTypes.toMutableMap()
            }

            fun forName(shapename: String): ShapeType {
                val typename = shapename.lowercase()
                val type = shapeTypeMap[typename]
                if (type != null) {
                    return type
                }
                throw IllegalArgumentException("unknown geo_shape [$shapename]")
            }
        }
    }
}
