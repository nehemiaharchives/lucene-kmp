/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnit.lucenekmp.queryparser.flexible.standard.parser

import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.queryparser.charstream.CharStream
import org.gnit.lucenekmp.queryparser.charstream.FastCharStream
import org.gnit.lucenekmp.queryparser.flexible.core.QueryNodeParseException
import org.gnit.lucenekmp.queryparser.flexible.core.messages.QueryParserMessages
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.AndQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.BooleanQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.BoostQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FuzzyQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.GroupQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.ModifierQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.OrQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QuotedFieldQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.SlopQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.parser.SyntaxParser
import org.gnit.lucenekmp.queryparser.flexible.messages.MessageImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.IntervalQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.MinShouldMatchNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.RegexpQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.TermRangeQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.After
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.AnalyzedText
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.AtLeast
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Before
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.ContainedBy
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Containing
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Extend
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.FuzzyTerm
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.IntervalFunction
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.MaxGaps
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.MaxWidth
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.NonOverlapping
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.NotContainedBy
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.NotContaining
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.NotWithin
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Or
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Ordered
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Overlapping
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Phrase
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Unordered
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.UnorderedNoOverlaps
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Wildcard
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Within
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.EscapeQuerySyntaxImpl.Companion.discardEscapeChar
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.AFTER
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.AND
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.ATLEAST
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.BEFORE
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.CARAT
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.CONTAINED_BY
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.CONTAINING
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.EOF
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.EXTEND
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.FN_OR
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.FN_PREFIX
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.FUZZYTERM
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.LPAREN
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.MAXGAPS
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.MAXWIDTH
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.MINUS
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.NON_OVERLAPPING
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.NOT
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.NOT_CONTAINED_BY
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.NOT_CONTAINING
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.NOT_WITHIN
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.NUMBER
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.OP_COLON
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.OP_EQUAL
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.OP_LESSTHAN
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.OP_LESSTHANEQ
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.OP_MORETHAN
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.OP_MORETHANEQ
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.OR
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.ORDERED
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.OVERLAPPING
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.PHRASE
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.PLUS
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.QUOTED
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.RANGEEX_END
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.RANGEEX_START
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.RANGEIN_END
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.RANGEIN_START
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.RANGE_GOOP
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.RANGE_QUOTED
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.RANGE_TO
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.REGEXPTERM
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.RPAREN
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.TERM
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.TILDE
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.UNORDERED
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.UNORDERED_NO_OVERLAPS
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.WILDCARD
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.WITHIN
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants.Companion.tokenImage
import org.gnit.lucenekmp.search.FuzzyQuery

/** Parser for the standard Lucene syntax */
class StandardSyntaxParser : SyntaxParser, StandardSyntaxParserConstants {
    private var tokenSource: StandardSyntaxParserTokenManager
    private var tokens: MutableList<Token> = mutableListOf()
    private var position: Int = 0

    constructor() : this(FastCharStream(StringReader("")))

    constructor(stream: CharStream) {
        tokenSource = StandardSyntaxParserTokenManager(stream)
        readTokens()
    }

    constructor(tm: StandardSyntaxParserTokenManager) {
        tokenSource = tm
        readTokens()
    }

    /**
     * Parses a query string, returning a [QueryNode].
     *
     * @param query the query string to be parsed.
     * @throws ParseException if the parsing fails
     */
    override fun parse(query: CharSequence, field: CharSequence?): QueryNode {
        try {
            ReInit(FastCharStream(StringReader(query.toString())))
            return TopLevelQuery(field)
        } catch (tme: ParseException) {
            tme.query = query
            throw tme
        } catch (tme: Error) {
            val message = MessageImpl(
                QueryParserMessages.INVALID_SYNTAX_CANNOT_PARSE,
                query,
                tme.message
            )
            val e = QueryNodeParseException(tme)
            e.query = query
            e.setNonLocalizedMessage(message)
            throw e
        }
    }

    /**
     * The top-level rule ensures that there is no garbage after the query string.
     *
     * <pre>{@code
     * TopLevelQuery ::= Query <EOF>
     * }</pre>
     */
    fun TopLevelQuery(field: CharSequence?): QueryNode {
        val q = Query(field)
        consume(EOF)
        return q
    }

