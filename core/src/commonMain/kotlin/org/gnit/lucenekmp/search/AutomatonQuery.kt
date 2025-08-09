package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton

/**
 * A [Query] that will match terms against a finite-state machine.
 *
 *
 * This query will match documents that contain terms accepted by a given finite-state machine.
 * The automaton can be constructed with the [org.apache.lucene.util.automaton] API.
 * Alternatively, it can be created from a regular expression with [RegexpQuery] or from the
 * standard Lucene wildcard syntax with [WildcardQuery].
 *
 *
 * When the query is executed, it will will enumerate the term dictionary in an intelligent way
 * to reduce the number of comparisons. For example: the regular expression of `[dl]og`
 * will make approximately four comparisons: do, dog, lo, and log.
 *
 * @lucene.experimental
 */
open class AutomatonQuery(
    /** term containing the field, and possibly some pattern structure  */
    protected open val term: Term,
    /** the automaton to match index terms against  */
    protected val automaton: Automaton,
    isBinary: Boolean = false,
    rewriteMethod: RewriteMethod = CONSTANT_SCORE_BLENDED_REWRITE
) : MultiTermQuery(term.field(), rewriteMethod), Accountable {

    protected val compiled: CompiledAutomaton = CompiledAutomaton(automaton, false,
        simplify = true,
        isBinary = isBinary
    )

    /** Is this a binary (byte) oriented automaton. See the constructor.  */
    val isAutomatonBinary: Boolean = isBinary

    private val ramBytesUsed: Long =
        BASE_RAM_BYTES + term.ramBytesUsed() + automaton.ramBytesUsed() + compiled.ramBytesUsed() // cache

    /**
     * Create a new AutomatonQuery from an [Automaton].
     *
     * @param term Term containing field and possibly some pattern structure. The term text is
     * ignored.
     * @param automaton Automaton to run, terms that are accepted are considered a match.
     * @param isBinary if true, this automaton is already binary and will not go through the
     * UTF32ToUTF8 conversion
     * @param rewriteMethod the rewriteMethod to use to build the final query from the automaton
     */
    /**
     * Create a new AutomatonQuery from an [Automaton].
     *
     * @param term Term containing field and possibly some pattern structure. The term text is
     * ignored.
     * @param automaton Automaton to run, terms that are accepted are considered a match.
     * @param isBinary if true, this automaton is already binary and will not go through the
     * UTF32ToUTF8 conversion
     */

    @Throws(IOException::class)
    override fun getTermsEnum(
        terms: Terms,
        atts: AttributeSource
    ): TermsEnum {
        return compiled.getTermsEnum(terms)
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        result = prime * result + compiled.hashCode()
        result = prime * result + (if (term == null) 0 else term.hashCode())
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (!super.equals(obj)) return false
        if (this::class != obj!!::class) return false
        val other = obj as AutomatonQuery
        if (compiled != other.compiled) return false
        if (term == null) {
            if (other.term != null) return false
        } else if (term != other.term) return false
        return true
    }

    override fun toString(field: String?): String {
        val buffer = StringBuilder()
        if (term.field() != field) {
            buffer.append(term.field())
            buffer.append(":")
        }
        buffer.append(this::class.simpleName)
        buffer.append(" {")
        buffer.append('\n')
        buffer.append(automaton.toString())
        buffer.append("}")
        return buffer.toString()
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            compiled.visit(visitor, this, field)
        }
    }

    /** Returns the automaton used to create this query  */
    /*fun getAutomaton(): Automaton {
        return automaton
    }*/

    /*fun getCompiled(): CompiledAutomaton {
        return compiled
    }*/

    override fun ramBytesUsed(): Long {
        return ramBytesUsed
    }

    companion object {
        private val BASE_RAM_BYTES: Long =
            RamUsageEstimator.shallowSizeOfInstance(AutomatonQuery::class)
    }
}
