#noinspection ShrinkerUnresolvedReference
# VectorizationProvider validates callers by their fully qualified class names.
# https://github.com/nehemiaharchives/lucene-kmp/issues/264
-keep,allowshrinking,allowoptimization class org.gnit.lucenekmp.codecs.hnsw.FlatVectorScorerUtil
-keep,allowshrinking,allowoptimization class org.gnit.lucenekmp.util.VectorUtil
-keep,allowshrinking,allowoptimization class org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsReader
-keep,allowshrinking,allowoptimization class org.gnit.lucenekmp.codecs.lucene101.PostingIndexInput