    /**
     * A query consists of one or more disjunction queries (solves operator precedence).
     * <pre>{@code
     * Query ::= DisjQuery ( DisjQuery )*
     * DisjQuery ::= ConjQuery ( OR ConjQuery )*
     * ConjQuery ::= ModClause ( AND ModClause )*
     * }</pre>
     */
    private fun Query(field: CharSequence?): QueryNode {
        val clauses = mutableListOf<QueryNode>()
        do {
            clauses.add(DisjQuery(field))
        } while (isClauseStart(peek().kind))

        // Handle the case of a "pure" negation query which
        // needs to be wrapped as a boolean query, otherwise
        // the returned result drops the negation.
        if (clauses.size == 1) {
            val first = clauses[0]
            if (first is ModifierQueryNode &&
                first.modifier == ModifierQueryNode.Modifier.MOD_NOT
            ) {
                clauses[0] = BooleanQueryNode(listOf(first))
            }
        }

        return if (clauses.size == 1) clauses[0] else BooleanQueryNode(clauses)
    }

    /**
     * A disjoint clause consists of one or more conjunction clauses.
     * <pre>{@code
     * DisjQuery ::= ConjQuery ( OR ConjQuery )*
     * }</pre>
     */
    private fun DisjQuery(field: CharSequence?): QueryNode {
        val clauses = mutableListOf<QueryNode>()
        clauses.add(ConjQuery(field))
        while (accept(OR) != null) clauses.add(ConjQuery(field))
        return if (clauses.size == 1) clauses[0] else OrQueryNode(clauses)
    }

    /**
     * A conjunction clause consists of one or more modifier-clause pairs.
     * <pre>{@code
     * ConjQuery ::= ModClause ( AND ModClause )*
     * }</pre>
     */
    private fun ConjQuery(field: CharSequence?): QueryNode {
        val clauses = mutableListOf<QueryNode>()
        clauses.add(ModClause(field))
        while (accept(AND) != null) clauses.add(ModClause(field))
        return if (clauses.size == 1) clauses[0] else AndQueryNode(clauses)
    }

    /**
     * A modifier-atomic clause pair.
     * <pre>{@code
     * ModClause ::= (Modifier)? Clause
     * }</pre>
     */
    private fun ModClause(field: CharSequence?): QueryNode {
        val modifier = when {
            accept(PLUS) != null -> ModifierQueryNode.Modifier.MOD_REQ
            accept(MINUS) != null || accept(NOT) != null -> ModifierQueryNode.Modifier.MOD_NOT
            else -> ModifierQueryNode.Modifier.MOD_NONE
        }
        var q = Clause(field)
        if (modifier != ModifierQueryNode.Modifier.MOD_NONE) {
            q = ModifierQueryNode(q, modifier)
        }
        return q
    }

    /**
     * An atomic clause consists of a field range expression, a potentially field-qualified term or
     * a group.
     *
     * <pre>{@code
     * Clause ::= FieldRangeExpr
     *          | (FieldName (':' | '='))? (Term | GroupingExpr)
     * }</pre>
     */
    private fun Clause(initialField: CharSequence?): QueryNode {
        var field = initialField
        if (peek().kind == TERM && peek(1).kind in COMPARISON_KINDS) {
            return FieldRangeExpr(field)
        }
        if (peek().kind == TERM && (peek(1).kind == OP_COLON || peek(1).kind == OP_EQUAL)) {
            field = FieldName()
            position++
        }
        return when (peek().kind) {
            LPAREN -> GroupingExpr(field)
            FN_PREFIX -> IntervalExpr(field)
            else -> Term(field)
        }
    }

    /** A field name. This utility method strips escape characters from field names. */
    private fun FieldName(): CharSequence {
        val name = consume(TERM)
        return discardEscapeChar(name.image!!)
    }

    /**
     * An grouping expression is a Query with potential boost applied to it.
     *
     * <pre>{@code
     * GroupingExpr ::= '(' Query ')' ('^' <NUMBER>)?
     * }</pre>
     */
    private fun GroupingExpr(field: CharSequence?): QueryNode {
        consume(LPAREN)
        var q = Query(field)
        consume(RPAREN)
        if (peek().kind == CARAT) q = Boost(q)
        val minShouldMatch = if (accept(56) != null) consume(NUMBER) else null
        q = if (minShouldMatch != null) {
            MinShouldMatchNode(parseInt(minShouldMatch), GroupQueryNode(q))
        } else {
            GroupQueryNode(q)
        }
        return q
    }

    /** An interval expression (functions) node. */
    private fun IntervalExpr(field: CharSequence?): IntervalQueryNode {
        val source = IntervalFun()
        return IntervalQueryNode(field?.toString(), source)
    }

