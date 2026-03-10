# Lucene KMP Port Progress
## Package statistics (priority‑1 deps)
| Java package | KMP mapped | Classes | Ported | % | Done |
| --- | --- | --- | --- | --- | --- |
| org.apache.lucene |     org.gnit.lucenekmp | 715 | 715 | 100% | [x] |
|   org.apache.lucene.analysis |       org.gnit.lucenekmp.analysis | 14 | 14 | 100% | [x] |
|     org.apache.lucene.analysis.standard |         org.gnit.lucenekmp.analysis.standard | 3 | 3 | 100% | [x] |
|     org.apache.lucene.analysis.tokenattributes |         org.gnit.lucenekmp.analysis.tokenattributes | 11 | 11 | 100% | [x] |
|   org.apache.lucene.codecs |       org.gnit.lucenekmp.codecs | 35 | 35 | 100% | [x] |
|     org.apache.lucene.codecs.compressing |         org.gnit.lucenekmp.codecs.compressing | 4 | 4 | 100% | [x] |
|     org.apache.lucene.codecs.hnsw |         org.gnit.lucenekmp.codecs.hnsw | 4 | 4 | 100% | [x] |
|     org.apache.lucene.codecs.lucene101 |         org.gnit.lucenekmp.codecs.lucene101 | 7 | 7 | 100% | [x] |
|       org.apache.lucene.codecs.lucene90.blocktree |           org.gnit.lucenekmp.codecs.lucene90.blocktree | 9 | 9 | 100% | [x] |
|       org.apache.lucene.codecs.lucene90.compressing |           org.gnit.lucenekmp.codecs.lucene90.compressing | 10 | 10 | 100% | [x] |
|     org.apache.lucene.codecs.perfield |         org.gnit.lucenekmp.codecs.perfield | 3 | 3 | 100% | [x] |
|   org.apache.lucene.document |       org.gnit.lucenekmp.document | 29 | 29 | 100% | [x] |
|   org.apache.lucene.geo |       org.gnit.lucenekmp.geo | 26 | 26 | 100% | [x] |
|   org.apache.lucene.index |       org.gnit.lucenekmp.index | 169 | 169 | 100% | [x] |
|     org.apache.lucene.internal.hppc |         org.gnit.lucenekmp.internal.hppc | 14 | 14 | 100% | [x] |
|     org.apache.lucene.internal.tests |         org.gnit.lucenekmp.internal.tests | 2 | 2 | 100% | [x] |
|     org.apache.lucene.internal.vectorization |         org.gnit.lucenekmp.internal.vectorization | 5 | 5 | 100% | [x] |
|   org.apache.lucene.search |       org.gnit.lucenekmp.search | 138 | 138 | 100% | [x] |
|     org.apache.lucene.search.comparators |         org.gnit.lucenekmp.search.comparators | 8 | 8 | 100% | [x] |
|     org.apache.lucene.search.knn |         org.gnit.lucenekmp.search.knn | 4 | 4 | 100% | [x] |
|     org.apache.lucene.search.similarities |         org.gnit.lucenekmp.search.similarities | 2 | 2 | 100% | [x] |
|   org.apache.lucene.store |       org.gnit.lucenekmp.store | 37 | 37 | 100% | [x] |
|   org.apache.lucene.util |       org.gnit.lucenekmp.util | 87 | 87 | 100% | [x] |
|     org.apache.lucene.util.automaton |         org.gnit.lucenekmp.util.automaton | 20 | 20 | 100% | [x] |
|     org.apache.lucene.util.bkd |         org.gnit.lucenekmp.util.bkd | 1 | 1 | 100% | [x] |
|     org.apache.lucene.util.compress |         org.gnit.lucenekmp.util.compress | 2 | 2 | 100% | [x] |
|     org.apache.lucene.util.fst |         org.gnit.lucenekmp.util.fst | 17 | 17 | 100% | [x] |
|     org.apache.lucene.util.hnsw |         org.gnit.lucenekmp.util.hnsw | 7 | 7 | 100% | [x] |
|     org.apache.lucene.util.packed |         org.gnit.lucenekmp.util.packed | 47 | 47 | 100% | [x] |


## Priority-1 API progress
| Java class | Mapped class | Java Deps | KMP Deps Ported | KMP Deps To Port | % | Done |
| --- | --- | --- | --- | --- | --- | --- |
| [org.apache.lucene.index.IndexWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexWriter.java) | org.gnit.lucenekmp.index.IndexWriter | 78 | 78 | 0 | 100% | [x] |
| [org.apache.lucene.index.IndexWriterConfig](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexWriterConfig.java) | org.gnit.lucenekmp.index.IndexWriterConfig | 15 | 15 | 0 | 100% | [ ] |
| [org.apache.lucene.store.FSDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/FSDirectory.java) | org.gnit.lucenekmp.store.FSDirectory | 8 | 8 | 0 | 100% | [x] |
| [org.apache.lucene.analysis.Analyzer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/Analyzer.java) | org.gnit.lucenekmp.analysis.Analyzer | 10 | 10 | 0 | 100% | [x] |
| [org.apache.lucene.document.Document](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/Document.java) | org.gnit.lucenekmp.document.Document | 2 | 2 | 0 | 100% | [x] |
| [org.apache.lucene.document.Field](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/Field.java) | org.gnit.lucenekmp.document.Field | 12 | 12 | 0 | 100% | [x] |
| [org.apache.lucene.document.IntPoint](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/IntPoint.java) | org.gnit.lucenekmp.document.IntPoint | 6 | 6 | 0 | 100% | [x] |
| [org.apache.lucene.document.StoredField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/StoredField.java) | org.gnit.lucenekmp.document.StoredField | 3 | 3 | 0 | 100% | [x] |
| [org.apache.lucene.document.TextField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/TextField.java) | org.gnit.lucenekmp.document.TextField | 4 | 4 | 0 | 100% | [x] |
| [org.apache.lucene.index.DirectoryReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DirectoryReader.java) | org.gnit.lucenekmp.index.DirectoryReader | 6 | 6 | 0 | 100% | [x] |
| [org.apache.lucene.index.StandardDirectoryReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/StandardDirectoryReader.java) | org.gnit.lucenekmp.index.StandardDirectoryReader | 13 | 13 | 0 | 100% | [x] |
| [org.apache.lucene.queryparser.classic.QueryParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/queryparser/classic/QueryParser.java) | org.gnit.lucenekmp.queryparser.classic.QueryParser | 0 | 0 | 0 | 100% | [ ] |
| [org.apache.lucene.search.IndexSearcher](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/IndexSearcher.java) | org.gnit.lucenekmp.search.IndexSearcher | 42 | 42 | 0 | 100% | [x] |
| [org.apache.lucene.store.FSLockFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/FSLockFactory.java) | org.gnit.lucenekmp.store.FSLockFactory | 4 | 4 | 0 | 100% | [ ] |
| [org.apache.lucene.store.NIOFSDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/NIOFSDirectory.java) | org.gnit.lucenekmp.store.NIOFSDirectory | 5 | 5 | 0 | 100% | [ ] |
| [org.apache.lucene.document.IntPoint](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/IntPoint.java) | org.gnit.lucenekmp.document.IntPoint | 6 | 6 | 0 | 100% | [x] |
| [org.apache.lucene.search.Query](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Query.java) | org.gnit.lucenekmp.search.Query | 3 | 3 | 0 | 100% | [ ] |
| [org.apache.lucene.search.BooleanQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/BooleanQuery.java) | org.gnit.lucenekmp.search.BooleanQuery | 11 | 11 | 0 | 100% | [x] |
| [org.apache.lucene.search.BooleanClause](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/BooleanClause.java) | org.gnit.lucenekmp.search.BooleanClause | 0 | 0 | 0 | 100% | [ ] |
| [org.apache.lucene.search.Sort](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Sort.java) | org.gnit.lucenekmp.search.Sort | 0 | 0 | 0 | 100% | [x] |
| [org.apache.lucene.search.SortField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/SortField.java) | org.gnit.lucenekmp.search.SortField | 19 | 19 | 0 | 100% | [x] |
| TOTAL |  | 171 | 171 | 0 | 100% | [ ] |

