package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.RegExp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Tests the FieldcacheRewriteMethod with random regular expressions */
class TestFieldCacheRewriteMethod : TestRegexpRandom2() {
    /** Test fieldcache rewrite against filter rewrite */
    @Throws(IOException::class)
    override fun assertSame(regexp: String) {
        val fieldCache =
            RegexpQuery(
                Term(fieldName, regexp),
                RegExp.NONE,
                0,
                RegexpQuery.DEFAULT_PROVIDER,
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                DocValuesRewriteMethod(),
            )

        val filter =
            RegexpQuery(
                Term(fieldName, regexp),
                RegExp.NONE,
                0,
                RegexpQuery.DEFAULT_PROVIDER,
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                MultiTermQuery.CONSTANT_SCORE_REWRITE,
            )

        val filter2 =
            RegexpQuery(
                Term(fieldName, regexp),
                RegExp.NONE,
                0,
                RegexpQuery.DEFAULT_PROVIDER,
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE,
            )

        val fieldCacheDocs = searcher1.search(fieldCache, 25)
        val filterDocs = searcher2.search(filter, 25)
        val filter2Docs = searcher2.search(filter2, 25)

        CheckHits.checkEqual(fieldCache, fieldCacheDocs.scoreDocs, filterDocs.scoreDocs)
        CheckHits.checkEqual(fieldCache, fieldCacheDocs.scoreDocs, filter2Docs.scoreDocs)
    }

    @Test
    @Throws(Exception::class)
    fun testEquals() {
        run {
            val a1 = RegexpQuery(Term(fieldName, "[aA]"), RegExp.NONE)
            val a2 = RegexpQuery(Term(fieldName, "[aA]"), RegExp.NONE)
            val b = RegexpQuery(Term(fieldName, "[bB]"), RegExp.NONE)
            assertEquals(a1, a2)
            assertFalse(a1 == b)
            QueryUtils.check(a1)
        }

        run {
            val a1 =
                RegexpQuery(
                    Term(fieldName, "[aA]"),
                    RegExp.NONE,
                    0,
                    RegexpQuery.DEFAULT_PROVIDER,
                    Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                    DocValuesRewriteMethod(),
                )
            val a2 =
                RegexpQuery(
                    Term(fieldName, "[aA]"),
                    RegExp.NONE,
                    0,
                    RegexpQuery.DEFAULT_PROVIDER,
                    Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                    DocValuesRewriteMethod(),
                )
            val b =
                RegexpQuery(
                    Term(fieldName, "[bB]"),
                    RegExp.NONE,
                    0,
                    RegexpQuery.DEFAULT_PROVIDER,
                    Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                    DocValuesRewriteMethod(),
                )
            assertEquals(a1, a2)
            assertFalse(a1 == b)
            QueryUtils.check(a1)
        }
    }

    // tests inherited from TestRegexpRandom2
    @Test
    override fun testRegexps() = super.testRegexps()
}