    private fun IntervalFun(): IntervalFunction {
        if (peek().kind != FN_PREFIX) return IntervalText()
        return when (peek(1).kind) {
            ATLEAST -> IntervalAtLeast()
            MAXWIDTH -> IntervalMaxWidth()
            MAXGAPS -> IntervalMaxGaps()
            ORDERED -> IntervalOrdered()
            UNORDERED -> IntervalUnordered()
            UNORDERED_NO_OVERLAPS -> IntervalUnorderedNoOverlaps()
            FN_OR -> IntervalOr()
            WILDCARD -> IntervalWildcard()
            AFTER -> IntervalAfter()
            BEFORE -> IntervalBefore()
            PHRASE -> IntervalPhrase()
            CONTAINING -> IntervalContaining()
            NOT_CONTAINING -> IntervalNotContaining()
            CONTAINED_BY -> IntervalContainedBy()
            NOT_CONTAINED_BY -> IntervalNotContainedBy()
            WITHIN -> IntervalWithin()
            NOT_WITHIN -> IntervalNotWithin()
            OVERLAPPING -> IntervalOverlapping()
            NON_OVERLAPPING -> IntervalNonOverlapping()
            EXTEND -> IntervalExtend()
            FUZZYTERM -> IntervalFuzzyTerm()
            else -> throw generateParseException()
        }
    }

    private fun intervalStart(kind: Int) {
        consume(FN_PREFIX)
        consume(kind)
        consume(LPAREN)
    }

    private fun intervalList(kind: Int): MutableList<IntervalFunction> {
        intervalStart(kind)
        val sources = mutableListOf<IntervalFunction>()
        do {
            sources.add(IntervalFun())
        } while (peek().kind != RPAREN)
        consume(RPAREN)
        return sources
    }

    private fun IntervalAtLeast(): IntervalFunction {
        intervalStart(ATLEAST)
        val minShouldMatch = consume(NUMBER)
        val sources = mutableListOf<IntervalFunction>()
        do {
            sources.add(IntervalFun())
        } while (peek().kind != RPAREN)
        consume(RPAREN)
        return AtLeast(parseInt(minShouldMatch), sources)
    }

    private fun IntervalMaxWidth(): IntervalFunction {
        intervalStart(MAXWIDTH)
        val maxWidth = consume(NUMBER)
        val source = IntervalFun()
        consume(RPAREN)
        return MaxWidth(parseInt(maxWidth), source)
    }

    private fun IntervalMaxGaps(): IntervalFunction {
        intervalStart(MAXGAPS)
        val maxGaps = consume(NUMBER)
        val source = IntervalFun()
        consume(RPAREN)
        return MaxGaps(parseInt(maxGaps), source)
    }

    private fun IntervalUnordered(): IntervalFunction = Unordered(intervalList(UNORDERED))

    private fun IntervalUnorderedNoOverlaps(): IntervalFunction {
        intervalStart(UNORDERED_NO_OVERLAPS)
        val a = IntervalFun()
        val b = IntervalFun()
        consume(RPAREN)
        return UnorderedNoOverlaps(a, b)
    }

    private fun IntervalOrdered(): IntervalFunction = Ordered(intervalList(ORDERED))

    private fun IntervalOr(): IntervalFunction = Or(intervalList(FN_OR))

    private fun IntervalPhrase(): IntervalFunction = Phrase(intervalList(PHRASE))

    private fun IntervalBefore(): IntervalFunction {
        intervalStart(BEFORE)
        val source = IntervalFun()
        val reference = IntervalFun()
        consume(RPAREN)
        return Before(source, reference)
    }

    private fun IntervalAfter(): IntervalFunction {
        intervalStart(AFTER)
        val source = IntervalFun()
        val reference = IntervalFun()
        consume(RPAREN)
        return After(source, reference)
    }

    private fun IntervalContaining(): IntervalFunction {
        intervalStart(CONTAINING)
        val big = IntervalFun()
        val small = IntervalFun()
        consume(RPAREN)
        return Containing(big, small)
    }

    private fun IntervalNotContaining(): IntervalFunction {
        intervalStart(NOT_CONTAINING)
        val minuend = IntervalFun()
        val subtrahend = IntervalFun()
        consume(RPAREN)
        return NotContaining(minuend, subtrahend)
    }

    private fun IntervalContainedBy(): IntervalFunction {
        intervalStart(CONTAINED_BY)
        val small = IntervalFun()
        val big = IntervalFun()
        consume(RPAREN)
        return ContainedBy(small, big)
    }

