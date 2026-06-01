package org.gnit.lucenekmp.analysis.synonym

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.jdkport.LineNumberReader
import org.gnit.lucenekmp.jdkport.ParseException
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.CharsRef
import org.gnit.lucenekmp.util.CharsRefBuilder

/**
 * Parser for wordnet prolog format
 *
 * <p>See https://wordnet.princeton.edu/documentation/prologdb5wn for a description of the format.
 *
 * @lucene.experimental
 */
// TODO: allow you to specify syntactic categories (e.g. just nouns, etc)
class WordnetSynonymParser(dedup: Boolean, private val expand: Boolean, analyzer: Analyzer) :
    SynonymMap.Parser(dedup, analyzer) {

    override fun parse(input: Reader) {
        val br = LineNumberReader(input)
        try {
            var line: String?
            var lastSynSetID = ""
            var synset = arrayOfNulls<CharsRef>(8)
            var synsetSize = 0

            while (br.readLine().also { line = it } != null) {
                val synSetID = line!!.substring(2, 11)

                if (synSetID != lastSynSetID) {
                    addInternal(synset, synsetSize)
                    synsetSize = 0
                }

                synset = ArrayUtil.grow(synset, synsetSize + 1)
                synset[synsetSize] = parseSynonym(line, CharsRefBuilder())
                synsetSize++
                lastSynSetID = synSetID
            }

            // final synset in the file
            addInternal(synset, synsetSize)
        } catch (e: IllegalArgumentException) {
            val ex = ParseException("Invalid synonym rule at line ${br.lineNumber}", 0)
            ex.initCause(e)
            throw ex
        } finally {
            br.close()
        }
    }

    @Throws(IOException::class)
    private fun parseSynonym(line: String, reuse: CharsRefBuilder?): CharsRef {
        var actualReuse = reuse
        if (actualReuse == null) {
            actualReuse = CharsRefBuilder()
        }

        val start = line.indexOf('\'') + 1
        val end = line.lastIndexOf('\'')

        val text = line.substring(start, end).replace("''", "'")
        return analyze(text, actualReuse)
    }

    private fun addInternal(synset: Array<CharsRef?>, size: Int) {
        if (size <= 1) {
            return // nothing to do
        }

        if (expand) {
            for (i in 0 until size) {
                for (j in 0 until size) {
                    if (i != j) {
                        add(synset[i]!!, synset[j]!!, true)
                    }
                }
            }
        } else {
            for (i in 0 until size) {
                add(synset[i]!!, synset[0]!!, false)
            }
        }
    }
}
