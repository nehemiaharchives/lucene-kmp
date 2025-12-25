package org.gnit.lucenekmp.tests.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.jdkport.PrintWriter


/** Consumes a TokenStream and outputs the dot (graphviz) string (graph).  */
class TokenStreamToDot(
    inputText: String,
    `in`: TokenStream,
    out: PrintWriter
) {
    private val `in`: TokenStream
    private val termAtt: CharTermAttribute
    private val posIncAtt: PositionIncrementAttribute
    private val posLengthAtt: PositionLengthAttribute
    private val offsetAtt: OffsetAttribute?
    private val inputText: String?
    protected val out: PrintWriter

    @Throws(IOException::class)
    fun toDot() {
        `in`.reset()
        writeHeader()

        // TODO: is there some way to tell dot that it should
        // make the "main path" a straight line and have the
        // non-sausage arcs not affect node placement...
        var pos = -1
        var lastEndPos = -1
        while (`in`.incrementToken()) {
            val isFirst = pos == -1
            var posInc: Int = posIncAtt.getPositionIncrement()
            if (isFirst && posInc == 0) {
                // TODO: hmm are TS's still allowed to do this...
                /*java.lang.System.err.*/println("WARNING: first posInc was 0; correcting to 1")
                posInc = 1
            }

            if (posInc > 0) {
                // New node:
                pos += posInc
                writeNode(pos, pos.toString())
            }

            if (posInc > 1) {
                // Gap!
                writeArc(lastEndPos, pos, null, "dotted")
            }

            if (isFirst) {
                writeNode(-1, null)
                writeArc(-1, pos, null, null)
            }

            var arcLabel = termAtt.toString()
            if (offsetAtt != null) {
                val startOffset: Int = offsetAtt.startOffset()
                val endOffset: Int = offsetAtt.endOffset()
                // System.out.println("start=" + startOffset + " end=" + endOffset + " len=" +
                // inputText.length());
                if (inputText != null) {
                    val fragment = inputText.substring(startOffset, endOffset)
                    if (fragment == termAtt.toString() == false) {
                        arcLabel += " / $fragment"
                    }
                } else {
                    arcLabel += " / $startOffset-$endOffset"
                }
            }

            writeArc(pos, pos + posLengthAtt.positionLength, arcLabel, null)
            lastEndPos = pos + posLengthAtt.positionLength
        }

        `in`.end()

        if (lastEndPos != -1) {
            // TODO: should we output any final text (from end
            // offsets) on this arc...
            writeNode(-2, null)
            writeArc(lastEndPos, -2, null, null)
        }

        writeTrailer()
    }

    protected fun writeArc(fromNode: Int, toNode: Int, label: String?, style: String?) {
        out.print("  $fromNode -> $toNode [")
        if (label != null) {
            out.print(" label=\"$label\"")
        }
        if (style != null) {
            out.print(" style=\"$style\"")
        }
        out.println("]")
    }

    protected fun writeNode(name: Int, label: String?) {
        out.print("  $name")
        if (label != null) {
            out.print(" [label=\"$label\"]")
        } else {
            out.print(" [shape=point color=white]")
        }
        out.println()
    }

    /**
     * If inputText is non-null, and the TokenStream has offsets, we include the surface form in each
     * arc's label.
     */
    init {
        this.`in` = `in`
        this.out = out
        this.inputText = inputText
        termAtt =
            `in`.addAttribute<CharTermAttribute>(CharTermAttribute::class)
        posIncAtt =
            `in`.addAttribute<PositionIncrementAttribute>(
                PositionIncrementAttribute::class
            )
        posLengthAtt =
            `in`.addAttribute<PositionLengthAttribute>(
                PositionLengthAttribute::class
            )
        if (`in`.hasAttribute(OffsetAttribute::class)) {
            offsetAtt =
                `in`.addAttribute<OffsetAttribute>(OffsetAttribute::class)
        } else {
            offsetAtt = null
        }
    }

    /** Override to customize.  */
    protected fun writeHeader() {
        out.println("digraph tokens {")
        out.println(
            "  graph [ fontsize=30 labelloc=\"t\" label=\"\" splines=true overlap=false rankdir = \"LR\" ];"
        )
        out.println("  // A2 paper size")
        out.println("  size = \"34.4,16.5\";")
        // out.println("  // try to fill paper");
        // out.println("  ratio = fill;");
        out.println("  edge [ fontname=\"$FONT_NAME\" fontcolor=\"red\" color=\"#606060\" ]")
        out.println(
            ("  node [ style=\"filled\" fillcolor=\"#e8e8f0\" shape=\"Mrecord\" fontname=\""
                    + FONT_NAME
                    + "\" ]")
        )
        out.println()
    }

    /** Override to customize.  */
    protected fun writeTrailer() {
        out.println("}")
    }

    companion object {
        private const val FONT_NAME = "Helvetica"
    }
}
