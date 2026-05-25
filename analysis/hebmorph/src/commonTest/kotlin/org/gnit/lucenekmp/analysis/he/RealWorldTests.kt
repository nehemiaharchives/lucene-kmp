package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.he.datastructures.DictRadix
import org.gnit.lucenekmp.jdkport.StringReader
import kotlin.test.Test
import kotlin.test.assertTrue

class RealWorldTests {
    private val specialTokenizationCases = DictRadix<Byte>(false).also {
        it.addNode("H&M", 0)
        it.addNode("C++", 0)
        it.addNode("i-phone", 0)
        it.addNode("i-pad", 0)
    }

    @Test
    fun testSpecialTokenizationCases() {
        val token = Reference("")
        val results = ArrayList<Token>()

        val contents = "H&M כתבה C++ ל-i-phone ול-i-pad"
        val sl = StreamLemmatizer(StringReader(contents), HebrewTestUtil.dictionary, specialTokenizationCases)
        var seenSpecialCase = false
        while (sl.getLemmatizeNextToken(token, results) != 0) {
            if (token.ref == "H&M" || token.ref == "C++" || token.ref == "i-phone" || token.ref == "i-pad") {
                seenSpecialCase = true
            }
        }
        assertTrue(seenSpecialCase)
    }
}
