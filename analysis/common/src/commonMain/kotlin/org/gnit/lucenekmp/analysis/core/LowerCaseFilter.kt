package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * Normalizes token text to lower case.
 *
 *
 * This class moved to Lucene Core, but a reference in the `analysis/common` module is
 * preserved for documentation purposes and consistency with filter factory.
 *
 * @see org.apache.lucene.analysis.LowerCaseFilter
 *
 * @see LowerCaseFilterFactory
 */
//@IgnoreRandomChains(reason = "clones of core's filters")
class LowerCaseFilter
/**
 * Create a new LowerCaseFilter, that normalizes token text to lower case.
 *
 * @param in TokenStream to filter
 */
    (`in`: TokenStream) : LowerCaseFilter(`in`)