    private fun IntervalNotContainedBy(): IntervalFunction {
        intervalStart(NOT_CONTAINED_BY)
        val small = IntervalFun()
        val big = IntervalFun()
        consume(RPAREN)
        return NotContainedBy(small, big)
    }

    private fun IntervalWithin(): IntervalFunction {
        intervalStart(WITHIN)
        val source = IntervalFun()
        val positions = consume(NUMBER)
        val reference = IntervalFun()
        consume(RPAREN)
        return Within(source, parseInt(positions), reference)
    }

    private fun IntervalExtend(): IntervalFunction {
        intervalStart(EXTEND)
        val source = IntervalFun()
        val before = consume(NUMBER)
        val after = consume(NUMBER)
        consume(RPAREN)
        return Extend(source, parseInt(before), parseInt(after))
    }

    private fun IntervalNotWithin(): IntervalFunction {
        intervalStart(NOT_WITHIN)
        val minuend = IntervalFun()
        val positions = consume(NUMBER)
        val subtrahend = IntervalFun()
        consume(RPAREN)
        return NotWithin(minuend, parseInt(positions), subtrahend)
    }

    private fun IntervalOverlapping(): IntervalFunction {
        intervalStart(OVERLAPPING)
        val source = IntervalFun()
        val reference = IntervalFun()
        consume(RPAREN)
        return Overlapping(source, reference)
    }

    private fun IntervalNonOverlapping(): IntervalFunction {
        intervalStart(NON_OVERLAPPING)
        val minuend = IntervalFun()
        val subtrahend = IntervalFun()
        consume(RPAREN)
        return NonOverlapping(minuend, subtrahend)
    }

    private fun IntervalWildcard(): IntervalFunction {
        intervalStart(WILDCARD)
        val term = consumeOneOf(TERM, NUMBER, QUOTED)
        val wildcard = if (term.kind == QUOTED) {
            term.image!!.substring(1, term.image!!.length - 1)
        } else {
            term.image!!
        }
        val maxExpansions = if (peek().kind == NUMBER) consume(NUMBER) else null
        consume(RPAREN)
        return Wildcard(wildcard, maxExpansions?.let(::parseInt) ?: 0)
    }

    private fun IntervalFuzzyTerm(): IntervalFunction {
        intervalStart(FUZZYTERM)
        val token = consumeOneOf(TERM, NUMBER, QUOTED)
        val term = if (token.kind == QUOTED) {
            token.image!!.substring(1, token.image!!.length - 1)
        } else {
            token.image!!
        }
        val maxEdits = if (peek().kind == NUMBER) consume(NUMBER) else null
        val maxExpansions = if (peek().kind == NUMBER) consume(NUMBER) else null
        consume(RPAREN)
        return FuzzyTerm(term, maxEdits?.let(::parseInt), maxExpansions?.let(::parseInt))
    }

    private fun IntervalText(): IntervalFunction {
        val token = consumeOneOf(QUOTED, TERM, NUMBER)
        return if (token.kind == QUOTED) {
            AnalyzedText(token.image!!.substring(1, token.image!!.length - 1))
        } else {
            AnalyzedText(token.image!!)
        }
    }

    /**
     * Score boost modifier.
     *
     * <pre>{@code
     * Boost ::= '^' <NUMBER>
     * }</pre>
     */
    private fun Boost(node: QueryNode?): QueryNode {
        consume(CARAT)
        val boost = consume(NUMBER)
        return if (node == null) node as QueryNode else BoostQueryNode(node, parseFloat(boost))
    }

    /**
     * Fuzzy term modifier.
     *
     * <pre>{@code
     * Fuzzy ::= '~' <NUMBER>?
     * }</pre>
     */
    private fun FuzzyOp(field: CharSequence?, term: Token, node: QueryNode): QueryNode {
        consume(TILDE)
        val similarity = if (peek().kind == NUMBER) consume(NUMBER) else null
        var fms = FuzzyQuery.defaultMaxEdits.toFloat()
        if (similarity != null) {
            fms = parseFloat(similarity)
            if (fms < 0.0f) {
                throw ParseException(MessageImpl(QueryParserMessages.INVALID_SYNTAX_FUZZY_LIMITS))
            } else if (fms >= 1.0f && fms != fms.toInt().toFloat()) {
                throw ParseException(MessageImpl(QueryParserMessages.INVALID_SYNTAX_FUZZY_EDITS))
            }
        }
        return FuzzyQueryNode(
            field,
            discardEscapeChar(term.image!!),
            fms,
            term.beginColumn,
            term.endColumn
        )
    }

