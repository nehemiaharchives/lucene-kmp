package org.gnit.lucenekmp.analysis.de

/**
 * A stemmer for German words.
 *
 * The algorithm is based on the report "A Fast and Simple Stemming Algorithm for German Words".
 */
class GermanStemmer {
    /** Buffer for the terms while stemming them. */
    private val sb = StringBuilder()

    /** Amount of characters that are removed with substitute() while stemming. */
    private var substCount = 0

    /**
     * Stems the given term to a unique discriminator.
     */
    fun stem(term: String): String {
        val t = term.lowercase()
        if (!isStemmable(t)) return t
        sb.setLength(0)
        sb.append(t)
        substitute(sb)
        strip(sb)
        optimize(sb)
        resubstitute(sb)
        removeParticleDenotion(sb)
        return sb.toString()
    }

    /** Checks if a term could be stemmed. */
    private fun isStemmable(term: String): Boolean {
        for (c in term) {
            if (!c.isLetter()) return false
        }
        return true
    }

    /**
     * Suffix stripping (stemming) on the current term.
     */
    private fun strip(buffer: StringBuilder) {
        var doMore = true
        while (doMore && buffer.length > 3) {
            val length = buffer.length
            when {
                (length + substCount > 5) && buffer.substring(length - 2, length) == "nd" -> {
                    buffer.deleteRange(length - 2, length)
                }
                (length + substCount > 4) && buffer.substring(length - 2, length) == "em" -> {
                    buffer.deleteRange(length - 2, length)
                }
                (length + substCount > 4) && buffer.substring(length - 2, length) == "er" -> {
                    buffer.deleteRange(length - 2, length)
                }
                buffer[length - 1] == 'e' -> buffer.deleteAt(length - 1)
                buffer[length - 1] == 's' -> buffer.deleteAt(length - 1)
                buffer[length - 1] == 'n' -> buffer.deleteAt(length - 1)
                buffer[length - 1] == 't' -> buffer.deleteAt(length - 1)
                else -> doMore = false
            }
        }
    }

    /** Does some optimizations on the term. This optimizations are contextual. */
    private fun optimize(buffer: StringBuilder) {
        if (buffer.length > 5 && buffer.substring(buffer.length - 5, buffer.length) == "erin*") {
            buffer.deleteAt(buffer.length - 1)
            strip(buffer)
        }
        if (buffer.isNotEmpty() && buffer[buffer.length - 1] == 'z') {
            buffer.set(buffer.length - 1, 'x')
        }
    }

    /** Removes a particle denotion ("ge") from a term. */
    private fun removeParticleDenotion(buffer: StringBuilder) {
        if (buffer.length > 4) {
            var c = 0
            while (c < buffer.length - 3) {
                if (buffer.substring(c, c + 4) == "gege") {
                    buffer.deleteRange(c, c + 2)
                    return
                }
                c++
            }
        }
    }

    /**
     * Do some substitutions for the term to reduce overstemming.
     */
    private fun substitute(buffer: StringBuilder) {
        substCount = 0
        var c = 0
        while (c < buffer.length) {
            if (c > 0 && buffer[c] == buffer[c - 1]) {
                buffer.set(c, '*')
            } else if (buffer[c] == 'ä') {
                buffer.set(c, 'a')
            } else if (buffer[c] == 'ö') {
                buffer.set(c, 'o')
            } else if (buffer[c] == 'ü') {
                buffer.set(c, 'u')
            } else if (buffer[c] == 'ß') {
                buffer.set(c, 's')
                buffer.insert(c + 1, 's')
                substCount++
            }

            if (c < buffer.length - 1) {
                if (c < buffer.length - 2 && buffer[c] == 's' && buffer[c + 1] == 'c' && buffer[c + 2] == 'h') {
                    buffer.set(c, '$')
                    buffer.deleteRange(c + 1, c + 3)
                    substCount += 2
                } else if (buffer[c] == 'c' && buffer[c + 1] == 'h') {
                    buffer.set(c, '§')
                    buffer.deleteAt(c + 1)
                    substCount++
                } else if (buffer[c] == 'e' && buffer[c + 1] == 'i') {
                    buffer.set(c, '%')
                    buffer.deleteAt(c + 1)
                    substCount++
                } else if (buffer[c] == 'i' && buffer[c + 1] == 'e') {
                    buffer.set(c, '&')
                    buffer.deleteAt(c + 1)
                    substCount++
                } else if (buffer[c] == 'i' && buffer[c + 1] == 'g') {
                    buffer.set(c, '#')
                    buffer.deleteAt(c + 1)
                    substCount++
                } else if (buffer[c] == 's' && buffer[c + 1] == 't') {
                    buffer.set(c, '!')
                    buffer.deleteAt(c + 1)
                    substCount++
                }
            }
            c++
        }
    }

    /**
     * Undoes the changes made by substitute().
     */
    private fun resubstitute(buffer: StringBuilder) {
        var c = 0
        while (c < buffer.length) {
            when (buffer[c]) {
                '*' -> buffer.set(c, buffer[c - 1])
                '$' -> {
                    buffer.set(c, 's')
                    buffer.insert(c + 1, "ch")
                }
                '§' -> {
                    buffer.set(c, 'c')
                    buffer.insert(c + 1, 'h')
                }
                '%' -> {
                    buffer.set(c, 'e')
                    buffer.insert(c + 1, 'i')
                }
                '&' -> {
                    buffer.set(c, 'i')
                    buffer.insert(c + 1, 'e')
                }
                '#' -> {
                    buffer.set(c, 'i')
                    buffer.insert(c + 1, 'g')
                }
                '!' -> {
                    buffer.set(c, 's')
                    buffer.insert(c + 1, 't')
                }
            }
            c++
        }
    }
}