## Package statistics (all deps)
| Java package | KMP mapped | Classes | Ported | % | Done |
| --- | --- | --- | --- | --- | --- |
| org.apache.lucene |     org.gnit.lucenekmp | 1369 | 1166 | 85% | [ ] |
| org.apache.lucene |     org.gnit.lucenekmp | 1 | 0 | 0% | [ ] |
|   org.apache.lucene.analysis |       org.gnit.lucenekmp.analysis | 26 | 25 | 96% | [ ] |
|     org.apache.lucene.analysis.standard |         org.gnit.lucenekmp.analysis.standard | 5 | 4 | 80% | [ ] |
|     org.apache.lucene.analysis.tokenattributes |         org.gnit.lucenekmp.analysis.tokenattributes | 25 | 21 | 84% | [ ] |
|   org.apache.lucene.codecs |       org.gnit.lucenekmp.codecs | 41 | 40 | 97% | [ ] |
|     org.apache.lucene.codecs.compressing |         org.gnit.lucenekmp.codecs.compressing | 5 | 4 | 80% | [ ] |
|     org.apache.lucene.codecs.hnsw |         org.gnit.lucenekmp.codecs.hnsw | 10 | 9 | 90% | [ ] |
|     org.apache.lucene.codecs.lucene101 |         org.gnit.lucenekmp.codecs.lucene101 | 10 | 8 | 80% | [ ] |
|     org.apache.lucene.codecs.lucene102 |         org.gnit.lucenekmp.codecs.lucene102 | 8 | 7 | 87% | [ ] |
|     org.apache.lucene.codecs.lucene90 |         org.gnit.lucenekmp.codecs.lucene90 | 18 | 17 | 94% | [ ] |
|       org.apache.lucene.codecs.lucene90.blocktree |           org.gnit.lucenekmp.codecs.lucene90.blocktree | 10 | 9 | 90% | [ ] |
|       org.apache.lucene.codecs.lucene90.compressing |           org.gnit.lucenekmp.codecs.lucene90.compressing | 11 | 10 | 90% | [ ] |
|     org.apache.lucene.codecs.lucene94 |         org.gnit.lucenekmp.codecs.lucene94 | 2 | 1 | 50% | [ ] |
|     org.apache.lucene.codecs.lucene95 |         org.gnit.lucenekmp.codecs.lucene95 | 5 | 4 | 80% | [ ] |
|     org.apache.lucene.codecs.lucene99 |         org.gnit.lucenekmp.codecs.lucene99 | 14 | 13 | 92% | [ ] |
|     org.apache.lucene.codecs.perfield |         org.gnit.lucenekmp.codecs.perfield | 5 | 4 | 80% | [ ] |
|   org.apache.lucene.document |       org.gnit.lucenekmp.document | 89 | 75 | 84% | [ ] |
|   org.apache.lucene.geo |       org.gnit.lucenekmp.geo | 28 | 27 | 96% | [ ] |
|   org.apache.lucene.index |       org.gnit.lucenekmp.index | 199 | 191 | 95% | [ ] |
|     org.apache.lucene.internal.hppc |         org.gnit.lucenekmp.internal.hppc | 26 | 25 | 96% | [ ] |
|     org.apache.lucene.internal.tests |         org.gnit.lucenekmp.internal.tests | 3 | 2 | 66% | [ ] |
|     org.apache.lucene.internal.vectorization |         org.gnit.lucenekmp.internal.vectorization | 6 | 5 | 83% | [ ] |
|   org.apache.lucene.search |       org.gnit.lucenekmp.search | 197 | 171 | 86% | [ ] |
|     org.apache.lucene.search.comparators |         org.gnit.lucenekmp.search.comparators | 9 | 8 | 88% | [ ] |
|     org.apache.lucene.search.knn |         org.gnit.lucenekmp.search.knn | 5 | 4 | 80% | [ ] |
|     org.apache.lucene.search.similarities |         org.gnit.lucenekmp.search.similarities | 48 | 47 | 97% | [ ] |
|   org.apache.lucene.store |       org.gnit.lucenekmp.store | 51 | 48 | 94% | [ ] |
|     org.apache.lucene.tests.analysis |         org.gnit.lucenekmp.tests.analysis | 29 | 19 | 65% | [ ] |
|       org.apache.lucene.tests.analysis.standard |           org.gnit.lucenekmp.tests.analysis.standard | 3 | 2 | 66% | [ ] |
|     org.apache.lucene.tests.codecs |         org.gnit.lucenekmp.tests.codecs | 1 | 0 | 0% | [ ] |
|       org.apache.lucene.tests.codecs.asserting |           org.gnit.lucenekmp.tests.codecs.asserting | 10 | 9 | 90% | [ ] |
|       org.apache.lucene.tests.codecs.blockterms |           org.gnit.lucenekmp.tests.codecs.blockterms | 4 | 1 | 25% | [ ] |
|       org.apache.lucene.tests.codecs.bloom |           org.gnit.lucenekmp.tests.codecs.bloom | 2 | 1 | 50% | [ ] |
|       org.apache.lucene.tests.codecs.cheapbastard |           org.gnit.lucenekmp.tests.codecs.cheapbastard | 2 | 0 | 0% | [ ] |
|       org.apache.lucene.tests.codecs.compressing |           org.gnit.lucenekmp.tests.codecs.compressing | 7 | 6 | 85% | [ ] |
|         org.apache.lucene.tests.codecs.compressing.dummy |             org.gnit.lucenekmp.tests.codecs.compressing.dummy | 2 | 1 | 50% | [ ] |
|       org.apache.lucene.tests.codecs.cranky |           org.gnit.lucenekmp.tests.codecs.cranky | 12 | 0 | 0% | [ ] |
|       org.apache.lucene.tests.codecs.mockrandom |           org.gnit.lucenekmp.tests.codecs.mockrandom | 2 | 0 | 0% | [ ] |
|       org.apache.lucene.tests.codecs.ramonly |           org.gnit.lucenekmp.tests.codecs.ramonly | 2 | 0 | 0% | [ ] |
|       org.apache.lucene.tests.codecs.uniformsplit |           org.gnit.lucenekmp.tests.codecs.uniformsplit | 3 | 0 | 0% | [ ] |
|         org.apache.lucene.tests.codecs.uniformsplit.sharedterms |             org.gnit.lucenekmp.tests.codecs.uniformsplit.sharedterms | 2 | 0 | 0% | [ ] |
|       org.apache.lucene.tests.codecs.vector |           org.gnit.lucenekmp.tests.codecs.vector | 2 | 0 | 0% | [ ] |
|     org.apache.lucene.tests.geo |         org.gnit.lucenekmp.tests.geo | 6 | 4 | 66% | [ ] |
|     org.apache.lucene.tests.index |         org.gnit.lucenekmp.tests.index | 40 | 33 | 82% | [ ] |
|     org.apache.lucene.tests.mockfile |         org.gnit.lucenekmp.tests.mockfile | 22 | 3 | 13% | [ ] |
|     org.apache.lucene.tests.search |         org.gnit.lucenekmp.tests.search | 25 | 18 | 72% | [ ] |
|       org.apache.lucene.tests.search.similarities |           org.gnit.lucenekmp.tests.search.similarities | 4 | 3 | 75% | [ ] |
|     org.apache.lucene.tests.store |         org.gnit.lucenekmp.tests.store | 13 | 12 | 92% | [ ] |
|     org.apache.lucene.tests.util |         org.gnit.lucenekmp.tests.util | 36 | 16 | 44% | [ ] |
|       org.apache.lucene.tests.util.automaton |           org.gnit.lucenekmp.tests.util.automaton | 2 | 1 | 50% | [ ] |
|       org.apache.lucene.tests.util.fst |           org.gnit.lucenekmp.tests.util.fst | 2 | 1 | 50% | [ ] |
|       org.apache.lucene.tests.util.hnsw |           org.gnit.lucenekmp.tests.util.hnsw | 1 | 0 | 0% | [ ] |
|   org.apache.lucene.util |       org.gnit.lucenekmp.util | 110 | 103 | 93% | [ ] |
|     org.apache.lucene.util.automaton |         org.gnit.lucenekmp.util.automaton | 29 | 28 | 96% | [ ] |
|     org.apache.lucene.util.bkd |         org.gnit.lucenekmp.util.bkd | 15 | 14 | 93% | [ ] |
|     org.apache.lucene.util.compress |         org.gnit.lucenekmp.util.compress | 3 | 2 | 66% | [ ] |
|     org.apache.lucene.util.fst |         org.gnit.lucenekmp.util.fst | 25 | 23 | 92% | [ ] |
|     org.apache.lucene.util.graph |         org.gnit.lucenekmp.util.graph | 2 | 1 | 50% | [ ] |
|     org.apache.lucene.util.hnsw |         org.gnit.lucenekmp.util.hnsw | 26 | 25 | 96% | [ ] |
|     org.apache.lucene.util.mutable |         org.gnit.lucenekmp.util.mutable | 9 | 8 | 88% | [ ] |
|     org.apache.lucene.util.packed |         org.gnit.lucenekmp.util.packed | 53 | 48 | 90% | [ ] |
|     org.apache.lucene.util.quantization |         org.gnit.lucenekmp.util.quantization | 6 | 5 | 83% | [ ] |