    /**
     * A field range expression selects all field values larger/ smaller (or equal) than a given one.
     * <pre>{@code
     * FieldRangeExpr ::= FieldName ('<' | '>' | '<=' | '>=') (<TERM> | <QUOTED> | <NUMBER>)
     * }</pre>
     */
    private fun FieldRangeExpr(initialField: CharSequence?): TermRangeQueryNode {
        val field = FieldName()
        val operator = consumeOneOf(
            OP_LESSTHAN,
            OP_LESSTHANEQ,
            OP_MORETHAN,
            OP_MORETHANEQ
        )
        val term = consumeOneOf(TERM, QUOTED, NUMBER)
        if (term.kind == QUOTED) {
            term.image = term.image!!.substring(1, term.image!!.length - 1)
        }
        val qLower: FieldQueryNode
        val qUpper: FieldQueryNode
        val lowerInclusive: Boolean
        val upperInclusive: Boolean
        when (operator.kind) {
            OP_LESSTHAN -> {
                lowerInclusive = true
                upperInclusive = false
                qLower = FieldQueryNode(field, "*", term.beginColumn, term.endColumn)
                qUpper = FieldQueryNode(
                    field,
                    discardEscapeChar(term.image!!),
                    term.beginColumn,
                    term.endColumn
                )
            }
            OP_LESSTHANEQ -> {
                lowerInclusive = true
                upperInclusive = true
                qLower = FieldQueryNode(field, "*", term.beginColumn, term.endColumn)
                qUpper = FieldQueryNode(
                    field,
                    discardEscapeChar(term.image!!),
                    term.beginColumn,
                    term.endColumn
                )
            }
            OP_MORETHAN -> {
                lowerInclusive = false
                upperInclusive = true
                qLower = FieldQueryNode(
                    field,
                    discardEscapeChar(term.image!!),
                    term.beginColumn,
                    term.endColumn
                )
                qUpper = FieldQueryNode(field, "*", term.beginColumn, term.endColumn)
            }
            OP_MORETHANEQ -> {
                lowerInclusive = true
                upperInclusive = true
                qLower = FieldQueryNode(
                    field,
                    discardEscapeChar(term.image!!),
                    term.beginColumn,
                    term.endColumn
                )
                qUpper = FieldQueryNode(field, "*", term.beginColumn, term.endColumn)
            }
            else -> throw Error("Unhandled case, operator=$operator")
        }
        return TermRangeQueryNode(qLower, qUpper, lowerInclusive, upperInclusive)
    }

    /**
     * A term expression.
     *
     * <pre>{@code
     * Term ::= (<TERM> | <NUMBER>) ('~' <NUM>)? ('^' <NUM>)?
     *        | <REGEXPTERM> ('^' <NUM>)?
     *        | TermRangeExpr ('^' <NUM>)?
     *        | QuotedTerm ('^' <NUM>)?
     * }</pre>
     */
    private fun Term(field: CharSequence?): QueryNode {
        var q: QueryNode = when (peek().kind) {
            REGEXPTERM -> {
                val term = consume(REGEXPTERM)
                val v = term.image!!.substring(1, term.image!!.length - 1)
                RegexpQueryNode(field, v, 0, v.length)
            }
            TERM, NUMBER -> {
                val term = consumeOneOf(TERM, NUMBER)
                var node: QueryNode = FieldQueryNode(
                    field,
                    discardEscapeChar(term.image!!),
                    term.beginColumn,
                    term.endColumn
                )
                if (peek().kind == TILDE) node = FuzzyOp(field, term, node)
                node
            }
            RANGEIN_START, RANGEEX_START -> TermRangeExpr(field)
            QUOTED -> QuotedTerm(field)
            else -> throw generateParseException()
        }
        if (peek().kind == CARAT) q = Boost(q)
        return q
    }

    /**
     * A quoted term (phrase).
     *
     * <pre>{@code
     * QuotedTerm ::= <QUOTED> ('~' <NUM>)?
     * }</pre>
     */
    private fun QuotedTerm(field: CharSequence?): QueryNode {
        val term = consume(QUOTED)
        val image = term.image!!.substring(1, term.image!!.length - 1)
        var q: QueryNode = QuotedFieldQueryNode(
            field,
            discardEscapeChar(image),
            term.beginColumn + 1,
            term.endColumn - 1
        )
        if (accept(TILDE) != null) {
            val slop = consume(NUMBER)
            q = SlopQueryNode(q, parseInt(slop))
        }
        return q
    }

