package org.gnit.lucenekmp.search


/** Represents hits returned by [IndexSearcher.search].  */
class TopFieldDocs
/**
 * Creates one of these objects.
 *
 * @param totalHits Total number of hits for the query.
 * @param scoreDocs The top hits for the query.
 * @param fields The sort criteria used to find the top hits.
 */(
    totalHits: TotalHits?, scoreDocs: Array<ScoreDoc>,
    /** The fields which were used to sort results by.  */
    var fields: Array<SortField>
) : TopDocs(totalHits!!, scoreDocs)