## Test Classes Port Progress
| Subpackage | Count | Ported | % |
| --- | --- | --- | --- |
| org.apache.lucene | 6 | 0 | 0% |
|   org.apache.lucene.analysis | 15 | 15 | 100% |
|     org.apache.lucene.analysis.standard | 2 | 2 | 100% |
|     org.apache.lucene.analysis.tokenattributes | 4 | 4 | 100% |
|   org.apache.lucene.codecs | 4 | 3 | 75% |
|     org.apache.lucene.codecs.compressing | 5 | 5 | 100% |
|     org.apache.lucene.codecs.hnsw | 1 | 1 | 100% |
|     org.apache.lucene.codecs.lucene101 | 6 | 6 | 100% |
|     org.apache.lucene.codecs.lucene102 | 2 | 2 | 100% |
|     org.apache.lucene.codecs.lucene90 | 14 | 14 | 100% |
|       org.apache.lucene.codecs.lucene90.blocktree | 1 | 1 | 100% |
|       org.apache.lucene.codecs.lucene90.compressing | 3 | 3 | 100% |
|     org.apache.lucene.codecs.lucene94 | 1 | 1 | 100% |
|     org.apache.lucene.codecs.lucene99 | 6 | 6 | 100% |
|     org.apache.lucene.codecs.perfield | 4 | 4 | 100% |
|   org.apache.lucene.document | 50 | 49 | 98% |
|   org.apache.lucene.geo | 17 | 17 | 100% |
|   org.apache.lucene.index | 197 | 81 | 41% |
|     org.apache.lucene.internal.hppc | 15 | 15 | 100% |
|     org.apache.lucene.internal.tests | 1 | 1 | 100% |
|     org.apache.lucene.internal.vectorization | 4 | 4 | 100% |
|   org.apache.lucene.search | 149 | 29 | 19% |
|     org.apache.lucene.search.knn | 1 | 1 | 100% |
|     org.apache.lucene.search.similarities | 25 | 25 | 100% |
|   org.apache.lucene.store | 27 | 27 | 100% |
|   org.apache.lucene.util | 63 | 62 | 98% |
|     org.apache.lucene.util.automaton | 15 | 15 | 100% |
|     org.apache.lucene.util.bkd | 8 | 8 | 100% |
|     org.apache.lucene.util.compress | 3 | 3 | 100% |
|     org.apache.lucene.util.fst | 8 | 8 | 100% |
|     org.apache.lucene.util.graph | 1 | 1 | 100% |
|     org.apache.lucene.util.hnsw | 8 | 8 | 100% |
|     org.apache.lucene.util.mutable | 1 | 1 | 100% |
|     org.apache.lucene.util.packed | 3 | 3 | 100% |
|     org.apache.lucene.util.quantization | 3 | 3 | 100% |
| Total | 673 | 429 | 63% |