    /**
     * A value range expression.
     *
     * <pre>{@code
     * TermRangeExpr ::= ('[' | '{') <RANGE_START> 'TO' <RANGE_END> (']' | '}')
     * }</pre>
     */
    private fun TermRangeExpr(field: CharSequence?): TermRangeQueryNode {
        var leftInclusive = false
        var rightInclusive = false

        // RANGE_TO can be consumed as range start/end because this needs to be accepted as a valid range:
        // [TO TO TO]
        if (accept(RANGEIN_START) != null) leftInclusive = true else consume(RANGEEX_START)
        val left = consumeOneOf(RANGE_GOOP, RANGE_QUOTED, RANGE_TO)
        consume(RANGE_TO)
        val right = consumeOneOf(RANGE_GOOP, RANGE_QUOTED, RANGE_TO)
        if (accept(RANGEIN_END) != null) rightInclusive = true else consume(RANGEEX_END)

        if (left.kind == RANGE_QUOTED) {
            left.image = left.image!!.substring(1, left.image!!.length - 1)
        }
        if (right.kind == RANGE_QUOTED) {
            right.image = right.image!!.substring(1, right.image!!.length - 1)
        }

        val qLower = FieldQueryNode(
            field,
            discardEscapeChar(left.image!!),
            left.beginColumn,
            left.endColumn
        )
        val qUpper = FieldQueryNode(
            field,
            discardEscapeChar(right.image!!),
            right.beginColumn,
            right.endColumn
        )

        return TermRangeQueryNode(qLower, qUpper, leftInclusive, rightInclusive)
    }

    /** Reinitialise. */
    fun ReInit(stream: CharStream) {
        tokenSource.ReInit(stream)
        readTokens()
    }

    /** Reinitialise. */
    fun ReInit(tm: StandardSyntaxParserTokenManager) {
        tokenSource = tm
        readTokens()
    }

    /** Get the next Token. */
    fun getNextToken(): Token {
        val token = peek()
        if (position < tokens.size - 1) position++
        return token
    }

    /** Get the specific Token. */
    fun getToken(index: Int): Token = peek(index)

    /** Generate ParseException. */
    fun generateParseException(): ParseException {
        val expected = if (peek().kind == EOF) EOF else peek().kind
        return ParseException(previous(), arrayOf(intArrayOf(expected)), tokenImage)
    }

    /** Trace enabled. */
    fun trace_enabled(): Boolean = false

    /** Enable tracing. */
    fun enable_tracing() {}

    /** Disable tracing. */
    fun disable_tracing() {}

    private fun readTokens() {
        tokens = mutableListOf()
        do {
            val token = tokenSource.getNextToken()
            if (tokens.isNotEmpty()) tokens.last().next = token
            tokens.add(token)
        } while (token.kind != EOF)
        position = 0
    }

    private fun peek(ahead: Int = 0): Token {
        return tokens[(position + ahead).coerceAtMost(tokens.lastIndex)]
    }

    private fun previous(): Token {
        return tokens[(position - 1).coerceAtLeast(0)]
    }

    private fun accept(kind: Int): Token? {
        if (peek().kind != kind) return null
        return consume(kind)
    }

    private fun consume(kind: Int): Token {
        val token = peek()
        if (token.kind != kind) {
            throw ParseException(previous(), arrayOf(intArrayOf(kind)), tokenImage)
        }
        position++
        return token
    }

    private fun consumeOneOf(vararg kinds: Int): Token {
        val token = peek()
        if (token.kind !in kinds) {
            throw ParseException(previous(), kinds.map { intArrayOf(it) }.toTypedArray(), tokenImage)
        }
        position++
        return token
    }

    private fun isClauseStart(kind: Int): Boolean {
        return kind == PLUS || kind == MINUS || kind == NOT || kind == TERM || kind == NUMBER ||
            kind == REGEXPTERM || kind == RANGEIN_START || kind == RANGEEX_START ||
            kind == QUOTED || kind == LPAREN || kind == FN_PREFIX
    }

    companion object {
        private val COMPARISON_KINDS = setOf(
            OP_LESSTHAN,
            OP_LESSTHANEQ,
            OP_MORETHAN,
            OP_MORETHANEQ
        )

        fun parseFloat(token: Token): Float = token.image!!.toFloat()

        fun parseInt(token: Token): Int = token.image!!.toInt()
    }
}
