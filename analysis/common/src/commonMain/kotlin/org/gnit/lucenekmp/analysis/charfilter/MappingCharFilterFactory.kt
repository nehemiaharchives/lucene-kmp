package org.gnit.lucenekmp.analysis.charfilter

import okio.IOException
import org.gnit.lucenekmp.analysis.CharFilterFactory
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.ResourceLoaderAware

/**
 * Factory for [MappingCharFilter].
 *
 * @since Solr 1.4
 */
open class MappingCharFilterFactory : CharFilterFactory, ResourceLoaderAware {
    protected var normMap: NormalizeCharMap? = null
    private var mapping: String? = null

    /** Creates a new MappingCharFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        mapping = get(args, "mapping")
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    @Throws(IOException::class)
    override fun inform(loader: ResourceLoader) {
        val mapping = mapping
        if (mapping != null) {
            val files = splitFileNames(mapping)
            val wlist = mutableListOf<String>()
            for (file in files) {
                val lines = getLines(loader, file.trim())
                wlist.addAll(lines)
            }
            val builder = NormalizeCharMap.Builder()
            parseRules(wlist, builder)
            normMap = builder.build()
            if (normMap?.map == null) {
                // if the inner FST is null, it means it accepts nothing (e.g. the file is empty)
                // so just set the whole map to null
                normMap = null
            }
        }
    }

    override fun create(input: Reader): Reader {
        // if the map is null, it means there's actually no mappings... just return the original stream
        // as there is nothing to do here.
        val normMap = normMap
        return if (normMap == null) input else MappingCharFilter(normMap, input)
    }

    override fun normalize(input: Reader): Reader {
        return create(input)
    }

    protected fun parseRules(rules: List<String>, builder: NormalizeCharMap.Builder) {
        for (rule in rules) {
            val m = p.find(rule)
                ?: throw IllegalArgumentException("Invalid Mapping Rule : [$rule], file = $mapping")
            builder.add(parseString(m.groupValues[1]), parseString(m.groupValues[2]))
        }
    }

    internal var out = CharArray(256)

    internal fun parseString(s: String): String {
        var readPos = 0
        val len = s.length
        var writePos = 0
        while (readPos < len) {
            var c = s[readPos++]
            if (c == '\\') {
                if (readPos >= len) {
                    throw IllegalArgumentException("Invalid escaped char in [$s]")
                }
                c = s[readPos++]
                when (c) {
                    '\\' -> c = '\\'
                    '"' -> c = '"'
                    'n' -> c = '\n'
                    't' -> c = '\t'
                    'r' -> c = '\r'
                    'b' -> c = '\b'
                    'f' -> c = '\u000C'
                    'u' -> {
                        if (readPos + 3 >= len) {
                            throw IllegalArgumentException("Invalid escaped char in [$s]")
                        }
                        c = s.substring(readPos, readPos + 4).toInt(16).toChar()
                        readPos += 4
                    }
                }
            }
            out[writePos++] = c
        }
        return out.concatToString(0, writePos)
    }

    companion object {
        /** SPI name */
        const val NAME: String = "mapping"

        // "source" => "target"
        private val p = Regex("\"(.*)\"\\s*=>\\s*\"(.*)\"\\s*$")
    }
}

