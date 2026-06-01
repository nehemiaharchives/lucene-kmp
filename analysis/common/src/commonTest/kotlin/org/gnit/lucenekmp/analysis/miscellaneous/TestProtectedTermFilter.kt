package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.Token
import kotlin.test.Test

class TestProtectedTermFilter : BaseTokenStreamTestCase() {
    @Test
    fun testBasic() {
        val cts =
            CannedTokenStream(
                Token("Alice", 1, 0, 5),
                Token("Bob", 1, 6, 9),
                Token("Clara", 1, 10, 15),
                Token("David", 1, 16, 21)
            )

        val protectedTerms = CharArraySet(5, true)
        protectedTerms.add("bob")

        val ts: TokenStream = ProtectedTermFilter(protectedTerms, cts, ::LowerCaseFilter)
        assertTokenStreamContents(ts, arrayOf("alice", "Bob", "clara", "david"))
    }
}