## Tests To Port
| Java Test FQN | Kotlin Test FQN |
| --- | --- |
| [org.apache.lucene.TestAssertions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/TestAssertions.java) | org.gnit.lucenekmp.TestAssertions |
| [org.apache.lucene.TestDemo](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/TestDemo.java) | org.gnit.lucenekmp.TestDemo |
| [org.apache.lucene.TestExternalCodecs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/TestExternalCodecs.java) | org.gnit.lucenekmp.TestExternalCodecs |
| [org.apache.lucene.TestMergeSchedulerExternal](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/TestMergeSchedulerExternal.java) | org.gnit.lucenekmp.TestMergeSchedulerExternal |
| [org.apache.lucene.TestSearchForDuplicates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/TestSearchForDuplicates.java) | org.gnit.lucenekmp.TestSearchForDuplicates |
| [org.apache.lucene.codecs.TestCodecLoadingDeadlock](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/codecs/TestCodecLoadingDeadlock.java) | org.gnit.lucenekmp.codecs.TestCodecLoadingDeadlock |
| [org.apache.lucene.index.Test4GBStoredFields](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/Test4GBStoredFields.java) | org.gnit.lucenekmp.index.Test4GBStoredFields |
| [org.apache.lucene.index.TestForTooMuchCloning](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestForTooMuchCloning.java) | org.gnit.lucenekmp.index.TestForTooMuchCloning |
| [org.apache.lucene.index.TestForceMergeForever](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestForceMergeForever.java) | org.gnit.lucenekmp.index.TestForceMergeForever |
| [org.apache.lucene.index.TestFrozenBufferedUpdates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestFrozenBufferedUpdates.java) | org.gnit.lucenekmp.index.TestFrozenBufferedUpdates |
| [org.apache.lucene.index.TestIndexCommit](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexCommit.java) | org.gnit.lucenekmp.index.TestIndexCommit |
| [org.apache.lucene.index.TestIndexFileDeleter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexFileDeleter.java) | org.gnit.lucenekmp.index.TestIndexFileDeleter |
| [org.apache.lucene.index.TestIndexManyDocuments](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexManyDocuments.java) | org.gnit.lucenekmp.index.TestIndexManyDocuments |
| [org.apache.lucene.index.TestIndexOptions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexOptions.java) | org.gnit.lucenekmp.index.TestIndexOptions |
| [org.apache.lucene.index.TestIndexReaderClose](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexReaderClose.java) | org.gnit.lucenekmp.index.TestIndexReaderClose |
| [org.apache.lucene.index.TestIndexSorting](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexSorting.java) | org.gnit.lucenekmp.index.TestIndexSorting |
| [org.apache.lucene.index.TestIndexTooManyDocs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexTooManyDocs.java) | org.gnit.lucenekmp.index.TestIndexTooManyDocs |
| [org.apache.lucene.index.TestIndexWriterCommit](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterCommit.java) | org.gnit.lucenekmp.index.TestIndexWriterCommit |
| [org.apache.lucene.index.TestIndexWriterDelete](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterDelete.java) | org.gnit.lucenekmp.index.TestIndexWriterDelete |
| [org.apache.lucene.index.TestIndexWriterExceptions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterExceptions.java) | org.gnit.lucenekmp.index.TestIndexWriterExceptions |
| [org.apache.lucene.index.TestIndexWriterExceptions2](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterExceptions2.java) | org.gnit.lucenekmp.index.TestIndexWriterExceptions2 |
| [org.apache.lucene.index.TestIndexWriterForceMerge](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterForceMerge.java) | org.gnit.lucenekmp.index.TestIndexWriterForceMerge |
| [org.apache.lucene.index.TestIndexWriterFromReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterFromReader.java) | org.gnit.lucenekmp.index.TestIndexWriterFromReader |
| [org.apache.lucene.index.TestIndexWriterLockRelease](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterLockRelease.java) | org.gnit.lucenekmp.index.TestIndexWriterLockRelease |
| [org.apache.lucene.index.TestIndexWriterMaxDocs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterMaxDocs.java) | org.gnit.lucenekmp.index.TestIndexWriterMaxDocs |
| [org.apache.lucene.index.TestIndexWriterMergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterMergePolicy.java) | org.gnit.lucenekmp.index.TestIndexWriterMergePolicy |
| [org.apache.lucene.index.TestIndexWriterMerging](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterMerging.java) | org.gnit.lucenekmp.index.TestIndexWriterMerging |
| [org.apache.lucene.index.TestIndexWriterNRTIsCurrent](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterNRTIsCurrent.java) | org.gnit.lucenekmp.index.TestIndexWriterNRTIsCurrent |
| [org.apache.lucene.index.TestIndexWriterOnDiskFull](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterOnDiskFull.java) | org.gnit.lucenekmp.index.TestIndexWriterOnDiskFull |
| [org.apache.lucene.index.TestIndexWriterOnError](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterOnError.java) | org.gnit.lucenekmp.index.TestIndexWriterOnError |
| [org.apache.lucene.index.TestIndexWriterOnJRECrash](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterOnJRECrash.java) | org.gnit.lucenekmp.index.TestIndexWriterOnJRECrash |
| [org.apache.lucene.index.TestIndexWriterOutOfFileDescriptors](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterOutOfFileDescriptors.java) | org.gnit.lucenekmp.index.TestIndexWriterOutOfFileDescriptors |
| [org.apache.lucene.index.TestIndexWriterReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterReader.java) | org.gnit.lucenekmp.index.TestIndexWriterReader |
| [org.apache.lucene.index.TestIndexWriterThreadsToSegments](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterThreadsToSegments.java) | org.gnit.lucenekmp.index.TestIndexWriterThreadsToSegments |
| [org.apache.lucene.index.TestIndexWriterUnicode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterUnicode.java) | org.gnit.lucenekmp.index.TestIndexWriterUnicode |
| [org.apache.lucene.index.TestIndexWriterWithThreads](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterWithThreads.java) | org.gnit.lucenekmp.index.TestIndexWriterWithThreads |
| [org.apache.lucene.index.TestIndexableField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexableField.java) | org.gnit.lucenekmp.index.TestIndexableField |
| [org.apache.lucene.index.TestIndexingSequenceNumbers](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexingSequenceNumbers.java) | org.gnit.lucenekmp.index.TestIndexingSequenceNumbers |
| [org.apache.lucene.index.TestInfoStream](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestInfoStream.java) | org.gnit.lucenekmp.index.TestInfoStream |
| [org.apache.lucene.index.TestIntBlockPool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIntBlockPool.java) | org.gnit.lucenekmp.index.TestIntBlockPool |
| [org.apache.lucene.index.TestIsCurrent](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIsCurrent.java) | org.gnit.lucenekmp.index.TestIsCurrent |
| [org.apache.lucene.index.TestKnnGraph](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestKnnGraph.java) | org.gnit.lucenekmp.index.TestKnnGraph |
| [org.apache.lucene.index.TestLongPostings](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestLongPostings.java) | org.gnit.lucenekmp.index.TestLongPostings |
| [org.apache.lucene.index.TestManyFields](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestManyFields.java) | org.gnit.lucenekmp.index.TestManyFields |
| [org.apache.lucene.index.TestMaxPosition](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestMaxPosition.java) | org.gnit.lucenekmp.index.TestMaxPosition |
| [org.apache.lucene.index.TestMaxTermFrequency](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestMaxTermFrequency.java) | org.gnit.lucenekmp.index.TestMaxTermFrequency |
| [org.apache.lucene.index.TestMergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestMergePolicy.java) | org.gnit.lucenekmp.index.TestMergePolicy |
| [org.apache.lucene.index.TestMergeRateLimiter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestMergeRateLimiter.java) | org.gnit.lucenekmp.index.TestMergeRateLimiter |
| [org.apache.lucene.index.TestMixedCodecs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestMixedCodecs.java) | org.gnit.lucenekmp.index.TestMixedCodecs |
| [org.apache.lucene.index.TestMixedDocValuesUpdates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestMixedDocValuesUpdates.java) | org.gnit.lucenekmp.index.TestMixedDocValuesUpdates |
| [org.apache.lucene.index.TestMultiDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestMultiDocValues.java) | org.gnit.lucenekmp.index.TestMultiDocValues |
| [org.apache.lucene.index.TestMultiFields](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestMultiFields.java) | org.gnit.lucenekmp.index.TestMultiFields |
| [org.apache.lucene.index.TestMultiLevelSkipList](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestMultiLevelSkipList.java) | org.gnit.lucenekmp.index.TestMultiLevelSkipList |
| [org.apache.lucene.index.TestMultiTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestMultiTermsEnum.java) | org.gnit.lucenekmp.index.TestMultiTermsEnum |
| [org.apache.lucene.index.TestNRTReaderCleanup](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestNRTReaderCleanup.java) | org.gnit.lucenekmp.index.TestNRTReaderCleanup |
| [org.apache.lucene.index.TestNRTReaderWithThreads](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestNRTReaderWithThreads.java) | org.gnit.lucenekmp.index.TestNRTReaderWithThreads |
| [org.apache.lucene.index.TestNRTThreads](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestNRTThreads.java) | org.gnit.lucenekmp.index.TestNRTThreads |
| [org.apache.lucene.index.TestNeverDelete](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestNeverDelete.java) | org.gnit.lucenekmp.index.TestNeverDelete |
| [org.apache.lucene.index.TestNewestSegment](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestNewestSegment.java) | org.gnit.lucenekmp.index.TestNewestSegment |
| [org.apache.lucene.index.TestNoDeletionPolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestNoDeletionPolicy.java) | org.gnit.lucenekmp.index.TestNoDeletionPolicy |
| [org.apache.lucene.index.TestNoMergeScheduler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestNoMergeScheduler.java) | org.gnit.lucenekmp.index.TestNoMergeScheduler |
| [org.apache.lucene.index.TestNumericDocValuesUpdates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestNumericDocValuesUpdates.java) | org.gnit.lucenekmp.index.TestNumericDocValuesUpdates |
| [org.apache.lucene.index.TestOmitNorms](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestOmitNorms.java) | org.gnit.lucenekmp.index.TestOmitNorms |
| [org.apache.lucene.index.TestOmitPositions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestOmitPositions.java) | org.gnit.lucenekmp.index.TestOmitPositions |
| [org.apache.lucene.index.TestOmitTf](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestOmitTf.java) | org.gnit.lucenekmp.index.TestOmitTf |
| [org.apache.lucene.index.TestOrdinalMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestOrdinalMap.java) | org.gnit.lucenekmp.index.TestOrdinalMap |
| [org.apache.lucene.index.TestParallelCompositeReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestParallelCompositeReader.java) | org.gnit.lucenekmp.index.TestParallelCompositeReader |
| [org.apache.lucene.index.TestParallelLeafReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestParallelLeafReader.java) | org.gnit.lucenekmp.index.TestParallelLeafReader |
| [org.apache.lucene.index.TestParallelReaderEmptyIndex](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestParallelReaderEmptyIndex.java) | org.gnit.lucenekmp.index.TestParallelReaderEmptyIndex |
| [org.apache.lucene.index.TestParallelTermEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestParallelTermEnum.java) | org.gnit.lucenekmp.index.TestParallelTermEnum |
| [org.apache.lucene.index.TestPayloads](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestPayloads.java) | org.gnit.lucenekmp.index.TestPayloads |
| [org.apache.lucene.index.TestPayloadsOnVectors](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestPayloadsOnVectors.java) | org.gnit.lucenekmp.index.TestPayloadsOnVectors |
| [org.apache.lucene.index.TestPendingSoftDeletes](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestPendingSoftDeletes.java) | org.gnit.lucenekmp.index.TestPendingSoftDeletes |
| [org.apache.lucene.index.TestPerSegmentDeletes](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestPerSegmentDeletes.java) | org.gnit.lucenekmp.index.TestPerSegmentDeletes |
| [org.apache.lucene.index.TestPersistentSnapshotDeletionPolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestPersistentSnapshotDeletionPolicy.java) | org.gnit.lucenekmp.index.TestPersistentSnapshotDeletionPolicy |
| [org.apache.lucene.index.TestPointValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestPointValues.java) | org.gnit.lucenekmp.index.TestPointValues |
| [org.apache.lucene.index.TestPostingsOffsets](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestPostingsOffsets.java) | org.gnit.lucenekmp.index.TestPostingsOffsets |
| [org.apache.lucene.index.TestPrefixCodedTerms](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestPrefixCodedTerms.java) | org.gnit.lucenekmp.index.TestPrefixCodedTerms |
| [org.apache.lucene.index.TestReadOnlyIndex](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestReadOnlyIndex.java) | org.gnit.lucenekmp.index.TestReadOnlyIndex |
| [org.apache.lucene.index.TestReaderClosed](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestReaderClosed.java) | org.gnit.lucenekmp.index.TestReaderClosed |
| [org.apache.lucene.index.TestReaderPool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestReaderPool.java) | org.gnit.lucenekmp.index.TestReaderPool |
| [org.apache.lucene.index.TestReaderWrapperDVTypeCheck](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestReaderWrapperDVTypeCheck.java) | org.gnit.lucenekmp.index.TestReaderWrapperDVTypeCheck |
| [org.apache.lucene.index.TestRollback](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestRollback.java) | org.gnit.lucenekmp.index.TestRollback |
| [org.apache.lucene.index.TestRollingUpdates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestRollingUpdates.java) | org.gnit.lucenekmp.index.TestRollingUpdates |
| [org.apache.lucene.index.TestSameTokenSamePosition](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestSameTokenSamePosition.java) | org.gnit.lucenekmp.index.TestSameTokenSamePosition |
| [org.apache.lucene.index.TestSegmentInfos](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestSegmentInfos.java) | org.gnit.lucenekmp.index.TestSegmentInfos |
| [org.apache.lucene.index.TestSegmentMerger](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestSegmentMerger.java) | org.gnit.lucenekmp.index.TestSegmentMerger |
| [org.apache.lucene.index.TestSegmentReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestSegmentReader.java) | org.gnit.lucenekmp.index.TestSegmentReader |
| [org.apache.lucene.index.TestSegmentTermDocs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestSegmentTermDocs.java) | org.gnit.lucenekmp.index.TestSegmentTermDocs |
| [org.apache.lucene.index.TestSegmentTermEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestSegmentTermEnum.java) | org.gnit.lucenekmp.index.TestSegmentTermEnum |
| [org.apache.lucene.index.TestSegmentToThreadMapping](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestSegmentToThreadMapping.java) | org.gnit.lucenekmp.index.TestSegmentToThreadMapping |
| [org.apache.lucene.index.TestSizeBoundedForceMerge](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestSizeBoundedForceMerge.java) | org.gnit.lucenekmp.index.TestSizeBoundedForceMerge |
| [org.apache.lucene.index.TestSnapshotDeletionPolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestSnapshotDeletionPolicy.java) | org.gnit.lucenekmp.index.TestSnapshotDeletionPolicy |
| [org.apache.lucene.index.TestSoftDeletesDirectoryReaderWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestSoftDeletesDirectoryReaderWrapper.java) | org.gnit.lucenekmp.index.TestSoftDeletesDirectoryReaderWrapper |
| [org.apache.lucene.index.TestSoftDeletesRetentionMergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestSoftDeletesRetentionMergePolicy.java) | org.gnit.lucenekmp.index.TestSoftDeletesRetentionMergePolicy |
| [org.apache.lucene.index.TestSortingCodecReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestSortingCodecReader.java) | org.gnit.lucenekmp.index.TestSortingCodecReader |
| [org.apache.lucene.index.TestStoredFieldsConsumer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestStoredFieldsConsumer.java) | org.gnit.lucenekmp.index.TestStoredFieldsConsumer |
| [org.apache.lucene.index.TestStressAdvance](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestStressAdvance.java) | org.gnit.lucenekmp.index.TestStressAdvance |
| [org.apache.lucene.index.TestStressDeletes](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestStressDeletes.java) | org.gnit.lucenekmp.index.TestStressDeletes |
| [org.apache.lucene.index.TestStressIndexing](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestStressIndexing.java) | org.gnit.lucenekmp.index.TestStressIndexing |
| [org.apache.lucene.index.TestStressIndexing2](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestStressIndexing2.java) | org.gnit.lucenekmp.index.TestStressIndexing2 |
| [org.apache.lucene.index.TestStressNRT](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestStressNRT.java) | org.gnit.lucenekmp.index.TestStressNRT |
| [org.apache.lucene.index.TestSumDocFreq](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestSumDocFreq.java) | org.gnit.lucenekmp.index.TestSumDocFreq |
| [org.apache.lucene.index.TestSwappedIndexFiles](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestSwappedIndexFiles.java) | org.gnit.lucenekmp.index.TestSwappedIndexFiles |
| [org.apache.lucene.index.TestTermStates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestTermStates.java) | org.gnit.lucenekmp.index.TestTermStates |
| [org.apache.lucene.index.TestTermVectors](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestTermVectors.java) | org.gnit.lucenekmp.index.TestTermVectors |
| [org.apache.lucene.index.TestTermVectorsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestTermVectorsWriter.java) | org.gnit.lucenekmp.index.TestTermVectorsWriter |
| [org.apache.lucene.index.TestTermdocPerf](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestTermdocPerf.java) | org.gnit.lucenekmp.index.TestTermdocPerf |
| [org.apache.lucene.index.TestTerms](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestTerms.java) | org.gnit.lucenekmp.index.TestTerms |
| [org.apache.lucene.index.TestTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestTermsEnum.java) | org.gnit.lucenekmp.index.TestTermsEnum |
| [org.apache.lucene.index.TestTermsEnum2](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestTermsEnum2.java) | org.gnit.lucenekmp.index.TestTermsEnum2 |
| [org.apache.lucene.index.TestTermsEnumIndex](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestTermsEnumIndex.java) | org.gnit.lucenekmp.index.TestTermsEnumIndex |
| [org.apache.lucene.index.TestTermsHashPerField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestTermsHashPerField.java) | org.gnit.lucenekmp.index.TestTermsHashPerField |
| [org.apache.lucene.index.TestThreadedForceMerge](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestThreadedForceMerge.java) | org.gnit.lucenekmp.index.TestThreadedForceMerge |
| [org.apache.lucene.index.TestTragicIndexWriterDeadlock](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestTragicIndexWriterDeadlock.java) | org.gnit.lucenekmp.index.TestTragicIndexWriterDeadlock |
| [org.apache.lucene.index.TestTransactionRollback](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestTransactionRollback.java) | org.gnit.lucenekmp.index.TestTransactionRollback |
| [org.apache.lucene.index.TestTransactions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestTransactions.java) | org.gnit.lucenekmp.index.TestTransactions |
| [org.apache.lucene.index.TestTryDelete](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestTryDelete.java) | org.gnit.lucenekmp.index.TestTryDelete |
| [org.apache.lucene.index.TestTwoPhaseCommitTool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestTwoPhaseCommitTool.java) | org.gnit.lucenekmp.index.TestTwoPhaseCommitTool |
| [org.apache.lucene.index.TestUniqueTermCount](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestUniqueTermCount.java) | org.gnit.lucenekmp.index.TestUniqueTermCount |
| [org.apache.lucene.search.TestCachingCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestCachingCollector.java) | org.gnit.lucenekmp.search.TestCachingCollector |
| [org.apache.lucene.search.TestCollectorManager](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestCollectorManager.java) | org.gnit.lucenekmp.search.TestCollectorManager |
| [org.apache.lucene.search.TestCombinedFieldQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestCombinedFieldQuery.java) | org.gnit.lucenekmp.search.TestCombinedFieldQuery |
| [org.apache.lucene.search.TestComplexExplanations](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestComplexExplanations.java) | org.gnit.lucenekmp.search.TestComplexExplanations |
| [org.apache.lucene.search.TestComplexExplanationsOfNonMatches](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestComplexExplanationsOfNonMatches.java) | org.gnit.lucenekmp.search.TestComplexExplanationsOfNonMatches |
| [org.apache.lucene.search.TestConjunctionDISI](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestConjunctionDISI.java) | org.gnit.lucenekmp.search.TestConjunctionDISI |
| [org.apache.lucene.search.TestConjunctions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestConjunctions.java) | org.gnit.lucenekmp.search.TestConjunctions |
| [org.apache.lucene.search.TestConstantScoreQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestConstantScoreQuery.java) | org.gnit.lucenekmp.search.TestConstantScoreQuery |
| [org.apache.lucene.search.TestConstantScoreScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestConstantScoreScorer.java) | org.gnit.lucenekmp.search.TestConstantScoreScorer |
| [org.apache.lucene.search.TestControlledRealTimeReopenThread](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestControlledRealTimeReopenThread.java) | org.gnit.lucenekmp.search.TestControlledRealTimeReopenThread |
| [org.apache.lucene.search.TestCustomSearcherSort](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestCustomSearcherSort.java) | org.gnit.lucenekmp.search.TestCustomSearcherSort |
| [org.apache.lucene.search.TestDateSort](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestDateSort.java) | org.gnit.lucenekmp.search.TestDateSort |
| [org.apache.lucene.search.TestDenseConjunctionBulkScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestDenseConjunctionBulkScorer.java) | org.gnit.lucenekmp.search.TestDenseConjunctionBulkScorer |
| [org.apache.lucene.search.TestDisjunctionMaxQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestDisjunctionMaxQuery.java) | org.gnit.lucenekmp.search.TestDisjunctionMaxQuery |
| [org.apache.lucene.search.TestDocIdSetIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestDocIdSetIterator.java) | org.gnit.lucenekmp.search.TestDocIdSetIterator |
| [org.apache.lucene.search.TestDocValuesQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestDocValuesQueries.java) | org.gnit.lucenekmp.search.TestDocValuesQueries |
| [org.apache.lucene.search.TestDocValuesRangeIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestDocValuesRangeIterator.java) | org.gnit.lucenekmp.search.TestDocValuesRangeIterator |
| [org.apache.lucene.search.TestDocValuesRewriteMethod](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestDocValuesRewriteMethod.java) | org.gnit.lucenekmp.search.TestDocValuesRewriteMethod |
| [org.apache.lucene.search.TestDoubleRangeFieldQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestDoubleRangeFieldQueries.java) | org.gnit.lucenekmp.search.TestDoubleRangeFieldQueries |
| [org.apache.lucene.search.TestDoubleValuesSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestDoubleValuesSource.java) | org.gnit.lucenekmp.search.TestDoubleValuesSource |
| [org.apache.lucene.search.TestEarlyTermination](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestEarlyTermination.java) | org.gnit.lucenekmp.search.TestEarlyTermination |
| [org.apache.lucene.search.TestElevationComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestElevationComparator.java) | org.gnit.lucenekmp.search.TestElevationComparator |
| [org.apache.lucene.search.TestFieldCacheRewriteMethod](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestFieldCacheRewriteMethod.java) | org.gnit.lucenekmp.search.TestFieldCacheRewriteMethod |
| [org.apache.lucene.search.TestFieldExistsQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestFieldExistsQuery.java) | org.gnit.lucenekmp.search.TestFieldExistsQuery |
| [org.apache.lucene.search.TestFilterWeight](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestFilterWeight.java) | org.gnit.lucenekmp.search.TestFilterWeight |
| [org.apache.lucene.search.TestFloatRangeFieldQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestFloatRangeFieldQueries.java) | org.gnit.lucenekmp.search.TestFloatRangeFieldQueries |
| [org.apache.lucene.search.TestFuzzyQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestFuzzyQuery.java) | org.gnit.lucenekmp.search.TestFuzzyQuery |
| [org.apache.lucene.search.TestFuzzyTermOnShortTerms](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestFuzzyTermOnShortTerms.java) | org.gnit.lucenekmp.search.TestFuzzyTermOnShortTerms |
| [org.apache.lucene.search.TestIndexOrDocValuesQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestIndexOrDocValuesQuery.java) | org.gnit.lucenekmp.search.TestIndexOrDocValuesQuery |
| [org.apache.lucene.search.TestIndexSortSortedNumericDocValuesRangeQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestIndexSortSortedNumericDocValuesRangeQuery.java) | org.gnit.lucenekmp.search.TestIndexSortSortedNumericDocValuesRangeQuery |
| [org.apache.lucene.search.TestIndriAndQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestIndriAndQuery.java) | org.gnit.lucenekmp.search.TestIndriAndQuery |
| [org.apache.lucene.search.TestInetAddressRangeQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestInetAddressRangeQueries.java) | org.gnit.lucenekmp.search.TestInetAddressRangeQueries |
| [org.apache.lucene.search.TestIntRangeFieldQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestIntRangeFieldQueries.java) | org.gnit.lucenekmp.search.TestIntRangeFieldQueries |
| [org.apache.lucene.search.TestKnnByteVectorQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestKnnByteVectorQuery.java) | org.gnit.lucenekmp.search.TestKnnByteVectorQuery |
| [org.apache.lucene.search.TestKnnByteVectorQueryMMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestKnnByteVectorQueryMMap.java) | org.gnit.lucenekmp.search.TestKnnByteVectorQueryMMap |
| [org.apache.lucene.search.TestKnnFloatVectorQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestKnnFloatVectorQuery.java) | org.gnit.lucenekmp.search.TestKnnFloatVectorQuery |
| [org.apache.lucene.search.TestLRUQueryCache](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestLRUQueryCache.java) | org.gnit.lucenekmp.search.TestLRUQueryCache |
| [org.apache.lucene.search.TestLiveFieldValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestLiveFieldValues.java) | org.gnit.lucenekmp.search.TestLiveFieldValues |
| [org.apache.lucene.search.TestLongRangeFieldQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestLongRangeFieldQueries.java) | org.gnit.lucenekmp.search.TestLongRangeFieldQueries |
| [org.apache.lucene.search.TestLongValuesSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestLongValuesSource.java) | org.gnit.lucenekmp.search.TestLongValuesSource |
| [org.apache.lucene.search.TestMatchAllDocsQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMatchAllDocsQuery.java) | org.gnit.lucenekmp.search.TestMatchAllDocsQuery |
| [org.apache.lucene.search.TestMatchNoDocsQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMatchNoDocsQuery.java) | org.gnit.lucenekmp.search.TestMatchNoDocsQuery |
| [org.apache.lucene.search.TestMatchesIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMatchesIterator.java) | org.gnit.lucenekmp.search.TestMatchesIterator |
| [org.apache.lucene.search.TestMaxClauseLimit](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMaxClauseLimit.java) | org.gnit.lucenekmp.search.TestMaxClauseLimit |
| [org.apache.lucene.search.TestMaxScoreAccumulator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMaxScoreAccumulator.java) | org.gnit.lucenekmp.search.TestMaxScoreAccumulator |
| [org.apache.lucene.search.TestMaxScoreBulkScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMaxScoreBulkScorer.java) | org.gnit.lucenekmp.search.TestMaxScoreBulkScorer |
| [org.apache.lucene.search.TestMinShouldMatch2](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMinShouldMatch2.java) | org.gnit.lucenekmp.search.TestMinShouldMatch2 |
| [org.apache.lucene.search.TestMultiCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMultiCollector.java) | org.gnit.lucenekmp.search.TestMultiCollector |
| [org.apache.lucene.search.TestMultiCollectorManager](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMultiCollectorManager.java) | org.gnit.lucenekmp.search.TestMultiCollectorManager |
| [org.apache.lucene.search.TestMultiPhraseEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMultiPhraseEnum.java) | org.gnit.lucenekmp.search.TestMultiPhraseEnum |
| [org.apache.lucene.search.TestMultiPhraseQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMultiPhraseQuery.java) | org.gnit.lucenekmp.search.TestMultiPhraseQuery |
| [org.apache.lucene.search.TestMultiSliceMerge](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMultiSliceMerge.java) | org.gnit.lucenekmp.search.TestMultiSliceMerge |
| [org.apache.lucene.search.TestMultiTermConstantScore](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMultiTermConstantScore.java) | org.gnit.lucenekmp.search.TestMultiTermConstantScore |
| [org.apache.lucene.search.TestMultiTermQueryRewrites](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMultiTermQueryRewrites.java) | org.gnit.lucenekmp.search.TestMultiTermQueryRewrites |
| [org.apache.lucene.search.TestMultiThreadTermVectors](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMultiThreadTermVectors.java) | org.gnit.lucenekmp.search.TestMultiThreadTermVectors |
| [org.apache.lucene.search.TestMultiset](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMultiset.java) | org.gnit.lucenekmp.search.TestMultiset |
| [org.apache.lucene.search.TestNGramPhraseQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestNGramPhraseQuery.java) | org.gnit.lucenekmp.search.TestNGramPhraseQuery |
| [org.apache.lucene.search.TestNearest](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestNearest.java) | org.gnit.lucenekmp.search.TestNearest |
| [org.apache.lucene.search.TestNeedsScores](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestNeedsScores.java) | org.gnit.lucenekmp.search.TestNeedsScores |
| [org.apache.lucene.search.TestNot](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestNot.java) | org.gnit.lucenekmp.search.TestNot |
| [org.apache.lucene.search.TestPhrasePrefixQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestPhrasePrefixQuery.java) | org.gnit.lucenekmp.search.TestPhrasePrefixQuery |
| [org.apache.lucene.search.TestPhraseQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestPhraseQuery.java) | org.gnit.lucenekmp.search.TestPhraseQuery |
| [org.apache.lucene.search.TestPointQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestPointQueries.java) | org.gnit.lucenekmp.search.TestPointQueries |
| [org.apache.lucene.search.TestPositionIncrement](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestPositionIncrement.java) | org.gnit.lucenekmp.search.TestPositionIncrement |
| [org.apache.lucene.search.TestPositiveScoresOnlyCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestPositiveScoresOnlyCollector.java) | org.gnit.lucenekmp.search.TestPositiveScoresOnlyCollector |
| [org.apache.lucene.search.TestPrefixInBooleanQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestPrefixInBooleanQuery.java) | org.gnit.lucenekmp.search.TestPrefixInBooleanQuery |
| [org.apache.lucene.search.TestPrefixQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestPrefixQuery.java) | org.gnit.lucenekmp.search.TestPrefixQuery |
| [org.apache.lucene.search.TestPrefixRandom](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestPrefixRandom.java) | org.gnit.lucenekmp.search.TestPrefixRandom |
| [org.apache.lucene.search.TestQueryRescorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestQueryRescorer.java) | org.gnit.lucenekmp.search.TestQueryRescorer |
| [org.apache.lucene.search.TestQueryVisitor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestQueryVisitor.java) | org.gnit.lucenekmp.search.TestQueryVisitor |
| [org.apache.lucene.search.TestRangeFieldsDocValuesQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestRangeFieldsDocValuesQuery.java) | org.gnit.lucenekmp.search.TestRangeFieldsDocValuesQuery |
| [org.apache.lucene.search.TestRegexpQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestRegexpQuery.java) | org.gnit.lucenekmp.search.TestRegexpQuery |
| [org.apache.lucene.search.TestRegexpRandom](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestRegexpRandom.java) | org.gnit.lucenekmp.search.TestRegexpRandom |
| [org.apache.lucene.search.TestRegexpRandom2](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestRegexpRandom2.java) | org.gnit.lucenekmp.search.TestRegexpRandom2 |
| [org.apache.lucene.search.TestReqOptSumScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestReqOptSumScorer.java) | org.gnit.lucenekmp.search.TestReqOptSumScorer |
| [org.apache.lucene.search.TestSameScoresWithThreads](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSameScoresWithThreads.java) | org.gnit.lucenekmp.search.TestSameScoresWithThreads |
| [org.apache.lucene.search.TestScoreCachingWrappingScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestScoreCachingWrappingScorer.java) | org.gnit.lucenekmp.search.TestScoreCachingWrappingScorer |
| [org.apache.lucene.search.TestScorerPerf](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestScorerPerf.java) | org.gnit.lucenekmp.search.TestScorerPerf |
| [org.apache.lucene.search.TestScorerUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestScorerUtil.java) | org.gnit.lucenekmp.search.TestScorerUtil |
| [org.apache.lucene.search.TestSearchAfter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSearchAfter.java) | org.gnit.lucenekmp.search.TestSearchAfter |
| [org.apache.lucene.search.TestSearchWithThreads](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSearchWithThreads.java) | org.gnit.lucenekmp.search.TestSearchWithThreads |
| [org.apache.lucene.search.TestSearcherManager](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSearcherManager.java) | org.gnit.lucenekmp.search.TestSearcherManager |
| [org.apache.lucene.search.TestSeededKnnByteVectorQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSeededKnnByteVectorQuery.java) | org.gnit.lucenekmp.search.TestSeededKnnByteVectorQuery |
| [org.apache.lucene.search.TestSeededKnnFloatVectorQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSeededKnnFloatVectorQuery.java) | org.gnit.lucenekmp.search.TestSeededKnnFloatVectorQuery |
| [org.apache.lucene.search.TestSegmentCacheables](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSegmentCacheables.java) | org.gnit.lucenekmp.search.TestSegmentCacheables |
| [org.apache.lucene.search.TestShardSearching](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestShardSearching.java) | org.gnit.lucenekmp.search.TestShardSearching |
| [org.apache.lucene.search.TestSimilarity](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSimilarity.java) | org.gnit.lucenekmp.search.TestSimilarity |
| [org.apache.lucene.search.TestSimilarityProvider](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSimilarityProvider.java) | org.gnit.lucenekmp.search.TestSimilarityProvider |
| [org.apache.lucene.search.TestSimpleExplanations](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSimpleExplanations.java) | org.gnit.lucenekmp.search.TestSimpleExplanations |
| [org.apache.lucene.search.TestSimpleExplanationsOfNonMatches](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSimpleExplanationsOfNonMatches.java) | org.gnit.lucenekmp.search.TestSimpleExplanationsOfNonMatches |
| [org.apache.lucene.search.TestSimpleExplanationsWithFillerDocs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSimpleExplanationsWithFillerDocs.java) | org.gnit.lucenekmp.search.TestSimpleExplanationsWithFillerDocs |
| [org.apache.lucene.search.TestSloppyPhraseQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSloppyPhraseQuery.java) | org.gnit.lucenekmp.search.TestSloppyPhraseQuery |
| [org.apache.lucene.search.TestSloppyPhraseQuery2](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSloppyPhraseQuery2.java) | org.gnit.lucenekmp.search.TestSloppyPhraseQuery2 |
| [org.apache.lucene.search.TestSortOptimization](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSortOptimization.java) | org.gnit.lucenekmp.search.TestSortOptimization |
| [org.apache.lucene.search.TestSortRandom](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSortRandom.java) | org.gnit.lucenekmp.search.TestSortRandom |
| [org.apache.lucene.search.TestSortRescorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSortRescorer.java) | org.gnit.lucenekmp.search.TestSortRescorer |
| [org.apache.lucene.search.TestSortedNumericSortField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSortedNumericSortField.java) | org.gnit.lucenekmp.search.TestSortedNumericSortField |
| [org.apache.lucene.search.TestSortedSetSelector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSortedSetSelector.java) | org.gnit.lucenekmp.search.TestSortedSetSelector |
| [org.apache.lucene.search.TestSortedSetSortField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSortedSetSortField.java) | org.gnit.lucenekmp.search.TestSortedSetSortField |
| [org.apache.lucene.search.TestSynonymQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSynonymQuery.java) | org.gnit.lucenekmp.search.TestSynonymQuery |
| [org.apache.lucene.search.TestTermInSetQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestTermInSetQuery.java) | org.gnit.lucenekmp.search.TestTermInSetQuery |
| [org.apache.lucene.search.TestTermQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestTermQuery.java) | org.gnit.lucenekmp.search.TestTermQuery |
| [org.apache.lucene.search.TestTermRangeQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestTermRangeQuery.java) | org.gnit.lucenekmp.search.TestTermRangeQuery |
| [org.apache.lucene.search.TestTermScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestTermScorer.java) | org.gnit.lucenekmp.search.TestTermScorer |
| [org.apache.lucene.search.TestTopDocsCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestTopDocsCollector.java) | org.gnit.lucenekmp.search.TestTopDocsCollector |
| [org.apache.lucene.search.TestTopDocsMerge](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestTopDocsMerge.java) | org.gnit.lucenekmp.search.TestTopDocsMerge |
| [org.apache.lucene.search.TestTopDocsRRF](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestTopDocsRRF.java) | org.gnit.lucenekmp.search.TestTopDocsRRF |
| [org.apache.lucene.search.TestTopFieldCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestTopFieldCollector.java) | org.gnit.lucenekmp.search.TestTopFieldCollector |
| [org.apache.lucene.search.TestTopFieldCollectorEarlyTermination](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestTopFieldCollectorEarlyTermination.java) | org.gnit.lucenekmp.search.TestTopFieldCollectorEarlyTermination |
| [org.apache.lucene.search.TestTopKnnResults](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestTopKnnResults.java) | org.gnit.lucenekmp.search.TestTopKnnResults |
| [org.apache.lucene.search.TestTotalHitCountCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestTotalHitCountCollector.java) | org.gnit.lucenekmp.search.TestTotalHitCountCollector |
| [org.apache.lucene.search.TestTotalHits](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestTotalHits.java) | org.gnit.lucenekmp.search.TestTotalHits |
| [org.apache.lucene.search.TestUsageTrackingFilterCachingPolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestUsageTrackingFilterCachingPolicy.java) | org.gnit.lucenekmp.search.TestUsageTrackingFilterCachingPolicy |
| [org.apache.lucene.search.TestVectorSimilarityCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestVectorSimilarityCollector.java) | org.gnit.lucenekmp.search.TestVectorSimilarityCollector |
| [org.apache.lucene.search.TestVectorSimilarityValuesSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestVectorSimilarityValuesSource.java) | org.gnit.lucenekmp.search.TestVectorSimilarityValuesSource |
| [org.apache.lucene.search.TestWANDScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestWANDScorer.java) | org.gnit.lucenekmp.search.TestWANDScorer |
| [org.apache.lucene.search.TestWildcardQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestWildcardQuery.java) | org.gnit.lucenekmp.search.TestWildcardQuery |
| [org.apache.lucene.search.TestWildcardRandom](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestWildcardRandom.java) | org.gnit.lucenekmp.search.TestWildcardRandom |
| [org.apache.lucene.search.TestXYPointDistanceSort](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestXYPointDistanceSort.java) | org.gnit.lucenekmp.search.TestXYPointDistanceSort |

