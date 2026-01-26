# Lucene KMP Port Progress

## Priority 1 Dependencies (Java)
* Dependencies: 272 at Depth 1
* Dependencies: 377 at Depth 2
* Dependencies: 351 at Depth 3
* Dependencies: 260 at Depth 4
* Dependencies: 84 at Depth 5
* Dependencies: 85 at Depth 6
* Dependencies: 34 at Depth 7
* Dependencies: 3 at Depth 8
### Total priority-1 classes and their dependencies: 668

## Unit Test Dependencies (Java)
### total unit test classes: 5016
* Unit Test Dependencies: 597 at Depth 1
* Unit Test Dependencies: 764 at Depth 2
* Unit Test Dependencies: 633 at Depth 3
* Unit Test Dependencies: 352 at Depth 4
* Unit Test Dependencies: 163 at Depth 5
* Unit Test Dependencies: 56 at Depth 6
* Unit Test Dependencies: 18 at Depth 7
### Total Unit Test and their Dependencies: 1078

## Priority 1 Dependencies (KMP)
### Total KMP classes: 948

## Unit Test Dependencies (KMP)
### Total KMP Unit Test classes: 1204

## Progress Table for Lucene Classes
| Java Class | KMP Class | Depth | Class Ported | Java Core Methods | KMP Core Methods | Semantic Progress | Missing Core Methods |
| --- | --- | --- | --- | --- | --- | --- | --- |
| [org.apache.lucene.analysis.Analyzer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/Analyzer.java) | org.gnit.lucenekmp.analysis.Analyzer | Depth 1 | [x] | 11 | 2 | 0% | 11 |
| [org.apache.lucene.analysis.CharArrayMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/CharArrayMap.java) | org.gnit.lucenekmp.analysis.CharArrayMap | Depth 4 | [x] | 9 | 8 | 95% | 1 |
| [org.apache.lucene.analysis.CharArraySet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/CharArraySet.java) | org.gnit.lucenekmp.analysis.CharArraySet | Depth 2 | [x] | 9 | 1 | 55% | 8 |
| [org.apache.lucene.analysis.CharFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/CharFilter.java) | org.gnit.lucenekmp.analysis.CharFilter | Depth 3 | [x] | 3 | 7 | 34% | 0 |
| [org.apache.lucene.analysis.CharacterUtils](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/CharacterUtils.java) | org.gnit.lucenekmp.analysis.CharacterUtils | Depth 4 | [x] | 1 | 1 | 53% | 0 |
| [org.apache.lucene.analysis.FilteringTokenFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/FilteringTokenFilter.java) | org.gnit.lucenekmp.analysis.FilteringTokenFilter | Depth 3 | [x] | 21 | 19 | 84% | 4 |
| [org.apache.lucene.analysis.LowerCaseFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/LowerCaseFilter.java) | org.gnit.lucenekmp.analysis.LowerCaseFilter | Depth 2 | [x] | 21 | 19 | 84% | 4 |
| [org.apache.lucene.analysis.ReusableStringReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/ReusableStringReader.java) | org.gnit.lucenekmp.analysis.ReusableStringReader | Depth 1 | [x] | 2 | 5 | 47% | 0 |
| [org.apache.lucene.analysis.StopFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/StopFilter.java) | org.gnit.lucenekmp.analysis.StopFilter | Depth 2 | [x] | 25 | 4 | 100% | 21 |
| [org.apache.lucene.analysis.StopwordAnalyzerBase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/StopwordAnalyzerBase.java) | org.gnit.lucenekmp.analysis.StopwordAnalyzerBase | Depth 2 | [x] | 13 | 2 | 100% | 11 |
| [org.apache.lucene.analysis.TokenFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/TokenFilter.java) | org.gnit.lucenekmp.analysis.TokenFilter | Depth 3 | [x] | 21 | 19 | 84% | 4 |
| [org.apache.lucene.analysis.TokenStream](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/TokenStream.java) | org.gnit.lucenekmp.analysis.TokenStream | Depth 1 | [x] | 20 | 0 | 0% | 20 |
| [org.apache.lucene.analysis.Tokenizer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/Tokenizer.java) | org.gnit.lucenekmp.analysis.Tokenizer | Depth 3 | [x] | 2 | 5 | 32% | 0 |
| [org.apache.lucene.analysis.standard.StandardAnalyzer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/standard/StandardAnalyzer.java) | org.gnit.lucenekmp.analysis.standard.StandardAnalyzer | Depth 1 | [x] | 13 | 0 | 100% | 13 |
| [org.apache.lucene.analysis.standard.StandardTokenizer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/standard/StandardTokenizer.java) | org.gnit.lucenekmp.analysis.standard.StandardTokenizer | Depth 2 | [x] | 23 | 0 | 0% | 23 |
| [org.apache.lucene.analysis.standard.StandardTokenizerImpl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/standard/StandardTokenizerImpl.java) | org.gnit.lucenekmp.analysis.standard.StandardTokenizerImpl | Depth 3 | [x] | 23 | 14 | 89% | 9 |
| [org.apache.lucene.analysis.tokenattributes.BytesTermAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/BytesTermAttribute.java) | org.gnit.lucenekmp.analysis.tokenattributes.BytesTermAttribute | Depth 2 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.analysis.tokenattributes.CharTermAttributeImpl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/CharTermAttributeImpl.java) | org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttributeImpl | Depth 3 | [x] | 19 | 0 | 100% | 19 |
| [org.apache.lucene.analysis.tokenattributes.PackedTokenAttributeImpl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/PackedTokenAttributeImpl.java) | org.gnit.lucenekmp.analysis.tokenattributes.PackedTokenAttributeImpl | Depth 2 | [x] | 22 | 24 | 86% | 1 |
| [org.apache.lucene.analysis.tokenattributes.PayloadAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/PayloadAttribute.java) | org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute | Depth 3 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/PositionIncrementAttribute.java) | org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute | Depth 3 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/PositionLengthAttribute.java) | org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute | Depth 3 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.analysis.tokenattributes.TermFrequencyAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/TermFrequencyAttribute.java) | org.gnit.lucenekmp.analysis.tokenattributes.TermFrequencyAttribute | Depth 3 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/TermToBytesRefAttribute.java) | org.gnit.lucenekmp.analysis.tokenattributes.TermToBytesRefAttribute | Depth 1 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.analysis.tokenattributes.TypeAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/TypeAttribute.java) | org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute | Depth 3 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.codecs.BlockTermState](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/BlockTermState.java) | org.gnit.lucenekmp.codecs.BlockTermState | Depth 4 | [x] | 1 | 1 | 21% | 0 |
| [org.apache.lucene.codecs.Codec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/Codec.java) | org.gnit.lucenekmp.codecs.Codec | Depth 2 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.codecs.CompetitiveImpactAccumulator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/CompetitiveImpactAccumulator.java) | org.gnit.lucenekmp.codecs.CompetitiveImpactAccumulator | Depth 4 | [x] | 5 | 6 | 85% | 0 |
| [org.apache.lucene.codecs.CompoundDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/CompoundDirectory.java) | org.gnit.lucenekmp.codecs.CompoundDirectory | Depth 1 | [x] | 16 | 15 | 100% | 1 |
| [org.apache.lucene.codecs.DocValuesConsumer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/DocValuesConsumer.java) | org.gnit.lucenekmp.codecs.DocValuesConsumer | Depth 3 | [x] | 8 | 12 | 0% | 8 |
| [org.apache.lucene.codecs.DocValuesFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/DocValuesFormat.java) | org.gnit.lucenekmp.codecs.DocValuesFormat | Depth 3 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.codecs.FieldsProducer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/FieldsProducer.java) | org.gnit.lucenekmp.codecs.FieldsProducer | Depth 2 | [x] | 3 | 3 | 96% | 0 |
| [org.apache.lucene.codecs.KnnFieldVectorsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/KnnFieldVectorsWriter.java) | org.gnit.lucenekmp.codecs.KnnFieldVectorsWriter | Depth 3 | [x] | 3 | 3 | 88% | 0 |
| [org.apache.lucene.codecs.KnnVectorsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/KnnVectorsFormat.java) | org.gnit.lucenekmp.codecs.KnnVectorsFormat | Depth 3 | [x] | 6 | 0 | 0% | 6 |
| [org.apache.lucene.codecs.KnnVectorsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/KnnVectorsWriter.java) | org.gnit.lucenekmp.codecs.KnnVectorsWriter | Depth 3 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.codecs.NormsConsumer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/NormsConsumer.java) | org.gnit.lucenekmp.codecs.NormsConsumer | Depth 3 | [x] | 3 | 7 | 0% | 3 |
| [org.apache.lucene.codecs.PointsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/PointsFormat.java) | org.gnit.lucenekmp.codecs.PointsFormat | Depth 3 | [x] | 2 | 3 | 0% | 2 |
| [org.apache.lucene.codecs.PointsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/PointsWriter.java) | org.gnit.lucenekmp.codecs.PointsWriter | Depth 3 | [x] | 3 | 3 | 0% | 3 |
| [org.apache.lucene.codecs.PostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/PostingsFormat.java) | org.gnit.lucenekmp.codecs.PostingsFormat | Depth 3 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.codecs.PushPostingsWriterBase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/PushPostingsWriterBase.java) | org.gnit.lucenekmp.codecs.PushPostingsWriterBase | Depth 4 | [x] | 10 | 10 | 59% | 0 |
| [org.apache.lucene.codecs.StoredFieldsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/StoredFieldsReader.java) | org.gnit.lucenekmp.codecs.StoredFieldsReader | Depth 2 | [x] | 5 | 5 | 96% | 0 |
| [org.apache.lucene.codecs.StoredFieldsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/StoredFieldsWriter.java) | org.gnit.lucenekmp.codecs.StoredFieldsWriter | Depth 3 | [x] | 9 | 2 | 0% | 9 |
| [org.apache.lucene.codecs.TermVectorsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/TermVectorsReader.java) | org.gnit.lucenekmp.codecs.TermVectorsReader | Depth 2 | [x] | 4 | 4 | 92% | 0 |
| [org.apache.lucene.codecs.TermVectorsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/TermVectorsWriter.java) | org.gnit.lucenekmp.codecs.TermVectorsWriter | Depth 3 | [x] | 2 | 2 | 35% | 0 |
| [org.apache.lucene.codecs.compressing.CompressionMode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/compressing/CompressionMode.java) | org.gnit.lucenekmp.codecs.compressing.CompressionMode | Depth 5 | [x] | 2 | 2 | 0% | 2 |
| [org.apache.lucene.codecs.compressing.Decompressor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/compressing/Decompressor.java) | org.gnit.lucenekmp.codecs.compressing.Decompressor | Depth 5 | [x] | 1 | 1 | 85% | 0 |
| [org.apache.lucene.codecs.compressing.MatchingReaders](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/compressing/MatchingReaders.java) | org.gnit.lucenekmp.codecs.compressing.MatchingReaders | Depth 6 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.codecs.hnsw.DefaultFlatVectorScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/hnsw/DefaultFlatVectorScorer.java) | org.gnit.lucenekmp.codecs.hnsw.DefaultFlatVectorScorer | Depth 7 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.codecs.hnsw.FlatVectorsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/hnsw/FlatVectorsReader.java) | org.gnit.lucenekmp.codecs.hnsw.FlatVectorsReader | Depth 3 | [x] | 9 | 9 | 91% | 0 |
| [org.apache.lucene.codecs.lucene101.ForDeltaUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene101/ForDeltaUtil.java) | org.gnit.lucenekmp.codecs.lucene101.ForDeltaUtil | Depth 4 | [x] | 19 | 16 | 85% | 3 |
| [org.apache.lucene.codecs.lucene101.ForUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene101/ForUtil.java) | org.gnit.lucenekmp.codecs.lucene101.ForUtil | Depth 3 | [x] | 30 | 28 | 53% | 2 |
| [org.apache.lucene.codecs.lucene101.Lucene101PostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene101/Lucene101PostingsFormat.java) | org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat | Depth 3 | [x] | 1 | 1 | 13% | 0 |
| [org.apache.lucene.codecs.lucene101.Lucene101PostingsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene101/Lucene101PostingsReader.java) | org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsReader | Depth 4 | [x] | 4 | 1 | 0% | 4 |
| [org.apache.lucene.codecs.lucene101.Lucene101PostingsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene101/Lucene101PostingsWriter.java) | org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsWriter | Depth 3 | [x] | 15 | 3 | 83% | 12 |
| [org.apache.lucene.codecs.lucene101.PForUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene101/PForUtil.java) | org.gnit.lucenekmp.codecs.lucene101.PForUtil | Depth 4 | [x] | 4 | 2 | 100% | 2 |
| [org.apache.lucene.codecs.lucene90.blocktree.CompressionAlgorithm](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/blocktree/CompressionAlgorithm.java) | org.gnit.lucenekmp.codecs.lucene90.blocktree.CompressionAlgorithm | Depth 6 | [x] | 3 | 2 | 66% | 1 |
| [org.apache.lucene.codecs.lucene90.blocktree.FieldReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/blocktree/FieldReader.java) | org.gnit.lucenekmp.codecs.lucene90.blocktree.FieldReader | Depth 4 | [x] | 8 | 1 | 100% | 7 |
| [org.apache.lucene.codecs.lucene90.blocktree.IntersectTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/blocktree/IntersectTermsEnum.java) | org.gnit.lucenekmp.codecs.lucene90.blocktree.IntersectTermsEnum | Depth 6 | [x] | 1 | 0 | 0% | 1 |
| [org.apache.lucene.codecs.lucene90.blocktree.IntersectTermsEnumFrame](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/blocktree/IntersectTermsEnumFrame.java) | org.gnit.lucenekmp.codecs.lucene90.blocktree.IntersectTermsEnumFrame | Depth 6 | [x] | 7 | 7 | 20% | 0 |
| [org.apache.lucene.codecs.lucene90.blocktree.Lucene90BlockTreeTermsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/blocktree/Lucene90BlockTreeTermsReader.java) | org.gnit.lucenekmp.codecs.lucene90.blocktree.Lucene90BlockTreeTermsReader | Depth 3 | [x] | 5 | 2 | 55% | 3 |
| [org.apache.lucene.codecs.lucene90.blocktree.Lucene90BlockTreeTermsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/blocktree/Lucene90BlockTreeTermsWriter.java) | org.gnit.lucenekmp.codecs.lucene90.blocktree.Lucene90BlockTreeTermsWriter | Depth 4 | [x] | 2 | 6 | 0% | 2 |
| [org.apache.lucene.codecs.lucene90.blocktree.SegmentTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/blocktree/SegmentTermsEnum.java) | org.gnit.lucenekmp.codecs.lucene90.blocktree.SegmentTermsEnum | Depth 6 | [x] | 24 | 24 | 80% | 0 |
| [org.apache.lucene.codecs.lucene90.blocktree.SegmentTermsEnumFrame](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/blocktree/SegmentTermsEnumFrame.java) | org.gnit.lucenekmp.codecs.lucene90.blocktree.SegmentTermsEnumFrame | Depth 6 | [x] | 15 | 15 | 32% | 0 |
| [org.apache.lucene.codecs.lucene90.blocktree.Stats](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/blocktree/Stats.java) | org.gnit.lucenekmp.codecs.lucene90.blocktree.Stats | Depth 5 | [x] | 4 | 1 | 14% | 4 |
| [org.apache.lucene.codecs.lucene90.compressing.FieldsIndex](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/compressing/FieldsIndex.java) | org.gnit.lucenekmp.codecs.lucene90.compressing.FieldsIndex | Depth 6 | [x] | 5 | 5 | 96% | 0 |
| [org.apache.lucene.codecs.lucene90.compressing.FieldsIndexReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/compressing/FieldsIndexReader.java) | org.gnit.lucenekmp.codecs.lucene90.compressing.FieldsIndexReader | Depth 6 | [x] | 6 | 0 | 100% | 6 |
| [org.apache.lucene.codecs.lucene90.compressing.FieldsIndexWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/compressing/FieldsIndexWriter.java) | org.gnit.lucenekmp.codecs.lucene90.compressing.FieldsIndexWriter | Depth 6 | [x] | 3 | 0 | 100% | 3 |
| [org.apache.lucene.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/compressing/Lucene90CompressingStoredFieldsReader.java) | org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsReader | Depth 6 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/compressing/Lucene90CompressingStoredFieldsWriter.java) | org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter | Depth 6 | [x] | 2 | 1 | 0% | 2 |
| [org.apache.lucene.codecs.lucene90.compressing.Lucene90CompressingTermVectorsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/compressing/Lucene90CompressingTermVectorsReader.java) | org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsReader | Depth 6 | [x] | 3 | 14 | 0% | 3 |
| [org.apache.lucene.codecs.lucene90.compressing.Lucene90CompressingTermVectorsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/compressing/Lucene90CompressingTermVectorsWriter.java) | org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsWriter | Depth 6 | [x] | 2 | 2 | 0% | 2 |
| [org.apache.lucene.codecs.perfield.PerFieldKnnVectorsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/perfield/PerFieldKnnVectorsFormat.java) | org.gnit.lucenekmp.codecs.perfield.PerFieldKnnVectorsFormat | Depth 4 | [x] | 10 | 1 | 45% | 9 |
| [org.apache.lucene.document.DocValuesLongHashSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/DocValuesLongHashSet.java) | org.gnit.lucenekmp.document.DocValuesLongHashSet | Depth 4 | [x] | 3 | 0 | 100% | 3 |
| [org.apache.lucene.document.Field](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/Field.java) | org.gnit.lucenekmp.document.Field | Depth 1 | [x] | 9 | 19 | 0% | 9 |
| [org.apache.lucene.document.IntPoint](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/IntPoint.java) | org.gnit.lucenekmp.document.IntPoint | Depth 1 | [x] | 7 | 0 | 0% | 7 |
| [org.apache.lucene.document.InvertableType](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/InvertableType.java) | org.gnit.lucenekmp.document.InvertableType | Depth 1 | [x] | 1 | 1 | 75% | 0 |
| [org.apache.lucene.document.KnnByteVectorField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/KnnByteVectorField.java) | org.gnit.lucenekmp.document.KnnByteVectorField | Depth 3 | [x] | 13 | 3 | 93% | 10 |
| [org.apache.lucene.document.KnnFloatVectorField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/KnnFloatVectorField.java) | org.gnit.lucenekmp.document.KnnFloatVectorField | Depth 3 | [x] | 13 | 3 | 93% | 10 |
| [org.apache.lucene.document.NumericDocValuesField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/NumericDocValuesField.java) | org.gnit.lucenekmp.document.NumericDocValuesField | Depth 2 | [x] | 13 | 4 | 86% | 9 |
| [org.apache.lucene.document.SortedNumericDocValuesRangeQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/SortedNumericDocValuesRangeQuery.java) | org.gnit.lucenekmp.document.SortedNumericDocValuesRangeQuery | Depth 4 | [x] | 7 | 1 | 0% | 7 |
| [org.apache.lucene.document.SortedNumericDocValuesSetQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/SortedNumericDocValuesSetQuery.java) | org.gnit.lucenekmp.document.SortedNumericDocValuesSetQuery | Depth 4 | [x] | 7 | 1 | 0% | 7 |
| [org.apache.lucene.document.StoredValue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/StoredValue.java) | org.gnit.lucenekmp.document.StoredValue | Depth 2 | [x] | 1 | 1 | 75% | 0 |
| [org.apache.lucene.index.ApproximatePriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ApproximatePriorityQueue.java) | org.gnit.lucenekmp.index.ApproximatePriorityQueue | Depth 5 | [x] | 4 | 4 | 34% | 3 |
| [org.apache.lucene.index.AutomatonTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/AutomatonTermsEnum.java) | org.gnit.lucenekmp.index.AutomatonTermsEnum | Depth 2 | [x] | 17 | 17 | 91% | 0 |
| [org.apache.lucene.index.BaseCompositeReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/BaseCompositeReader.java) | org.gnit.lucenekmp.index.BaseCompositeReader | Depth 2 | [x] | 3 | 3 | 93% | 0 |
| [org.apache.lucene.index.BinaryDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/BinaryDocValues.java) | org.gnit.lucenekmp.index.BinaryDocValues | Depth 2 | [x] | 9 | 7 | 100% | 2 |
| [org.apache.lucene.index.BinaryDocValuesFieldUpdates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/BinaryDocValuesFieldUpdates.java) | org.gnit.lucenekmp.index.BinaryDocValuesFieldUpdates | Depth 2 | [x] | 15 | 11 | 100% | 4 |
| [org.apache.lucene.index.BufferedUpdates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/BufferedUpdates.java) | org.gnit.lucenekmp.index.BufferedUpdates | Depth 3 | [x] | 3 | 1 | 0% | 3 |
| [org.apache.lucene.index.BufferedUpdatesStream](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/BufferedUpdatesStream.java) | org.gnit.lucenekmp.index.BufferedUpdatesStream | Depth 2 | [x] | 3 | 3 | 0% | 3 |
| [org.apache.lucene.index.ByteSlicePool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ByteSlicePool.java) | org.gnit.lucenekmp.index.ByteSlicePool | Depth 4 | [x] | 3 | 0 | 0% | 3 |
| [org.apache.lucene.index.ByteSliceReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ByteSliceReader.java) | org.gnit.lucenekmp.index.ByteSliceReader | Depth 4 | [x] | 21 | 21 | 69% | 0 |
| [org.apache.lucene.index.ByteVectorValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ByteVectorValues.java) | org.gnit.lucenekmp.index.ByteVectorValues | Depth 3 | [x] | 9 | 6 | 100% | 3 |
| [org.apache.lucene.index.CachingMergeContext](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/CachingMergeContext.java) | org.gnit.lucenekmp.index.CachingMergeContext | Depth 1 | [x] | 2 | 2 | 72% | 0 |
| [org.apache.lucene.index.CheckIndex](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/CheckIndex.java) | org.gnit.lucenekmp.index.CheckIndex | Depth 3 | [x] | 0 | 3 | 0% | 0 |
| [org.apache.lucene.index.CompositeReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/CompositeReader.java) | org.gnit.lucenekmp.index.CompositeReader | Depth 2 | [x] | 20 | 20 | 89% | 2 |
| [org.apache.lucene.index.CompositeReaderContext](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/CompositeReaderContext.java) | org.gnit.lucenekmp.index.CompositeReaderContext | Depth 2 | [x] | 1 | 1 | 0% | 1 |
| [org.apache.lucene.index.ConcurrentApproximatePriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ConcurrentApproximatePriorityQueue.java) | org.gnit.lucenekmp.index.ConcurrentApproximatePriorityQueue | Depth 4 | [x] | 4 | 0 | 75% | 4 |
| [org.apache.lucene.index.ConcurrentMergeScheduler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ConcurrentMergeScheduler.java) | org.gnit.lucenekmp.index.ConcurrentMergeScheduler | Depth 3 | [x] | 16 | 3 | 0% | 16 |
| [org.apache.lucene.index.CorruptIndexException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/CorruptIndexException.java) | org.gnit.lucenekmp.index.CorruptIndexException | Depth 2 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.index.DirectoryReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DirectoryReader.java) | org.gnit.lucenekmp.index.DirectoryReader | Depth 1 | [x] | 37 | 13 | 89% | 25 |
| [org.apache.lucene.index.DocIDMerger](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocIDMerger.java) | org.gnit.lucenekmp.index.DocIDMerger | Depth 4 | [x] | 3 | 2 | 0% | 3 |
| [org.apache.lucene.index.DocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocValues.java) | org.gnit.lucenekmp.index.DocValues | Depth 2 | [x] | 9 | 11 | 54% | 3 |
| [org.apache.lucene.index.DocValuesFieldUpdates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocValuesFieldUpdates.java) | org.gnit.lucenekmp.index.DocValuesFieldUpdates | Depth 2 | [x] | 10 | 21 | 0% | 10 |
| [org.apache.lucene.index.DocValuesIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocValuesIterator.java) | org.gnit.lucenekmp.index.DocValuesIterator | Depth 2 | [x] | 8 | 6 | 100% | 2 |
| [org.apache.lucene.index.DocValuesLeafReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocValuesLeafReader.java) | org.gnit.lucenekmp.index.DocValuesLeafReader | Depth 4 | [x] | 38 | 42 | 86% | 2 |
| [org.apache.lucene.index.DocValuesSkipIndexType](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocValuesSkipIndexType.java) | org.gnit.lucenekmp.index.DocValuesSkipIndexType | Depth 3 | [x] | 2 | 2 | 82% | 0 |
| [org.apache.lucene.index.DocValuesType](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocValuesType.java) | org.gnit.lucenekmp.index.DocValuesType | Depth 1 | [x] | 1 | 1 | 75% | 0 |
| [org.apache.lucene.index.DocValuesUpdate](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocValuesUpdate.java) | org.gnit.lucenekmp.index.DocValuesUpdate | Depth 1 | [x] | 5 | 1 | 0% | 5 |
| [org.apache.lucene.index.DocsWithFieldSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocsWithFieldSet.java) | org.gnit.lucenekmp.index.DocsWithFieldSet | Depth 3 | [x] | 2 | 0 | 100% | 2 |
| [org.apache.lucene.index.DocumentsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriter.java) | org.gnit.lucenekmp.index.DocumentsWriter | Depth 1 | [x] | 27 | 1 | 0% | 27 |
| [org.apache.lucene.index.DocumentsWriterDeleteQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriterDeleteQueue.java) | org.gnit.lucenekmp.index.DocumentsWriterDeleteQueue | Depth 2 | [x] | 0 | 1 | 0% | 0 |
| [org.apache.lucene.index.DocumentsWriterFlushControl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriterFlushControl.java) | org.gnit.lucenekmp.index.DocumentsWriterFlushControl | Depth 1 | [x] | 37 | 1 | 0% | 37 |
| [org.apache.lucene.index.DocumentsWriterFlushQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriterFlushQueue.java) | org.gnit.lucenekmp.index.DocumentsWriterFlushQueue | Depth 1 | [x] | 9 | 3 | 0% | 9 |
| [org.apache.lucene.index.DocumentsWriterPerThread](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriterPerThread.java) | org.gnit.lucenekmp.index.DocumentsWriterPerThread | Depth 2 | [x] | 1 | 2 | 45% | 0 |
| [org.apache.lucene.index.DocumentsWriterPerThreadPool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriterPerThreadPool.java) | org.gnit.lucenekmp.index.DocumentsWriterPerThreadPool | Depth 2 | [x] | 10 | 1 | 0% | 10 |
| [org.apache.lucene.index.DocumentsWriterStallControl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriterStallControl.java) | org.gnit.lucenekmp.index.DocumentsWriterStallControl | Depth 2 | [x] | 8 | 7 | 100% | 1 |
| [org.apache.lucene.index.FieldInfo](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FieldInfo.java) | org.gnit.lucenekmp.index.FieldInfo | Depth 1 | [x] | 21 | 7 | 100% | 14 |
| [org.apache.lucene.index.FieldInfos](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FieldInfos.java) | org.gnit.lucenekmp.index.FieldInfos | Depth 2 | [x] | 4 | 0 | 0% | 4 |
| [org.apache.lucene.index.FieldInvertState](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FieldInvertState.java) | org.gnit.lucenekmp.index.FieldInvertState | Depth 2 | [x] | 1 | 1 | 44% | 0 |
| [org.apache.lucene.index.FieldUpdatesBuffer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FieldUpdatesBuffer.java) | org.gnit.lucenekmp.index.FieldUpdatesBuffer | Depth 2 | [x] | 11 | 2 | 83% | 9 |
| [org.apache.lucene.index.Fields](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/Fields.java) | org.gnit.lucenekmp.index.Fields | Depth 2 | [x] | 1 | 0 | 0% | 1 |
| [org.apache.lucene.index.FilterCodecReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FilterCodecReader.java) | org.gnit.lucenekmp.index.FilterCodecReader | Depth 2 | [x] | 41 | 43 | 86% | 4 |
| [org.apache.lucene.index.FilterLeafReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FilterLeafReader.java) | org.gnit.lucenekmp.index.FilterLeafReader | Depth 5 | [x] | 11 | 11 | 94% | 0 |
| [org.apache.lucene.index.FilterMergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FilterMergePolicy.java) | org.gnit.lucenekmp.index.FilterMergePolicy | Depth 2 | [x] | 16 | 16 | 86% | 1 |
| [org.apache.lucene.index.FilteredTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FilteredTermsEnum.java) | org.gnit.lucenekmp.index.FilteredTermsEnum | Depth 3 | [x] | 13 | 0 | 100% | 13 |
| [org.apache.lucene.index.FloatVectorValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FloatVectorValues.java) | org.gnit.lucenekmp.index.FloatVectorValues | Depth 3 | [x] | 9 | 6 | 100% | 3 |
| [org.apache.lucene.index.FlushByRamOrCountsPolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FlushByRamOrCountsPolicy.java) | org.gnit.lucenekmp.index.FlushByRamOrCountsPolicy | Depth 2 | [x] | 9 | 9 | 78% | 0 |
| [org.apache.lucene.index.FlushPolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FlushPolicy.java) | org.gnit.lucenekmp.index.FlushPolicy | Depth 1 | [x] | 4 | 4 | 62% | 0 |
| [org.apache.lucene.index.FreqProxFields](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FreqProxFields.java) | org.gnit.lucenekmp.index.FreqProxFields | Depth 5 | [x] | 12 | 1 | 0% | 12 |
| [org.apache.lucene.index.FreqProxTermsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FreqProxTermsWriter.java) | org.gnit.lucenekmp.index.FreqProxTermsWriter | Depth 4 | [x] | 1 | 1 | 73% | 0 |
| [org.apache.lucene.index.FreqProxTermsWriterPerField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FreqProxTermsWriterPerField.java) | org.gnit.lucenekmp.index.FreqProxTermsWriterPerField | Depth 5 | [x] | 3 | 3 | 30% | 0 |
| [org.apache.lucene.index.FrozenBufferedUpdates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FrozenBufferedUpdates.java) | org.gnit.lucenekmp.index.FrozenBufferedUpdates | Depth 2 | [x] | 0 | 1 | 0% | 0 |
| [org.apache.lucene.index.Impact](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/Impact.java) | org.gnit.lucenekmp.index.Impact | Depth 3 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.index.ImpactsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ImpactsEnum.java) | org.gnit.lucenekmp.index.ImpactsEnum | Depth 3 | [x] | 12 | 9 | 91% | 3 |
| [org.apache.lucene.index.IndexFileDeleter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexFileDeleter.java) | org.gnit.lucenekmp.index.IndexFileDeleter | Depth 2 | [x] | 2 | 1 | 0% | 2 |
| [org.apache.lucene.index.IndexFileNames](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexFileNames.java) | org.gnit.lucenekmp.index.IndexFileNames | Depth 1 | [x] | 9 | 9 | 93% | 0 |
| [org.apache.lucene.index.IndexFormatTooNewException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexFormatTooNewException.java) | org.gnit.lucenekmp.index.IndexFormatTooNewException | Depth 3 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.index.IndexFormatTooOldException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexFormatTooOldException.java) | org.gnit.lucenekmp.index.IndexFormatTooOldException | Depth 2 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.index.IndexNotFoundException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexNotFoundException.java) | org.gnit.lucenekmp.index.IndexNotFoundException | Depth 1 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.index.IndexOptions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexOptions.java) | org.gnit.lucenekmp.index.IndexOptions | Depth 1 | [x] | 1 | 1 | 75% | 0 |
| [org.apache.lucene.index.IndexReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexReader.java) | org.gnit.lucenekmp.index.IndexReader | Depth 1 | [x] | 20 | 1 | 0% | 20 |
| [org.apache.lucene.index.IndexReaderContext](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexReaderContext.java) | org.gnit.lucenekmp.index.IndexReaderContext | Depth 1 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.index.IndexWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexWriter.java) | org.gnit.lucenekmp.index.IndexWriter | Depth 1 | [x] | 6 | 3 | 0% | 6 |
| [org.apache.lucene.index.IndexWriterConfig](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexWriterConfig.java) | org.gnit.lucenekmp.index.IndexWriterConfig | Depth 1 | [x] | 26 | 1 | 0% | 26 |
| [org.apache.lucene.index.IndexableField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexableField.java) | org.gnit.lucenekmp.index.IndexableField | Depth 1 | [x] | 8 | 1 | 0% | 8 |
| [org.apache.lucene.index.IndexingChain](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexingChain.java) | org.gnit.lucenekmp.index.IndexingChain | Depth 3 | [x] | 38 | 42 | 86% | 2 |
| [org.apache.lucene.index.KnnVectorValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/KnnVectorValues.java) | org.gnit.lucenekmp.index.KnnVectorValues | Depth 3 | [x] | 2 | 1 | 80% | 1 |
| [org.apache.lucene.index.LeafMetaData](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/LeafMetaData.java) | org.gnit.lucenekmp.index.LeafMetaData | Depth 1 | [x] | 3 | 0 | 25% | 3 |
| [org.apache.lucene.index.LeafReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/LeafReader.java) | org.gnit.lucenekmp.index.LeafReader | Depth 1 | [x] | 38 | 42 | 86% | 2 |
| [org.apache.lucene.index.LeafReaderContext](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/LeafReaderContext.java) | org.gnit.lucenekmp.index.LeafReaderContext | Depth 1 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.index.LiveIndexWriterConfig](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/LiveIndexWriterConfig.java) | org.gnit.lucenekmp.index.LiveIndexWriterConfig | Depth 1 | [x] | 6 | 6 | 52% | 0 |
| [org.apache.lucene.index.LockableConcurrentApproximatePriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/LockableConcurrentApproximatePriorityQueue.java) | org.gnit.lucenekmp.index.LockableConcurrentApproximatePriorityQueue | Depth 3 | [x] | 4 | 1 | 0% | 4 |
| [org.apache.lucene.index.MappedMultiFields](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MappedMultiFields.java) | org.gnit.lucenekmp.index.MappedMultiFields | Depth 4 | [x] | 6 | 11 | 0% | 6 |
| [org.apache.lucene.index.MappingMultiPostingsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MappingMultiPostingsEnum.java) | org.gnit.lucenekmp.index.MappingMultiPostingsEnum | Depth 6 | [x] | 2 | 2 | 40% | 0 |
| [org.apache.lucene.index.MergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MergePolicy.java) | org.gnit.lucenekmp.index.MergePolicy | Depth 1 | [x] | 15 | 1 | 0% | 15 |
| [org.apache.lucene.index.MergeRateLimiter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MergeRateLimiter.java) | org.gnit.lucenekmp.index.MergeRateLimiter | Depth 3 | [x] | 2 | 1 | 0% | 2 |
| [org.apache.lucene.index.MergeScheduler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MergeScheduler.java) | org.gnit.lucenekmp.index.MergeScheduler | Depth 1 | [x] | 6 | 3 | 0% | 6 |
| [org.apache.lucene.index.MergeState](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MergeState.java) | org.gnit.lucenekmp.index.MergeState | Depth 1 | [x] | 4 | 1 | 0% | 4 |
| [org.apache.lucene.index.MergeTrigger](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MergeTrigger.java) | org.gnit.lucenekmp.index.MergeTrigger | Depth 1 | [x] | 1 | 1 | 75% | 0 |
| [org.apache.lucene.index.MultiBits](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MultiBits.java) | org.gnit.lucenekmp.index.MultiBits | Depth 2 | [x] | 4 | 1 | 100% | 3 |
| [org.apache.lucene.index.MultiDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MultiDocValues.java) | org.gnit.lucenekmp.index.MultiDocValues | Depth 4 | [x] | 9 | 8 | 76% | 3 |
| [org.apache.lucene.index.MultiFields](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MultiFields.java) | org.gnit.lucenekmp.index.MultiFields | Depth 3 | [x] | 1 | 1 | 91% | 0 |
| [org.apache.lucene.index.MultiPostingsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MultiPostingsEnum.java) | org.gnit.lucenekmp.index.MultiPostingsEnum | Depth 6 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.index.MultiReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MultiReader.java) | org.gnit.lucenekmp.index.MultiReader | Depth 2 | [x] | 22 | 3 | 0% | 22 |
| [org.apache.lucene.index.MultiSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MultiSorter.java) | org.gnit.lucenekmp.index.MultiSorter | Depth 3 | [x] | 10 | 15 | 70% | 0 |
| [org.apache.lucene.index.MultiTerms](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MultiTerms.java) | org.gnit.lucenekmp.index.MultiTerms | Depth 4 | [x] | 9 | 3 | 100% | 6 |
| [org.apache.lucene.index.MultiTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MultiTermsEnum.java) | org.gnit.lucenekmp.index.MultiTermsEnum | Depth 6 | [x] | 12 | 6 | 0% | 12 |
| [org.apache.lucene.index.NumericDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/NumericDocValues.java) | org.gnit.lucenekmp.index.NumericDocValues | Depth 1 | [x] | 9 | 7 | 100% | 2 |
| [org.apache.lucene.index.NumericDocValuesFieldUpdates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/NumericDocValuesFieldUpdates.java) | org.gnit.lucenekmp.index.NumericDocValuesFieldUpdates | Depth 2 | [x] | 14 | 10 | 9% | 13 |
| [org.apache.lucene.index.NumericDocValuesWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/NumericDocValuesWriter.java) | org.gnit.lucenekmp.index.NumericDocValuesWriter | Depth 3 | [x] | 8 | 7 | 0% | 8 |
| [org.apache.lucene.index.OneMergeWrappingMergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/OneMergeWrappingMergePolicy.java) | org.gnit.lucenekmp.index.OneMergeWrappingMergePolicy | Depth 1 | [x] | 17 | 17 | 87% | 1 |
| [org.apache.lucene.index.OrdTermState](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/OrdTermState.java) | org.gnit.lucenekmp.index.OrdTermState | Depth 3 | [x] | 1 | 1 | 57% | 0 |
| [org.apache.lucene.index.OrdinalMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/OrdinalMap.java) | org.gnit.lucenekmp.index.OrdinalMap | Depth 3 | [x] | 1 | 15 | 0% | 1 |
| [org.apache.lucene.index.ParallelPostingsArray](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ParallelPostingsArray.java) | org.gnit.lucenekmp.index.ParallelPostingsArray | Depth 4 | [x] | 3 | 0 | 100% | 3 |
| [org.apache.lucene.index.PendingDeletes](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/PendingDeletes.java) | org.gnit.lucenekmp.index.PendingDeletes | Depth 2 | [x] | 13 | 13 | 83% | 0 |
| [org.apache.lucene.index.PendingSoftDeletes](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/PendingSoftDeletes.java) | org.gnit.lucenekmp.index.PendingSoftDeletes | Depth 1 | [x] | 18 | 2 | 100% | 16 |
| [org.apache.lucene.index.PointValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/PointValues.java) | org.gnit.lucenekmp.index.PointValues | Depth 3 | [x] | 7 | 0 | 100% | 7 |
| [org.apache.lucene.index.PointValuesWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/PointValuesWriter.java) | org.gnit.lucenekmp.index.PointValuesWriter | Depth 4 | [x] | 11 | 4 | 19% | 11 |
| [org.apache.lucene.index.PostingsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/PostingsEnum.java) | org.gnit.lucenekmp.index.PostingsEnum | Depth 2 | [x] | 11 | 1 | 25% | 10 |
| [org.apache.lucene.index.PrefixCodedTerms](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/PrefixCodedTerms.java) | org.gnit.lucenekmp.index.PrefixCodedTerms | Depth 2 | [x] | 1 | 2 | 0% | 1 |
| [org.apache.lucene.index.ReaderPool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ReaderPool.java) | org.gnit.lucenekmp.index.ReaderPool | Depth 2 | [x] | 0 | 1 | 0% | 0 |
| [org.apache.lucene.index.ReaderSlice](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ReaderSlice.java) | org.gnit.lucenekmp.index.ReaderSlice | Depth 3 | [x] | 1 | 0 | 0% | 1 |
| [org.apache.lucene.index.ReadersAndUpdates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ReadersAndUpdates.java) | org.gnit.lucenekmp.index.ReadersAndUpdates | Depth 2 | [x] | 8 | 1 | 0% | 8 |
| [org.apache.lucene.index.SegmentCommitInfo](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentCommitInfo.java) | org.gnit.lucenekmp.index.SegmentCommitInfo | Depth 1 | [x] | 11 | 11 | 97% | 0 |
| [org.apache.lucene.index.SegmentCoreReaders](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentCoreReaders.java) | org.gnit.lucenekmp.index.SegmentCoreReaders | Depth 3 | [x] | 1 | 1 | 0% | 1 |
| [org.apache.lucene.index.SegmentDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentDocValues.java) | org.gnit.lucenekmp.index.SegmentDocValues | Depth 3 | [x] | 3 | 3 | 84% | 0 |
| [org.apache.lucene.index.SegmentDocValuesProducer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentDocValuesProducer.java) | org.gnit.lucenekmp.index.SegmentDocValuesProducer | Depth 2 | [x] | 8 | 8 | 83% | 0 |
| [org.apache.lucene.index.SegmentInfo](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentInfo.java) | org.gnit.lucenekmp.index.SegmentInfo | Depth 1 | [x] | 9 | 0 | 100% | 9 |
| [org.apache.lucene.index.SegmentInfos](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentInfos.java) | org.gnit.lucenekmp.index.SegmentInfos | Depth 2 | [x] | 2 | 2 | 47% | 1 |
| [org.apache.lucene.index.SegmentMerger](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentMerger.java) | org.gnit.lucenekmp.index.SegmentMerger | Depth 2 | [x] | 0 | 1 | 0% | 0 |
| [org.apache.lucene.index.SegmentReadState](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentReadState.java) | org.gnit.lucenekmp.index.SegmentReadState | Depth 2 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.index.SegmentReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentReader.java) | org.gnit.lucenekmp.index.SegmentReader | Depth 2 | [x] | 1 | 1 | 37% | 1 |
| [org.apache.lucene.index.SegmentWriteState](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentWriteState.java) | org.gnit.lucenekmp.index.SegmentWriteState | Depth 2 | [x] | 1 | 1 | 11% | 0 |
| [org.apache.lucene.index.SingleTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SingleTermsEnum.java) | org.gnit.lucenekmp.index.SingleTermsEnum | Depth 2 | [x] | 13 | 13 | 88% | 0 |
| [org.apache.lucene.index.SingletonSortedNumericDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SingletonSortedNumericDocValues.java) | org.gnit.lucenekmp.index.SingletonSortedNumericDocValues | Depth 2 | [x] | 10 | 8 | 100% | 2 |
| [org.apache.lucene.index.SingletonSortedSetDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SingletonSortedSetDocValues.java) | org.gnit.lucenekmp.index.SingletonSortedSetDocValues | Depth 2 | [x] | 14 | 12 | 100% | 2 |
| [org.apache.lucene.index.SlowCompositeCodecReaderWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SlowCompositeCodecReaderWrapper.java) | org.gnit.lucenekmp.index.SlowCompositeCodecReaderWrapper | Depth 2 | [x] | 2 | 5 | 0% | 2 |
| [org.apache.lucene.index.SortFieldProvider](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortFieldProvider.java) | org.gnit.lucenekmp.index.SortFieldProvider | Depth 3 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.index.SortedDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortedDocValues.java) | org.gnit.lucenekmp.index.SortedDocValues | Depth 2 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.index.SortedDocValuesTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortedDocValuesTermsEnum.java) | org.gnit.lucenekmp.index.SortedDocValuesTermsEnum | Depth 2 | [x] | 11 | 12 | 91% | 0 |
| [org.apache.lucene.index.SortedDocValuesWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortedDocValuesWriter.java) | org.gnit.lucenekmp.index.SortedDocValuesWriter | Depth 4 | [x] | 8 | 11 | 0% | 8 |
| [org.apache.lucene.index.SortedNumericDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortedNumericDocValues.java) | org.gnit.lucenekmp.index.SortedNumericDocValues | Depth 2 | [x] | 10 | 8 | 100% | 2 |
| [org.apache.lucene.index.SortedSetDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortedSetDocValues.java) | org.gnit.lucenekmp.index.SortedSetDocValues | Depth 3 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.index.SortedSetDocValuesTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortedSetDocValuesTermsEnum.java) | org.gnit.lucenekmp.index.SortedSetDocValuesTermsEnum | Depth 3 | [x] | 11 | 12 | 91% | 0 |
| [org.apache.lucene.index.Sorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/Sorter.java) | org.gnit.lucenekmp.index.Sorter | Depth 2 | [x] | 2 | 1 | 0% | 2 |
| [org.apache.lucene.index.SortingCodecReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortingCodecReader.java) | org.gnit.lucenekmp.index.SortingCodecReader | Depth 2 | [x] | 41 | 7 | 52% | 37 |
| [org.apache.lucene.index.SortingStoredFieldsConsumer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortingStoredFieldsConsumer.java) | org.gnit.lucenekmp.index.SortingStoredFieldsConsumer | Depth 4 | [x] | 2 | 8 | 0% | 2 |
| [org.apache.lucene.index.SortingTermVectorsConsumer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortingTermVectorsConsumer.java) | org.gnit.lucenekmp.index.SortingTermVectorsConsumer | Depth 3 | [x] | 12 | 1 | 83% | 11 |
| [org.apache.lucene.index.StandardDirectoryReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/StandardDirectoryReader.java) | org.gnit.lucenekmp.index.StandardDirectoryReader | Depth 1 | [x] | 46 | 1 | 0% | 46 |
| [org.apache.lucene.index.StoredFieldDataInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/StoredFieldDataInput.java) | org.gnit.lucenekmp.index.StoredFieldDataInput | Depth 1 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.index.StoredFieldVisitor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/StoredFieldVisitor.java) | org.gnit.lucenekmp.index.StoredFieldVisitor | Depth 3 | [x] | 1 | 1 | 75% | 0 |
| [org.apache.lucene.index.StoredFieldsConsumer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/StoredFieldsConsumer.java) | org.gnit.lucenekmp.index.StoredFieldsConsumer | Depth 4 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.index.Term](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/Term.java) | org.gnit.lucenekmp.index.Term | Depth 1 | [x] | 3 | 0 | 100% | 3 |
| [org.apache.lucene.index.TermStates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/TermStates.java) | org.gnit.lucenekmp.index.TermStates | Depth 2 | [x] | 1 | 0 | 0% | 1 |
| [org.apache.lucene.index.TermVectors](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/TermVectors.java) | org.gnit.lucenekmp.index.TermVectors | Depth 2 | [x] | 3 | 3 | 93% | 0 |
| [org.apache.lucene.index.TermVectorsConsumer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/TermVectorsConsumer.java) | org.gnit.lucenekmp.index.TermVectorsConsumer | Depth 3 | [x] | 11 | 11 | 50% | 0 |
| [org.apache.lucene.index.TermVectorsConsumerPerField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/TermVectorsConsumerPerField.java) | org.gnit.lucenekmp.index.TermVectorsConsumerPerField | Depth 5 | [x] | 3 | 3 | 37% | 0 |
| [org.apache.lucene.index.Terms](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/Terms.java) | org.gnit.lucenekmp.index.Terms | Depth 2 | [x] | 17 | 17 | 91% | 0 |
| [org.apache.lucene.index.TermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/TermsEnum.java) | org.gnit.lucenekmp.index.TermsEnum | Depth 3 | [x] | 11 | 1 | 0% | 11 |
| [org.apache.lucene.index.TermsEnumIndex](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/TermsEnumIndex.java) | org.gnit.lucenekmp.index.TermsEnumIndex | Depth 3 | [x] | 7 | 1 | 0% | 7 |
| [org.apache.lucene.index.TermsHash](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/TermsHash.java) | org.gnit.lucenekmp.index.TermsHash | Depth 3 | [x] | 6 | 6 | 62% | 0 |
| [org.apache.lucene.index.TieredMergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/TieredMergePolicy.java) | org.gnit.lucenekmp.index.TieredMergePolicy | Depth 3 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.index.TrackingTmpOutputDirectoryWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/TrackingTmpOutputDirectoryWrapper.java) | org.gnit.lucenekmp.index.TrackingTmpOutputDirectoryWrapper | Depth 4 | [x] | 16 | 14 | 93% | 2 |
| [org.apache.lucene.index.VectorEncoding](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/VectorEncoding.java) | org.gnit.lucenekmp.index.VectorEncoding | Depth 2 | [x] | 1 | 1 | 60% | 0 |
| [org.apache.lucene.index.VectorSimilarityFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/VectorSimilarityFunction.java) | org.gnit.lucenekmp.index.VectorSimilarityFunction | Depth 3 | [x] | 3 | 3 | 86% | 0 |
| [org.apache.lucene.internal.hppc.AbstractIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/AbstractIterator.java) | org.gnit.lucenekmp.internal.hppc.AbstractIterator | Depth 3 | [x] | 2 | 0 | 100% | 2 |
| [org.apache.lucene.internal.hppc.BufferAllocationException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/BufferAllocationException.java) | org.gnit.lucenekmp.internal.hppc.BufferAllocationException | Depth 2 | [x] | 1 | 1 | 83% | 0 |
| [org.apache.lucene.internal.hppc.HashContainers](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/HashContainers.java) | org.gnit.lucenekmp.internal.hppc.HashContainers | Depth 2 | [x] | 6 | 6 | 69% | 0 |
| [org.apache.lucene.internal.hppc.IntCursor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/IntCursor.java) | org.gnit.lucenekmp.internal.hppc.IntCursor | Depth 4 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.internal.hppc.IntIntHashMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/IntIntHashMap.java) | org.gnit.lucenekmp.internal.hppc.IntIntHashMap | Depth 4 | [x] | 2 | 2 | 85% | 0 |
| [org.apache.lucene.internal.hppc.IntObjectHashMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/IntObjectHashMap.java) | org.gnit.lucenekmp.internal.hppc.IntObjectHashMap | Depth 3 | [x] | 2 | 2 | 85% | 0 |
| [org.apache.lucene.internal.hppc.LongCursor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/LongCursor.java) | org.gnit.lucenekmp.internal.hppc.LongCursor | Depth 3 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.internal.hppc.LongObjectHashMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/LongObjectHashMap.java) | org.gnit.lucenekmp.internal.hppc.LongObjectHashMap | Depth 2 | [x] | 2 | 2 | 85% | 0 |
| [org.apache.lucene.internal.hppc.ObjectCursor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/ObjectCursor.java) | org.gnit.lucenekmp.internal.hppc.ObjectCursor | Depth 1 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.internal.tests.ConcurrentMergeSchedulerAccess](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/tests/ConcurrentMergeSchedulerAccess.java) | org.gnit.lucenekmp.internal.tests.ConcurrentMergeSchedulerAccess | Depth 2 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.internal.tests.TestSecrets](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/tests/TestSecrets.java) | org.gnit.lucenekmp.internal.tests.TestSecrets | Depth 1 | [x] | 2 | 4 | 77% | 0 |
| [org.apache.lucene.internal.vectorization.DefaultVectorUtilSupport](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/vectorization/DefaultVectorUtilSupport.java) | org.gnit.lucenekmp.internal.vectorization.DefaultVectorUtilSupport | Depth 6 | [x] | 11 | 11 | 81% | 2 |
| [org.apache.lucene.internal.vectorization.DefaultVectorizationProvider](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/vectorization/DefaultVectorizationProvider.java) | org.gnit.lucenekmp.internal.vectorization.DefaultVectorizationProvider | Depth 5 | [x] | 4 | 1 | 100% | 3 |
| [org.apache.lucene.internal.vectorization.PostingDecodingUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/vectorization/PostingDecodingUtil.java) | org.gnit.lucenekmp.internal.vectorization.PostingDecodingUtil | Depth 4 | [x] | 1 | 1 | 62% | 0 |
| [org.apache.lucene.internal.vectorization.VectorizationProvider](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/vectorization/VectorizationProvider.java) | org.gnit.lucenekmp.internal.vectorization.VectorizationProvider | Depth 5 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.search.AbstractKnnCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/AbstractKnnCollector.java) | org.gnit.lucenekmp.search.AbstractKnnCollector | Depth 3 | [x] | 8 | 8 | 88% | 0 |
| [org.apache.lucene.search.AbstractKnnVectorQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/AbstractKnnVectorQuery.java) | org.gnit.lucenekmp.search.AbstractKnnVectorQuery | Depth 6 | [x] | 8 | 3 | 0% | 8 |
| [org.apache.lucene.search.AbstractMultiTermQueryConstantScoreWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/AbstractMultiTermQueryConstantScoreWrapper.java) | org.gnit.lucenekmp.search.AbstractMultiTermQueryConstantScoreWrapper | Depth 3 | [x] | 12 | 0 | 0% | 12 |
| [org.apache.lucene.search.BlockMaxConjunctionBulkScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/BlockMaxConjunctionBulkScorer.java) | org.gnit.lucenekmp.search.BlockMaxConjunctionBulkScorer | Depth 4 | [x] | 1 | 1 | 0% | 1 |
| [org.apache.lucene.search.BlockMaxConjunctionScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/BlockMaxConjunctionScorer.java) | org.gnit.lucenekmp.search.BlockMaxConjunctionScorer | Depth 4 | [x] | 3 | 1 | 100% | 2 |
| [org.apache.lucene.search.BooleanClause](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/BooleanClause.java) | org.gnit.lucenekmp.search.BooleanClause | Depth 1 | [x] | 1 | 1 | 69% | 0 |
| [org.apache.lucene.search.BooleanQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/BooleanQuery.java) | org.gnit.lucenekmp.search.BooleanQuery | Depth 1 | [x] | 10 | 0 | 100% | 10 |
| [org.apache.lucene.search.BooleanScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/BooleanScorer.java) | org.gnit.lucenekmp.search.BooleanScorer | Depth 4 | [x] | 0 | 16 | 0% | 0 |
| [org.apache.lucene.search.BooleanScorerSupplier](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/BooleanScorerSupplier.java) | org.gnit.lucenekmp.search.BooleanScorerSupplier | Depth 3 | [x] | 1 | 5 | 0% | 1 |
| [org.apache.lucene.search.BooleanWeight](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/BooleanWeight.java) | org.gnit.lucenekmp.search.BooleanWeight | Depth 2 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.search.BoostAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/BoostAttribute.java) | org.gnit.lucenekmp.search.BoostAttribute | Depth 5 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.search.CollectionStatistics](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/CollectionStatistics.java) | org.gnit.lucenekmp.search.CollectionStatistics | Depth 1 | [x] | 4 | 0 | 24% | 4 |
| [org.apache.lucene.search.Collector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Collector.java) | org.gnit.lucenekmp.search.Collector | Depth 1 | [x] | 2 | 2 | 81% | 0 |
| [org.apache.lucene.search.ConjunctionBulkScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ConjunctionBulkScorer.java) | org.gnit.lucenekmp.search.ConjunctionBulkScorer | Depth 4 | [x] | 1 | 1 | 0% | 1 |
| [org.apache.lucene.search.ConjunctionDISI](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ConjunctionDISI.java) | org.gnit.lucenekmp.search.ConjunctionDISI | Depth 6 | [x] | 9 | 1 | 0% | 9 |
| [org.apache.lucene.search.ConjunctionScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ConjunctionScorer.java) | org.gnit.lucenekmp.search.ConjunctionScorer | Depth 4 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.search.ConstantScoreQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ConstantScoreQuery.java) | org.gnit.lucenekmp.search.ConstantScoreQuery | Depth 2 | [x] | 7 | 3 | 0% | 7 |
| [org.apache.lucene.search.ConstantScoreScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ConstantScoreScorer.java) | org.gnit.lucenekmp.search.ConstantScoreScorer | Depth 4 | [x] | 3 | 5 | 0% | 3 |
| [org.apache.lucene.search.ConstantScoreScorerSupplier](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ConstantScoreScorerSupplier.java) | org.gnit.lucenekmp.search.ConstantScoreScorerSupplier | Depth 4 | [x] | 5 | 4 | 100% | 1 |
| [org.apache.lucene.search.DenseConjunctionBulkScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DenseConjunctionBulkScorer.java) | org.gnit.lucenekmp.search.DenseConjunctionBulkScorer | Depth 4 | [x] | 1 | 1 | 0% | 1 |
| [org.apache.lucene.search.DisiPriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisiPriorityQueue.java) | org.gnit.lucenekmp.search.DisiPriorityQueue | Depth 4 | [x] | 7 | 1 | 100% | 6 |
| [org.apache.lucene.search.DisiPriorityQueue2](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisiPriorityQueue2.java) | org.gnit.lucenekmp.search.DisiPriorityQueue2 | Depth 5 | [x] | 7 | 6 | 100% | 1 |
| [org.apache.lucene.search.DisiPriorityQueueN](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisiPriorityQueueN.java) | org.gnit.lucenekmp.search.DisiPriorityQueueN | Depth 4 | [x] | 14 | 3 | 100% | 11 |
| [org.apache.lucene.search.DisiWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisiWrapper.java) | org.gnit.lucenekmp.search.DisiWrapper | Depth 4 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.search.DisjunctionDISIApproximation](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisjunctionDISIApproximation.java) | org.gnit.lucenekmp.search.DisjunctionDISIApproximation | Depth 4 | [x] | 10 | 1 | 0% | 10 |
| [org.apache.lucene.search.DisjunctionMatchesIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisjunctionMatchesIterator.java) | org.gnit.lucenekmp.search.DisjunctionMatchesIterator | Depth 4 | [x] | 0 | 5 | 0% | 0 |
| [org.apache.lucene.search.DisjunctionMaxBulkScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisjunctionMaxBulkScorer.java) | org.gnit.lucenekmp.search.DisjunctionMaxBulkScorer | Depth 5 | [x] | 10 | 1 | 0% | 10 |
| [org.apache.lucene.search.DisjunctionMaxQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisjunctionMaxQuery.java) | org.gnit.lucenekmp.search.DisjunctionMaxQuery | Depth 3 | [x] | 7 | 3 | 0% | 7 |
| [org.apache.lucene.search.DisjunctionMaxScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisjunctionMaxScorer.java) | org.gnit.lucenekmp.search.DisjunctionMaxScorer | Depth 4 | [x] | 6 | 6 | 83% | 0 |
| [org.apache.lucene.search.DisjunctionScoreBlockBoundaryPropagator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisjunctionScoreBlockBoundaryPropagator.java) | org.gnit.lucenekmp.search.DisjunctionScoreBlockBoundaryPropagator | Depth 5 | [x] | 1 | 1 | 0% | 1 |
| [org.apache.lucene.search.DisjunctionScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisjunctionScorer.java) | org.gnit.lucenekmp.search.DisjunctionScorer | Depth 5 | [x] | 10 | 15 | 70% | 0 |
| [org.apache.lucene.search.DisjunctionSumScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisjunctionSumScorer.java) | org.gnit.lucenekmp.search.DisjunctionSumScorer | Depth 3 | [x] | 6 | 6 | 88% | 0 |
| [org.apache.lucene.search.DocIdSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DocIdSet.java) | org.gnit.lucenekmp.search.DocIdSet | Depth 3 | [x] | 1 | 1 | 72% | 0 |
| [org.apache.lucene.search.DocIdSetIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DocIdSetIterator.java) | org.gnit.lucenekmp.search.DocIdSetIterator | Depth 2 | [x] | 7 | 5 | 100% | 2 |
| [org.apache.lucene.search.DocIdStream](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DocIdStream.java) | org.gnit.lucenekmp.search.DocIdStream | Depth 2 | [x] | 1 | 2 | 61% | 0 |
| [org.apache.lucene.search.DocValuesRangeIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DocValuesRangeIterator.java) | org.gnit.lucenekmp.search.DocValuesRangeIterator | Depth 6 | [x] | 8 | 0 | 100% | 8 |
| [org.apache.lucene.search.DocValuesRewriteMethod](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DocValuesRewriteMethod.java) | org.gnit.lucenekmp.search.DocValuesRewriteMethod | Depth 4 | [x] | 5 | 1 | 0% | 5 |
| [org.apache.lucene.search.FieldComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FieldComparator.java) | org.gnit.lucenekmp.search.FieldComparator | Depth 1 | [x] | 6 | 10 | 29% | 2 |
| [org.apache.lucene.search.FieldDoc](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FieldDoc.java) | org.gnit.lucenekmp.search.FieldDoc | Depth 1 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.search.FieldExistsQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FieldExistsQuery.java) | org.gnit.lucenekmp.search.FieldExistsQuery | Depth 2 | [x] | 7 | 0 | 100% | 7 |
| [org.apache.lucene.search.FieldValueHitQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FieldValueHitQueue.java) | org.gnit.lucenekmp.search.FieldValueHitQueue | Depth 3 | [x] | 13 | 20 | 62% | 2 |
| [org.apache.lucene.search.FilterDocIdSetIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FilterDocIdSetIterator.java) | org.gnit.lucenekmp.search.FilterDocIdSetIterator | Depth 2 | [x] | 7 | 5 | 90% | 2 |
| [org.apache.lucene.search.FilterLeafCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FilterLeafCollector.java) | org.gnit.lucenekmp.search.FilterLeafCollector | Depth 3 | [x] | 4 | 2 | 56% | 2 |
| [org.apache.lucene.search.FilterScorable](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FilterScorable.java) | org.gnit.lucenekmp.search.FilterScorable | Depth 2 | [x] | 1 | 1 | 70% | 0 |
| [org.apache.lucene.search.FilterScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FilterScorer.java) | org.gnit.lucenekmp.search.FilterScorer | Depth 4 | [x] | 6 | 5 | 87% | 1 |
| [org.apache.lucene.search.FilteredDocIdSetIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FilteredDocIdSetIterator.java) | org.gnit.lucenekmp.search.FilteredDocIdSetIterator | Depth 7 | [x] | 8 | 6 | 92% | 2 |
| [org.apache.lucene.search.HitQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/HitQueue.java) | org.gnit.lucenekmp.search.HitQueue | Depth 3 | [x] | 10 | 17 | 64% | 0 |
| [org.apache.lucene.search.ImpactsDISI](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ImpactsDISI.java) | org.gnit.lucenekmp.search.ImpactsDISI | Depth 3 | [x] | 8 | 6 | 100% | 2 |
| [org.apache.lucene.search.IndexSearcher](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/IndexSearcher.java) | org.gnit.lucenekmp.search.IndexSearcher | Depth 1 | [x] | 26 | 3 | 0% | 26 |
| [org.apache.lucene.search.KnnByteVectorQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/KnnByteVectorQuery.java) | org.gnit.lucenekmp.search.KnnByteVectorQuery | Depth 4 | [x] | 13 | 0 | 100% | 13 |
| [org.apache.lucene.search.KnnCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/KnnCollector.java) | org.gnit.lucenekmp.search.KnnCollector | Depth 3 | [x] | 7 | 7 | 93% | 0 |
| [org.apache.lucene.search.KnnFloatVectorQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/KnnFloatVectorQuery.java) | org.gnit.lucenekmp.search.KnnFloatVectorQuery | Depth 4 | [x] | 13 | 0 | 100% | 13 |
| [org.apache.lucene.search.LRUQueryCache](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/LRUQueryCache.java) | org.gnit.lucenekmp.search.LRUQueryCache | Depth 2 | [x] | 4 | 3 | 0% | 4 |
| [org.apache.lucene.search.LeafCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/LeafCollector.java) | org.gnit.lucenekmp.search.LeafCollector | Depth 1 | [x] | 4 | 1 | 0% | 4 |
| [org.apache.lucene.search.LeafFieldComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/LeafFieldComparator.java) | org.gnit.lucenekmp.search.LeafFieldComparator | Depth 2 | [x] | 4 | 1 | 0% | 4 |
| [org.apache.lucene.search.MaxNonCompetitiveBoostAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/MaxNonCompetitiveBoostAttribute.java) | org.gnit.lucenekmp.search.MaxNonCompetitiveBoostAttribute | Depth 6 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.search.MaxScoreAccumulator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/MaxScoreAccumulator.java) | org.gnit.lucenekmp.search.MaxScoreAccumulator | Depth 2 | [x] | 4 | 3 | 93% | 1 |
| [org.apache.lucene.search.MaxScoreBulkScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/MaxScoreBulkScorer.java) | org.gnit.lucenekmp.search.MaxScoreBulkScorer | Depth 4 | [x] | 1 | 1 | 65% | 0 |
| [org.apache.lucene.search.MultiLeafFieldComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/MultiLeafFieldComparator.java) | org.gnit.lucenekmp.search.MultiLeafFieldComparator | Depth 3 | [x] | 4 | 4 | 93% | 0 |
| [org.apache.lucene.search.MultiTermQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/MultiTermQuery.java) | org.gnit.lucenekmp.search.MultiTermQuery | Depth 3 | [x] | 2 | 8 | 18% | 0 |
| [org.apache.lucene.search.MultiTermQueryConstantScoreBlendedWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/MultiTermQueryConstantScoreBlendedWrapper.java) | org.gnit.lucenekmp.search.MultiTermQueryConstantScoreBlendedWrapper | Depth 3 | [x] | 12 | 15 | 0% | 12 |
| [org.apache.lucene.search.MultiTermQueryConstantScoreWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/MultiTermQueryConstantScoreWrapper.java) | org.gnit.lucenekmp.search.MultiTermQueryConstantScoreWrapper | Depth 3 | [x] | 12 | 12 | 88% | 1 |
| [org.apache.lucene.search.Multiset](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Multiset.java) | org.gnit.lucenekmp.search.Multiset | Depth 2 | [x] | 1 | 2 | 32% | 0 |
| [org.apache.lucene.search.PointInSetQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/PointInSetQuery.java) | org.gnit.lucenekmp.search.PointInSetQuery | Depth 2 | [x] | 7 | 2 | 0% | 7 |
| [org.apache.lucene.search.PointRangeQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/PointRangeQuery.java) | org.gnit.lucenekmp.search.PointRangeQuery | Depth 2 | [x] | 14 | 4 | 0% | 14 |
| [org.apache.lucene.search.Pruning](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Pruning.java) | org.gnit.lucenekmp.search.Pruning | Depth 1 | [x] | 1 | 1 | 75% | 0 |
| [org.apache.lucene.search.QueryVisitor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/QueryVisitor.java) | org.gnit.lucenekmp.search.QueryVisitor | Depth 2 | [x] | 6 | 5 | 96% | 1 |
| [org.apache.lucene.search.ReqExclScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ReqExclScorer.java) | org.gnit.lucenekmp.search.ReqExclScorer | Depth 4 | [x] | 3 | 1 | 100% | 2 |
| [org.apache.lucene.search.ReqOptSumScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ReqOptSumScorer.java) | org.gnit.lucenekmp.search.ReqOptSumScorer | Depth 4 | [x] | 10 | 1 | 0% | 10 |
| [org.apache.lucene.search.Scorable](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Scorable.java) | org.gnit.lucenekmp.search.Scorable | Depth 3 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.search.Score](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Score.java) | org.gnit.lucenekmp.search.Score | Depth 4 | [x] | 1 | 1 | 73% | 0 |
| [org.apache.lucene.search.ScoreCachingWrappingScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ScoreCachingWrappingScorer.java) | org.gnit.lucenekmp.search.ScoreCachingWrappingScorer | Depth 4 | [x] | 4 | 3 | 65% | 1 |
| [org.apache.lucene.search.ScoreDoc](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ScoreDoc.java) | org.gnit.lucenekmp.search.ScoreDoc | Depth 1 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.search.ScoreMode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ScoreMode.java) | org.gnit.lucenekmp.search.ScoreMode | Depth 1 | [x] | 2 | 2 | 85% | 0 |
| [org.apache.lucene.search.Scorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Scorer.java) | org.gnit.lucenekmp.search.Scorer | Depth 2 | [x] | 5 | 5 | 92% | 0 |
| [org.apache.lucene.search.ScorerUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ScorerUtil.java) | org.gnit.lucenekmp.search.ScorerUtil | Depth 2 | [x] | 10 | 15 | 70% | 0 |
| [org.apache.lucene.search.ScoringRewrite](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ScoringRewrite.java) | org.gnit.lucenekmp.search.ScoringRewrite | Depth 4 | [x] | 7 | 1 | 0% | 7 |
| [org.apache.lucene.search.SimpleScorable](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/SimpleScorable.java) | org.gnit.lucenekmp.search.SimpleScorable | Depth 4 | [x] | 2 | 2 | 89% | 0 |
| [org.apache.lucene.search.Sort](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Sort.java) | org.gnit.lucenekmp.search.Sort | Depth 1 | [x] | 2 | 0 | 0% | 2 |
| [org.apache.lucene.search.SortField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/SortField.java) | org.gnit.lucenekmp.search.SortField | Depth 1 | [x] | 6 | 1 | 0% | 6 |
| [org.apache.lucene.search.TaskExecutor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TaskExecutor.java) | org.gnit.lucenekmp.search.TaskExecutor | Depth 2 | [x] | 2 | 1 | 0% | 2 |
| [org.apache.lucene.search.TermCollectingRewrite](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TermCollectingRewrite.java) | org.gnit.lucenekmp.search.TermCollectingRewrite | Depth 4 | [x] | 2 | 2 | 46% | 0 |
| [org.apache.lucene.search.TermInSetQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TermInSetQuery.java) | org.gnit.lucenekmp.search.TermInSetQuery | Depth 3 | [x] | 24 | 0 | 0% | 24 |
| [org.apache.lucene.search.TermQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TermQuery.java) | org.gnit.lucenekmp.search.TermQuery | Depth 2 | [x] | 9 | 8 | 0% | 9 |
| [org.apache.lucene.search.TermScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TermScorer.java) | org.gnit.lucenekmp.search.TermScorer | Depth 2 | [x] | 5 | 5 | 86% | 0 |
| [org.apache.lucene.search.TermStatistics](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TermStatistics.java) | org.gnit.lucenekmp.search.TermStatistics | Depth 1 | [x] | 2 | 0 | 33% | 2 |
| [org.apache.lucene.search.TimeLimitingBulkScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TimeLimitingBulkScorer.java) | org.gnit.lucenekmp.search.TimeLimitingBulkScorer | Depth 1 | [x] | 1 | 1 | 0% | 1 |
| [org.apache.lucene.search.TimeLimitingKnnCollectorManager](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TimeLimitingKnnCollectorManager.java) | org.gnit.lucenekmp.search.TimeLimitingKnnCollectorManager | Depth 6 | [x] | 7 | 7 | 93% | 0 |
| [org.apache.lucene.search.TopDocs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TopDocs.java) | org.gnit.lucenekmp.search.TopDocs | Depth 2 | [x] | 10 | 0 | 0% | 10 |
| [org.apache.lucene.search.TopDocsCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TopDocsCollector.java) | org.gnit.lucenekmp.search.TopDocsCollector | Depth 2 | [x] | 8 | 0 | 0% | 8 |
| [org.apache.lucene.search.TopFieldCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TopFieldCollector.java) | org.gnit.lucenekmp.search.TopFieldCollector | Depth 2 | [x] | 16 | 6 | 0% | 16 |
| [org.apache.lucene.search.TopFieldDocs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TopFieldDocs.java) | org.gnit.lucenekmp.search.TopFieldDocs | Depth 1 | [x] | 9 | 3 | 0% | 9 |
| [org.apache.lucene.search.TopKnnCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TopKnnCollector.java) | org.gnit.lucenekmp.search.TopKnnCollector | Depth 2 | [x] | 8 | 8 | 83% | 0 |
| [org.apache.lucene.search.TopScoreDocCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TopScoreDocCollector.java) | org.gnit.lucenekmp.search.TopScoreDocCollector | Depth 3 | [x] | 7 | 4 | 71% | 3 |
| [org.apache.lucene.search.TopTermsRewrite](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TopTermsRewrite.java) | org.gnit.lucenekmp.search.TopTermsRewrite | Depth 5 | [x] | 3 | 3 | 54% | 0 |
| [org.apache.lucene.search.TotalHitCountCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TotalHitCountCollector.java) | org.gnit.lucenekmp.search.TotalHitCountCollector | Depth 3 | [x] | 4 | 2 | 61% | 2 |
| [org.apache.lucene.search.TotalHitCountCollectorManager](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TotalHitCountCollectorManager.java) | org.gnit.lucenekmp.search.TotalHitCountCollectorManager | Depth 2 | [x] | 3 | 3 | 0% | 3 |
| [org.apache.lucene.search.TotalHits](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TotalHits.java) | org.gnit.lucenekmp.search.TotalHits | Depth 2 | [x] | 0 | 1 | 0% | 0 |
| [org.apache.lucene.search.TwoPhaseIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TwoPhaseIterator.java) | org.gnit.lucenekmp.search.TwoPhaseIterator | Depth 3 | [x] | 8 | 6 | 84% | 2 |
| [org.apache.lucene.search.UsageTrackingQueryCachingPolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/UsageTrackingQueryCachingPolicy.java) | org.gnit.lucenekmp.search.UsageTrackingQueryCachingPolicy | Depth 1 | [x] | 7 | 3 | 93% | 4 |
| [org.apache.lucene.search.VectorScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/VectorScorer.java) | org.gnit.lucenekmp.search.VectorScorer | Depth 3 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.search.WANDScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/WANDScorer.java) | org.gnit.lucenekmp.search.WANDScorer | Depth 4 | [x] | 7 | 5 | 100% | 2 |
| [org.apache.lucene.search.Weight](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Weight.java) | org.gnit.lucenekmp.search.Weight | Depth 2 | [x] | 5 | 3 | 16% | 5 |
| [org.apache.lucene.search.comparators.DocComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/comparators/DocComparator.java) | org.gnit.lucenekmp.search.comparators.DocComparator | Depth 2 | [x] | 5 | 5 | 0% | 5 |
| [org.apache.lucene.search.comparators.DoubleComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/comparators/DoubleComparator.java) | org.gnit.lucenekmp.search.comparators.DoubleComparator | Depth 2 | [x] | 12 | 12 | 89% | 0 |
| [org.apache.lucene.search.comparators.FloatComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/comparators/FloatComparator.java) | org.gnit.lucenekmp.search.comparators.FloatComparator | Depth 2 | [x] | 12 | 12 | 89% | 0 |
| [org.apache.lucene.search.comparators.IntComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/comparators/IntComparator.java) | org.gnit.lucenekmp.search.comparators.IntComparator | Depth 2 | [x] | 12 | 12 | 89% | 0 |
| [org.apache.lucene.search.comparators.LongComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/comparators/LongComparator.java) | org.gnit.lucenekmp.search.comparators.LongComparator | Depth 2 | [x] | 12 | 12 | 89% | 0 |
| [org.apache.lucene.search.comparators.MinDocIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/comparators/MinDocIterator.java) | org.gnit.lucenekmp.search.comparators.MinDocIterator | Depth 3 | [x] | 7 | 5 | 70% | 2 |
| [org.apache.lucene.search.comparators.NumericComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/comparators/NumericComparator.java) | org.gnit.lucenekmp.search.comparators.NumericComparator | Depth 3 | [x] | 11 | 4 | 0% | 11 |
| [org.apache.lucene.search.comparators.TermOrdValComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/comparators/TermOrdValComparator.java) | org.gnit.lucenekmp.search.comparators.TermOrdValComparator | Depth 2 | [x] | 9 | 6 | 0% | 9 |
| [org.apache.lucene.search.knn.KnnSearchStrategy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/knn/KnnSearchStrategy.java) | org.gnit.lucenekmp.search.knn.KnnSearchStrategy | Depth 4 | [x] | 2 | 3 | 11% | 2 |
| [org.apache.lucene.search.knn.MultiLeafKnnCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/knn/MultiLeafKnnCollector.java) | org.gnit.lucenekmp.search.knn.MultiLeafKnnCollector | Depth 7 | [x] | 7 | 3 | 0% | 7 |
| [org.apache.lucene.search.similarities.BM25Similarity](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/BM25Similarity.java) | org.gnit.lucenekmp.search.similarities.BM25Similarity | Depth 2 | [x] | 4 | 0 | 100% | 4 |
| [org.apache.lucene.store.AlreadyClosedException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/AlreadyClosedException.java) | org.gnit.lucenekmp.store.AlreadyClosedException | Depth 1 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.store.BaseDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/BaseDirectory.java) | org.gnit.lucenekmp.store.BaseDirectory | Depth 1 | [x] | 15 | 14 | 89% | 1 |
| [org.apache.lucene.store.BufferedChecksum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/BufferedChecksum.java) | org.gnit.lucenekmp.store.BufferedChecksum | Depth 3 | [x] | 8 | 0 | 100% | 8 |
| [org.apache.lucene.store.BufferedChecksumIndexInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/BufferedChecksumIndexInput.java) | org.gnit.lucenekmp.store.BufferedChecksumIndexInput | Depth 2 | [x] | 27 | 27 | 96% | 0 |
| [org.apache.lucene.store.BufferedIndexInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/BufferedIndexInput.java) | org.gnit.lucenekmp.store.BufferedIndexInput | Depth 3 | [x] | 0 | 37 | 0% | 0 |
| [org.apache.lucene.store.ByteArrayDataInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/ByteArrayDataInput.java) | org.gnit.lucenekmp.store.ByteArrayDataInput | Depth 2 | [x] | 21 | 22 | 95% | 0 |
| [org.apache.lucene.store.ByteArrayDataOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/ByteArrayDataOutput.java) | org.gnit.lucenekmp.store.ByteArrayDataOutput | Depth 5 | [x] | 19 | 20 | 94% | 0 |
| [org.apache.lucene.store.ByteBuffersDataInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/ByteBuffersDataInput.java) | org.gnit.lucenekmp.store.ByteBuffersDataInput | Depth 3 | [x] | 37 | 4 | 90% | 33 |
| [org.apache.lucene.store.ByteBuffersDataOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/ByteBuffersDataOutput.java) | org.gnit.lucenekmp.store.ByteBuffersDataOutput | Depth 4 | [x] | 2 | 4 | 0% | 2 |
| [org.apache.lucene.store.ChecksumIndexInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/ChecksumIndexInput.java) | org.gnit.lucenekmp.store.ChecksumIndexInput | Depth 1 | [x] | 27 | 0 | 100% | 27 |
| [org.apache.lucene.store.DataOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/DataOutput.java) | org.gnit.lucenekmp.store.DataOutput | Depth 1 | [x] | 17 | 0 | 100% | 17 |
| [org.apache.lucene.store.Directory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/Directory.java) | org.gnit.lucenekmp.store.Directory | Depth 1 | [x] | 15 | 1 | 100% | 14 |
| [org.apache.lucene.store.FSDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/FSDirectory.java) | org.gnit.lucenekmp.store.FSDirectory | Depth 1 | [x] | 20 | 5 | 14% | 19 |
| [org.apache.lucene.store.FSLockFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/FSLockFactory.java) | org.gnit.lucenekmp.store.FSLockFactory | Depth 1 | [x] | 2 | 0 | 100% | 2 |
| [org.apache.lucene.store.FilterDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/FilterDirectory.java) | org.gnit.lucenekmp.store.FilterDirectory | Depth 2 | [x] | 16 | 1 | 100% | 15 |
| [org.apache.lucene.store.FilterIndexInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/FilterIndexInput.java) | org.gnit.lucenekmp.store.FilterIndexInput | Depth 2 | [x] | 28 | 2 | 76% | 26 |
| [org.apache.lucene.store.FilterIndexOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/FilterIndexOutput.java) | org.gnit.lucenekmp.store.FilterIndexOutput | Depth 5 | [x] | 21 | 1 | 100% | 20 |
| [org.apache.lucene.store.FlushInfo](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/FlushInfo.java) | org.gnit.lucenekmp.store.FlushInfo | Depth 1 | [x] | 2 | 0 | 23% | 2 |
| [org.apache.lucene.store.IOContext](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/IOContext.java) | org.gnit.lucenekmp.store.IOContext | Depth 2 | [x] | 1 | 0 | 100% | 1 |
| [org.apache.lucene.store.IndexInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/IndexInput.java) | org.gnit.lucenekmp.store.IndexInput | Depth 2 | [x] | 6 | 6 | 94% | 0 |
| [org.apache.lucene.store.IndexOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/IndexOutput.java) | org.gnit.lucenekmp.store.IndexOutput | Depth 1 | [x] | 20 | 1 | 100% | 19 |
| [org.apache.lucene.store.LockObtainFailedException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/LockObtainFailedException.java) | org.gnit.lucenekmp.store.LockObtainFailedException | Depth 2 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.store.LockValidatingDirectoryWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/LockValidatingDirectoryWrapper.java) | org.gnit.lucenekmp.store.LockValidatingDirectoryWrapper | Depth 1 | [x] | 16 | 14 | 96% | 2 |
| [org.apache.lucene.store.MMapDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/MMapDirectory.java) | org.gnit.lucenekmp.store.MMapDirectory | Depth 2 | [x] | 3 | 2 | 0% | 3 |
| [org.apache.lucene.store.MergeInfo](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/MergeInfo.java) | org.gnit.lucenekmp.store.MergeInfo | Depth 1 | [x] | 3 | 0 | 28% | 3 |
| [org.apache.lucene.store.NIOFSDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/NIOFSDirectory.java) | org.gnit.lucenekmp.store.NIOFSDirectory | Depth 1 | [x] | 24 | 0 | 100% | 24 |
| [org.apache.lucene.store.NativeFSLockFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/NativeFSLockFactory.java) | org.gnit.lucenekmp.store.NativeFSLockFactory | Depth 2 | [x] | 2 | 2 | 47% | 0 |
| [org.apache.lucene.store.OutputStreamIndexOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/OutputStreamIndexOutput.java) | org.gnit.lucenekmp.store.OutputStreamIndexOutput | Depth 2 | [x] | 20 | 11 | 26% | 16 |
| [org.apache.lucene.store.RandomAccessInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/RandomAccessInput.java) | org.gnit.lucenekmp.store.RandomAccessInput | Depth 2 | [x] | 6 | 2 | 0% | 6 |
| [org.apache.lucene.store.RateLimitedIndexOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/RateLimitedIndexOutput.java) | org.gnit.lucenekmp.store.RateLimitedIndexOutput | Depth 4 | [x] | 22 | 3 | 0% | 22 |
| [org.apache.lucene.store.RateLimiter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/RateLimiter.java) | org.gnit.lucenekmp.store.RateLimiter | Depth 4 | [x] | 1 | 1 | 0% | 1 |
| [org.apache.lucene.store.ReadAdvice](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/ReadAdvice.java) | org.gnit.lucenekmp.store.ReadAdvice | Depth 2 | [x] | 1 | 1 | 75% | 0 |
| [org.apache.lucene.store.TrackingDirectoryWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/TrackingDirectoryWrapper.java) | org.gnit.lucenekmp.store.TrackingDirectoryWrapper | Depth 1 | [x] | 17 | 15 | 96% | 2 |
| [org.apache.lucene.util.Accountable](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/Accountable.java) | org.gnit.lucenekmp.util.Accountable | Depth 1 | [x] | 1 | 1 | 0% | 1 |
| [org.apache.lucene.util.Accountables](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/Accountables.java) | org.gnit.lucenekmp.util.Accountables | Depth 3 | [x] | 1 | 1 | 90% | 0 |
| [org.apache.lucene.util.ArrayIntroSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/ArrayIntroSorter.java) | org.gnit.lucenekmp.util.ArrayIntroSorter | Depth 2 | [x] | 23 | 21 | 99% | 2 |
| [org.apache.lucene.util.ArrayTimSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/ArrayTimSorter.java) | org.gnit.lucenekmp.util.ArrayTimSorter | Depth 2 | [x] | 42 | 39 | 87% | 3 |
| [org.apache.lucene.util.ArrayUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/ArrayUtil.java) | org.gnit.lucenekmp.util.ArrayUtil | Depth 2 | [x] | 11 | 11 | 90% | 0 |
| [org.apache.lucene.util.Attribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/Attribute.java) | org.gnit.lucenekmp.util.Attribute | Depth 1 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.util.AttributeFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/AttributeFactory.java) | org.gnit.lucenekmp.util.AttributeFactory | Depth 2 | [x] | 4 | 2 | 85% | 2 |
| [org.apache.lucene.util.AttributeImpl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/AttributeImpl.java) | org.gnit.lucenekmp.util.AttributeImpl | Depth 2 | [x] | 5 | 7 | 72% | 0 |
| [org.apache.lucene.util.AttributeSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/AttributeSource.java) | org.gnit.lucenekmp.util.AttributeSource | Depth 3 | [x] | 2 | 1 | 0% | 2 |
| [org.apache.lucene.util.BitDocIdSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BitDocIdSet.java) | org.gnit.lucenekmp.util.BitDocIdSet | Depth 2 | [x] | 1 | 0 | 100% | 1 |
| [org.apache.lucene.util.BitSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BitSet.java) | org.gnit.lucenekmp.util.BitSet | Depth 2 | [x] | 15 | 1 | 100% | 14 |
| [org.apache.lucene.util.BitSetIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BitSetIterator.java) | org.gnit.lucenekmp.util.BitSetIterator | Depth 3 | [x] | 10 | 3 | 66% | 8 |
| [org.apache.lucene.util.BitUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BitUtil.java) | org.gnit.lucenekmp.util.BitUtil | Depth 2 | [x] | 10 | 1 | 0% | 10 |
| [org.apache.lucene.util.Bits](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/Bits.java) | org.gnit.lucenekmp.util.Bits | Depth 2 | [x] | 2 | 1 | 61% | 1 |
| [org.apache.lucene.util.ByteBlockPool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/ByteBlockPool.java) | org.gnit.lucenekmp.util.ByteBlockPool | Depth 2 | [x] | 1 | 1 | 72% | 0 |
| [org.apache.lucene.util.BytesRef](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BytesRef.java) | org.gnit.lucenekmp.util.BytesRef | Depth 1 | [x] | 4 | 2 | 38% | 3 |
| [org.apache.lucene.util.BytesRefArray](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BytesRefArray.java) | org.gnit.lucenekmp.util.BytesRefArray | Depth 4 | [x] | 26 | 22 | 85% | 4 |
| [org.apache.lucene.util.BytesRefBlockPool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BytesRefBlockPool.java) | org.gnit.lucenekmp.util.BytesRefBlockPool | Depth 4 | [x] | 5 | 0 | 100% | 5 |
| [org.apache.lucene.util.BytesRefBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BytesRefBuilder.java) | org.gnit.lucenekmp.util.BytesRefBuilder | Depth 2 | [x] | 16 | 17 | 94% | 0 |
| [org.apache.lucene.util.BytesRefComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BytesRefComparator.java) | org.gnit.lucenekmp.util.BytesRefComparator | Depth 4 | [x] | 3 | 3 | 80% | 0 |
| [org.apache.lucene.util.BytesRefHash](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BytesRefHash.java) | org.gnit.lucenekmp.util.BytesRefHash | Depth 4 | [x] | 1 | 34 | 0% | 1 |
| [org.apache.lucene.util.BytesRefIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BytesRefIterator.java) | org.gnit.lucenekmp.util.BytesRefIterator | Depth 2 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.util.CharsRef](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/CharsRef.java) | org.gnit.lucenekmp.util.CharsRef | Depth 5 | [x] | 1 | 2 | 0% | 1 |
| [org.apache.lucene.util.CloseableThreadLocal](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/CloseableThreadLocal.java) | org.gnit.lucenekmp.util.CloseableThreadLocal | Depth 1 | [x] | 5 | 1 | 0% | 5 |
| [org.apache.lucene.util.CollectionUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/CollectionUtil.java) | org.gnit.lucenekmp.util.CollectionUtil | Depth 2 | [x] | 23 | 39 | 41% | 4 |
| [org.apache.lucene.util.CommandLineUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/CommandLineUtil.java) | org.gnit.lucenekmp.util.CommandLineUtil | Depth 3 | [x] | 7 | 7 | 94% | 0 |
| [org.apache.lucene.util.Constants](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/Constants.java) | org.gnit.lucenekmp.util.Constants | Depth 1 | [x] | 6 | 8 | 34% | 0 |
| [org.apache.lucene.util.Counter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/Counter.java) | org.gnit.lucenekmp.util.Counter | Depth 2 | [x] | 3 | 1 | 100% | 2 |
| [org.apache.lucene.util.DocBaseBitSetIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/DocBaseBitSetIterator.java) | org.gnit.lucenekmp.util.DocBaseBitSetIterator | Depth 3 | [x] | 7 | 5 | 100% | 2 |
| [org.apache.lucene.util.DocIdSetBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/DocIdSetBuilder.java) | org.gnit.lucenekmp.util.DocIdSetBuilder | Depth 4 | [x] | 0 | 3 | 0% | 0 |
| [org.apache.lucene.util.FileDeleter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/FileDeleter.java) | org.gnit.lucenekmp.util.FileDeleter | Depth 3 | [x] | 2 | 2 | 40% | 0 |
| [org.apache.lucene.util.FixedBitSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/FixedBitSet.java) | org.gnit.lucenekmp.util.FixedBitSet | Depth 2 | [x] | 43 | 9 | 100% | 34 |
| [org.apache.lucene.util.FixedBits](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/FixedBits.java) | org.gnit.lucenekmp.util.FixedBits | Depth 3 | [x] | 2 | 2 | 76% | 0 |
| [org.apache.lucene.util.FrequencyTrackingRingBuffer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/FrequencyTrackingRingBuffer.java) | org.gnit.lucenekmp.util.FrequencyTrackingRingBuffer | Depth 3 | [x] | 7 | 1 | 83% | 6 |
| [org.apache.lucene.util.HotspotVMOptions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/HotspotVMOptions.java) | org.gnit.lucenekmp.util.HotspotVMOptions | Depth 2 | [] | 1 | 0 | 0% | 1 |
| [org.apache.lucene.util.IOBooleanSupplier](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/IOBooleanSupplier.java) | org.gnit.lucenekmp.util.IOBooleanSupplier | Depth 2 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.util.IOSupplier](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/IOSupplier.java) | org.gnit.lucenekmp.util.IOSupplier | Depth 1 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.util.IOUtils](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/IOUtils.java) | org.gnit.lucenekmp.util.IOUtils | Depth 2 | [x] | 4 | 25 | 0% | 4 |
| [org.apache.lucene.util.InPlaceMergeSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/InPlaceMergeSorter.java) | org.gnit.lucenekmp.util.InPlaceMergeSorter | Depth 4 | [x] | 22 | 20 | 99% | 2 |
| [org.apache.lucene.util.InfoStream](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/InfoStream.java) | org.gnit.lucenekmp.util.InfoStream | Depth 2 | [x] | 3 | 3 | 83% | 0 |
| [org.apache.lucene.util.IntArrayDocIdSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/IntArrayDocIdSet.java) | org.gnit.lucenekmp.util.IntArrayDocIdSet | Depth 5 | [x] | 7 | 5 | 100% | 2 |
| [org.apache.lucene.util.IntBlockPool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/IntBlockPool.java) | org.gnit.lucenekmp.util.IntBlockPool | Depth 4 | [x] | 1 | 1 | 72% | 0 |
| [org.apache.lucene.util.IntroSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/IntroSorter.java) | org.gnit.lucenekmp.util.IntroSorter | Depth 3 | [x] | 23 | 0 | 100% | 23 |
| [org.apache.lucene.util.IntsRef](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/IntsRef.java) | org.gnit.lucenekmp.util.IntsRef | Depth 2 | [x] | 3 | 1 | 62% | 2 |
| [org.apache.lucene.util.LSBRadixSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/LSBRadixSorter.java) | org.gnit.lucenekmp.util.LSBRadixSorter | Depth 4 | [x] | 6 | 5 | 92% | 1 |
| [org.apache.lucene.util.LongBitSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/LongBitSet.java) | org.gnit.lucenekmp.util.LongBitSet | Depth 3 | [x] | 21 | 2 | 76% | 19 |
| [org.apache.lucene.util.LongValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/LongValues.java) | org.gnit.lucenekmp.util.LongValues | Depth 3 | [x] | 1 | 1 | 71% | 0 |
| [org.apache.lucene.util.LongsRef](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/LongsRef.java) | org.gnit.lucenekmp.util.LongsRef | Depth 4 | [x] | 3 | 1 | 62% | 2 |
| [org.apache.lucene.util.MSBRadixSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/MSBRadixSorter.java) | org.gnit.lucenekmp.util.MSBRadixSorter | Depth 6 | [x] | 23 | 21 | 99% | 2 |
| [org.apache.lucene.util.MathUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/MathUtil.java) | org.gnit.lucenekmp.util.MathUtil | Depth 4 | [x] | 8 | 9 | 88% | 0 |
| [org.apache.lucene.util.MergedIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/MergedIterator.java) | org.gnit.lucenekmp.util.MergedIterator | Depth 5 | [x] | 0 | 15 | 0% | 0 |
| [org.apache.lucene.util.NamedSPILoader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/NamedSPILoader.java) | org.gnit.lucenekmp.util.NamedSPILoader | Depth 2 | [x] | 5 | 0 | 0% | 5 |
| [org.apache.lucene.util.NamedThreadFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/NamedThreadFactory.java) | org.gnit.lucenekmp.util.NamedThreadFactory | Depth 3 | [x] | 2 | 3 | 0% | 2 |
| [org.apache.lucene.util.NotDocIdSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/NotDocIdSet.java) | org.gnit.lucenekmp.util.NotDocIdSet | Depth 4 | [x] | 7 | 5 | 70% | 2 |
| [org.apache.lucene.util.NumericUtils](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/NumericUtils.java) | org.gnit.lucenekmp.util.NumericUtils | Depth 1 | [x] | 14 | 16 | 87% | 0 |
| [org.apache.lucene.util.PagedBytes](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/PagedBytes.java) | org.gnit.lucenekmp.util.PagedBytes | Depth 4 | [x] | 4 | 0 | 100% | 4 |
| [org.apache.lucene.util.PrintStreamInfoStream](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/PrintStreamInfoStream.java) | org.gnit.lucenekmp.util.PrintStreamInfoStream | Depth 1 | [x] | 3 | 0 | 0% | 3 |
| [org.apache.lucene.util.PriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/PriorityQueue.java) | org.gnit.lucenekmp.util.PriorityQueue | Depth 3 | [x] | 1 | 2 | 42% | 0 |
| [org.apache.lucene.util.RamUsageEstimator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/RamUsageEstimator.java) | org.gnit.lucenekmp.util.RamUsageEstimator | Depth 3 | [x] | 6 | 5 | 56% | 1 |
| [org.apache.lucene.util.RefCount](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/RefCount.java) | org.gnit.lucenekmp.util.RefCount | Depth 3 | [x] | 3 | 3 | 84% | 0 |
| [org.apache.lucene.util.RoaringDocIdSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/RoaringDocIdSet.java) | org.gnit.lucenekmp.util.RoaringDocIdSet | Depth 3 | [x] | 8 | 6 | 62% | 3 |
| [org.apache.lucene.util.SameThreadExecutorService](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/SameThreadExecutorService.java) | org.gnit.lucenekmp.util.SameThreadExecutorService | Depth 2 | [x] | 5 | 15 | 23% | 2 |
| [org.apache.lucene.util.SetOnce](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/SetOnce.java) | org.gnit.lucenekmp.util.SetOnce | Depth 2 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.util.Sorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/Sorter.java) | org.gnit.lucenekmp.util.Sorter | Depth 4 | [x] | 21 | 2 | 100% | 19 |
| [org.apache.lucene.util.SparseFixedBitSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/SparseFixedBitSet.java) | org.gnit.lucenekmp.util.SparseFixedBitSet | Depth 3 | [x] | 31 | 3 | 83% | 28 |
| [org.apache.lucene.util.StableMSBRadixSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/StableMSBRadixSorter.java) | org.gnit.lucenekmp.util.StableMSBRadixSorter | Depth 8 | [x] | 26 | 24 | 99% | 2 |
| [org.apache.lucene.util.StableStringSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/StableStringSorter.java) | org.gnit.lucenekmp.util.StableStringSorter | Depth 6 | [x] | 37 | 35 | 95% | 3 |
| [org.apache.lucene.util.StringHelper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/StringHelper.java) | org.gnit.lucenekmp.util.StringHelper | Depth 1 | [x] | 14 | 14 | 92% | 0 |
| [org.apache.lucene.util.StringSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/StringSorter.java) | org.gnit.lucenekmp.util.StringSorter | Depth 5 | [x] | 35 | 21 | 89% | 16 |
| [org.apache.lucene.util.SuppressForbidden](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/SuppressForbidden.java) | org.gnit.lucenekmp.util.SuppressForbidden | Depth 2 | [] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.util.ThreadInterruptedException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/ThreadInterruptedException.java) | org.gnit.lucenekmp.util.ThreadInterruptedException | Depth 1 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.util.TimSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/TimSorter.java) | org.gnit.lucenekmp.util.TimSorter | Depth 3 | [x] | 42 | 1 | 100% | 41 |
| [org.apache.lucene.util.UnicodeUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/UnicodeUtil.java) | org.gnit.lucenekmp.util.UnicodeUtil | Depth 2 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.util.VectorUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/VectorUtil.java) | org.gnit.lucenekmp.util.VectorUtil | Depth 4 | [x] | 20 | 20 | 97% | 0 |
| [org.apache.lucene.util.Version](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/Version.java) | org.gnit.lucenekmp.util.Version | Depth 1 | [x] | 5 | 3 | 41% | 2 |
| [org.apache.lucene.util.automaton.Automaton](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/Automaton.java) | org.gnit.lucenekmp.util.automaton.Automaton | Depth 4 | [x] | 7 | 21 | 0% | 7 |
| [org.apache.lucene.util.automaton.ByteRunAutomaton](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/ByteRunAutomaton.java) | org.gnit.lucenekmp.util.automaton.ByteRunAutomaton | Depth 2 | [x] | 6 | 1 | 100% | 5 |
| [org.apache.lucene.util.automaton.ByteRunnable](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/ByteRunnable.java) | org.gnit.lucenekmp.util.automaton.ByteRunnable | Depth 3 | [x] | 3 | 1 | 0% | 3 |
| [org.apache.lucene.util.automaton.CompiledAutomaton](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/CompiledAutomaton.java) | org.gnit.lucenekmp.util.automaton.CompiledAutomaton | Depth 2 | [x] | 6 | 0 | 100% | 6 |
| [org.apache.lucene.util.automaton.FrozenIntSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/FrozenIntSet.java) | org.gnit.lucenekmp.util.automaton.FrozenIntSet | Depth 4 | [x] | 1 | 1 | 60% | 0 |
| [org.apache.lucene.util.automaton.NFARunAutomaton](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/NFARunAutomaton.java) | org.gnit.lucenekmp.util.automaton.NFARunAutomaton | Depth 4 | [x] | 6 | 6 | 65% | 0 |
| [org.apache.lucene.util.automaton.Operations](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/Operations.java) | org.gnit.lucenekmp.util.automaton.Operations | Depth 4 | [x] | 5 | 1 | 29% | 4 |
| [org.apache.lucene.util.automaton.RegExp](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/RegExp.java) | org.gnit.lucenekmp.util.automaton.RegExp | Depth 6 | [x] | 1 | 1 | 0% | 1 |
| [org.apache.lucene.util.automaton.RunAutomaton](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/RunAutomaton.java) | org.gnit.lucenekmp.util.automaton.RunAutomaton | Depth 3 | [x] | 4 | 0 | 100% | 4 |
| [org.apache.lucene.util.automaton.StatePair](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/StatePair.java) | org.gnit.lucenekmp.util.automaton.StatePair | Depth 4 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.util.automaton.StringsToAutomaton](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/StringsToAutomaton.java) | org.gnit.lucenekmp.util.automaton.StringsToAutomaton | Depth 5 | [x] | 7 | 2 | 45% | 6 |
| [org.apache.lucene.util.automaton.TooComplexToDeterminizeException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/TooComplexToDeterminizeException.java) | org.gnit.lucenekmp.util.automaton.TooComplexToDeterminizeException | Depth 4 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.util.automaton.Transition](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/Transition.java) | org.gnit.lucenekmp.util.automaton.Transition | Depth 3 | [x] | 1 | 0 | 0% | 1 |
| [org.apache.lucene.util.automaton.UTF32ToUTF8](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/UTF32ToUTF8.java) | org.gnit.lucenekmp.util.automaton.UTF32ToUTF8 | Depth 4 | [x] | 0 | 4 | 0% | 0 |
| [org.apache.lucene.util.bkd.BKDConfig](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/bkd/BKDConfig.java) | org.gnit.lucenekmp.util.bkd.BKDConfig | Depth 3 | [x] | 7 | 0 | 100% | 7 |
| [org.apache.lucene.util.compress.LZ4](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/compress/LZ4.java) | org.gnit.lucenekmp.util.compress.LZ4 | Depth 6 | [x] | 5 | 2 | 0% | 5 |
| [org.apache.lucene.util.fst.ByteSequenceOutputs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/ByteSequenceOutputs.java) | org.gnit.lucenekmp.util.fst.ByteSequenceOutputs | Depth 4 | [x] | 12 | 0 | 100% | 12 |
| [org.apache.lucene.util.fst.BytesRefFSTEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/BytesRefFSTEnum.java) | org.gnit.lucenekmp.util.fst.BytesRefFSTEnum | Depth 5 | [x] | 27 | 0 | 0% | 27 |
| [org.apache.lucene.util.fst.FST](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/FST.java) | org.gnit.lucenekmp.util.fst.FST | Depth 6 | [x] | 10 | 1 | 0% | 10 |
| [org.apache.lucene.util.fst.FSTCompiler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/FSTCompiler.java) | org.gnit.lucenekmp.util.fst.FSTCompiler | Depth 6 | [x] | 0 | 6 | 0% | 0 |
| [org.apache.lucene.util.fst.FSTEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/FSTEnum.java) | org.gnit.lucenekmp.util.fst.FSTEnum | Depth 6 | [x] | 23 | 23 | 78% | 0 |
| [org.apache.lucene.util.fst.FSTReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/FSTReader.java) | org.gnit.lucenekmp.util.fst.FSTReader | Depth 5 | [x] | 2 | 1 | 0% | 2 |
| [org.apache.lucene.util.fst.FSTSuffixNodeCache](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/FSTSuffixNodeCache.java) | org.gnit.lucenekmp.util.fst.FSTSuffixNodeCache | Depth 7 | [x] | 9 | 9 | 68% | 0 |
| [org.apache.lucene.util.fst.GrowableByteArrayDataOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/GrowableByteArrayDataOutput.java) | org.gnit.lucenekmp.util.fst.GrowableByteArrayDataOutput | Depth 6 | [x] | 21 | 0 | 100% | 21 |
| [org.apache.lucene.util.fst.OffHeapFSTStore](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/OffHeapFSTStore.java) | org.gnit.lucenekmp.util.fst.OffHeapFSTStore | Depth 5 | [x] | 2 | 0 | 100% | 2 |
| [org.apache.lucene.util.fst.OnHeapFSTStore](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/OnHeapFSTStore.java) | org.gnit.lucenekmp.util.fst.OnHeapFSTStore | Depth 6 | [x] | 2 | 0 | 100% | 2 |
| [org.apache.lucene.util.fst.Util](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/Util.java) | org.gnit.lucenekmp.util.fst.Util | Depth 6 | [x] | 1 | 0 | 0% | 1 |
| [org.apache.lucene.util.hnsw.BlockingFloatHeap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/BlockingFloatHeap.java) | org.gnit.lucenekmp.util.hnsw.BlockingFloatHeap | Depth 7 | [x] | 6 | 1 | 0% | 6 |
| [org.apache.lucene.util.hnsw.HnswGraph](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/HnswGraph.java) | org.gnit.lucenekmp.util.hnsw.HnswGraph | Depth 4 | [x] | 8 | 1 | 0% | 8 |
| [org.apache.lucene.util.hnsw.NeighborQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/NeighborQueue.java) | org.gnit.lucenekmp.util.hnsw.NeighborQueue | Depth 4 | [x] | 2 | 2 | 82% | 0 |
| [org.apache.lucene.util.hnsw.RandomVectorScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/RandomVectorScorer.java) | org.gnit.lucenekmp.util.hnsw.RandomVectorScorer | Depth 5 | [x] | 4 | 2 | 0% | 4 |
| [org.apache.lucene.util.hnsw.RandomVectorScorerSupplier](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/RandomVectorScorerSupplier.java) | org.gnit.lucenekmp.util.hnsw.RandomVectorScorerSupplier | Depth 5 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.util.hnsw.UpdateableRandomVectorScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/UpdateableRandomVectorScorer.java) | org.gnit.lucenekmp.util.hnsw.UpdateableRandomVectorScorer | Depth 7 | [x] | 4 | 2 | 0% | 4 |
| [org.apache.lucene.util.packed.AbstractBlockPackedWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/AbstractBlockPackedWriter.java) | org.gnit.lucenekmp.util.packed.AbstractBlockPackedWriter | Depth 5 | [x] | 8 | 1 | 100% | 7 |
| [org.apache.lucene.util.packed.AbstractPagedMutable](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/AbstractPagedMutable.java) | org.gnit.lucenekmp.util.packed.AbstractPagedMutable | Depth 2 | [x] | 13 | 0 | 100% | 13 |
| [org.apache.lucene.util.packed.BlockPackedReaderIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BlockPackedReaderIterator.java) | org.gnit.lucenekmp.util.packed.BlockPackedReaderIterator | Depth 6 | [x] | 6 | 1 | 100% | 5 |
| [org.apache.lucene.util.packed.BlockPackedWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BlockPackedWriter.java) | org.gnit.lucenekmp.util.packed.BlockPackedWriter | Depth 6 | [x] | 5 | 7 | 28% | 1 |
| [org.apache.lucene.util.packed.BulkOperation](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperation.java) | org.gnit.lucenekmp.util.packed.BulkOperation | Depth 4 | [x] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.util.packed.BulkOperationPacked](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked1](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked1.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked1 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked10](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked10.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked10 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked11](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked11.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked11 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked12](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked12.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked12 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked13](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked13.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked13 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked14](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked14.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked14 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked15](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked15.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked15 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked16](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked16.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked16 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked17](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked17.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked17 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked18](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked18.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked18 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked19](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked19.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked19 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked2](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked2.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked2 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked20](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked20.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked20 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked21](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked21.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked21 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked22](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked22.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked22 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked23](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked23.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked23 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked24](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked24.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked24 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked3](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked3.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked3 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked4](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked4.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked4 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked5](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked5.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked5 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked6](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked6.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked6 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked7](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked7.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked7 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked8](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked8.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked8 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPacked9](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked9.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked9 | Depth 4 | [x] | 15 | 14 | 98% | 1 |
| [org.apache.lucene.util.packed.BulkOperationPackedSingleBlock](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPackedSingleBlock.java) | org.gnit.lucenekmp.util.packed.BulkOperationPackedSingleBlock | Depth 4 | [x] | 20 | 1 | 83% | 19 |
| [org.apache.lucene.util.packed.DeltaPackedLongValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/DeltaPackedLongValues.java) | org.gnit.lucenekmp.util.packed.DeltaPackedLongValues | Depth 3 | [x] | 10 | 0 | 100% | 10 |
| [org.apache.lucene.util.packed.DirectMonotonicReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/DirectMonotonicReader.java) | org.gnit.lucenekmp.util.packed.DirectMonotonicReader | Depth 7 | [x] | 6 | 0 | 0% | 6 |
| [org.apache.lucene.util.packed.DirectMonotonicWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/DirectMonotonicWriter.java) | org.gnit.lucenekmp.util.packed.DirectMonotonicWriter | Depth 5 | [x] | 4 | 1 | 100% | 3 |
| [org.apache.lucene.util.packed.DirectReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/DirectReader.java) | org.gnit.lucenekmp.util.packed.DirectReader | Depth 7 | [x] | 2 | 2 | 83% | 0 |
| [org.apache.lucene.util.packed.DirectWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/DirectWriter.java) | org.gnit.lucenekmp.util.packed.DirectWriter | Depth 6 | [x] | 8 | 5 | 86% | 3 |
| [org.apache.lucene.util.packed.GrowableWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/GrowableWriter.java) | org.gnit.lucenekmp.util.packed.GrowableWriter | Depth 3 | [x] | 10 | 1 | 83% | 9 |
| [org.apache.lucene.util.packed.MonotonicBlockPackedReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/MonotonicBlockPackedReader.java) | org.gnit.lucenekmp.util.packed.MonotonicBlockPackedReader | Depth 5 | [x] | 1 | 2 | 0% | 1 |
| [org.apache.lucene.util.packed.MonotonicLongValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/MonotonicLongValues.java) | org.gnit.lucenekmp.util.packed.MonotonicLongValues | Depth 3 | [x] | 10 | 0 | 100% | 10 |
| [org.apache.lucene.util.packed.Packed64](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/Packed64.java) | org.gnit.lucenekmp.util.packed.Packed64 | Depth 3 | [x] | 8 | 1 | 83% | 7 |
| [org.apache.lucene.util.packed.Packed64SingleBlock](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/Packed64SingleBlock.java) | org.gnit.lucenekmp.util.packed.Packed64SingleBlock | Depth 4 | [x] | 10 | 7 | 82% | 3 |
| [org.apache.lucene.util.packed.PackedInts](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/PackedInts.java) | org.gnit.lucenekmp.util.packed.PackedInts | Depth 3 | [x] | 3 | 3 | 0% | 3 |
| [org.apache.lucene.util.packed.PackedLongValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/PackedLongValues.java) | org.gnit.lucenekmp.util.packed.PackedLongValues | Depth 3 | [x] | 2 | 2 | 38% | 0 |
| [org.apache.lucene.util.packed.PackedReaderIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/PackedReaderIterator.java) | org.gnit.lucenekmp.util.packed.PackedReaderIterator | Depth 3 | [x] | 1 | 1 | 36% | 0 |
| [org.apache.lucene.util.packed.PackedWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/PackedWriter.java) | org.gnit.lucenekmp.util.packed.PackedWriter | Depth 3 | [x] | 4 | 4 | 40% | 0 |
| [org.apache.lucene.util.packed.PagedGrowableWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/PagedGrowableWriter.java) | org.gnit.lucenekmp.util.packed.PagedGrowableWriter | Depth 2 | [x] | 13 | 13 | 79% | 0 |
| [org.apache.lucene.util.packed.PagedMutable](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/PagedMutable.java) | org.gnit.lucenekmp.util.packed.PagedMutable | Depth 2 | [x] | 13 | 13 | 79% | 0 |


## Detailed Method Analysis Reports

## Detailed Analysis: org.apache.lucene.analysis.Analyzer -> org.gnit.lucenekmp.analysis.Analyzer

### Method Categories:
- **Java Core Business Logic**: 11
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 3
- **Semantic Completion**: 0%

### Missing Core Methods:
- `protected org.apache.lucene.analysis.Analyzer$TokenStreamComponents createComponents(java.lang.String)`
- `protected org.apache.lucene.analysis.TokenStream normalize(java.lang.String, org.apache.lucene.analysis.TokenStream)`
- `public org.apache.lucene.analysis.TokenStream tokenStream(java.lang.String, java.io.Reader)`
- `public org.apache.lucene.analysis.TokenStream tokenStream(java.lang.String, java.lang.String)`
- `public org.apache.lucene.util.BytesRef normalize(java.lang.String, java.lang.String)`
- `protected java.io.Reader initReader(java.lang.String, java.io.Reader)`
- `protected java.io.Reader initReaderForNormalization(java.lang.String, java.io.Reader)`
- `protected org.apache.lucene.util.AttributeFactory attributeFactory(java.lang.String)`
- `public int getPositionIncrementGap(java.lang.String)`
- `public int getOffsetGap(java.lang.String)`
- `public void close()`


## Detailed Analysis: org.apache.lucene.analysis.CharArrayMap -> org.gnit.lucenekmp.analysis.CharArrayMap

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 2
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 8
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 2
- **Semantic Completion**: 95%

### Missing Core Methods:
- `public static org.apache.lucene.analysis.CharArraySet unmodifiableSet(org.apache.lucene.analysis.CharArraySet)`


## Detailed Analysis: org.apache.lucene.analysis.CharArraySet -> org.gnit.lucenekmp.analysis.CharArraySet

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 2
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 55%

### Missing Core Methods:
- `public void clear()`
- `public boolean contains(char[], int, int)`
- `public boolean contains(java.lang.CharSequence)`
- `public boolean contains(java.lang.Object)`
- `public boolean add(java.lang.Object)`
- `public boolean add(java.lang.CharSequence)`
- `public boolean add(java.lang.String)`
- `public boolean add(char[])`


## Detailed Analysis: org.apache.lucene.analysis.CharFilter -> org.gnit.lucenekmp.analysis.CharFilter

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 7
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 34%


## Detailed Analysis: org.apache.lucene.analysis.CharacterUtils -> org.gnit.lucenekmp.analysis.CharacterUtils

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 7
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 53%


## Detailed Analysis: org.apache.lucene.analysis.FilteringTokenFilter -> org.gnit.lucenekmp.analysis.FilteringTokenFilter

### Method Categories:
- **Java Core Business Logic**: 21
- **Java Property Accessors**: 6
- **Java Auto-Generated**: 3
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 19
- **KMP Property Accessors**: 7
- **KMP Auto-Generated**: 3
- **KMP Synthetic**: 4
- **Semantic Completion**: 84%

### Missing Core Methods:
- `package-private static java.lang.Class<? extends org.apache.lucene.util.Attribute>[] getAttributeInterfaces(java.lang.Class<? extends org.apache.lucene.util.AttributeImpl>)`
- `public T addAttribute(java.lang.Class<T>)`
- `public T getAttribute(java.lang.Class<T>)`
- `public static T unwrapAll(T)`


## Detailed Analysis: org.apache.lucene.analysis.LowerCaseFilter -> org.gnit.lucenekmp.analysis.LowerCaseFilter

### Method Categories:
- **Java Core Business Logic**: 21
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 3
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 19
- **KMP Property Accessors**: 6
- **KMP Auto-Generated**: 3
- **KMP Synthetic**: 4
- **Semantic Completion**: 84%

### Missing Core Methods:
- `package-private static java.lang.Class<? extends org.apache.lucene.util.Attribute>[] getAttributeInterfaces(java.lang.Class<? extends org.apache.lucene.util.AttributeImpl>)`
- `public T addAttribute(java.lang.Class<T>)`
- `public T getAttribute(java.lang.Class<T>)`
- `public static T unwrapAll(T)`


## Detailed Analysis: org.apache.lucene.analysis.ReusableStringReader -> org.gnit.lucenekmp.analysis.ReusableStringReader

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 47%


## Detailed Analysis: org.apache.lucene.analysis.StopFilter -> org.gnit.lucenekmp.analysis.StopFilter

### Method Categories:
- **Java Core Business Logic**: 25
- **Java Property Accessors**: 6
- **Java Auto-Generated**: 3
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public boolean incrementToken()`
- `public void reset()`
- `public void end()`
- `public void close()`
- `private boolean assertFinal()`
- `package-private static java.lang.Class<? extends org.apache.lucene.util.Attribute>[] getAttributeInterfaces(java.lang.Class<? extends org.apache.lucene.util.AttributeImpl>)`
- `public void addAttributeImpl(org.apache.lucene.util.AttributeImpl)`
- `public T addAttribute(java.lang.Class<T>)`
- `public boolean hasAttributes()`
- `public boolean hasAttribute(java.lang.Class<? extends org.apache.lucene.util.Attribute>)`
- `public T getAttribute(java.lang.Class<T>)`
- `public void clearAttributes()`
- `public void endAttributes()`
- `public void removeAllAttributes()`
- `public org.apache.lucene.util.AttributeSource$State captureState()`
- `public void restoreState(org.apache.lucene.util.AttributeSource$State)`
- `public java.lang.String reflectAsString(boolean)`
- `public void reflectWith(org.apache.lucene.util.AttributeReflector)`
- `public org.apache.lucene.util.AttributeSource cloneAttributes()`
- `public void copyTo(org.apache.lucene.util.AttributeSource)`
- `public static T unwrapAll(T)`


## Detailed Analysis: org.apache.lucene.analysis.StopwordAnalyzerBase -> org.gnit.lucenekmp.analysis.StopwordAnalyzerBase

### Method Categories:
- **Java Core Business Logic**: 13
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `protected org.apache.lucene.analysis.Analyzer$TokenStreamComponents createComponents(java.lang.String)`
- `protected org.apache.lucene.analysis.TokenStream normalize(java.lang.String, org.apache.lucene.analysis.TokenStream)`
- `public org.apache.lucene.analysis.TokenStream tokenStream(java.lang.String, java.io.Reader)`
- `public org.apache.lucene.analysis.TokenStream tokenStream(java.lang.String, java.lang.String)`
- `public org.apache.lucene.util.BytesRef normalize(java.lang.String, java.lang.String)`
- `protected java.io.Reader initReader(java.lang.String, java.io.Reader)`
- `protected java.io.Reader initReaderForNormalization(java.lang.String, java.io.Reader)`
- `protected org.apache.lucene.util.AttributeFactory attributeFactory(java.lang.String)`
- `public int getPositionIncrementGap(java.lang.String)`
- `public int getOffsetGap(java.lang.String)`
- `public void close()`


## Detailed Analysis: org.apache.lucene.analysis.TokenFilter -> org.gnit.lucenekmp.analysis.TokenFilter

### Method Categories:
- **Java Core Business Logic**: 21
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 3
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 19
- **KMP Property Accessors**: 6
- **KMP Auto-Generated**: 3
- **KMP Synthetic**: 4
- **Semantic Completion**: 84%

### Missing Core Methods:
- `package-private static java.lang.Class<? extends org.apache.lucene.util.Attribute>[] getAttributeInterfaces(java.lang.Class<? extends org.apache.lucene.util.AttributeImpl>)`
- `public T addAttribute(java.lang.Class<T>)`
- `public T getAttribute(java.lang.Class<T>)`
- `public static T unwrapAll(T)`


## Detailed Analysis: org.apache.lucene.analysis.TokenStream -> org.gnit.lucenekmp.analysis.TokenStream

### Method Categories:
- **Java Core Business Logic**: 20
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `private boolean assertFinal()`
- `public boolean incrementToken()`
- `public void end()`
- `public void reset()`
- `public void close()`
- `package-private static java.lang.Class<? extends org.apache.lucene.util.Attribute>[] getAttributeInterfaces(java.lang.Class<? extends org.apache.lucene.util.AttributeImpl>)`
- `public void addAttributeImpl(org.apache.lucene.util.AttributeImpl)`
- `public T addAttribute(java.lang.Class<T>)`
- `public boolean hasAttributes()`
- `public boolean hasAttribute(java.lang.Class<? extends org.apache.lucene.util.Attribute>)`
- `public T getAttribute(java.lang.Class<T>)`
- `public void clearAttributes()`
- `public void endAttributes()`
- `public void removeAllAttributes()`
- `public org.apache.lucene.util.AttributeSource$State captureState()`
- `public void restoreState(org.apache.lucene.util.AttributeSource$State)`
- `public java.lang.String reflectAsString(boolean)`
- `public void reflectWith(org.apache.lucene.util.AttributeReflector)`
- `public org.apache.lucene.util.AttributeSource cloneAttributes()`
- `public void copyTo(org.apache.lucene.util.AttributeSource)`


## Detailed Analysis: org.apache.lucene.analysis.Tokenizer -> org.gnit.lucenekmp.analysis.Tokenizer

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 32%


## Detailed Analysis: org.apache.lucene.analysis.standard.StandardAnalyzer -> org.gnit.lucenekmp.analysis.standard.StandardAnalyzer

### Method Categories:
- **Java Core Business Logic**: 13
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `protected org.apache.lucene.analysis.Analyzer$TokenStreamComponents createComponents(java.lang.String)`
- `protected org.apache.lucene.analysis.TokenStream normalize(java.lang.String, org.apache.lucene.analysis.TokenStream)`
- `protected static org.apache.lucene.analysis.CharArraySet loadStopwordSet(java.nio.file.Path)`
- `protected static org.apache.lucene.analysis.CharArraySet loadStopwordSet(java.io.Reader)`
- `public org.apache.lucene.analysis.TokenStream tokenStream(java.lang.String, java.io.Reader)`
- `public org.apache.lucene.analysis.TokenStream tokenStream(java.lang.String, java.lang.String)`
- `public org.apache.lucene.util.BytesRef normalize(java.lang.String, java.lang.String)`
- `protected java.io.Reader initReader(java.lang.String, java.io.Reader)`
- `protected java.io.Reader initReaderForNormalization(java.lang.String, java.io.Reader)`
- `protected org.apache.lucene.util.AttributeFactory attributeFactory(java.lang.String)`
- `public int getPositionIncrementGap(java.lang.String)`
- `public int getOffsetGap(java.lang.String)`
- `public void close()`


## Detailed Analysis: org.apache.lucene.analysis.standard.StandardTokenizer -> org.gnit.lucenekmp.analysis.standard.StandardTokenizer

### Method Categories:
- **Java Core Business Logic**: 23
- **Java Property Accessors**: 7
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `private void init()`
- `public boolean incrementToken()`
- `public void end()`
- `public void close()`
- `public void reset()`
- `protected int correctOffset(int)`
- `protected void setReaderTestPoint()`
- `private boolean assertFinal()`
- `package-private static java.lang.Class<? extends org.apache.lucene.util.Attribute>[] getAttributeInterfaces(java.lang.Class<? extends org.apache.lucene.util.AttributeImpl>)`
- `public void addAttributeImpl(org.apache.lucene.util.AttributeImpl)`
- `public T addAttribute(java.lang.Class<T>)`
- `public boolean hasAttributes()`
- `public boolean hasAttribute(java.lang.Class<? extends org.apache.lucene.util.Attribute>)`
- `public T getAttribute(java.lang.Class<T>)`
- `public void clearAttributes()`
- `public void endAttributes()`
- `public void removeAllAttributes()`
- `public org.apache.lucene.util.AttributeSource$State captureState()`
- `public void restoreState(org.apache.lucene.util.AttributeSource$State)`
- `public java.lang.String reflectAsString(boolean)`
- `public void reflectWith(org.apache.lucene.util.AttributeReflector)`
- `public org.apache.lucene.util.AttributeSource cloneAttributes()`
- `public void copyTo(org.apache.lucene.util.AttributeSource)`


## Detailed Analysis: org.apache.lucene.analysis.standard.StandardTokenizerImpl -> org.gnit.lucenekmp.analysis.standard.StandardTokenizerImpl

### Method Categories:
- **Java Core Business Logic**: 23
- **Java Property Accessors**: 6
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 8
- **Semantic Completion**: 89%

### Missing Core Methods:
- `public void getText(org.apache.lucene.analysis.tokenattributes.CharTermAttribute)`
- `private boolean zzRefill()`
- `public void yyclose()`
- `public void yyreset(java.io.Reader)`
- `private void yyResetPosition()`
- `public boolean yyatEOF()`
- `public void yybegin(int)`
- `public char yycharat(int)`
- `public void yypushback(int)`


## Detailed Analysis: org.apache.lucene.analysis.tokenattributes.BytesTermAttribute -> org.gnit.lucenekmp.analysis.tokenattributes.BytesTermAttribute

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.analysis.tokenattributes.CharTermAttributeImpl -> org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttributeImpl

### Method Categories:
- **Java Core Business Logic**: 19
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 4
- **Java Synthetic**: 6
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public void copyBuffer(char[], int, int)`
- `public char[] resizeBuffer(int)`
- `private void growTermBuffer(int)`
- `public org.apache.lucene.analysis.tokenattributes.CharTermAttribute setLength(int)`
- `public org.apache.lucene.analysis.tokenattributes.CharTermAttribute setEmpty()`
- `public char charAt(int)`
- `public java.lang.CharSequence subSequence(int, int)`
- `public org.apache.lucene.analysis.tokenattributes.CharTermAttribute append(java.lang.CharSequence)`
- `public org.apache.lucene.analysis.tokenattributes.CharTermAttribute append(java.lang.CharSequence, int, int)`
- `public org.apache.lucene.analysis.tokenattributes.CharTermAttribute append(char)`
- `public org.apache.lucene.analysis.tokenattributes.CharTermAttribute append(java.lang.String)`
- `public org.apache.lucene.analysis.tokenattributes.CharTermAttribute append(java.lang.StringBuilder)`
- `public org.apache.lucene.analysis.tokenattributes.CharTermAttribute append(org.apache.lucene.analysis.tokenattributes.CharTermAttribute)`
- `private org.apache.lucene.analysis.tokenattributes.CharTermAttribute appendNull()`
- `public void clear()`
- `public void reflectWith(org.apache.lucene.util.AttributeReflector)`
- `public void copyTo(org.apache.lucene.util.AttributeImpl)`
- `public void end()`
- `public java.lang.String reflectAsString(boolean)`


## Detailed Analysis: org.apache.lucene.analysis.tokenattributes.PackedTokenAttributeImpl -> org.gnit.lucenekmp.analysis.tokenattributes.PackedTokenAttributeImpl

### Method Categories:
- **Java Core Business Logic**: 22
- **Java Property Accessors**: 11
- **Java Auto-Generated**: 4
- **Java Synthetic**: 7
- **KMP Core Business Logic**: 24
- **KMP Property Accessors**: 13
- **KMP Auto-Generated**: 4
- **KMP Synthetic**: 8
- **Semantic Completion**: 86%

### Missing Core Methods:
- `public char charAt(int)`


## Detailed Analysis: org.apache.lucene.analysis.tokenattributes.PayloadAttribute -> org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute -> org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute -> org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.analysis.tokenattributes.TermFrequencyAttribute -> org.gnit.lucenekmp.analysis.tokenattributes.TermFrequencyAttribute

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute -> org.gnit.lucenekmp.analysis.tokenattributes.TermToBytesRefAttribute

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.analysis.tokenattributes.TypeAttribute -> org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.codecs.BlockTermState -> org.gnit.lucenekmp.codecs.BlockTermState

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 2
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 10
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 1
- **Semantic Completion**: 21%


## Detailed Analysis: org.apache.lucene.codecs.Codec -> org.gnit.lucenekmp.codecs.Codec

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 33%


## Detailed Analysis: org.apache.lucene.codecs.CompetitiveImpactAccumulator -> org.gnit.lucenekmp.codecs.CompetitiveImpactAccumulator

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 2
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 0
- **Semantic Completion**: 85%


## Detailed Analysis: org.apache.lucene.codecs.CompoundDirectory -> org.gnit.lucenekmp.codecs.CompoundDirectory

### Method Categories:
- **Java Core Business Logic**: 16
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 15
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `protected static java.lang.String getTempFileName(java.lang.String, java.lang.String, long)`


## Detailed Analysis: org.apache.lucene.codecs.DocValuesConsumer -> org.gnit.lucenekmp.codecs.DocValuesConsumer

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 12
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.index.NumericDocValues getNumeric(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.BinaryDocValues getBinary(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.SortedDocValues getSorted(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.SortedNumericDocValues getSortedNumeric(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.SortedSetDocValues getSortedSet(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.DocValuesSkipper getSkipper(org.apache.lucene.index.FieldInfo)`
- `public void checkIntegrity()`
- `public void close()`


## Detailed Analysis: org.apache.lucene.codecs.DocValuesFormat -> org.gnit.lucenekmp.codecs.DocValuesFormat

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.codecs.FieldsProducer -> org.gnit.lucenekmp.codecs.FieldsProducer

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 96%


## Detailed Analysis: org.apache.lucene.codecs.KnnFieldVectorsWriter -> org.gnit.lucenekmp.codecs.KnnFieldVectorsWriter

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 88%


## Detailed Analysis: org.apache.lucene.codecs.KnnVectorsFormat -> org.gnit.lucenekmp.codecs.KnnVectorsFormat

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.codecs.KnnVectorsWriter fieldsWriter(org.apache.lucene.index.SegmentWriteState)`
- `public org.apache.lucene.codecs.KnnVectorsReader fieldsReader(org.apache.lucene.index.SegmentReadState)`
- `public int getMaxDimensions(java.lang.String)`
- `public static void reloadKnnVectorsFormat(java.lang.ClassLoader)`
- `public static org.apache.lucene.codecs.KnnVectorsFormat forName(java.lang.String)`
- `public static java.util.Set<java.lang.String> availableKnnVectorsFormats()`


## Detailed Analysis: org.apache.lucene.codecs.KnnVectorsWriter -> org.gnit.lucenekmp.codecs.KnnVectorsWriter

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.codecs.NormsConsumer -> org.gnit.lucenekmp.codecs.NormsConsumer

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 7
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.index.NumericDocValues getNorms(org.apache.lucene.index.FieldInfo)`
- `public void checkIntegrity()`
- `public void close()`


## Detailed Analysis: org.apache.lucene.codecs.PointsFormat -> org.gnit.lucenekmp.codecs.PointsFormat

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.codecs.PointsWriter fieldsWriter(org.apache.lucene.index.SegmentWriteState)`
- `public org.apache.lucene.codecs.PointsReader fieldsReader(org.apache.lucene.index.SegmentReadState)`


## Detailed Analysis: org.apache.lucene.codecs.PointsWriter -> org.gnit.lucenekmp.codecs.PointsWriter

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 8
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void close()`
- `public org.apache.lucene.index.PointValues getValues(java.lang.String)`
- `public void checkIntegrity()`


## Detailed Analysis: org.apache.lucene.codecs.PostingsFormat -> org.gnit.lucenekmp.codecs.PostingsFormat

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.codecs.PushPostingsWriterBase -> org.gnit.lucenekmp.codecs.PushPostingsWriterBase

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 10
- **KMP Property Accessors**: 13
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 59%


## Detailed Analysis: org.apache.lucene.codecs.StoredFieldsReader -> org.gnit.lucenekmp.codecs.StoredFieldsReader

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 0
- **Semantic Completion**: 96%


## Detailed Analysis: org.apache.lucene.codecs.StoredFieldsWriter -> org.gnit.lucenekmp.codecs.StoredFieldsWriter

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 7
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void binaryField(org.apache.lucene.index.FieldInfo, org.apache.lucene.index.StoredFieldDataInput)`
- `public void binaryField(org.apache.lucene.index.FieldInfo, byte[])`
- `public void stringField(org.apache.lucene.index.FieldInfo, java.lang.String)`
- `public void intField(org.apache.lucene.index.FieldInfo, int)`
- `public void longField(org.apache.lucene.index.FieldInfo, long)`
- `public void floatField(org.apache.lucene.index.FieldInfo, float)`
- `public void doubleField(org.apache.lucene.index.FieldInfo, double)`
- `public org.apache.lucene.index.StoredFieldVisitor$Status needsField(org.apache.lucene.index.FieldInfo)`
- `private org.apache.lucene.index.FieldInfo remap(org.apache.lucene.index.FieldInfo)`


## Detailed Analysis: org.apache.lucene.codecs.TermVectorsReader -> org.gnit.lucenekmp.codecs.TermVectorsReader

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 1
- **Semantic Completion**: 92%


## Detailed Analysis: org.apache.lucene.codecs.TermVectorsWriter -> org.gnit.lucenekmp.codecs.TermVectorsWriter

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 6
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 35%


## Detailed Analysis: org.apache.lucene.codecs.compressing.CompressionMode -> org.gnit.lucenekmp.codecs.compressing.CompressionMode

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.codecs.compressing.Compressor newCompressor()`
- `public org.apache.lucene.codecs.compressing.Decompressor newDecompressor()`


## Detailed Analysis: org.apache.lucene.codecs.compressing.Decompressor -> org.gnit.lucenekmp.codecs.compressing.Decompressor

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 0
- **Semantic Completion**: 85%


## Detailed Analysis: org.apache.lucene.codecs.compressing.MatchingReaders -> org.gnit.lucenekmp.codecs.compressing.MatchingReaders

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.codecs.hnsw.DefaultFlatVectorScorer -> org.gnit.lucenekmp.codecs.hnsw.DefaultFlatVectorScorer

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.codecs.hnsw.FlatVectorsReader -> org.gnit.lucenekmp.codecs.hnsw.FlatVectorsReader

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 9
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 3
- **Semantic Completion**: 91%


## Detailed Analysis: org.apache.lucene.codecs.lucene101.ForDeltaUtil -> org.gnit.lucenekmp.codecs.lucene101.ForDeltaUtil

### Method Categories:
- **Java Core Business Logic**: 19
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 16
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 14
- **Semantic Completion**: 85%

### Missing Core Methods:
- `package-private int bitsRequired(int[])`
- `package-private void encodeDeltas(int, int[], org.apache.lucene.store.DataOutput)`
- `package-private void decodeAndPrefixSum(int, org.apache.lucene.internal.vectorization.PostingDecodingUtil, int, int[])`


## Detailed Analysis: org.apache.lucene.codecs.lucene101.ForUtil -> org.gnit.lucenekmp.codecs.lucene101.ForUtil

### Method Categories:
- **Java Core Business Logic**: 30
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 28
- **KMP Property Accessors**: 41
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 53%

### Missing Core Methods:
- `package-private void encode(int[], int, org.apache.lucene.store.DataOutput)`
- `package-private void decode(int, org.apache.lucene.internal.vectorization.PostingDecodingUtil, int[])`


## Detailed Analysis: org.apache.lucene.codecs.lucene101.Lucene101PostingsFormat -> org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 2
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 20
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 2
- **Semantic Completion**: 13%


## Detailed Analysis: org.apache.lucene.codecs.lucene101.Lucene101PostingsReader -> org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsReader

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 7
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public int numLevels()`
- `public int getDocIdUpTo(int)`
- `public java.util.List<org.apache.lucene.index.Impact> getImpacts(int)`
- `private java.util.List<org.apache.lucene.index.Impact> readImpacts(org.apache.lucene.util.BytesRef, org.apache.lucene.codecs.lucene101.Lucene101PostingsReader$MutableImpactList)`


## Detailed Analysis: org.apache.lucene.codecs.lucene101.Lucene101PostingsWriter -> org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsWriter

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 83%

### Missing Core Methods:
- `public org.apache.lucene.codecs.lucene101.Lucene101PostingsFormat$IntBlockTermState newTermState()`
- `public void init(org.apache.lucene.store.IndexOutput, org.apache.lucene.index.SegmentWriteState)`
- `public void startTerm(org.apache.lucene.index.NumericDocValues)`
- `public void startDoc(int, int)`
- `public void addPosition(int, org.apache.lucene.util.BytesRef, int, int)`
- `public void finishDoc()`
- `private void flushDocBlock(boolean)`
- `private void writeLevel1SkipData()`
- `public void finishTerm(org.apache.lucene.codecs.BlockTermState)`
- `public void encodeTerm(org.apache.lucene.store.DataOutput, org.apache.lucene.index.FieldInfo, org.apache.lucene.codecs.BlockTermState, boolean)`
- `public void close()`
- `public org.apache.lucene.codecs.BlockTermState writeTerm(org.apache.lucene.util.BytesRef, org.apache.lucene.index.TermsEnum, org.apache.lucene.util.FixedBitSet, org.apache.lucene.codecs.NormsProducer)`


## Detailed Analysis: org.apache.lucene.codecs.lucene101.PForUtil -> org.gnit.lucenekmp.codecs.lucene101.PForUtil

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `package-private void encode(int[], org.apache.lucene.store.DataOutput)`
- `package-private void decode(org.apache.lucene.internal.vectorization.PostingDecodingUtil, int[])`


## Detailed Analysis: org.apache.lucene.codecs.lucene90.blocktree.CompressionAlgorithm -> org.gnit.lucenekmp.codecs.lucene90.blocktree.CompressionAlgorithm

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 66%

### Missing Core Methods:
- `package-private static org.apache.lucene.codecs.lucene90.blocktree.CompressionAlgorithm byCode(int)`


## Detailed Analysis: org.apache.lucene.codecs.lucene90.blocktree.FieldReader -> org.gnit.lucenekmp.codecs.lucene90.blocktree.FieldReader

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 8
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `package-private long readVLongOutput(org.apache.lucene.store.DataInput)`
- `public boolean hasFreqs()`
- `public boolean hasOffsets()`
- `public boolean hasPositions()`
- `public boolean hasPayloads()`
- `public org.apache.lucene.index.TermsEnum intersect(org.apache.lucene.util.automaton.CompiledAutomaton, org.apache.lucene.util.BytesRef)`
- `public static org.apache.lucene.index.Terms getTerms(org.apache.lucene.index.LeafReader, java.lang.String)`


## Detailed Analysis: org.apache.lucene.codecs.lucene90.blocktree.IntersectTermsEnum -> org.gnit.lucenekmp.codecs.lucene90.blocktree.IntersectTermsEnum

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public java.lang.Throwable fillInStackTrace()`


## Detailed Analysis: org.apache.lucene.codecs.lucene90.blocktree.IntersectTermsEnumFrame -> org.gnit.lucenekmp.codecs.lucene90.blocktree.IntersectTermsEnumFrame

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 7
- **KMP Property Accessors**: 60
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 20%


## Detailed Analysis: org.apache.lucene.codecs.lucene90.blocktree.Lucene90BlockTreeTermsReader -> org.gnit.lucenekmp.codecs.lucene90.blocktree.Lucene90BlockTreeTermsReader

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 55%

### Missing Core Methods:
- `public void close()`
- `public org.apache.lucene.index.Terms terms(java.lang.String)`
- `public void checkIntegrity()`


## Detailed Analysis: org.apache.lucene.codecs.lucene90.blocktree.Lucene90BlockTreeTermsWriter -> org.gnit.lucenekmp.codecs.lucene90.blocktree.Lucene90BlockTreeTermsWriter

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void compileIndex(java.util.List<org.apache.lucene.codecs.lucene90.blocktree.Lucene90BlockTreeTermsWriter$PendingBlock>, org.apache.lucene.store.ByteBuffersDataOutput, org.apache.lucene.util.IntsRefBuilder)`
- `private void append(org.apache.lucene.util.fst.FSTCompiler<org.apache.lucene.util.BytesRef>, org.apache.lucene.util.fst.FST<org.apache.lucene.util.BytesRef>, org.apache.lucene.util.IntsRefBuilder)`


## Detailed Analysis: org.apache.lucene.codecs.lucene90.blocktree.SegmentTermsEnum -> org.gnit.lucenekmp.codecs.lucene90.blocktree.SegmentTermsEnum

### Method Categories:
- **Java Core Business Logic**: 24
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 24
- **KMP Property Accessors**: 11
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 1
- **Semantic Completion**: 80%


## Detailed Analysis: org.apache.lucene.codecs.lucene90.blocktree.SegmentTermsEnumFrame -> org.gnit.lucenekmp.codecs.lucene90.blocktree.SegmentTermsEnumFrame

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 15
- **KMP Property Accessors**: 60
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 32%


## Detailed Analysis: org.apache.lucene.codecs.lucene90.blocktree.Stats -> org.gnit.lucenekmp.codecs.lucene90.blocktree.Stats

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 1
- **Semantic Completion**: 14%

### Missing Core Methods:
- `package-private void startBlock(org.apache.lucene.codecs.lucene90.blocktree.SegmentTermsEnumFrame, boolean)`
- `package-private void endBlock(org.apache.lucene.codecs.lucene90.blocktree.SegmentTermsEnumFrame)`
- `package-private void term(org.apache.lucene.util.BytesRef)`
- `package-private void finish()`


## Detailed Analysis: org.apache.lucene.codecs.lucene90.compressing.FieldsIndex -> org.gnit.lucenekmp.codecs.lucene90.compressing.FieldsIndex

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 0
- **Semantic Completion**: 96%


## Detailed Analysis: org.apache.lucene.codecs.lucene90.compressing.FieldsIndexReader -> org.gnit.lucenekmp.codecs.lucene90.compressing.FieldsIndexReader

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public void close()`
- `package-private long getBlockID(int)`
- `package-private long getBlockStartPointer(long)`
- `package-private long getBlockLength(long)`
- `package-private void checkIntegrity()`
- `package-private long getStartPointer(int)`


## Detailed Analysis: org.apache.lucene.codecs.lucene90.compressing.FieldsIndexWriter -> org.gnit.lucenekmp.codecs.lucene90.compressing.FieldsIndexWriter

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `package-private void writeIndex(int, long)`
- `package-private void finish(int, long, org.apache.lucene.store.IndexOutput)`
- `public void close()`


## Detailed Analysis: org.apache.lucene.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsReader -> org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsReader

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter -> org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public int nextDoc()`
- `public int nextMappedDoc()`


## Detailed Analysis: org.apache.lucene.codecs.lucene90.compressing.Lucene90CompressingTermVectorsReader -> org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsReader

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public long startPointer()`
- `public int docBase()`
- `public int chunkDocs()`


## Detailed Analysis: org.apache.lucene.codecs.lucene90.compressing.Lucene90CompressingTermVectorsWriter -> org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsWriter

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 16
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public int nextDoc()`
- `public int nextMappedDoc()`


## Detailed Analysis: org.apache.lucene.codecs.perfield.PerFieldKnnVectorsFormat -> org.gnit.lucenekmp.codecs.perfield.PerFieldKnnVectorsFormat

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 45%

### Missing Core Methods:
- `public org.apache.lucene.codecs.KnnFieldVectorsWriter<?> addField(org.apache.lucene.index.FieldInfo)`
- `public void flush(int, org.apache.lucene.index.Sorter$DocMap)`
- `public void mergeOneField(org.apache.lucene.index.FieldInfo, org.apache.lucene.index.MergeState)`
- `public void finish()`
- `private org.apache.lucene.codecs.KnnVectorsWriter getInstance(org.apache.lucene.index.FieldInfo)`
- `public long ramBytesUsed()`
- `public void merge(org.apache.lucene.index.MergeState)`
- `private void finishMerge(org.apache.lucene.index.MergeState)`
- `public static void mapOldOrdToNewOrd(org.apache.lucene.index.DocsWithFieldSet, org.apache.lucene.index.Sorter$DocMap, int[], int[], org.apache.lucene.index.DocsWithFieldSet)`


## Detailed Analysis: org.apache.lucene.document.DocValuesLongHashSet -> org.gnit.lucenekmp.document.DocValuesLongHashSet

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 3
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `private boolean add(long)`
- `package-private boolean contains(long)`
- `public long ramBytesUsed()`


## Detailed Analysis: org.apache.lucene.document.Field -> org.gnit.lucenekmp.document.Field

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 13
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 19
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 3
- **KMP Synthetic**: 3
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public java.lang.String stringValue()`
- `public java.io.Reader readerValue()`
- `public org.apache.lucene.analysis.TokenStream tokenStreamValue()`
- `public java.lang.Number numericValue()`
- `public org.apache.lucene.util.BytesRef binaryValue()`
- `public org.apache.lucene.index.IndexableFieldType fieldType()`
- `public org.apache.lucene.document.InvertableType invertableType()`
- `public org.apache.lucene.analysis.TokenStream tokenStream(org.apache.lucene.analysis.Analyzer, org.apache.lucene.analysis.TokenStream)`
- `public org.apache.lucene.document.StoredValue storedValue()`


## Detailed Analysis: org.apache.lucene.document.IntPoint -> org.gnit.lucenekmp.document.IntPoint

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 5
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public static void checkArgs(java.lang.String, java.lang.Object, java.lang.Object)`
- `public void visit(org.apache.lucene.search.QueryVisitor)`
- `public org.apache.lucene.search.Weight createWeight(org.apache.lucene.search.IndexSearcher, org.apache.lucene.search.ScoreMode, float)`
- `private boolean equalsTo(org.apache.lucene.search.PointRangeQuery)`
- `public org.apache.lucene.search.Query rewrite(org.apache.lucene.search.IndexSearcher)`
- `protected boolean sameClassAs(java.lang.Object)`
- `protected int classHash()`


## Detailed Analysis: org.apache.lucene.document.InvertableType -> org.gnit.lucenekmp.document.InvertableType

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 75%


## Detailed Analysis: org.apache.lucene.document.KnnByteVectorField -> org.gnit.lucenekmp.document.KnnByteVectorField

### Method Categories:
- **Java Core Business Logic**: 13
- **Java Property Accessors**: 14
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 93%

### Missing Core Methods:
- `public byte[] vectorValue()`
- `public java.lang.String stringValue()`
- `public java.io.Reader readerValue()`
- `public org.apache.lucene.analysis.TokenStream tokenStreamValue()`
- `public java.lang.Number numericValue()`
- `public org.apache.lucene.util.BytesRef binaryValue()`
- `public org.apache.lucene.index.IndexableFieldType fieldType()`
- `public org.apache.lucene.document.InvertableType invertableType()`
- `public org.apache.lucene.analysis.TokenStream tokenStream(org.apache.lucene.analysis.Analyzer, org.apache.lucene.analysis.TokenStream)`
- `public org.apache.lucene.document.StoredValue storedValue()`


## Detailed Analysis: org.apache.lucene.document.KnnFloatVectorField -> org.gnit.lucenekmp.document.KnnFloatVectorField

### Method Categories:
- **Java Core Business Logic**: 13
- **Java Property Accessors**: 14
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 93%

### Missing Core Methods:
- `public float[] vectorValue()`
- `public java.lang.String stringValue()`
- `public java.io.Reader readerValue()`
- `public org.apache.lucene.analysis.TokenStream tokenStreamValue()`
- `public java.lang.Number numericValue()`
- `public org.apache.lucene.util.BytesRef binaryValue()`
- `public org.apache.lucene.index.IndexableFieldType fieldType()`
- `public org.apache.lucene.document.InvertableType invertableType()`
- `public org.apache.lucene.analysis.TokenStream tokenStream(org.apache.lucene.analysis.Analyzer, org.apache.lucene.analysis.TokenStream)`
- `public org.apache.lucene.document.StoredValue storedValue()`


## Detailed Analysis: org.apache.lucene.document.NumericDocValuesField -> org.gnit.lucenekmp.document.NumericDocValuesField

### Method Categories:
- **Java Core Business Logic**: 13
- **Java Property Accessors**: 13
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 86%

### Missing Core Methods:
- `public java.lang.String stringValue()`
- `public java.io.Reader readerValue()`
- `public org.apache.lucene.analysis.TokenStream tokenStreamValue()`
- `public java.lang.Number numericValue()`
- `public org.apache.lucene.util.BytesRef binaryValue()`
- `public org.apache.lucene.index.IndexableFieldType fieldType()`
- `public org.apache.lucene.document.InvertableType invertableType()`
- `public org.apache.lucene.analysis.TokenStream tokenStream(org.apache.lucene.analysis.Analyzer, org.apache.lucene.analysis.TokenStream)`
- `public org.apache.lucene.document.StoredValue storedValue()`


## Detailed Analysis: org.apache.lucene.document.SortedNumericDocValuesRangeQuery -> org.gnit.lucenekmp.document.SortedNumericDocValuesRangeQuery

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public boolean isCacheable(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.ScorerSupplier scorerSupplier(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.Explanation explain(org.apache.lucene.index.LeafReaderContext, int)`
- `public org.apache.lucene.search.Matches matches(org.apache.lucene.index.LeafReaderContext, int)`
- `public org.apache.lucene.search.Scorer scorer(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.BulkScorer bulkScorer(org.apache.lucene.index.LeafReaderContext)`
- `public int count(org.apache.lucene.index.LeafReaderContext)`


## Detailed Analysis: org.apache.lucene.document.SortedNumericDocValuesSetQuery -> org.gnit.lucenekmp.document.SortedNumericDocValuesSetQuery

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public boolean isCacheable(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.ScorerSupplier scorerSupplier(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.Explanation explain(org.apache.lucene.index.LeafReaderContext, int)`
- `public org.apache.lucene.search.Matches matches(org.apache.lucene.index.LeafReaderContext, int)`
- `public org.apache.lucene.search.Scorer scorer(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.BulkScorer bulkScorer(org.apache.lucene.index.LeafReaderContext)`
- `public int count(org.apache.lucene.index.LeafReaderContext)`


## Detailed Analysis: org.apache.lucene.document.StoredValue -> org.gnit.lucenekmp.document.StoredValue

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 75%


## Detailed Analysis: org.apache.lucene.index.ApproximatePriorityQueue -> org.gnit.lucenekmp.index.ApproximatePriorityQueue

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 34%

### Missing Core Methods:
- `package-private T poll(java.util.function.Predicate<T>)`
- `package-private boolean contains(java.lang.Object)`
- `package-private boolean remove(java.lang.Object)`


## Detailed Analysis: org.apache.lucene.index.AutomatonTermsEnum -> org.gnit.lucenekmp.index.AutomatonTermsEnum

### Method Categories:
- **Java Core Business Logic**: 17
- **Java Property Accessors**: 7
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 17
- **KMP Property Accessors**: 10
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 91%


## Detailed Analysis: org.apache.lucene.index.BaseCompositeReader -> org.gnit.lucenekmp.index.BaseCompositeReader

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 93%


## Detailed Analysis: org.apache.lucene.index.BinaryDocValues -> org.gnit.lucenekmp.index.BinaryDocValues

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 7
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.index.BinaryDocValuesFieldUpdates -> org.gnit.lucenekmp.index.BinaryDocValuesFieldUpdates

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 11
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `package-private static org.apache.lucene.index.BinaryDocValues asBinaryDocValues(org.apache.lucene.index.DocValuesFieldUpdates$Iterator)`
- `package-private static org.apache.lucene.index.NumericDocValues asNumericDocValues(org.apache.lucene.index.DocValuesFieldUpdates$Iterator)`
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.index.BufferedUpdates -> org.gnit.lucenekmp.index.BufferedUpdates

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `private java.util.Set<org.apache.lucene.util.BytesRef> keySet()`
- `private boolean put(org.apache.lucene.util.BytesRef, int)`
- `private int get(org.apache.lucene.util.BytesRef)`


## Detailed Analysis: org.apache.lucene.index.BufferedUpdatesStream -> org.gnit.lucenekmp.index.BufferedUpdatesStream

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `package-private void clear()`
- `package-private boolean stillRunning(long)`
- `package-private void finishedSegment(long)`


## Detailed Analysis: org.apache.lucene.index.ByteSlicePool -> org.gnit.lucenekmp.index.ByteSlicePool

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public int newSlice(int)`
- `public int allocSlice(byte[], int)`
- `public int allocKnownSizeSlice(byte[], int)`


## Detailed Analysis: org.apache.lucene.index.ByteSliceReader -> org.gnit.lucenekmp.index.ByteSliceReader

### Method Categories:
- **Java Core Business Logic**: 21
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 21
- **KMP Property Accessors**: 17
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 1
- **Semantic Completion**: 69%


## Detailed Analysis: org.apache.lucene.index.ByteVectorValues -> org.gnit.lucenekmp.index.ByteVectorValues

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 1
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static void checkField(org.apache.lucene.index.LeafReader, java.lang.String)`
- `public static org.apache.lucene.index.ByteVectorValues fromBytes(java.util.List<byte[]>, int)`
- `protected static org.apache.lucene.index.KnnVectorValues$DocIndexIterator fromDISI(org.apache.lucene.search.DocIdSetIterator)`


## Detailed Analysis: org.apache.lucene.index.CachingMergeContext -> org.gnit.lucenekmp.index.CachingMergeContext

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 72%


## Detailed Analysis: org.apache.lucene.index.CheckIndex -> org.gnit.lucenekmp.index.CheckIndex

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.index.CompositeReader -> org.gnit.lucenekmp.index.CompositeReader

### Method Categories:
- **Java Core Business Logic**: 20
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 3
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 20
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 3
- **KMP Synthetic**: 4
- **Semantic Completion**: 89%

### Missing Core Methods:
- `protected void notifyReaderClosedListeners()`
- `public void decRef()`


## Detailed Analysis: org.apache.lucene.index.CompositeReaderContext -> org.gnit.lucenekmp.index.CompositeReaderContext

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `private org.apache.lucene.index.IndexReaderContext build(org.apache.lucene.index.CompositeReaderContext, org.apache.lucene.index.IndexReader, int, int)`


## Detailed Analysis: org.apache.lucene.index.ConcurrentApproximatePriorityQueue -> org.gnit.lucenekmp.index.ConcurrentApproximatePriorityQueue

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 75%

### Missing Core Methods:
- `package-private void add(T, long)`
- `package-private T poll(java.util.function.Predicate<T>)`
- `package-private boolean contains(java.lang.Object)`
- `package-private boolean remove(java.lang.Object)`


## Detailed Analysis: org.apache.lucene.index.ConcurrentMergeScheduler -> org.gnit.lucenekmp.index.ConcurrentMergeScheduler

### Method Categories:
- **Java Core Business Logic**: 16
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.store.IndexOutput createOutput(java.lang.String, org.apache.lucene.store.IOContext)`
- `public static org.apache.lucene.store.Directory unwrap(org.apache.lucene.store.Directory)`
- `public java.lang.String[] listAll()`
- `public void deleteFile(java.lang.String)`
- `public long fileLength(java.lang.String)`
- `public org.apache.lucene.store.IndexOutput createTempOutput(java.lang.String, java.lang.String, org.apache.lucene.store.IOContext)`
- `public void sync(java.util.Collection<java.lang.String>)`
- `public void rename(java.lang.String, java.lang.String)`
- `public void syncMetaData()`
- `public org.apache.lucene.store.IndexInput openInput(java.lang.String, org.apache.lucene.store.IOContext)`
- `public org.apache.lucene.store.Lock obtainLock(java.lang.String)`
- `public void close()`
- `protected void ensureOpen()`
- `public org.apache.lucene.store.ChecksumIndexInput openChecksumInput(java.lang.String)`
- `public void copyFrom(org.apache.lucene.store.Directory, java.lang.String, java.lang.String, org.apache.lucene.store.IOContext)`
- `protected static java.lang.String getTempFileName(java.lang.String, java.lang.String, long)`


## Detailed Analysis: org.apache.lucene.index.CorruptIndexException -> org.gnit.lucenekmp.index.CorruptIndexException

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.index.DirectoryReader -> org.gnit.lucenekmp.index.DirectoryReader

### Method Categories:
- **Java Core Business Logic**: 37
- **Java Property Accessors**: 9
- **Java Auto-Generated**: 3
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 13
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 89%

### Missing Core Methods:
- `protected org.apache.lucene.index.DirectoryReader doOpenIfChanged()`
- `protected org.apache.lucene.index.DirectoryReader doOpenIfChanged(org.apache.lucene.index.IndexCommit)`
- `protected org.apache.lucene.index.DirectoryReader doOpenIfChanged(org.apache.lucene.index.IndexWriter, boolean)`
- `public org.apache.lucene.index.TermVectors termVectors()`
- `public int numDocs()`
- `public int maxDoc()`
- `public org.apache.lucene.index.StoredFields storedFields()`
- `public int docFreq(org.apache.lucene.index.Term)`
- `public long totalTermFreq(org.apache.lucene.index.Term)`
- `public long getSumDocFreq(java.lang.String)`
- `public int getDocCount(java.lang.String)`
- `public long getSumTotalTermFreq(java.lang.String)`
- `protected int readerIndex(int)`
- `protected int readerBase(int)`
- `public void registerParentReader(org.apache.lucene.index.IndexReader)`
- `protected void notifyReaderClosedListeners()`
- `private void reportCloseToParentReaders()`
- `public void incRef()`
- `public boolean tryIncRef()`
- `public void decRef()`
- `protected void ensureOpen()`
- `public int numDeletedDocs()`
- `public boolean hasDeletions()`
- `public void close()`
- `protected void doClose()`


## Detailed Analysis: org.apache.lucene.index.DocIDMerger -> org.gnit.lucenekmp.index.DocIDMerger

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void reset()`
- `public static org.apache.lucene.index.DocIDMerger<T> of(java.util.List<T>, int, boolean)`
- `public static org.apache.lucene.index.DocIDMerger<T> of(java.util.List<T>, boolean)`


## Detailed Analysis: org.apache.lucene.index.DocValues -> org.gnit.lucenekmp.index.DocValues

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 11
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 54%

### Missing Core Methods:
- `public org.apache.lucene.util.BytesRef binaryValue()`
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.index.DocValuesFieldUpdates -> org.gnit.lucenekmp.index.DocValuesFieldUpdates

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 21
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `protected boolean lessThan(org.apache.lucene.index.DocValuesFieldUpdates$Iterator, org.apache.lucene.index.DocValuesFieldUpdates$Iterator)`
- `public void addAll(java.util.Collection<T>)`
- `public T add(T)`
- `public T insertWithOverflow(T)`
- `public T updateTop()`
- `public T updateTop(T)`
- `public void clear()`
- `public boolean remove(T)`
- `private boolean upHeap(int)`
- `private void downHeap(int)`


## Detailed Analysis: org.apache.lucene.index.DocValuesIterator -> org.gnit.lucenekmp.index.DocValuesIterator

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.index.DocValuesLeafReader -> org.gnit.lucenekmp.index.DocValuesLeafReader

### Method Categories:
- **Java Core Business Logic**: 38
- **Java Property Accessors**: 8
- **Java Auto-Generated**: 2
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 42
- **KMP Property Accessors**: 8
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 4
- **Semantic Completion**: 86%

### Missing Core Methods:
- `protected void notifyReaderClosedListeners()`
- `public void decRef()`


## Detailed Analysis: org.apache.lucene.index.DocValuesSkipIndexType -> org.gnit.lucenekmp.index.DocValuesSkipIndexType

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 82%


## Detailed Analysis: org.apache.lucene.index.DocValuesType -> org.gnit.lucenekmp.index.DocValuesType

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 75%


## Detailed Analysis: org.apache.lucene.index.DocValuesUpdate -> org.gnit.lucenekmp.index.DocValuesUpdate

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `package-private long valueSizeInBytes()`
- `package-private long sizeInBytes()`
- `protected java.lang.String valueToString()`
- `package-private void writeTo(org.apache.lucene.store.DataOutput)`
- `package-private boolean hasValue()`


## Detailed Analysis: org.apache.lucene.index.DocsWithFieldSet -> org.gnit.lucenekmp.index.DocsWithFieldSet

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public void add(int)`
- `public long ramBytesUsed()`


## Detailed Analysis: org.apache.lucene.index.DocumentsWriter -> org.gnit.lucenekmp.index.DocumentsWriter

### Method Categories:
- **Java Core Business Logic**: 27
- **Java Property Accessors**: 6
- **Java Auto-Generated**: 0
- **Java Synthetic**: 12
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `package-private long deleteQueries(org.apache.lucene.search.Query[])`
- `package-private long deleteTerms(org.apache.lucene.index.Term[])`
- `package-private long updateDocValues(org.apache.lucene.index.DocValuesUpdate[])`
- `private long applyDeleteOrUpdate(java.util.function.ToLongFunction<org.apache.lucene.index.DocumentsWriterDeleteQueue>)`
- `private boolean applyAllDeletes()`
- `package-private void purgeFlushTickets(boolean, org.apache.lucene.util.IOConsumer<org.apache.lucene.index.DocumentsWriterFlushQueue$FlushTicket>)`
- `private void ensureOpen()`
- `package-private void abort()`
- `package-private boolean flushOneDWPT()`
- `package-private java.io.Closeable lockAndAbortAll()`
- `private void abortDocumentsWriterPerThread(org.apache.lucene.index.DocumentsWriterPerThread)`
- `package-private boolean anyChanges()`
- `package-private boolean anyDeletions()`
- `public void close()`
- `private boolean preUpdate()`
- `private boolean postUpdate(org.apache.lucene.index.DocumentsWriterPerThread, boolean)`
- `package-private long updateDocuments(java.lang.Iterable<? extends java.lang.Iterable<? extends org.apache.lucene.index.IndexableField>>, org.apache.lucene.index.DocumentsWriterDeleteQueue$Node<?>)`
- `private boolean maybeFlush()`
- `private void doFlush(org.apache.lucene.index.DocumentsWriterPerThread)`
- `package-private long resetDeleteQueue(int)`
- `package-private void subtractFlushedNumDocs(int)`
- `private boolean setFlushingDeleteQueue(org.apache.lucene.index.DocumentsWriterDeleteQueue)`
- `private boolean assertTicketQueueModification(org.apache.lucene.index.DocumentsWriterDeleteQueue)`
- `package-private long flushAllThreads()`
- `private org.apache.lucene.index.DocumentsWriterFlushQueue$FlushTicket maybeFreezeGlobalBuffer(org.apache.lucene.index.DocumentsWriterDeleteQueue)`
- `package-private void finishFullFlush(boolean)`
- `public long ramBytesUsed()`


## Detailed Analysis: org.apache.lucene.index.DocumentsWriterDeleteQueue -> org.gnit.lucenekmp.index.DocumentsWriterDeleteQueue

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.index.DocumentsWriterFlushControl -> org.gnit.lucenekmp.index.DocumentsWriterFlushControl

### Method Categories:
- **Java Core Business Logic**: 37
- **Java Property Accessors**: 10
- **Java Auto-Generated**: 1
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public long activeBytes()`
- `package-private long netBytes()`
- `private long stallLimitBytes()`
- `private boolean assertMemory()`
- `private boolean updatePeaks(long)`
- `private long ramBufferGranularity()`
- `package-private org.apache.lucene.index.DocumentsWriterPerThread doAfterDocument(org.apache.lucene.index.DocumentsWriterPerThread)`
- `private org.apache.lucene.index.DocumentsWriterPerThread checkout(org.apache.lucene.index.DocumentsWriterPerThread, boolean)`
- `private boolean assertNumDocsSinceStalled(boolean)`
- `package-private void doAfterFlush(org.apache.lucene.index.DocumentsWriterPerThread)`
- `private boolean updateStallState()`
- `public void waitForFlush()`
- `package-private void doOnAbort(org.apache.lucene.index.DocumentsWriterPerThread)`
- `private void checkoutAndBlock(org.apache.lucene.index.DocumentsWriterPerThread)`
- `private org.apache.lucene.index.DocumentsWriterPerThread checkOutForFlush(org.apache.lucene.index.DocumentsWriterPerThread)`
- `private void addFlushingDWPT(org.apache.lucene.index.DocumentsWriterPerThread)`
- `package-private org.apache.lucene.index.DocumentsWriterPerThread nextPendingFlush()`
- `public void close()`
- `public java.util.Iterator<org.apache.lucene.index.DocumentsWriterPerThread> allActiveWriters()`
- `package-private void doOnDelete()`
- `public long ramBytesUsed()`
- `package-private int numFlushingDWPT()`
- `public void setApplyAllDeletes()`
- `package-private org.apache.lucene.index.DocumentsWriterPerThread obtainAndLock()`
- `package-private long markForFullFlush()`
- `private boolean assertActiveDeleteQueue(org.apache.lucene.index.DocumentsWriterDeleteQueue)`
- `private void pruneBlockedQueue(org.apache.lucene.index.DocumentsWriterDeleteQueue)`
- `package-private void finishFullFlush()`
- `package-private boolean assertBlockedFlushes(org.apache.lucene.index.DocumentsWriterDeleteQueue)`
- `package-private void abortFullFlushes()`
- `package-private void abortPendingFlushes()`
- `package-private int numQueuedFlushes()`
- `package-private int numBlockedFlushes()`
- `package-private void waitIfStalled()`
- `package-private boolean anyStalledThreads()`
- `package-private org.apache.lucene.index.DocumentsWriterPerThread findLargestNonPendingWriter()`
- `package-private org.apache.lucene.index.DocumentsWriterPerThread checkoutLargestNonPendingWriter()`


## Detailed Analysis: org.apache.lucene.index.DocumentsWriterFlushQueue -> org.gnit.lucenekmp.index.DocumentsWriterFlushQueue

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `package-private org.apache.lucene.index.DocumentsWriterFlushQueue$FlushTicket addTicket(java.util.function.Supplier<org.apache.lucene.index.DocumentsWriterFlushQueue$FlushTicket>)`
- `private void incTickets()`
- `private void decTickets()`
- `package-private void addSegment(org.apache.lucene.index.DocumentsWriterFlushQueue$FlushTicket, org.apache.lucene.index.DocumentsWriterPerThread$FlushedSegment)`
- `package-private void markTicketFailed(org.apache.lucene.index.DocumentsWriterFlushQueue$FlushTicket)`
- `package-private boolean hasTickets()`
- `private void innerPurge(org.apache.lucene.util.IOConsumer<org.apache.lucene.index.DocumentsWriterFlushQueue$FlushTicket>)`
- `package-private void forcePurge(org.apache.lucene.util.IOConsumer<org.apache.lucene.index.DocumentsWriterFlushQueue$FlushTicket>)`
- `package-private void tryPurge(org.apache.lucene.util.IOConsumer<org.apache.lucene.index.DocumentsWriterFlushQueue$FlushTicket>)`


## Detailed Analysis: org.apache.lucene.index.DocumentsWriterPerThread -> org.gnit.lucenekmp.index.DocumentsWriterPerThread

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 45%


## Detailed Analysis: org.apache.lucene.index.DocumentsWriterPerThreadPool -> org.gnit.lucenekmp.index.DocumentsWriterPerThreadPool

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `package-private void lockNewWriters()`
- `package-private void unlockNewWriters()`
- `private org.apache.lucene.index.DocumentsWriterPerThread newWriter()`
- `private void ensureOpen()`
- `private boolean contains(org.apache.lucene.index.DocumentsWriterPerThread)`
- `package-private void marksAsFreeAndUnlock(org.apache.lucene.index.DocumentsWriterPerThread)`
- `package-private java.util.List<org.apache.lucene.index.DocumentsWriterPerThread> filterAndLock(java.util.function.Predicate<org.apache.lucene.index.DocumentsWriterPerThread>)`
- `package-private boolean checkout(org.apache.lucene.index.DocumentsWriterPerThread)`
- `package-private boolean isRegistered(org.apache.lucene.index.DocumentsWriterPerThread)`
- `public void close()`


## Detailed Analysis: org.apache.lucene.index.DocumentsWriterStallControl -> org.gnit.lucenekmp.index.DocumentsWriterStallControl

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 7
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `package-private boolean isThreadQueued(java.lang.Thread)`


## Detailed Analysis: org.apache.lucene.index.FieldInfo -> org.gnit.lucenekmp.index.FieldInfo

### Method Categories:
- **Java Core Business Logic**: 21
- **Java Property Accessors**: 16
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 7
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public void checkConsistency()`
- `package-private void verifySameSchema(org.apache.lucene.index.FieldInfo)`
- `public void setPointDimensions(int, int, int)`
- `public org.apache.lucene.index.DocValuesSkipIndexType docValuesSkipIndexType()`
- `package-private void setStoreTermVectors()`
- `package-private void setStorePayloads()`
- `public boolean omitsNorms()`
- `public void setOmitsNorms()`
- `public boolean hasNorms()`
- `public boolean hasPayloads()`
- `public boolean hasTermVectors()`
- `public boolean hasVectorValues()`
- `public java.lang.String getAttribute(java.lang.String)`
- `public java.lang.String putAttribute(java.lang.String, java.lang.String)`


## Detailed Analysis: org.apache.lucene.index.FieldInfos -> org.gnit.lucenekmp.index.FieldInfos

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 6
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.index.FieldInfo add(org.apache.lucene.index.FieldInfo)`
- `package-private org.apache.lucene.index.FieldInfo add(org.apache.lucene.index.FieldInfo, long)`
- `public org.apache.lucene.index.FieldInfo fieldInfo(java.lang.String)`
- `private boolean assertNotFinished()`


## Detailed Analysis: org.apache.lucene.index.FieldInvertState -> org.gnit.lucenekmp.index.FieldInvertState

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 13
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 31
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 44%


## Detailed Analysis: org.apache.lucene.index.FieldUpdatesBuffer -> org.gnit.lucenekmp.index.FieldUpdatesBuffer

### Method Categories:
- **Java Core Business Logic**: 11
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 83%

### Missing Core Methods:
- `package-private void add(java.lang.String, int, int, boolean)`
- `package-private void addUpdate(org.apache.lucene.index.Term, long, int)`
- `package-private void addNoValue(org.apache.lucene.index.Term, int)`
- `package-private void addUpdate(org.apache.lucene.index.Term, org.apache.lucene.util.BytesRef, int)`
- `private int append(org.apache.lucene.index.Term)`
- `package-private void finish()`
- `private boolean assertTermAndDocInOrder()`
- `package-private boolean hasSingleValue()`
- `package-private long getNumericValue(int)`


## Detailed Analysis: org.apache.lucene.index.Fields -> org.gnit.lucenekmp.index.Fields

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.index.Terms terms(java.lang.String)`


## Detailed Analysis: org.apache.lucene.index.FilterCodecReader -> org.gnit.lucenekmp.index.FilterCodecReader

### Method Categories:
- **Java Core Business Logic**: 41
- **Java Property Accessors**: 16
- **Java Auto-Generated**: 2
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 43
- **KMP Property Accessors**: 17
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 4
- **Semantic Completion**: 86%

### Missing Core Methods:
- `public static org.apache.lucene.index.CodecReader unwrap(org.apache.lucene.index.CodecReader)`
- `package-private static org.apache.lucene.index.FilterCodecReader wrapLiveDocs(org.apache.lucene.index.CodecReader, org.apache.lucene.util.Bits, int)`
- `protected void notifyReaderClosedListeners()`
- `public void decRef()`


## Detailed Analysis: org.apache.lucene.index.FilterLeafReader -> org.gnit.lucenekmp.index.FilterLeafReader

### Method Categories:
- **Java Core Business Logic**: 11
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 11
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 94%


## Detailed Analysis: org.apache.lucene.index.FilterMergePolicy -> org.gnit.lucenekmp.index.FilterMergePolicy

### Method Categories:
- **Java Core Business Logic**: 16
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 1
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 16
- **KMP Property Accessors**: 8
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 1
- **Semantic Completion**: 86%

### Missing Core Methods:
- `public static T unwrapAll(T)`


## Detailed Analysis: org.apache.lucene.index.FilteredTermsEnum -> org.gnit.lucenekmp.index.FilteredTermsEnum

### Method Categories:
- **Java Core Business Logic**: 13
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `protected org.apache.lucene.index.FilteredTermsEnum$AcceptStatus accept(org.apache.lucene.util.BytesRef)`
- `protected org.apache.lucene.util.BytesRef nextSeekTerm(org.apache.lucene.util.BytesRef)`
- `public int docFreq()`
- `public long totalTermFreq()`
- `public boolean seekExact(org.apache.lucene.util.BytesRef)`
- `public org.apache.lucene.util.IOBooleanSupplier prepareSeekExact(org.apache.lucene.util.BytesRef)`
- `public org.apache.lucene.index.TermsEnum$SeekStatus seekCeil(org.apache.lucene.util.BytesRef)`
- `public void seekExact(long)`
- `public org.apache.lucene.index.PostingsEnum postings(org.apache.lucene.index.PostingsEnum, int)`
- `public org.apache.lucene.index.ImpactsEnum impacts(int)`
- `public void seekExact(org.apache.lucene.util.BytesRef, org.apache.lucene.index.TermState)`
- `public org.apache.lucene.index.TermState termState()`
- `public org.apache.lucene.index.PostingsEnum postings(org.apache.lucene.index.PostingsEnum)`


## Detailed Analysis: org.apache.lucene.index.FloatVectorValues -> org.gnit.lucenekmp.index.FloatVectorValues

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 1
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static void checkField(org.apache.lucene.index.LeafReader, java.lang.String)`
- `public static org.apache.lucene.index.FloatVectorValues fromFloats(java.util.List<float[]>, int)`
- `protected static org.apache.lucene.index.KnnVectorValues$DocIndexIterator fromDISI(org.apache.lucene.search.DocIdSetIterator)`


## Detailed Analysis: org.apache.lucene.index.FlushByRamOrCountsPolicy -> org.gnit.lucenekmp.index.FlushByRamOrCountsPolicy

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 9
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 78%


## Detailed Analysis: org.apache.lucene.index.FlushPolicy -> org.gnit.lucenekmp.index.FlushPolicy

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 62%


## Detailed Analysis: org.apache.lucene.index.FreqProxFields -> org.gnit.lucenekmp.index.FreqProxFields

### Method Categories:
- **Java Core Business Logic**: 12
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void reset(int)`
- `public int docID()`
- `public int nextPosition()`
- `public int startOffset()`
- `public int endOffset()`
- `public int nextDoc()`
- `public int advance(int)`
- `public static boolean featureRequested(int, short)`
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`
- `protected int slowAdvance(int)`
- `public void intoBitSet(int, org.apache.lucene.util.FixedBitSet, int)`


## Detailed Analysis: org.apache.lucene.index.FreqProxTermsWriter -> org.gnit.lucenekmp.index.FreqProxTermsWriter

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 73%


## Detailed Analysis: org.apache.lucene.index.FreqProxTermsWriterPerField -> org.gnit.lucenekmp.index.FreqProxTermsWriterPerField

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 15
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 30%


## Detailed Analysis: org.apache.lucene.index.FrozenBufferedUpdates -> org.gnit.lucenekmp.index.FrozenBufferedUpdates

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.index.Impact -> org.gnit.lucenekmp.index.Impact

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 3
- **KMP Synthetic**: 0
- **Semantic Completion**: 20%


## Detailed Analysis: org.apache.lucene.index.ImpactsEnum -> org.gnit.lucenekmp.index.ImpactsEnum

### Method Categories:
- **Java Core Business Logic**: 12
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 9
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 5
- **Semantic Completion**: 91%

### Missing Core Methods:
- `public static boolean featureRequested(int, short)`
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.index.IndexFileDeleter -> org.gnit.lucenekmp.index.IndexFileDeleter

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 8
- **Java Auto-Generated**: 3
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void delete()`
- `public int compareTo(org.apache.lucene.index.IndexCommit)`


## Detailed Analysis: org.apache.lucene.index.IndexFileNames -> org.gnit.lucenekmp.index.IndexFileNames

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 9
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 93%


## Detailed Analysis: org.apache.lucene.index.IndexFormatTooNewException -> org.gnit.lucenekmp.index.IndexFormatTooNewException

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.index.IndexFormatTooOldException -> org.gnit.lucenekmp.index.IndexFormatTooOldException

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.index.IndexNotFoundException -> org.gnit.lucenekmp.index.IndexNotFoundException

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.index.IndexOptions -> org.gnit.lucenekmp.index.IndexOptions

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 75%


## Detailed Analysis: org.apache.lucene.index.IndexReader -> org.gnit.lucenekmp.index.IndexReader

### Method Categories:
- **Java Core Business Logic**: 20
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 2
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void registerParentReader(org.apache.lucene.index.IndexReader)`
- `protected void notifyReaderClosedListeners()`
- `private void reportCloseToParentReaders()`
- `public void incRef()`
- `public boolean tryIncRef()`
- `public void decRef()`
- `protected void ensureOpen()`
- `public org.apache.lucene.index.TermVectors termVectors()`
- `public int numDocs()`
- `public int maxDoc()`
- `public int numDeletedDocs()`
- `public org.apache.lucene.index.StoredFields storedFields()`
- `public boolean hasDeletions()`
- `public void close()`
- `protected void doClose()`
- `public int docFreq(org.apache.lucene.index.Term)`
- `public long totalTermFreq(org.apache.lucene.index.Term)`
- `public long getSumDocFreq(java.lang.String)`
- `public int getDocCount(java.lang.String)`
- `public long getSumTotalTermFreq(java.lang.String)`


## Detailed Analysis: org.apache.lucene.index.IndexReaderContext -> org.gnit.lucenekmp.index.IndexReaderContext

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 9
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 44%


## Detailed Analysis: org.apache.lucene.index.IndexWriter -> org.gnit.lucenekmp.index.IndexWriter

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 4
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void deleteUnusedFiles(java.util.Collection<java.lang.String>)`
- `public void flushFailed(org.apache.lucene.index.SegmentInfo)`
- `public void afterSegmentsFlushed()`
- `public void onTragicEvent(java.lang.Throwable, java.lang.String)`
- `public void onDeletesApplied()`
- `public void onTicketBacklog()`


## Detailed Analysis: org.apache.lucene.index.IndexWriterConfig -> org.gnit.lucenekmp.index.IndexWriterConfig

### Method Categories:
- **Java Core Business Logic**: 26
- **Java Property Accessors**: 26
- **Java Auto-Generated**: 1
- **Java Synthetic**: 6
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `package-private org.apache.lucene.index.IndexWriterConfig setIndexWriter(org.apache.lucene.index.IndexWriter)`
- `public org.apache.lucene.index.IndexWriterConfig setOpenMode(org.apache.lucene.index.IndexWriterConfig$OpenMode)`
- `public org.apache.lucene.index.IndexWriterConfig setIndexCreatedVersionMajor(int)`
- `public org.apache.lucene.index.IndexWriterConfig setIndexDeletionPolicy(org.apache.lucene.index.IndexDeletionPolicy)`
- `public org.apache.lucene.index.IndexWriterConfig setIndexCommit(org.apache.lucene.index.IndexCommit)`
- `public org.apache.lucene.index.IndexWriterConfig setSimilarity(org.apache.lucene.search.similarities.Similarity)`
- `public org.apache.lucene.index.IndexWriterConfig setMergeScheduler(org.apache.lucene.index.MergeScheduler)`
- `public org.apache.lucene.index.IndexWriterConfig setCodec(org.apache.lucene.codecs.Codec)`
- `public org.apache.lucene.index.IndexWriterConfig setReaderPooling(boolean)`
- `package-private org.apache.lucene.index.IndexWriterConfig setFlushPolicy(org.apache.lucene.index.FlushPolicy)`
- `public org.apache.lucene.index.IndexWriterConfig setRAMPerThreadHardLimitMB(int)`
- `public org.apache.lucene.index.IndexWriterConfig setInfoStream(org.apache.lucene.util.InfoStream)`
- `public org.apache.lucene.index.IndexWriterConfig setInfoStream(java.io.PrintStream)`
- `public org.apache.lucene.index.IndexWriterConfig setMergePolicy(org.apache.lucene.index.MergePolicy)`
- `public org.apache.lucene.index.IndexWriterConfig setMaxBufferedDocs(int)`
- `public org.apache.lucene.index.IndexWriterConfig setMergedSegmentWarmer(org.apache.lucene.index.IndexWriter$IndexReaderWarmer)`
- `public org.apache.lucene.index.IndexWriterConfig setRAMBufferSizeMB(double)`
- `public org.apache.lucene.index.IndexWriterConfig setUseCompoundFile(boolean)`
- `public org.apache.lucene.index.IndexWriterConfig setCommitOnClose(boolean)`
- `public org.apache.lucene.index.IndexWriterConfig setMaxFullFlushMergeWaitMillis(long)`
- `public org.apache.lucene.index.IndexWriterConfig setIndexSort(org.apache.lucene.search.Sort)`
- `public org.apache.lucene.index.IndexWriterConfig setLeafSorter(java.util.Comparator<org.apache.lucene.index.LeafReader>)`
- `public org.apache.lucene.index.IndexWriterConfig setCheckPendingFlushUpdate(boolean)`
- `public org.apache.lucene.index.IndexWriterConfig setSoftDeletesField(java.lang.String)`
- `public org.apache.lucene.index.IndexWriterConfig setIndexWriterEventListener(org.apache.lucene.index.IndexWriterEventListener)`
- `public org.apache.lucene.index.IndexWriterConfig setParentField(java.lang.String)`


## Detailed Analysis: org.apache.lucene.index.IndexableField -> org.gnit.lucenekmp.index.IndexableField

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.index.IndexableFieldType fieldType()`
- `public org.apache.lucene.analysis.TokenStream tokenStream(org.apache.lucene.analysis.Analyzer, org.apache.lucene.analysis.TokenStream)`
- `public org.apache.lucene.util.BytesRef binaryValue()`
- `public java.lang.String stringValue()`
- `public java.io.Reader readerValue()`
- `public java.lang.Number numericValue()`
- `public org.apache.lucene.document.StoredValue storedValue()`
- `public org.apache.lucene.document.InvertableType invertableType()`


## Detailed Analysis: org.apache.lucene.index.IndexingChain -> org.gnit.lucenekmp.index.IndexingChain

### Method Categories:
- **Java Core Business Logic**: 38
- **Java Property Accessors**: 8
- **Java Auto-Generated**: 2
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 42
- **KMP Property Accessors**: 8
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 4
- **Semantic Completion**: 86%

### Missing Core Methods:
- `protected void notifyReaderClosedListeners()`
- `public void decRef()`


## Detailed Analysis: org.apache.lucene.index.KnnVectorValues -> org.gnit.lucenekmp.index.KnnVectorValues

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 80%

### Missing Core Methods:
- `public void applyMask(org.apache.lucene.util.FixedBitSet, int)`


## Detailed Analysis: org.apache.lucene.index.LeafMetaData -> org.gnit.lucenekmp.index.LeafMetaData

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 25%

### Missing Core Methods:
- `public int createdVersionMajor()`
- `public org.apache.lucene.util.Version minVersion()`
- `public boolean hasBlocks()`


## Detailed Analysis: org.apache.lucene.index.LeafReader -> org.gnit.lucenekmp.index.LeafReader

### Method Categories:
- **Java Core Business Logic**: 38
- **Java Property Accessors**: 8
- **Java Auto-Generated**: 2
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 42
- **KMP Property Accessors**: 8
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 4
- **Semantic Completion**: 86%

### Missing Core Methods:
- `protected void notifyReaderClosedListeners()`
- `public void decRef()`


## Detailed Analysis: org.apache.lucene.index.LeafReaderContext -> org.gnit.lucenekmp.index.LeafReaderContext

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 13
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 1
- **Semantic Completion**: 34%


## Detailed Analysis: org.apache.lucene.index.LiveIndexWriterConfig -> org.gnit.lucenekmp.index.LiveIndexWriterConfig

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 26
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 55
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 52%


## Detailed Analysis: org.apache.lucene.index.LockableConcurrentApproximatePriorityQueue -> org.gnit.lucenekmp.index.LockableConcurrentApproximatePriorityQueue

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `package-private T lockAndPoll()`
- `package-private boolean remove(java.lang.Object)`
- `package-private boolean contains(java.lang.Object)`
- `package-private void addAndUnlock(T, long)`


## Detailed Analysis: org.apache.lucene.index.MappedMultiFields -> org.gnit.lucenekmp.index.MappedMultiFields

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 8
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 11
- **KMP Property Accessors**: 7
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public boolean hasFreqs()`
- `public boolean hasOffsets()`
- `public boolean hasPositions()`
- `public boolean hasPayloads()`
- `public static org.apache.lucene.index.Terms getTerms(org.apache.lucene.index.LeafReader, java.lang.String)`
- `public org.apache.lucene.index.TermsEnum intersect(org.apache.lucene.util.automaton.CompiledAutomaton, org.apache.lucene.util.BytesRef)`


## Detailed Analysis: org.apache.lucene.index.MappingMultiPostingsEnum -> org.gnit.lucenekmp.index.MappingMultiPostingsEnum

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 40%


## Detailed Analysis: org.apache.lucene.index.MergePolicy -> org.gnit.lucenekmp.index.MergePolicy

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.index.MergePolicy$MergeSpecification findMerges(org.apache.lucene.index.MergeTrigger, org.apache.lucene.index.SegmentInfos, org.apache.lucene.index.MergePolicy$MergeContext)`
- `public org.apache.lucene.index.MergePolicy$MergeSpecification findMerges(org.apache.lucene.index.CodecReader[])`
- `public org.apache.lucene.index.MergePolicy$MergeSpecification findForcedMerges(org.apache.lucene.index.SegmentInfos, int, java.util.Map<org.apache.lucene.index.SegmentCommitInfo, java.lang.Boolean>, org.apache.lucene.index.MergePolicy$MergeContext)`
- `public org.apache.lucene.index.MergePolicy$MergeSpecification findForcedDeletesMerges(org.apache.lucene.index.SegmentInfos, org.apache.lucene.index.MergePolicy$MergeContext)`
- `public org.apache.lucene.index.MergePolicy$MergeSpecification findFullFlushMerges(org.apache.lucene.index.MergeTrigger, org.apache.lucene.index.SegmentInfos, org.apache.lucene.index.MergePolicy$MergeContext)`
- `public boolean useCompoundFile(org.apache.lucene.index.SegmentInfos, org.apache.lucene.index.SegmentCommitInfo, org.apache.lucene.index.MergePolicy$MergeContext)`
- `protected long size(org.apache.lucene.index.SegmentCommitInfo, org.apache.lucene.index.MergePolicy$MergeContext)`
- `protected long maxFullFlushMergeSize()`
- `protected boolean assertDelCount(int, org.apache.lucene.index.SegmentCommitInfo)`
- `protected boolean isMerged(org.apache.lucene.index.SegmentInfos, org.apache.lucene.index.SegmentCommitInfo, org.apache.lucene.index.MergePolicy$MergeContext)`
- `public boolean keepFullyDeletedSegment(org.apache.lucene.util.IOSupplier<org.apache.lucene.index.CodecReader>)`
- `public int numDeletesToMerge(org.apache.lucene.index.SegmentCommitInfo, int, org.apache.lucene.util.IOSupplier<org.apache.lucene.index.CodecReader>)`
- `protected java.lang.String segString(org.apache.lucene.index.MergePolicy$MergeContext, java.lang.Iterable<org.apache.lucene.index.SegmentCommitInfo>)`
- `protected void message(java.lang.String, org.apache.lucene.index.MergePolicy$MergeContext)`
- `protected boolean verbose(org.apache.lucene.index.MergePolicy$MergeContext)`


## Detailed Analysis: org.apache.lucene.index.MergeRateLimiter -> org.gnit.lucenekmp.index.MergeRateLimiter

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 6
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public long pause(long)`
- `private long maybePause(long)`


## Detailed Analysis: org.apache.lucene.index.MergeScheduler -> org.gnit.lucenekmp.index.MergeScheduler

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void merge(org.apache.lucene.index.MergeScheduler$MergeSource, org.apache.lucene.index.MergeTrigger)`
- `public org.apache.lucene.store.Directory wrapForMerge(org.apache.lucene.index.MergePolicy$OneMerge, org.apache.lucene.store.Directory)`
- `public java.util.concurrent.Executor getIntraMergeExecutor(org.apache.lucene.index.MergePolicy$OneMerge)`
- `public void close()`
- `package-private void initialize(org.apache.lucene.util.InfoStream, org.apache.lucene.store.Directory)`
- `protected void message(java.lang.String)`


## Detailed Analysis: org.apache.lucene.index.MergeState -> org.gnit.lucenekmp.index.MergeState

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `private org.apache.lucene.index.MergeState$DocMap[] buildDeletionDocMaps(java.util.List<org.apache.lucene.index.CodecReader>)`
- `private org.apache.lucene.index.MergeState$DocMap[] buildDocMaps(java.util.List<org.apache.lucene.index.CodecReader>, org.apache.lucene.search.Sort)`
- `private static void verifyIndexSort(java.util.List<org.apache.lucene.index.CodecReader>, org.apache.lucene.index.SegmentInfo)`
- `package-private static org.apache.lucene.util.packed.PackedLongValues removeDeletes(int, org.apache.lucene.util.Bits)`


## Detailed Analysis: org.apache.lucene.index.MergeTrigger -> org.gnit.lucenekmp.index.MergeTrigger

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 75%


## Detailed Analysis: org.apache.lucene.index.MultiBits -> org.gnit.lucenekmp.index.MultiBits

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `private boolean checkLength(int, int)`
- `public boolean get(int)`
- `public void applyMask(org.apache.lucene.util.FixedBitSet, int)`


## Detailed Analysis: org.apache.lucene.index.MultiDocValues -> org.gnit.lucenekmp.index.MultiDocValues

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 8
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 76%

### Missing Core Methods:
- `public long longValue()`
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.index.MultiFields -> org.gnit.lucenekmp.index.MultiFields

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 91%


## Detailed Analysis: org.apache.lucene.index.MultiPostingsEnum -> org.gnit.lucenekmp.index.MultiPostingsEnum

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 7%


## Detailed Analysis: org.apache.lucene.index.MultiReader -> org.gnit.lucenekmp.index.MultiReader

### Method Categories:
- **Java Core Business Logic**: 22
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 3
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `protected void doClose()`
- `public org.apache.lucene.index.TermVectors termVectors()`
- `public int numDocs()`
- `public int maxDoc()`
- `public org.apache.lucene.index.StoredFields storedFields()`
- `public int docFreq(org.apache.lucene.index.Term)`
- `public long totalTermFreq(org.apache.lucene.index.Term)`
- `public long getSumDocFreq(java.lang.String)`
- `public int getDocCount(java.lang.String)`
- `public long getSumTotalTermFreq(java.lang.String)`
- `protected int readerIndex(int)`
- `protected int readerBase(int)`
- `public void registerParentReader(org.apache.lucene.index.IndexReader)`
- `protected void notifyReaderClosedListeners()`
- `private void reportCloseToParentReaders()`
- `public void incRef()`
- `public boolean tryIncRef()`
- `public void decRef()`
- `protected void ensureOpen()`
- `public int numDeletedDocs()`
- `public boolean hasDeletions()`
- `public void close()`


## Detailed Analysis: org.apache.lucene.index.MultiSorter -> org.gnit.lucenekmp.index.MultiSorter

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 15
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 3
- **Semantic Completion**: 70%


## Detailed Analysis: org.apache.lucene.index.MultiTerms -> org.gnit.lucenekmp.index.MultiTerms

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 10
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public org.apache.lucene.index.TermsEnum intersect(org.apache.lucene.util.automaton.CompiledAutomaton, org.apache.lucene.util.BytesRef)`
- `public boolean hasFreqs()`
- `public boolean hasOffsets()`
- `public boolean hasPositions()`
- `public boolean hasPayloads()`
- `public static org.apache.lucene.index.Terms getTerms(org.apache.lucene.index.LeafReader, java.lang.String)`


## Detailed Analysis: org.apache.lucene.index.MultiTermsEnum -> org.gnit.lucenekmp.index.MultiTermsEnum

### Method Categories:
- **Java Core Business Logic**: 12
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 7
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 2
- **Semantic Completion**: 0%

### Missing Core Methods:
- `protected boolean lessThan(org.apache.lucene.index.MultiTermsEnum$TermsEnumWithSlice, org.apache.lucene.index.MultiTermsEnum$TermsEnumWithSlice)`
- `package-private int fillTop(org.apache.lucene.index.MultiTermsEnum$TermsEnumWithSlice[])`
- `private org.apache.lucene.index.MultiTermsEnum$TermsEnumWithSlice get(int)`
- `public void addAll(java.util.Collection<T>)`
- `public T add(T)`
- `public T insertWithOverflow(T)`
- `public T updateTop()`
- `public T updateTop(T)`
- `public void clear()`
- `public boolean remove(T)`
- `private boolean upHeap(int)`
- `private void downHeap(int)`


## Detailed Analysis: org.apache.lucene.index.NumericDocValues -> org.gnit.lucenekmp.index.NumericDocValues

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 7
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.index.NumericDocValuesFieldUpdates -> org.gnit.lucenekmp.index.NumericDocValuesFieldUpdates

### Method Categories:
- **Java Core Business Logic**: 14
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 10
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 9%

### Missing Core Methods:
- `package-private void add(int, long)`
- `package-private void add(int, org.apache.lucene.util.BytesRef)`
- `package-private void reset(int)`
- `package-private void add(int, org.apache.lucene.index.DocValuesFieldUpdates$Iterator)`
- `public long ramBytesUsed()`
- `public static org.apache.lucene.index.DocValuesFieldUpdates$Iterator mergedIterator(org.apache.lucene.index.DocValuesFieldUpdates$Iterator[])`
- `package-private void finish()`
- `package-private int add(int)`
- `private int addInternal(int, long)`
- `protected void swap(int, int)`
- `protected void grow(int)`
- `protected void resize(int)`
- `protected void ensureFinished()`


## Detailed Analysis: org.apache.lucene.index.NumericDocValuesWriter -> org.gnit.lucenekmp.index.NumericDocValuesWriter

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 7
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.index.NumericDocValues getNumeric(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.BinaryDocValues getBinary(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.SortedDocValues getSorted(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.SortedNumericDocValues getSortedNumeric(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.SortedSetDocValues getSortedSet(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.DocValuesSkipper getSkipper(org.apache.lucene.index.FieldInfo)`
- `public void checkIntegrity()`
- `public void close()`


## Detailed Analysis: org.apache.lucene.index.OneMergeWrappingMergePolicy -> org.gnit.lucenekmp.index.OneMergeWrappingMergePolicy

### Method Categories:
- **Java Core Business Logic**: 17
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 1
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 17
- **KMP Property Accessors**: 8
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 1
- **Semantic Completion**: 87%

### Missing Core Methods:
- `public static T unwrapAll(T)`


## Detailed Analysis: org.apache.lucene.index.OrdTermState -> org.gnit.lucenekmp.index.OrdTermState

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 2
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 1
- **Semantic Completion**: 57%


## Detailed Analysis: org.apache.lucene.index.OrdinalMap -> org.gnit.lucenekmp.index.OrdinalMap

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 15
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 3
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public long get(long)`


## Detailed Analysis: org.apache.lucene.index.ParallelPostingsArray -> org.gnit.lucenekmp.index.ParallelPostingsArray

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `package-private int bytesPerPosting()`
- `package-private org.apache.lucene.index.ParallelPostingsArray newInstance(int)`
- `package-private void copyTo(org.apache.lucene.index.ParallelPostingsArray, int)`


## Detailed Analysis: org.apache.lucene.index.PendingDeletes -> org.gnit.lucenekmp.index.PendingDeletes

### Method Categories:
- **Java Core Business Logic**: 13
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 13
- **KMP Property Accessors**: 9
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 83%


## Detailed Analysis: org.apache.lucene.index.PendingSoftDeletes -> org.gnit.lucenekmp.index.PendingSoftDeletes

### Method Categories:
- **Java Core Business Logic**: 18
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `package-private boolean delete(int)`
- `protected int numPendingDeletes()`
- `package-private void onNewReader(org.apache.lucene.index.CodecReader, org.apache.lucene.index.SegmentCommitInfo)`
- `package-private boolean writeLiveDocs(org.apache.lucene.store.Directory)`
- `package-private void dropChanges()`
- `package-private void onDocValuesUpdate(org.apache.lucene.index.FieldInfo, org.apache.lucene.index.DocValuesFieldUpdates$Iterator)`
- `private boolean assertPendingDeletes()`
- `package-private int numDeletesToMerge(org.apache.lucene.index.MergePolicy, org.apache.lucene.util.IOSupplier<org.apache.lucene.index.CodecReader>)`
- `private void ensureInitialized(org.apache.lucene.util.IOSupplier<org.apache.lucene.index.CodecReader>)`
- `package-private boolean isFullyDeleted(org.apache.lucene.util.IOSupplier<org.apache.lucene.index.CodecReader>)`
- `private org.apache.lucene.index.FieldInfos readFieldInfos()`
- `package-private boolean mustInitOnDelete()`
- `private boolean assertCheckLiveDocs(org.apache.lucene.util.Bits, int, int)`
- `package-private boolean needsRefresh(org.apache.lucene.index.CodecReader)`
- `package-private int numDocs()`
- `package-private boolean verifyDocCounts(org.apache.lucene.index.CodecReader)`


## Detailed Analysis: org.apache.lucene.index.PointValues -> org.gnit.lucenekmp.index.PointValues

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public void visit(int)`
- `public void visit(org.apache.lucene.search.DocIdSetIterator)`
- `public void visit(org.apache.lucene.util.IntsRef)`
- `public void visit(int, byte[])`
- `public void visit(org.apache.lucene.search.DocIdSetIterator, byte[])`
- `public org.apache.lucene.index.PointValues$Relation compare(byte[], byte[])`
- `public void grow(int)`


## Detailed Analysis: org.apache.lucene.index.PointValuesWriter -> org.gnit.lucenekmp.index.PointValuesWriter

### Method Categories:
- **Java Core Business Logic**: 11
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 8
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 19%

### Missing Core Methods:
- `public void visitDocValues(org.apache.lucene.index.PointValues$IntersectVisitor)`
- `public void swap(int, int)`
- `public int getDocID(int)`
- `public void getValue(int, org.apache.lucene.util.BytesRef)`
- `public byte getByteAt(int, int)`
- `public void save(int, int)`
- `public void restore(int, int)`
- `public boolean moveToChild()`
- `public boolean moveToSibling()`
- `public boolean moveToParent()`
- `public void visitDocIDs(org.apache.lucene.index.PointValues$IntersectVisitor)`


## Detailed Analysis: org.apache.lucene.index.PostingsEnum -> org.gnit.lucenekmp.index.PostingsEnum

### Method Categories:
- **Java Core Business Logic**: 11
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 25%

### Missing Core Methods:
- `public int nextPosition()`
- `public int startOffset()`
- `public int endOffset()`
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`
- `public int docID()`
- `public int nextDoc()`
- `public int advance(int)`
- `protected int slowAdvance(int)`
- `public void intoBitSet(int, org.apache.lucene.util.FixedBitSet, int)`


## Detailed Analysis: org.apache.lucene.index.PrefixCodedTerms -> org.gnit.lucenekmp.index.PrefixCodedTerms

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 2
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 9
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public long ramBytesUsed()`


## Detailed Analysis: org.apache.lucene.index.ReaderPool -> org.gnit.lucenekmp.index.ReaderPool

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.index.ReaderSlice -> org.gnit.lucenekmp.index.ReaderSlice

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public int readerIndex()`


## Detailed Analysis: org.apache.lucene.index.ReadersAndUpdates -> org.gnit.lucenekmp.index.ReadersAndUpdates

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.index.BinaryDocValues getBinary(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.NumericDocValues getNumeric(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.SortedDocValues getSorted(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.SortedNumericDocValues getSortedNumeric(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.SortedSetDocValues getSortedSet(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.DocValuesSkipper getSkipper(org.apache.lucene.index.FieldInfo)`
- `public void checkIntegrity()`
- `public void close()`


## Detailed Analysis: org.apache.lucene.index.SegmentCommitInfo -> org.gnit.lucenekmp.index.SegmentCommitInfo

### Method Categories:
- **Java Core Business Logic**: 11
- **Java Property Accessors**: 24
- **Java Auto-Generated**: 3
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 11
- **KMP Property Accessors**: 25
- **KMP Auto-Generated**: 3
- **KMP Synthetic**: 0
- **Semantic Completion**: 97%


## Detailed Analysis: org.apache.lucene.index.SegmentCoreReaders -> org.gnit.lucenekmp.index.SegmentCoreReaders

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void addClosedListener(org.apache.lucene.index.IndexReader$ClosedListener)`


## Detailed Analysis: org.apache.lucene.index.SegmentDocValues -> org.gnit.lucenekmp.index.SegmentDocValues

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 84%


## Detailed Analysis: org.apache.lucene.index.SegmentDocValuesProducer -> org.gnit.lucenekmp.index.SegmentDocValuesProducer

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 8
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 83%


## Detailed Analysis: org.apache.lucene.index.SegmentInfo -> org.gnit.lucenekmp.index.SegmentInfo

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 15
- **Java Auto-Generated**: 4
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public void addDiagnostics(java.util.Map<java.lang.String, java.lang.String>)`
- `package-private void setHasBlocks()`
- `public int maxDoc()`
- `public void addFiles(java.util.Collection<java.lang.String>)`
- `public void addFile(java.lang.String)`
- `private void checkFileNames(java.util.Collection<java.lang.String>)`
- `package-private java.lang.String namedForThisSegment(java.lang.String)`
- `public java.lang.String getAttribute(java.lang.String)`
- `public java.lang.String putAttribute(java.lang.String, java.lang.String)`


## Detailed Analysis: org.apache.lucene.index.SegmentInfos -> org.gnit.lucenekmp.index.SegmentInfos

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 47%

### Missing Core Methods:
- `protected org.apache.lucene.index.SegmentInfos doBody(java.lang.String)`


## Detailed Analysis: org.apache.lucene.index.SegmentMerger -> org.gnit.lucenekmp.index.SegmentMerger

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.index.SegmentReadState -> org.gnit.lucenekmp.index.SegmentReadState

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.index.SegmentReader -> org.gnit.lucenekmp.index.SegmentReader

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 37%

### Missing Core Methods:
- `public void addClosedListener(org.apache.lucene.index.IndexReader$ClosedListener)`


## Detailed Analysis: org.apache.lucene.index.SegmentWriteState -> org.gnit.lucenekmp.index.SegmentWriteState

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 13
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 11%


## Detailed Analysis: org.apache.lucene.index.SingleTermsEnum -> org.gnit.lucenekmp.index.SingleTermsEnum

### Method Categories:
- **Java Core Business Logic**: 13
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 13
- **KMP Property Accessors**: 8
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 88%


## Detailed Analysis: org.apache.lucene.index.SingletonSortedNumericDocValues -> org.gnit.lucenekmp.index.SingletonSortedNumericDocValues

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 8
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.index.SingletonSortedSetDocValues -> org.gnit.lucenekmp.index.SingletonSortedSetDocValues

### Method Categories:
- **Java Core Business Logic**: 14
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 12
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.index.SlowCompositeCodecReaderWrapper -> org.gnit.lucenekmp.index.SlowCompositeCodecReaderWrapper

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 4
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 2
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public int docStart()`
- `public int ordStart()`


## Detailed Analysis: org.apache.lucene.index.SortFieldProvider -> org.gnit.lucenekmp.index.SortFieldProvider

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.index.SortedDocValues -> org.gnit.lucenekmp.index.SortedDocValues

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.index.SortedDocValuesTermsEnum -> org.gnit.lucenekmp.index.SortedDocValuesTermsEnum

### Method Categories:
- **Java Core Business Logic**: 11
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 12
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 91%


## Detailed Analysis: org.apache.lucene.index.SortedDocValuesWriter -> org.gnit.lucenekmp.index.SortedDocValuesWriter

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 11
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.index.SortedDocValues getSorted(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.NumericDocValues getNumeric(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.BinaryDocValues getBinary(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.SortedNumericDocValues getSortedNumeric(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.SortedSetDocValues getSortedSet(org.apache.lucene.index.FieldInfo)`
- `public org.apache.lucene.index.DocValuesSkipper getSkipper(org.apache.lucene.index.FieldInfo)`
- `public void checkIntegrity()`
- `public void close()`


## Detailed Analysis: org.apache.lucene.index.SortedNumericDocValues -> org.gnit.lucenekmp.index.SortedNumericDocValues

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 8
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.index.SortedSetDocValues -> org.gnit.lucenekmp.index.SortedSetDocValues

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.index.SortedSetDocValuesTermsEnum -> org.gnit.lucenekmp.index.SortedSetDocValuesTermsEnum

### Method Categories:
- **Java Core Business Logic**: 11
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 12
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 91%


## Detailed Analysis: org.apache.lucene.index.Sorter -> org.gnit.lucenekmp.index.Sorter

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public int oldToNew(int)`
- `public int newToOld(int)`


## Detailed Analysis: org.apache.lucene.index.SortingCodecReader -> org.gnit.lucenekmp.index.SortingCodecReader

### Method Categories:
- **Java Core Business Logic**: 41
- **Java Property Accessors**: 16
- **Java Auto-Generated**: 3
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 7
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 52%

### Missing Core Methods:
- `public static org.apache.lucene.index.CodecReader unwrap(org.apache.lucene.index.CodecReader)`
- `public int numDocs()`
- `public int maxDoc()`
- `protected void doClose()`
- `package-private static org.apache.lucene.index.FilterCodecReader wrapLiveDocs(org.apache.lucene.index.CodecReader, org.apache.lucene.util.Bits, int)`
- `public org.apache.lucene.index.StoredFields storedFields()`
- `public org.apache.lucene.index.TermVectors termVectors()`
- `public org.apache.lucene.index.Terms terms(java.lang.String)`
- `private org.apache.lucene.index.FieldInfo getDVField(java.lang.String, org.apache.lucene.index.DocValuesType)`
- `public org.apache.lucene.index.NumericDocValues getNumericDocValues(java.lang.String)`
- `public org.apache.lucene.index.BinaryDocValues getBinaryDocValues(java.lang.String)`
- `public org.apache.lucene.index.SortedDocValues getSortedDocValues(java.lang.String)`
- `public org.apache.lucene.index.SortedNumericDocValues getSortedNumericDocValues(java.lang.String)`
- `public org.apache.lucene.index.SortedSetDocValues getSortedSetDocValues(java.lang.String)`
- `public org.apache.lucene.index.DocValuesSkipper getDocValuesSkipper(java.lang.String)`
- `public org.apache.lucene.index.NumericDocValues getNormValues(java.lang.String)`
- `public org.apache.lucene.index.PointValues getPointValues(java.lang.String)`
- `public void searchNearestVectors(java.lang.String, float[], org.apache.lucene.search.KnnCollector, org.apache.lucene.util.Bits)`
- `public void searchNearestVectors(java.lang.String, byte[], org.apache.lucene.search.KnnCollector, org.apache.lucene.util.Bits)`
- `public int docFreq(org.apache.lucene.index.Term)`
- `public long totalTermFreq(org.apache.lucene.index.Term)`
- `public long getSumDocFreq(java.lang.String)`
- `public int getDocCount(java.lang.String)`
- `public long getSumTotalTermFreq(java.lang.String)`
- `public org.apache.lucene.index.PostingsEnum postings(org.apache.lucene.index.Term, int)`
- `public org.apache.lucene.index.PostingsEnum postings(org.apache.lucene.index.Term)`
- `public org.apache.lucene.search.TopDocs searchNearestVectors(java.lang.String, float[], int, org.apache.lucene.util.Bits, int)`
- `public org.apache.lucene.search.TopDocs searchNearestVectors(java.lang.String, byte[], int, org.apache.lucene.util.Bits, int)`
- `public void registerParentReader(org.apache.lucene.index.IndexReader)`
- `protected void notifyReaderClosedListeners()`
- `private void reportCloseToParentReaders()`
- `public void incRef()`
- `public boolean tryIncRef()`
- `public void decRef()`
- `protected void ensureOpen()`
- `public int numDeletedDocs()`
- `public boolean hasDeletions()`


## Detailed Analysis: org.apache.lucene.index.SortingStoredFieldsConsumer -> org.gnit.lucenekmp.index.SortingStoredFieldsConsumer

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 8
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.codecs.compressing.Compressor newCompressor()`
- `public org.apache.lucene.codecs.compressing.Decompressor newDecompressor()`


## Detailed Analysis: org.apache.lucene.index.SortingTermVectorsConsumer -> org.gnit.lucenekmp.index.SortingTermVectorsConsumer

### Method Categories:
- **Java Core Business Logic**: 12
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 83%

### Missing Core Methods:
- `package-private void flush(java.util.Map<java.lang.String, org.apache.lucene.index.TermsHashPerField>, org.apache.lucene.index.SegmentWriteState, org.apache.lucene.index.Sorter$DocMap, org.apache.lucene.codecs.NormsProducer)`
- `package-private void initTermVectorsWriter()`
- `public void abort()`
- `package-private void fill(int)`
- `package-private void setHasVectors()`
- `package-private void finishDocument(int)`
- `package-private void resetFields()`
- `public org.apache.lucene.index.TermsHashPerField addField(org.apache.lucene.index.FieldInvertState, org.apache.lucene.index.FieldInfo)`
- `package-private void addFieldToFlush(org.apache.lucene.index.TermVectorsConsumerPerField)`
- `package-private void startDocument()`
- `package-private void reset()`


## Detailed Analysis: org.apache.lucene.index.StandardDirectoryReader -> org.gnit.lucenekmp.index.StandardDirectoryReader

### Method Categories:
- **Java Core Business Logic**: 46
- **Java Property Accessors**: 10
- **Java Auto-Generated**: 3
- **Java Synthetic**: 4
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `package-private static org.apache.lucene.index.DirectoryReader open(org.apache.lucene.store.Directory, org.apache.lucene.index.IndexCommit, java.util.Comparator<org.apache.lucene.index.LeafReader>)`
- `package-private static org.apache.lucene.index.DirectoryReader open(org.apache.lucene.store.Directory, int, org.apache.lucene.index.IndexCommit, java.util.Comparator<org.apache.lucene.index.LeafReader>)`
- `package-private static org.apache.lucene.index.StandardDirectoryReader open(org.apache.lucene.index.IndexWriter, org.apache.lucene.util.IOFunction<org.apache.lucene.index.SegmentCommitInfo, org.apache.lucene.index.SegmentReader>, org.apache.lucene.index.SegmentInfos, boolean, boolean)`
- `public static org.apache.lucene.index.DirectoryReader open(org.apache.lucene.store.Directory, org.apache.lucene.index.SegmentInfos, java.util.List<? extends org.apache.lucene.index.LeafReader>, java.util.Comparator<org.apache.lucene.index.LeafReader>)`
- `private static void decRefWhileHandlingException(org.apache.lucene.index.SegmentReader[])`
- `protected org.apache.lucene.index.DirectoryReader doOpenIfChanged()`
- `protected org.apache.lucene.index.DirectoryReader doOpenIfChanged(org.apache.lucene.index.IndexCommit)`
- `protected org.apache.lucene.index.DirectoryReader doOpenIfChanged(org.apache.lucene.index.IndexWriter, boolean)`
- `private org.apache.lucene.index.DirectoryReader doOpenFromWriter(org.apache.lucene.index.IndexCommit)`
- `private org.apache.lucene.index.DirectoryReader doOpenNoWriter(org.apache.lucene.index.IndexCommit)`
- `private org.apache.lucene.index.DirectoryReader doOpenFromCommit(org.apache.lucene.index.IndexCommit)`
- `package-private org.apache.lucene.index.DirectoryReader doOpenIfChanged(org.apache.lucene.index.SegmentInfos)`
- `protected void doClose()`
- `protected void notifyReaderClosedListeners()`
- `public static org.apache.lucene.index.DirectoryReader open(org.apache.lucene.store.Directory)`
- `public static org.apache.lucene.index.DirectoryReader open(org.apache.lucene.store.Directory, java.util.Comparator<org.apache.lucene.index.LeafReader>)`
- `public static org.apache.lucene.index.DirectoryReader open(org.apache.lucene.index.IndexWriter)`
- `public static org.apache.lucene.index.DirectoryReader open(org.apache.lucene.index.IndexWriter, boolean, boolean)`
- `public static org.apache.lucene.index.DirectoryReader open(org.apache.lucene.index.IndexCommit)`
- `public static org.apache.lucene.index.DirectoryReader open(org.apache.lucene.index.IndexCommit, int, java.util.Comparator<org.apache.lucene.index.LeafReader>)`
- `public static org.apache.lucene.index.DirectoryReader openIfChanged(org.apache.lucene.index.DirectoryReader)`
- `public static org.apache.lucene.index.DirectoryReader openIfChanged(org.apache.lucene.index.DirectoryReader, org.apache.lucene.index.IndexCommit)`
- `public static org.apache.lucene.index.DirectoryReader openIfChanged(org.apache.lucene.index.DirectoryReader, org.apache.lucene.index.IndexWriter)`
- `public static org.apache.lucene.index.DirectoryReader openIfChanged(org.apache.lucene.index.DirectoryReader, org.apache.lucene.index.IndexWriter, boolean)`
- `public static java.util.List<org.apache.lucene.index.IndexCommit> listCommits(org.apache.lucene.store.Directory)`
- `public static boolean indexExists(org.apache.lucene.store.Directory)`
- `public org.apache.lucene.index.TermVectors termVectors()`
- `public int numDocs()`
- `public int maxDoc()`
- `public org.apache.lucene.index.StoredFields storedFields()`
- `public int docFreq(org.apache.lucene.index.Term)`
- `public long totalTermFreq(org.apache.lucene.index.Term)`
- `public long getSumDocFreq(java.lang.String)`
- `public int getDocCount(java.lang.String)`
- `public long getSumTotalTermFreq(java.lang.String)`
- `protected int readerIndex(int)`
- `protected int readerBase(int)`
- `public void registerParentReader(org.apache.lucene.index.IndexReader)`
- `private void reportCloseToParentReaders()`
- `public void incRef()`
- `public boolean tryIncRef()`
- `public void decRef()`
- `protected void ensureOpen()`
- `public int numDeletedDocs()`
- `public boolean hasDeletions()`
- `public void close()`


## Detailed Analysis: org.apache.lucene.index.StoredFieldDataInput -> org.gnit.lucenekmp.index.StoredFieldDataInput

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.index.StoredFieldVisitor -> org.gnit.lucenekmp.index.StoredFieldVisitor

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 75%


## Detailed Analysis: org.apache.lucene.index.StoredFieldsConsumer -> org.gnit.lucenekmp.index.StoredFieldsConsumer

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.index.Term -> org.gnit.lucenekmp.index.Term

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 4
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public int compareTo(org.apache.lucene.index.Term)`
- `package-private void set(java.lang.String, org.apache.lucene.util.BytesRef)`
- `public long ramBytesUsed()`


## Detailed Analysis: org.apache.lucene.index.TermStates -> org.gnit.lucenekmp.index.TermStates

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 2
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void copyFrom(org.apache.lucene.index.TermState)`


## Detailed Analysis: org.apache.lucene.index.TermVectors -> org.gnit.lucenekmp.index.TermVectors

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 93%


## Detailed Analysis: org.apache.lucene.index.TermVectorsConsumer -> org.gnit.lucenekmp.index.TermVectorsConsumer

### Method Categories:
- **Java Core Business Logic**: 11
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 11
- **KMP Property Accessors**: 18
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 50%


## Detailed Analysis: org.apache.lucene.index.TermVectorsConsumerPerField -> org.gnit.lucenekmp.index.TermVectorsConsumerPerField

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 11
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 37%


## Detailed Analysis: org.apache.lucene.index.Terms -> org.gnit.lucenekmp.index.Terms

### Method Categories:
- **Java Core Business Logic**: 17
- **Java Property Accessors**: 7
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 17
- **KMP Property Accessors**: 10
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 91%


## Detailed Analysis: org.apache.lucene.index.TermsEnum -> org.gnit.lucenekmp.index.TermsEnum

### Method Categories:
- **Java Core Business Logic**: 11
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.index.TermsEnum$SeekStatus seekCeil(org.apache.lucene.util.BytesRef)`
- `public void seekExact(long)`
- `public int docFreq()`
- `public long totalTermFreq()`
- `public org.apache.lucene.index.PostingsEnum postings(org.apache.lucene.index.PostingsEnum, int)`
- `public org.apache.lucene.index.ImpactsEnum impacts(int)`
- `public org.apache.lucene.index.TermState termState()`
- `public void seekExact(org.apache.lucene.util.BytesRef, org.apache.lucene.index.TermState)`
- `public boolean seekExact(org.apache.lucene.util.BytesRef)`
- `public org.apache.lucene.util.IOBooleanSupplier prepareSeekExact(org.apache.lucene.util.BytesRef)`
- `public org.apache.lucene.index.PostingsEnum postings(org.apache.lucene.index.PostingsEnum)`


## Detailed Analysis: org.apache.lucene.index.TermsEnumIndex -> org.gnit.lucenekmp.index.TermsEnumIndex

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `package-private static long prefix8ToComparableUnsignedLong(org.apache.lucene.util.BytesRef)`
- `package-private org.apache.lucene.index.TermsEnum$SeekStatus seekCeil(org.apache.lucene.util.BytesRef)`
- `package-private boolean seekExact(org.apache.lucene.util.BytesRef)`
- `package-private void seekExact(long)`
- `package-private void reset(org.apache.lucene.index.TermsEnumIndex)`
- `package-private int compareTermTo(org.apache.lucene.index.TermsEnumIndex)`
- `package-private boolean termEquals(org.apache.lucene.index.TermsEnumIndex$TermState)`


## Detailed Analysis: org.apache.lucene.index.TermsHash -> org.gnit.lucenekmp.index.TermsHash

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 6
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 62%


## Detailed Analysis: org.apache.lucene.index.TieredMergePolicy -> org.gnit.lucenekmp.index.TieredMergePolicy

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.index.TrackingTmpOutputDirectoryWrapper -> org.gnit.lucenekmp.index.TrackingTmpOutputDirectoryWrapper

### Method Categories:
- **Java Core Business Logic**: 16
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 93%

### Missing Core Methods:
- `public static org.apache.lucene.store.Directory unwrap(org.apache.lucene.store.Directory)`
- `protected static java.lang.String getTempFileName(java.lang.String, java.lang.String, long)`


## Detailed Analysis: org.apache.lucene.index.VectorEncoding -> org.gnit.lucenekmp.index.VectorEncoding

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 60%


## Detailed Analysis: org.apache.lucene.index.VectorSimilarityFunction -> org.gnit.lucenekmp.index.VectorSimilarityFunction

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 86%


## Detailed Analysis: org.apache.lucene.internal.hppc.AbstractIterator -> org.gnit.lucenekmp.internal.hppc.AbstractIterator

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public boolean hasNext()`
- `public void remove()`


## Detailed Analysis: org.apache.lucene.internal.hppc.BufferAllocationException -> org.gnit.lucenekmp.internal.hppc.BufferAllocationException

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 83%


## Detailed Analysis: org.apache.lucene.internal.hppc.HashContainers -> org.gnit.lucenekmp.internal.hppc.HashContainers

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 69%


## Detailed Analysis: org.apache.lucene.internal.hppc.IntCursor -> org.gnit.lucenekmp.internal.hppc.IntCursor

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 7%


## Detailed Analysis: org.apache.lucene.internal.hppc.IntIntHashMap -> org.gnit.lucenekmp.internal.hppc.IntIntHashMap

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 85%


## Detailed Analysis: org.apache.lucene.internal.hppc.IntObjectHashMap -> org.gnit.lucenekmp.internal.hppc.IntObjectHashMap

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 85%


## Detailed Analysis: org.apache.lucene.internal.hppc.LongCursor -> org.gnit.lucenekmp.internal.hppc.LongCursor

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 7%


## Detailed Analysis: org.apache.lucene.internal.hppc.LongObjectHashMap -> org.gnit.lucenekmp.internal.hppc.LongObjectHashMap

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 85%


## Detailed Analysis: org.apache.lucene.internal.hppc.ObjectCursor -> org.gnit.lucenekmp.internal.hppc.ObjectCursor

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 7%


## Detailed Analysis: org.apache.lucene.internal.tests.ConcurrentMergeSchedulerAccess -> org.gnit.lucenekmp.internal.tests.ConcurrentMergeSchedulerAccess

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.internal.tests.TestSecrets -> org.gnit.lucenekmp.internal.tests.TestSecrets

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 10
- **Java Auto-Generated**: 0
- **Java Synthetic**: 3
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 8
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 77%


## Detailed Analysis: org.apache.lucene.internal.vectorization.DefaultVectorUtilSupport -> org.gnit.lucenekmp.internal.vectorization.DefaultVectorUtilSupport

### Method Categories:
- **Java Core Business Logic**: 11
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 11
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 81%

### Missing Core Methods:
- `private static float fma(float, float, float)`
- `public static long int4BitDotProductImpl(byte[], byte[])`


## Detailed Analysis: org.apache.lucene.internal.vectorization.DefaultVectorizationProvider -> org.gnit.lucenekmp.internal.vectorization.DefaultVectorizationProvider

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `package-private static org.apache.lucene.internal.vectorization.VectorizationProvider lookup(boolean)`
- `private static java.util.Optional<java.lang.Module> lookupVectorModule()`
- `private static void ensureCaller()`


## Detailed Analysis: org.apache.lucene.internal.vectorization.PostingDecodingUtil -> org.gnit.lucenekmp.internal.vectorization.PostingDecodingUtil

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 62%


## Detailed Analysis: org.apache.lucene.internal.vectorization.VectorizationProvider -> org.gnit.lucenekmp.internal.vectorization.VectorizationProvider

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.search.AbstractKnnCollector -> org.gnit.lucenekmp.search.AbstractKnnCollector

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 8
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 88%


## Detailed Analysis: org.apache.lucene.search.AbstractKnnVectorQuery -> org.gnit.lucenekmp.search.AbstractKnnVectorQuery

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `protected boolean match(int)`
- `public int docID()`
- `public int nextDoc()`
- `public int advance(int)`
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`
- `protected int slowAdvance(int)`
- `public void intoBitSet(int, org.apache.lucene.util.FixedBitSet, int)`


## Detailed Analysis: org.apache.lucene.search.AbstractMultiTermQueryConstantScoreWrapper -> org.gnit.lucenekmp.search.AbstractMultiTermQueryConstantScoreWrapper

### Method Categories:
- **Java Core Business Logic**: 12
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `protected org.apache.lucene.search.AbstractMultiTermQueryConstantScoreWrapper$WeightOrDocIdSetIterator rewriteInner(org.apache.lucene.index.LeafReaderContext, int, org.apache.lucene.index.Terms, org.apache.lucene.index.TermsEnum, java.util.List<org.apache.lucene.search.AbstractMultiTermQueryConstantScoreWrapper$TermAndState>, long)`
- `private org.apache.lucene.search.AbstractMultiTermQueryConstantScoreWrapper$WeightOrDocIdSetIterator rewriteAsBooleanQuery(org.apache.lucene.index.LeafReaderContext, java.util.List<org.apache.lucene.search.AbstractMultiTermQueryConstantScoreWrapper$TermAndState>)`
- `private boolean collectTerms(int, org.apache.lucene.index.TermsEnum, java.util.List<org.apache.lucene.search.AbstractMultiTermQueryConstantScoreWrapper$TermAndState>)`
- `private org.apache.lucene.search.Scorer scorerForIterator(org.apache.lucene.search.DocIdSetIterator)`
- `public org.apache.lucene.search.Matches matches(org.apache.lucene.index.LeafReaderContext, int)`
- `public org.apache.lucene.search.ScorerSupplier scorerSupplier(org.apache.lucene.index.LeafReaderContext)`
- `private static long estimateCost(org.apache.lucene.index.Terms, long)`
- `public boolean isCacheable(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.Explanation explain(org.apache.lucene.index.LeafReaderContext, int)`
- `public org.apache.lucene.search.Scorer scorer(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.BulkScorer bulkScorer(org.apache.lucene.index.LeafReaderContext)`
- `public int count(org.apache.lucene.index.LeafReaderContext)`


## Detailed Analysis: org.apache.lucene.search.BlockMaxConjunctionBulkScorer -> org.gnit.lucenekmp.search.BlockMaxConjunctionBulkScorer

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public float smoothingScore(int)`


## Detailed Analysis: org.apache.lucene.search.BlockMaxConjunctionScorer -> org.gnit.lucenekmp.search.BlockMaxConjunctionScorer

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static org.apache.lucene.search.DocIdSetIterator asDocIdSetIterator(org.apache.lucene.search.TwoPhaseIterator)`
- `public static org.apache.lucene.search.TwoPhaseIterator unwrap(org.apache.lucene.search.DocIdSetIterator)`


## Detailed Analysis: org.apache.lucene.search.BooleanClause -> org.gnit.lucenekmp.search.BooleanClause

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 1
- **Semantic Completion**: 69%


## Detailed Analysis: org.apache.lucene.search.BooleanQuery -> org.gnit.lucenekmp.search.BooleanQuery

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 4
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public java.util.Collection<org.apache.lucene.search.Query> getClauses(org.apache.lucene.search.BooleanClause$Occur)`
- `package-private org.apache.lucene.search.Query[] rewriteTwoClauseDisjunctionWithTermsForCount(org.apache.lucene.search.IndexSearcher)`
- `package-private org.apache.lucene.search.BooleanQuery rewriteNoScoring()`
- `public org.apache.lucene.search.Weight createWeight(org.apache.lucene.search.IndexSearcher, org.apache.lucene.search.ScoreMode, float)`
- `public org.apache.lucene.search.Query rewrite(org.apache.lucene.search.IndexSearcher)`
- `public void visit(org.apache.lucene.search.QueryVisitor)`
- `private boolean equalsTo(org.apache.lucene.search.BooleanQuery)`
- `private int computeHashCode()`
- `protected boolean sameClassAs(java.lang.Object)`
- `protected int classHash()`


## Detailed Analysis: org.apache.lucene.search.BooleanScorer -> org.gnit.lucenekmp.search.BooleanScorer

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 16
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 3
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.search.BooleanScorerSupplier -> org.gnit.lucenekmp.search.BooleanScorerSupplier

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 7
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public int score(org.apache.lucene.search.LeafCollector, org.apache.lucene.util.Bits, int, int)`


## Detailed Analysis: org.apache.lucene.search.BooleanWeight -> org.gnit.lucenekmp.search.BooleanWeight

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.search.BoostAttribute -> org.gnit.lucenekmp.search.BoostAttribute

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.search.CollectionStatistics -> org.gnit.lucenekmp.search.CollectionStatistics

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 9
- **KMP Synthetic**: 1
- **Semantic Completion**: 24%

### Missing Core Methods:
- `public long maxDoc()`
- `public long docCount()`
- `public long sumTotalTermFreq()`
- `public long sumDocFreq()`


## Detailed Analysis: org.apache.lucene.search.Collector -> org.gnit.lucenekmp.search.Collector

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 81%


## Detailed Analysis: org.apache.lucene.search.ConjunctionBulkScorer -> org.gnit.lucenekmp.search.ConjunctionBulkScorer

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public float smoothingScore(int)`


## Detailed Analysis: org.apache.lucene.search.ConjunctionDISI -> org.gnit.lucenekmp.search.ConjunctionDISI

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public int docID()`
- `public int nextDoc()`
- `public int advance(int)`
- `private int doNext(int)`
- `private boolean assertItersOnSameDoc()`
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`
- `protected int slowAdvance(int)`
- `public void intoBitSet(int, org.apache.lucene.util.FixedBitSet, int)`


## Detailed Analysis: org.apache.lucene.search.ConjunctionScorer -> org.gnit.lucenekmp.search.ConjunctionScorer

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.search.ConstantScoreQuery -> org.gnit.lucenekmp.search.ConstantScoreQuery

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.search.ScorerSupplier scorerSupplier(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.Matches matches(org.apache.lucene.index.LeafReaderContext, int)`
- `public boolean isCacheable(org.apache.lucene.index.LeafReaderContext)`
- `public int count(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.Explanation explain(org.apache.lucene.index.LeafReaderContext, int)`
- `public org.apache.lucene.search.Scorer scorer(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.BulkScorer bulkScorer(org.apache.lucene.index.LeafReaderContext)`


## Detailed Analysis: org.apache.lucene.search.ConstantScoreScorer -> org.gnit.lucenekmp.search.ConstantScoreScorer

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public float matchCost()`
- `public static org.apache.lucene.search.DocIdSetIterator asDocIdSetIterator(org.apache.lucene.search.TwoPhaseIterator)`
- `public static org.apache.lucene.search.TwoPhaseIterator unwrap(org.apache.lucene.search.DocIdSetIterator)`


## Detailed Analysis: org.apache.lucene.search.ConstantScoreScorerSupplier -> org.gnit.lucenekmp.search.ConstantScoreScorerSupplier

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static org.apache.lucene.search.ConstantScoreScorerSupplier fromIterator(org.apache.lucene.search.DocIdSetIterator, float, org.apache.lucene.search.ScoreMode, int)`


## Detailed Analysis: org.apache.lucene.search.DenseConjunctionBulkScorer -> org.gnit.lucenekmp.search.DenseConjunctionBulkScorer

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void forEach(org.apache.lucene.search.CheckedIntConsumer<java.io.IOException>)`


## Detailed Analysis: org.apache.lucene.search.DisiPriorityQueue -> org.gnit.lucenekmp.search.DisiPriorityQueue

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public org.apache.lucene.search.DisiWrapper topList()`
- `public org.apache.lucene.search.DisiWrapper add(org.apache.lucene.search.DisiWrapper)`
- `public void addAll(org.apache.lucene.search.DisiWrapper[], int, int)`
- `public org.apache.lucene.search.DisiWrapper updateTop()`
- `package-private org.apache.lucene.search.DisiWrapper updateTop(org.apache.lucene.search.DisiWrapper)`
- `public void clear()`


## Detailed Analysis: org.apache.lucene.search.DisiPriorityQueue2 -> org.gnit.lucenekmp.search.DisiPriorityQueue2

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static org.apache.lucene.search.DisiPriorityQueue ofMaxSize(int)`


## Detailed Analysis: org.apache.lucene.search.DisiPriorityQueueN -> org.gnit.lucenekmp.search.DisiPriorityQueueN

### Method Categories:
- **Java Core Business Logic**: 14
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public org.apache.lucene.search.DisiWrapper topList()`
- `private org.apache.lucene.search.DisiWrapper prepend(org.apache.lucene.search.DisiWrapper, org.apache.lucene.search.DisiWrapper)`
- `private org.apache.lucene.search.DisiWrapper topList(org.apache.lucene.search.DisiWrapper, org.apache.lucene.search.DisiWrapper[], int, int)`
- `public org.apache.lucene.search.DisiWrapper add(org.apache.lucene.search.DisiWrapper)`
- `public void addAll(org.apache.lucene.search.DisiWrapper[], int, int)`
- `public org.apache.lucene.search.DisiWrapper updateTop()`
- `package-private org.apache.lucene.search.DisiWrapper updateTop(org.apache.lucene.search.DisiWrapper)`
- `public void clear()`
- `package-private void upHeap(int)`
- `package-private void downHeap(int)`
- `public static org.apache.lucene.search.DisiPriorityQueue ofMaxSize(int)`


## Detailed Analysis: org.apache.lucene.search.DisiWrapper -> org.gnit.lucenekmp.search.DisiWrapper

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 18
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.search.DisjunctionDISIApproximation -> org.gnit.lucenekmp.search.DisjunctionDISIApproximation

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public static org.apache.lucene.search.DisjunctionDISIApproximation of(java.util.Collection<? extends org.apache.lucene.search.DisiWrapper>, long)`
- `public int docID()`
- `public int nextDoc()`
- `public int advance(int)`
- `public void intoBitSet(int, org.apache.lucene.util.FixedBitSet, int)`
- `public org.apache.lucene.search.DisiWrapper topList()`
- `private org.apache.lucene.search.DisiWrapper computeTopList()`
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`
- `protected int slowAdvance(int)`


## Detailed Analysis: org.apache.lucene.search.DisjunctionMatchesIterator -> org.gnit.lucenekmp.search.DisjunctionMatchesIterator

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.search.DisjunctionMaxBulkScorer -> org.gnit.lucenekmp.search.DisjunctionMaxBulkScorer

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 6
- **Semantic Completion**: 0%

### Missing Core Methods:
- `protected boolean lessThan(org.apache.lucene.search.DisjunctionMaxBulkScorer$BulkScorerAndNext, org.apache.lucene.search.DisjunctionMaxBulkScorer$BulkScorerAndNext)`
- `public void addAll(java.util.Collection<T>)`
- `public T add(T)`
- `public T insertWithOverflow(T)`
- `public T updateTop()`
- `public T updateTop(T)`
- `public void clear()`
- `public boolean remove(T)`
- `private boolean upHeap(int)`
- `private void downHeap(int)`


## Detailed Analysis: org.apache.lucene.search.DisjunctionMaxQuery -> org.gnit.lucenekmp.search.DisjunctionMaxQuery

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.search.Matches matches(org.apache.lucene.index.LeafReaderContext, int)`
- `public org.apache.lucene.search.ScorerSupplier scorerSupplier(org.apache.lucene.index.LeafReaderContext)`
- `public boolean isCacheable(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.Explanation explain(org.apache.lucene.index.LeafReaderContext, int)`
- `public org.apache.lucene.search.Scorer scorer(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.BulkScorer bulkScorer(org.apache.lucene.index.LeafReaderContext)`
- `public int count(org.apache.lucene.index.LeafReaderContext)`


## Detailed Analysis: org.apache.lucene.search.DisjunctionMaxScorer -> org.gnit.lucenekmp.search.DisjunctionMaxScorer

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 7
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 3
- **Semantic Completion**: 83%


## Detailed Analysis: org.apache.lucene.search.DisjunctionScoreBlockBoundaryPropagator -> org.gnit.lucenekmp.search.DisjunctionScoreBlockBoundaryPropagator

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 3
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `package-private int advanceShallow(int)`


## Detailed Analysis: org.apache.lucene.search.DisjunctionScorer -> org.gnit.lucenekmp.search.DisjunctionScorer

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 15
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 3
- **Semantic Completion**: 70%


## Detailed Analysis: org.apache.lucene.search.DisjunctionSumScorer -> org.gnit.lucenekmp.search.DisjunctionSumScorer

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 6
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 3
- **Semantic Completion**: 88%


## Detailed Analysis: org.apache.lucene.search.DocIdSet -> org.gnit.lucenekmp.search.DocIdSet

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 3
- **Semantic Completion**: 72%


## Detailed Analysis: org.apache.lucene.search.DocIdSetIterator -> org.gnit.lucenekmp.search.DocIdSetIterator

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.search.DocIdStream -> org.gnit.lucenekmp.search.DocIdStream

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 61%


## Detailed Analysis: org.apache.lucene.search.DocValuesRangeIterator -> org.gnit.lucenekmp.search.DocValuesRangeIterator

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public int docID()`
- `public int nextDoc()`
- `public int advance(int)`
- `protected org.apache.lucene.search.DocValuesRangeIterator$Match match(int)`
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`
- `protected int slowAdvance(int)`
- `public void intoBitSet(int, org.apache.lucene.util.FixedBitSet, int)`


## Detailed Analysis: org.apache.lucene.search.DocValuesRewriteMethod -> org.gnit.lucenekmp.search.DocValuesRewriteMethod

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 4
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void visit(org.apache.lucene.search.QueryVisitor)`
- `public org.apache.lucene.search.Weight createWeight(org.apache.lucene.search.IndexSearcher, org.apache.lucene.search.ScoreMode, float)`
- `public org.apache.lucene.search.Query rewrite(org.apache.lucene.search.IndexSearcher)`
- `protected boolean sameClassAs(java.lang.Object)`
- `protected int classHash()`


## Detailed Analysis: org.apache.lucene.search.FieldComparator -> org.gnit.lucenekmp.search.FieldComparator

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 10
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 7
- **Semantic Completion**: 29%

### Missing Core Methods:
- `public T value(int)`
- `public int compareValues(T, T)`


## Detailed Analysis: org.apache.lucene.search.FieldDoc -> org.gnit.lucenekmp.search.FieldDoc

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 8
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 4%


## Detailed Analysis: org.apache.lucene.search.FieldExistsQuery -> org.gnit.lucenekmp.search.FieldExistsQuery

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public org.apache.lucene.search.ScorerSupplier scorerSupplier(org.apache.lucene.index.LeafReaderContext)`
- `public int count(org.apache.lucene.index.LeafReaderContext)`
- `public boolean isCacheable(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.Explanation explain(org.apache.lucene.index.LeafReaderContext, int)`
- `public org.apache.lucene.search.Matches matches(org.apache.lucene.index.LeafReaderContext, int)`
- `public org.apache.lucene.search.Scorer scorer(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.BulkScorer bulkScorer(org.apache.lucene.index.LeafReaderContext)`


## Detailed Analysis: org.apache.lucene.search.FieldValueHitQueue -> org.gnit.lucenekmp.search.FieldValueHitQueue

### Method Categories:
- **Java Core Business Logic**: 13
- **Java Property Accessors**: 8
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 20
- **KMP Property Accessors**: 8
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 3
- **Semantic Completion**: 62%

### Missing Core Methods:
- `protected boolean lessThan(org.apache.lucene.search.FieldValueHitQueue$Entry, org.apache.lucene.search.FieldValueHitQueue$Entry)`
- `public static org.apache.lucene.search.FieldValueHitQueue<T> create(org.apache.lucene.search.SortField[], int)`


## Detailed Analysis: org.apache.lucene.search.FilterDocIdSetIterator -> org.gnit.lucenekmp.search.FilterDocIdSetIterator

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 90%

### Missing Core Methods:
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.search.FilterLeafCollector -> org.gnit.lucenekmp.search.FilterLeafCollector

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 5
- **Semantic Completion**: 56%

### Missing Core Methods:
- `public void collect(org.apache.lucene.search.DocIdStream)`
- `public org.apache.lucene.search.DocIdSetIterator competitiveIterator()`


## Detailed Analysis: org.apache.lucene.search.FilterScorable -> org.gnit.lucenekmp.search.FilterScorable

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 70%


## Detailed Analysis: org.apache.lucene.search.FilterScorer -> org.gnit.lucenekmp.search.FilterScorer

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 7
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 87%

### Missing Core Methods:
- `public static T unwrapAll(T)`


## Detailed Analysis: org.apache.lucene.search.FilteredDocIdSetIterator -> org.gnit.lucenekmp.search.FilteredDocIdSetIterator

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 92%

### Missing Core Methods:
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.search.HitQueue -> org.gnit.lucenekmp.search.HitQueue

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 4
- **KMP Core Business Logic**: 17
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 3
- **Semantic Completion**: 64%


## Detailed Analysis: org.apache.lucene.search.ImpactsDISI -> org.gnit.lucenekmp.search.ImpactsDISI

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.search.IndexSearcher -> org.gnit.lucenekmp.search.IndexSearcher

### Method Categories:
- **Java Core Business Logic**: 26
- **Java Property Accessors**: 21
- **Java Auto-Generated**: 1
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `protected org.apache.lucene.search.IndexSearcher$LeafSlice[] slices(java.util.List<org.apache.lucene.index.LeafReaderContext>)`
- `public static org.apache.lucene.search.IndexSearcher$LeafSlice[] slices(java.util.List<org.apache.lucene.index.LeafReaderContext>, int, int, boolean)`
- `public org.apache.lucene.index.StoredFields storedFields()`
- `public int count(org.apache.lucene.search.Query)`
- `private org.apache.lucene.search.IndexSearcher$LeafSlice[] computeAndCacheSlices()`
- `private static void enforceDistinctLeaves(org.apache.lucene.search.IndexSearcher$LeafSlice)`
- `public org.apache.lucene.search.TopDocs searchAfter(org.apache.lucene.search.ScoreDoc, org.apache.lucene.search.Query, int)`
- `public org.apache.lucene.search.TopDocs search(org.apache.lucene.search.Query, int)`
- `public void search(org.apache.lucene.search.Query, org.apache.lucene.search.Collector)`
- `public boolean timedOut()`
- `public org.apache.lucene.search.TopFieldDocs search(org.apache.lucene.search.Query, int, org.apache.lucene.search.Sort, boolean)`
- `public org.apache.lucene.search.TopFieldDocs search(org.apache.lucene.search.Query, int, org.apache.lucene.search.Sort)`
- `public org.apache.lucene.search.TopDocs searchAfter(org.apache.lucene.search.ScoreDoc, org.apache.lucene.search.Query, int, org.apache.lucene.search.Sort)`
- `public org.apache.lucene.search.TopFieldDocs searchAfter(org.apache.lucene.search.ScoreDoc, org.apache.lucene.search.Query, int, org.apache.lucene.search.Sort, boolean)`
- `private org.apache.lucene.search.TopFieldDocs searchAfter(org.apache.lucene.search.FieldDoc, org.apache.lucene.search.Query, int, org.apache.lucene.search.Sort, boolean)`
- `public T search(org.apache.lucene.search.Query, org.apache.lucene.search.CollectorManager<C, T>)`
- `private T search(org.apache.lucene.search.Weight, org.apache.lucene.search.CollectorManager<C, T>, C)`
- `protected void search(org.apache.lucene.search.IndexSearcher$LeafReaderContextPartition[], org.apache.lucene.search.Weight, org.apache.lucene.search.Collector)`
- `protected void searchLeaf(org.apache.lucene.index.LeafReaderContext, int, int, org.apache.lucene.search.Weight, org.apache.lucene.search.Collector)`
- `public org.apache.lucene.search.Query rewrite(org.apache.lucene.search.Query)`
- `private org.apache.lucene.search.Query rewrite(org.apache.lucene.search.Query, boolean)`
- `public org.apache.lucene.search.Explanation explain(org.apache.lucene.search.Query, int)`
- `protected org.apache.lucene.search.Explanation explain(org.apache.lucene.search.Weight, int)`
- `public org.apache.lucene.search.Weight createWeight(org.apache.lucene.search.Query, org.apache.lucene.search.ScoreMode, float)`
- `public org.apache.lucene.search.TermStatistics termStatistics(org.apache.lucene.index.Term, int, long)`
- `public org.apache.lucene.search.CollectionStatistics collectionStatistics(java.lang.String)`


## Detailed Analysis: org.apache.lucene.search.KnnByteVectorQuery -> org.gnit.lucenekmp.search.KnnByteVectorQuery

### Method Categories:
- **Java Core Business Logic**: 13
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 4
- **Java Synthetic**: 8
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `protected org.apache.lucene.search.TopDocs approximateSearch(org.apache.lucene.index.LeafReaderContext, org.apache.lucene.util.Bits, int, org.apache.lucene.search.knn.KnnCollectorManager)`
- `package-private org.apache.lucene.search.VectorScorer createVectorScorer(org.apache.lucene.index.LeafReaderContext, org.apache.lucene.index.FieldInfo)`
- `private org.apache.lucene.search.TopDocs searchLeaf(org.apache.lucene.index.LeafReaderContext, org.apache.lucene.search.Weight, org.apache.lucene.search.TimeLimitingKnnCollectorManager)`
- `private org.apache.lucene.search.TopDocs getLeafResults(org.apache.lucene.index.LeafReaderContext, org.apache.lucene.search.Weight, org.apache.lucene.search.TimeLimitingKnnCollectorManager)`
- `private org.apache.lucene.util.BitSet createBitSet(org.apache.lucene.search.DocIdSetIterator, org.apache.lucene.util.Bits, int)`
- `protected org.apache.lucene.search.knn.KnnCollectorManager getKnnCollectorManager(int, org.apache.lucene.search.IndexSearcher)`
- `protected org.apache.lucene.search.TopDocs exactSearch(org.apache.lucene.index.LeafReaderContext, org.apache.lucene.search.DocIdSetIterator, org.apache.lucene.index.QueryTimeout)`
- `protected org.apache.lucene.search.TopDocs mergeLeafResults(org.apache.lucene.search.TopDocs[])`
- `private org.apache.lucene.search.Query createRewrittenQuery(org.apache.lucene.index.IndexReader, org.apache.lucene.search.TopDocs)`
- `package-private static int[] findSegmentStarts(java.util.List<org.apache.lucene.index.LeafReaderContext>, int[])`
- `public org.apache.lucene.search.Weight createWeight(org.apache.lucene.search.IndexSearcher, org.apache.lucene.search.ScoreMode, float)`
- `protected boolean sameClassAs(java.lang.Object)`
- `protected int classHash()`


## Detailed Analysis: org.apache.lucene.search.KnnCollector -> org.gnit.lucenekmp.search.KnnCollector

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 7
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 93%


## Detailed Analysis: org.apache.lucene.search.KnnFloatVectorQuery -> org.gnit.lucenekmp.search.KnnFloatVectorQuery

### Method Categories:
- **Java Core Business Logic**: 13
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 4
- **Java Synthetic**: 8
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `protected org.apache.lucene.search.TopDocs approximateSearch(org.apache.lucene.index.LeafReaderContext, org.apache.lucene.util.Bits, int, org.apache.lucene.search.knn.KnnCollectorManager)`
- `package-private org.apache.lucene.search.VectorScorer createVectorScorer(org.apache.lucene.index.LeafReaderContext, org.apache.lucene.index.FieldInfo)`
- `private org.apache.lucene.search.TopDocs searchLeaf(org.apache.lucene.index.LeafReaderContext, org.apache.lucene.search.Weight, org.apache.lucene.search.TimeLimitingKnnCollectorManager)`
- `private org.apache.lucene.search.TopDocs getLeafResults(org.apache.lucene.index.LeafReaderContext, org.apache.lucene.search.Weight, org.apache.lucene.search.TimeLimitingKnnCollectorManager)`
- `private org.apache.lucene.util.BitSet createBitSet(org.apache.lucene.search.DocIdSetIterator, org.apache.lucene.util.Bits, int)`
- `protected org.apache.lucene.search.knn.KnnCollectorManager getKnnCollectorManager(int, org.apache.lucene.search.IndexSearcher)`
- `protected org.apache.lucene.search.TopDocs exactSearch(org.apache.lucene.index.LeafReaderContext, org.apache.lucene.search.DocIdSetIterator, org.apache.lucene.index.QueryTimeout)`
- `protected org.apache.lucene.search.TopDocs mergeLeafResults(org.apache.lucene.search.TopDocs[])`
- `private org.apache.lucene.search.Query createRewrittenQuery(org.apache.lucene.index.IndexReader, org.apache.lucene.search.TopDocs)`
- `package-private static int[] findSegmentStarts(java.util.List<org.apache.lucene.index.LeafReaderContext>, int[])`
- `public org.apache.lucene.search.Weight createWeight(org.apache.lucene.search.IndexSearcher, org.apache.lucene.search.ScoreMode, float)`
- `protected boolean sameClassAs(java.lang.Object)`
- `protected int classHash()`


## Detailed Analysis: org.apache.lucene.search.LRUQueryCache -> org.gnit.lucenekmp.search.LRUQueryCache

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void collect(int)`
- `public void collect(org.apache.lucene.search.DocIdStream)`
- `public org.apache.lucene.search.DocIdSetIterator competitiveIterator()`
- `public void finish()`


## Detailed Analysis: org.apache.lucene.search.LeafCollector -> org.gnit.lucenekmp.search.LeafCollector

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void collect(int)`
- `public void collect(org.apache.lucene.search.DocIdStream)`
- `public org.apache.lucene.search.DocIdSetIterator competitiveIterator()`
- `public void finish()`


## Detailed Analysis: org.apache.lucene.search.LeafFieldComparator -> org.gnit.lucenekmp.search.LeafFieldComparator

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public int compareBottom(int)`
- `public int compareTop(int)`
- `public org.apache.lucene.search.DocIdSetIterator competitiveIterator()`
- `public void setHitsThresholdReached()`


## Detailed Analysis: org.apache.lucene.search.MaxNonCompetitiveBoostAttribute -> org.gnit.lucenekmp.search.MaxNonCompetitiveBoostAttribute

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.search.MaxScoreAccumulator -> org.gnit.lucenekmp.search.MaxScoreAccumulator

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 93%

### Missing Core Methods:
- `package-private void accumulate(int, float)`


## Detailed Analysis: org.apache.lucene.search.MaxScoreBulkScorer -> org.gnit.lucenekmp.search.MaxScoreBulkScorer

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 7
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 65%


## Detailed Analysis: org.apache.lucene.search.MultiLeafFieldComparator -> org.gnit.lucenekmp.search.MultiLeafFieldComparator

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 2
- **Semantic Completion**: 93%


## Detailed Analysis: org.apache.lucene.search.MultiTermQuery -> org.gnit.lucenekmp.search.MultiTermQuery

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 8
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 3
- **Semantic Completion**: 18%


## Detailed Analysis: org.apache.lucene.search.MultiTermQueryConstantScoreBlendedWrapper -> org.gnit.lucenekmp.search.MultiTermQueryConstantScoreBlendedWrapper

### Method Categories:
- **Java Core Business Logic**: 12
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 15
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 3
- **Semantic Completion**: 0%

### Missing Core Methods:
- `protected org.apache.lucene.search.AbstractMultiTermQueryConstantScoreWrapper$WeightOrDocIdSetIterator rewriteInner(org.apache.lucene.index.LeafReaderContext, int, org.apache.lucene.index.Terms, org.apache.lucene.index.TermsEnum, java.util.List<org.apache.lucene.search.AbstractMultiTermQueryConstantScoreWrapper$TermAndState>, long)`
- `private org.apache.lucene.search.AbstractMultiTermQueryConstantScoreWrapper$WeightOrDocIdSetIterator rewriteAsBooleanQuery(org.apache.lucene.index.LeafReaderContext, java.util.List<org.apache.lucene.search.AbstractMultiTermQueryConstantScoreWrapper$TermAndState>)`
- `private boolean collectTerms(int, org.apache.lucene.index.TermsEnum, java.util.List<org.apache.lucene.search.AbstractMultiTermQueryConstantScoreWrapper$TermAndState>)`
- `private org.apache.lucene.search.Scorer scorerForIterator(org.apache.lucene.search.DocIdSetIterator)`
- `public org.apache.lucene.search.Matches matches(org.apache.lucene.index.LeafReaderContext, int)`
- `public org.apache.lucene.search.ScorerSupplier scorerSupplier(org.apache.lucene.index.LeafReaderContext)`
- `private static long estimateCost(org.apache.lucene.index.Terms, long)`
- `public boolean isCacheable(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.Explanation explain(org.apache.lucene.index.LeafReaderContext, int)`
- `public org.apache.lucene.search.Scorer scorer(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.BulkScorer bulkScorer(org.apache.lucene.index.LeafReaderContext)`
- `public int count(org.apache.lucene.index.LeafReaderContext)`


## Detailed Analysis: org.apache.lucene.search.MultiTermQueryConstantScoreWrapper -> org.gnit.lucenekmp.search.MultiTermQueryConstantScoreWrapper

### Method Categories:
- **Java Core Business Logic**: 12
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 12
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 3
- **Semantic Completion**: 88%

### Missing Core Methods:
- `private static long estimateCost(org.apache.lucene.index.Terms, long)`


## Detailed Analysis: org.apache.lucene.search.Multiset -> org.gnit.lucenekmp.search.Multiset

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 32%


## Detailed Analysis: org.apache.lucene.search.PointInSetQuery -> org.gnit.lucenekmp.search.PointInSetQuery

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.search.ScorerSupplier scorerSupplier(org.apache.lucene.index.LeafReaderContext)`
- `public boolean isCacheable(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.Explanation explain(org.apache.lucene.index.LeafReaderContext, int)`
- `public org.apache.lucene.search.Matches matches(org.apache.lucene.index.LeafReaderContext, int)`
- `public org.apache.lucene.search.Scorer scorer(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.BulkScorer bulkScorer(org.apache.lucene.index.LeafReaderContext)`
- `public int count(org.apache.lucene.index.LeafReaderContext)`


## Detailed Analysis: org.apache.lucene.search.PointRangeQuery -> org.gnit.lucenekmp.search.PointRangeQuery

### Method Categories:
- **Java Core Business Logic**: 14
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `private boolean matches(byte[])`
- `private org.apache.lucene.index.PointValues$Relation relate(byte[], byte[])`
- `private org.apache.lucene.index.PointValues$IntersectVisitor getIntersectVisitor(org.apache.lucene.util.DocIdSetBuilder)`
- `private org.apache.lucene.index.PointValues$IntersectVisitor getInverseIntersectVisitor(org.apache.lucene.util.FixedBitSet, long[])`
- `private boolean checkValidPointValues(org.apache.lucene.index.PointValues)`
- `public org.apache.lucene.search.ScorerSupplier scorerSupplier(org.apache.lucene.index.LeafReaderContext)`
- `public int count(org.apache.lucene.index.LeafReaderContext)`
- `private long pointCount(org.apache.lucene.index.PointValues$PointTree, java.util.function.BiFunction<byte[], byte[], org.apache.lucene.index.PointValues$Relation>, java.util.function.Predicate<byte[]>)`
- `private void pointCount(org.apache.lucene.index.PointValues$IntersectVisitor, org.apache.lucene.index.PointValues$PointTree, long[])`
- `public boolean isCacheable(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.Explanation explain(org.apache.lucene.index.LeafReaderContext, int)`
- `public org.apache.lucene.search.Matches matches(org.apache.lucene.index.LeafReaderContext, int)`
- `public org.apache.lucene.search.Scorer scorer(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.BulkScorer bulkScorer(org.apache.lucene.index.LeafReaderContext)`


## Detailed Analysis: org.apache.lucene.search.Pruning -> org.gnit.lucenekmp.search.Pruning

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 75%


## Detailed Analysis: org.apache.lucene.search.QueryVisitor -> org.gnit.lucenekmp.search.QueryVisitor

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 96%

### Missing Core Methods:
- `public static org.apache.lucene.search.QueryVisitor termCollector(java.util.Set<org.apache.lucene.index.Term>)`


## Detailed Analysis: org.apache.lucene.search.ReqExclScorer -> org.gnit.lucenekmp.search.ReqExclScorer

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static org.apache.lucene.search.DocIdSetIterator asDocIdSetIterator(org.apache.lucene.search.TwoPhaseIterator)`
- `public static org.apache.lucene.search.TwoPhaseIterator unwrap(org.apache.lucene.search.DocIdSetIterator)`


## Detailed Analysis: org.apache.lucene.search.ReqOptSumScorer -> org.gnit.lucenekmp.search.ReqOptSumScorer

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `private void moveToNextBlock(int)`
- `private int advanceImpacts(int)`
- `public int nextDoc()`
- `public int advance(int)`
- `private int advanceInternal(int)`
- `public int docID()`
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`
- `protected int slowAdvance(int)`
- `public void intoBitSet(int, org.apache.lucene.util.FixedBitSet, int)`


## Detailed Analysis: org.apache.lucene.search.Scorable -> org.gnit.lucenekmp.search.Scorable

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 6
- **KMP Synthetic**: 1
- **Semantic Completion**: 69%


## Detailed Analysis: org.apache.lucene.search.Score -> org.gnit.lucenekmp.search.Score

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 6
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 73%


## Detailed Analysis: org.apache.lucene.search.ScoreCachingWrappingScorer -> org.gnit.lucenekmp.search.ScoreCachingWrappingScorer

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 4
- **Semantic Completion**: 65%

### Missing Core Methods:
- `public void collect(org.apache.lucene.search.DocIdStream)`


## Detailed Analysis: org.apache.lucene.search.ScoreDoc -> org.gnit.lucenekmp.search.ScoreDoc

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 6
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 5%


## Detailed Analysis: org.apache.lucene.search.ScoreMode -> org.gnit.lucenekmp.search.ScoreMode

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 85%


## Detailed Analysis: org.apache.lucene.search.Scorer -> org.gnit.lucenekmp.search.Scorer

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 92%


## Detailed Analysis: org.apache.lucene.search.ScorerUtil -> org.gnit.lucenekmp.search.ScorerUtil

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 15
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 3
- **Semantic Completion**: 70%


## Detailed Analysis: org.apache.lucene.search.ScoringRewrite -> org.gnit.lucenekmp.search.ScoringRewrite

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 3
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 8
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `protected org.apache.lucene.search.Query build(org.apache.lucene.search.BooleanQuery$Builder)`
- `protected void addClause(org.apache.lucene.search.BooleanQuery$Builder, org.apache.lucene.index.Term, int, float, org.apache.lucene.index.TermStates)`
- `protected void checkMaxClauseCount(int)`
- `public org.apache.lucene.search.Query rewrite(org.apache.lucene.search.IndexSearcher, org.apache.lucene.search.MultiTermQuery)`
- `protected void addClause(B, org.apache.lucene.index.Term, int, float)`
- `package-private void collectTerms(org.apache.lucene.index.IndexReader, org.apache.lucene.search.MultiTermQuery, org.apache.lucene.search.TermCollectingRewrite$TermCollector)`
- `protected org.apache.lucene.index.TermsEnum getTermsEnum(org.apache.lucene.search.MultiTermQuery, org.apache.lucene.index.Terms, org.apache.lucene.util.AttributeSource)`


## Detailed Analysis: org.apache.lucene.search.SimpleScorable -> org.gnit.lucenekmp.search.SimpleScorable

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 6
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 89%


## Detailed Analysis: org.apache.lucene.search.Sort -> org.gnit.lucenekmp.search.Sort

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.search.Sort rewrite(org.apache.lucene.search.IndexSearcher)`
- `public boolean needsScores()`


## Detailed Analysis: org.apache.lucene.search.SortField -> org.gnit.lucenekmp.search.SortField

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 13
- **Java Auto-Generated**: 3
- **Java Synthetic**: 5
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `protected static org.apache.lucene.search.SortField$Type readType(org.apache.lucene.store.DataInput)`
- `private void serialize(org.apache.lucene.store.DataOutput)`
- `private void initFieldType(java.lang.String, org.apache.lucene.search.SortField$Type)`
- `public org.apache.lucene.search.FieldComparator<?> getComparator(int, org.apache.lucene.search.Pruning)`
- `public org.apache.lucene.search.SortField rewrite(org.apache.lucene.search.IndexSearcher)`
- `public boolean needsScores()`


## Detailed Analysis: org.apache.lucene.search.TaskExecutor -> org.gnit.lucenekmp.search.TaskExecutor

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void run()`
- `public boolean cancel(boolean)`


## Detailed Analysis: org.apache.lucene.search.TermCollectingRewrite -> org.gnit.lucenekmp.search.TermCollectingRewrite

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 6
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 46%


## Detailed Analysis: org.apache.lucene.search.TermInSetQuery -> org.gnit.lucenekmp.search.TermInSetQuery

### Method Categories:
- **Java Core Business Logic**: 24
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `protected void get(org.apache.lucene.util.BytesRefBuilder, org.apache.lucene.util.BytesRef, int)`
- `protected void swap(int, int)`
- `protected int compare(int, int)`
- `public void sort(int, int)`
- `protected org.apache.lucene.util.Sorter radixSorter(org.apache.lucene.util.BytesRefComparator)`
- `protected org.apache.lucene.util.Sorter fallbackSorter(java.util.Comparator<org.apache.lucene.util.BytesRef>)`
- `protected int comparePivot(int)`
- `package-private void checkRange(int, int)`
- `package-private void mergeInPlace(int, int, int)`
- `package-private int lower(int, int, int)`
- `package-private int upper(int, int, int)`
- `package-private int lower2(int, int, int)`
- `package-private int upper2(int, int, int)`
- `package-private void reverse(int, int)`
- `package-private void rotate(int, int, int)`
- `package-private void doRotate(int, int, int)`
- `package-private void binarySort(int, int)`
- `package-private void binarySort(int, int, int)`
- `package-private void insertionSort(int, int)`
- `package-private void heapSort(int, int)`
- `package-private void heapify(int, int)`
- `package-private void siftDown(int, int, int)`
- `package-private static int heapParent(int, int)`
- `package-private static int heapChild(int, int)`


## Detailed Analysis: org.apache.lucene.search.TermQuery -> org.gnit.lucenekmp.search.TermQuery

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 8
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.search.Matches matches(org.apache.lucene.index.LeafReaderContext, int)`
- `public org.apache.lucene.search.ScorerSupplier scorerSupplier(org.apache.lucene.index.LeafReaderContext)`
- `public boolean isCacheable(org.apache.lucene.index.LeafReaderContext)`
- `private org.apache.lucene.index.TermsEnum getTermsEnum(org.apache.lucene.index.LeafReaderContext)`
- `private boolean termNotInReader(org.apache.lucene.index.LeafReader, org.apache.lucene.index.Term)`
- `public org.apache.lucene.search.Explanation explain(org.apache.lucene.index.LeafReaderContext, int)`
- `public int count(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.Scorer scorer(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.BulkScorer bulkScorer(org.apache.lucene.index.LeafReaderContext)`


## Detailed Analysis: org.apache.lucene.search.TermScorer -> org.gnit.lucenekmp.search.TermScorer

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 7
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 86%


## Detailed Analysis: org.apache.lucene.search.TermStatistics -> org.gnit.lucenekmp.search.TermStatistics

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 33%

### Missing Core Methods:
- `public long docFreq()`
- `public long totalTermFreq()`


## Detailed Analysis: org.apache.lucene.search.TimeLimitingBulkScorer -> org.gnit.lucenekmp.search.TimeLimitingBulkScorer

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public int score(org.apache.lucene.search.LeafCollector, org.apache.lucene.util.Bits, int, int)`


## Detailed Analysis: org.apache.lucene.search.TimeLimitingKnnCollectorManager -> org.gnit.lucenekmp.search.TimeLimitingKnnCollectorManager

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 7
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 93%


## Detailed Analysis: org.apache.lucene.search.TopDocs -> org.gnit.lucenekmp.search.TopDocs

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public boolean lessThan(org.apache.lucene.search.TopDocs$ShardRef, org.apache.lucene.search.TopDocs$ShardRef)`
- `public void addAll(java.util.Collection<T>)`
- `public T add(T)`
- `public T insertWithOverflow(T)`
- `public T updateTop()`
- `public T updateTop(T)`
- `public void clear()`
- `public boolean remove(T)`
- `private boolean upHeap(int)`
- `private void downHeap(int)`


## Detailed Analysis: org.apache.lucene.search.TopDocsCollector -> org.gnit.lucenekmp.search.TopDocsCollector

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `protected void populateResults(org.apache.lucene.search.ScoreDoc[], int)`
- `protected org.apache.lucene.search.TopDocs newTopDocs(org.apache.lucene.search.ScoreDoc[], int)`
- `protected int topDocsSize()`
- `public org.apache.lucene.search.TopDocs topDocs()`
- `public org.apache.lucene.search.TopDocs topDocs(int)`
- `public org.apache.lucene.search.TopDocs topDocs(int, int)`
- `public org.apache.lucene.search.LeafCollector getLeafCollector(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.ScoreMode scoreMode()`


## Detailed Analysis: org.apache.lucene.search.TopFieldCollector -> org.gnit.lucenekmp.search.TopFieldCollector

### Method Categories:
- **Java Core Business Logic**: 16
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 6
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 5
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.search.LeafCollector getLeafCollector(org.apache.lucene.index.LeafReaderContext)`
- `package-private static boolean canEarlyTerminate(org.apache.lucene.search.Sort, org.apache.lucene.search.Sort)`
- `private static boolean canEarlyTerminateOnDocId(org.apache.lucene.search.Sort)`
- `private static boolean canEarlyTerminateOnPrefix(org.apache.lucene.search.Sort, org.apache.lucene.search.Sort)`
- `public org.apache.lucene.search.ScoreMode scoreMode()`
- `protected void updateGlobalMinCompetitiveScore(org.apache.lucene.search.Scorable)`
- `protected void updateMinCompetitiveScore(org.apache.lucene.search.Scorable)`
- `public static void populateScores(org.apache.lucene.search.ScoreDoc[], org.apache.lucene.search.IndexSearcher, org.apache.lucene.search.Query)`
- `package-private void add(int, int)`
- `package-private void updateBottom(int)`
- `protected void populateResults(org.apache.lucene.search.ScoreDoc[], int)`
- `protected org.apache.lucene.search.TopDocs newTopDocs(org.apache.lucene.search.ScoreDoc[], int)`
- `public org.apache.lucene.search.TopFieldDocs topDocs()`
- `protected int topDocsSize()`
- `public org.apache.lucene.search.TopDocs topDocs(int)`
- `public org.apache.lucene.search.TopDocs topDocs(int, int)`


## Detailed Analysis: org.apache.lucene.search.TopFieldDocs -> org.gnit.lucenekmp.search.TopFieldDocs

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 3
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 6
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `package-private static boolean tieBreakLessThan(org.apache.lucene.search.TopDocs$ShardRef, org.apache.lucene.search.ScoreDoc, org.apache.lucene.search.TopDocs$ShardRef, org.apache.lucene.search.ScoreDoc, java.util.Comparator<org.apache.lucene.search.ScoreDoc>)`
- `public static org.apache.lucene.search.TopDocs merge(int, org.apache.lucene.search.TopDocs[])`
- `public static org.apache.lucene.search.TopDocs merge(int, int, org.apache.lucene.search.TopDocs[])`
- `public static org.apache.lucene.search.TopDocs merge(int, int, org.apache.lucene.search.TopDocs[], java.util.Comparator<org.apache.lucene.search.ScoreDoc>)`
- `public static org.apache.lucene.search.TopFieldDocs merge(org.apache.lucene.search.Sort, int, org.apache.lucene.search.TopFieldDocs[])`
- `public static org.apache.lucene.search.TopFieldDocs merge(org.apache.lucene.search.Sort, int, int, org.apache.lucene.search.TopFieldDocs[])`
- `public static org.apache.lucene.search.TopFieldDocs merge(org.apache.lucene.search.Sort, int, int, org.apache.lucene.search.TopFieldDocs[], java.util.Comparator<org.apache.lucene.search.ScoreDoc>)`
- `private static org.apache.lucene.search.TopDocs mergeAux(org.apache.lucene.search.Sort, int, int, org.apache.lucene.search.TopDocs[], java.util.Comparator<org.apache.lucene.search.ScoreDoc>)`
- `public static org.apache.lucene.search.TopDocs rrf(int, int, org.apache.lucene.search.TopDocs[])`


## Detailed Analysis: org.apache.lucene.search.TopKnnCollector -> org.gnit.lucenekmp.search.TopKnnCollector

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 8
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 83%


## Detailed Analysis: org.apache.lucene.search.TopScoreDocCollector -> org.gnit.lucenekmp.search.TopScoreDocCollector

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 6
- **Semantic Completion**: 71%

### Missing Core Methods:
- `public void collect(org.apache.lucene.search.DocIdStream)`
- `public org.apache.lucene.search.DocIdSetIterator competitiveIterator()`
- `public void finish()`


## Detailed Analysis: org.apache.lucene.search.TopTermsRewrite -> org.gnit.lucenekmp.search.TopTermsRewrite

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 6
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 54%


## Detailed Analysis: org.apache.lucene.search.TotalHitCountCollector -> org.gnit.lucenekmp.search.TotalHitCountCollector

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 5
- **Semantic Completion**: 61%

### Missing Core Methods:
- `public org.apache.lucene.search.DocIdSetIterator competitiveIterator()`
- `public void finish()`


## Detailed Analysis: org.apache.lucene.search.TotalHitCountCollectorManager -> org.gnit.lucenekmp.search.TotalHitCountCollectorManager

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.search.LeafCollector getLeafCollector(org.apache.lucene.index.LeafReaderContext)`
- `public org.apache.lucene.search.ScoreMode scoreMode()`
- `protected org.apache.lucene.search.LeafCollector createLeafCollector()`


## Detailed Analysis: org.apache.lucene.search.TotalHits -> org.gnit.lucenekmp.search.TotalHits

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.search.TwoPhaseIterator -> org.gnit.lucenekmp.search.TwoPhaseIterator

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 84%

### Missing Core Methods:
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.search.UsageTrackingQueryCachingPolicy -> org.gnit.lucenekmp.search.UsageTrackingQueryCachingPolicy

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 93%

### Missing Core Methods:
- `protected int minFrequencyToCache(org.apache.lucene.search.Query)`
- `public void onUse(org.apache.lucene.search.Query)`
- `package-private int frequency(org.apache.lucene.search.Query)`
- `public boolean shouldCache(org.apache.lucene.search.Query)`


## Detailed Analysis: org.apache.lucene.search.VectorScorer -> org.gnit.lucenekmp.search.VectorScorer

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.search.WANDScorer -> org.gnit.lucenekmp.search.WANDScorer

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.search.Weight -> org.gnit.lucenekmp.search.Weight

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 16%

### Missing Core Methods:
- `public int score(org.apache.lucene.search.LeafCollector, org.apache.lucene.util.Bits, int, int)`
- `private static void scoreIterator(org.apache.lucene.search.LeafCollector, org.apache.lucene.util.Bits, org.apache.lucene.search.DocIdSetIterator, int)`
- `private static void scoreTwoPhaseIterator(org.apache.lucene.search.LeafCollector, org.apache.lucene.util.Bits, org.apache.lucene.search.DocIdSetIterator, org.apache.lucene.search.TwoPhaseIterator, int)`
- `private static void scoreCompetitiveIterator(org.apache.lucene.search.LeafCollector, org.apache.lucene.util.Bits, org.apache.lucene.search.DocIdSetIterator, org.apache.lucene.search.DocIdSetIterator, int)`
- `private static void scoreTwoPhaseOrCompetitiveIterator(org.apache.lucene.search.LeafCollector, org.apache.lucene.util.Bits, org.apache.lucene.search.DocIdSetIterator, org.apache.lucene.search.TwoPhaseIterator, org.apache.lucene.search.DocIdSetIterator, int)`


## Detailed Analysis: org.apache.lucene.search.comparators.DocComparator -> org.gnit.lucenekmp.search.comparators.DocComparator

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public int compareBottom(int)`
- `public int compareTop(int)`
- `public org.apache.lucene.search.DocIdSetIterator competitiveIterator()`
- `public void setHitsThresholdReached()`
- `private void updateIterator()`


## Detailed Analysis: org.apache.lucene.search.comparators.DoubleComparator -> org.gnit.lucenekmp.search.comparators.DoubleComparator

### Method Categories:
- **Java Core Business Logic**: 12
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 12
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 6
- **Semantic Completion**: 89%


## Detailed Analysis: org.apache.lucene.search.comparators.FloatComparator -> org.gnit.lucenekmp.search.comparators.FloatComparator

### Method Categories:
- **Java Core Business Logic**: 12
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 12
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 6
- **Semantic Completion**: 89%


## Detailed Analysis: org.apache.lucene.search.comparators.IntComparator -> org.gnit.lucenekmp.search.comparators.IntComparator

### Method Categories:
- **Java Core Business Logic**: 12
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 12
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 6
- **Semantic Completion**: 89%


## Detailed Analysis: org.apache.lucene.search.comparators.LongComparator -> org.gnit.lucenekmp.search.comparators.LongComparator

### Method Categories:
- **Java Core Business Logic**: 12
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 12
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 6
- **Semantic Completion**: 89%


## Detailed Analysis: org.apache.lucene.search.comparators.MinDocIterator -> org.gnit.lucenekmp.search.comparators.MinDocIterator

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 70%

### Missing Core Methods:
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.search.comparators.NumericComparator -> org.gnit.lucenekmp.search.comparators.NumericComparator

### Method Categories:
- **Java Core Business Logic**: 11
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 7
- **Semantic Completion**: 0%

### Missing Core Methods:
- `protected org.apache.lucene.index.NumericDocValues getNumericDocValues(org.apache.lucene.index.LeafReaderContext, java.lang.String)`
- `public void setHitsThresholdReached()`
- `private void updateCompetitiveIterator()`
- `private void updateSkipInterval(boolean)`
- `private void encodeBottom()`
- `private void encodeTop()`
- `public org.apache.lucene.search.DocIdSetIterator competitiveIterator()`
- `protected long bottomAsComparableLong()`
- `protected long topAsComparableLong()`
- `public int compareBottom(int)`
- `public int compareTop(int)`


## Detailed Analysis: org.apache.lucene.search.comparators.TermOrdValComparator -> org.gnit.lucenekmp.search.comparators.TermOrdValComparator

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 13
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 2
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public int docID()`
- `public int nextDoc()`
- `public int advance(int)`
- `public void intoBitSet(int, org.apache.lucene.util.FixedBitSet, int)`
- `private void update(int, int)`
- `private void init(int, int)`
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`
- `protected int slowAdvance(int)`


## Detailed Analysis: org.apache.lucene.search.knn.KnnSearchStrategy -> org.gnit.lucenekmp.search.knn.KnnSearchStrategy

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 2
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 0
- **Semantic Completion**: 11%

### Missing Core Methods:
- `public int filteredSearchThreshold()`
- `public boolean useFilteredSearch(float)`


## Detailed Analysis: org.apache.lucene.search.knn.MultiLeafKnnCollector -> org.gnit.lucenekmp.search.knn.MultiLeafKnnCollector

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public boolean collect(int, float)`
- `public float minCompetitiveSimilarity()`
- `public boolean earlyTerminated()`
- `public void incVisitedCount(int)`
- `public long visitedCount()`
- `public long visitLimit()`
- `public org.apache.lucene.search.TopDocs topDocs()`


## Detailed Analysis: org.apache.lucene.search.similarities.BM25Similarity -> org.gnit.lucenekmp.search.similarities.BM25Similarity

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public float score(float, long)`
- `public org.apache.lucene.search.Explanation explain(org.apache.lucene.search.Explanation, long)`
- `private org.apache.lucene.search.Explanation explainTF(org.apache.lucene.search.Explanation, long)`
- `private java.util.List<org.apache.lucene.search.Explanation> explainConstantFactors()`


## Detailed Analysis: org.apache.lucene.store.AlreadyClosedException -> org.gnit.lucenekmp.store.AlreadyClosedException

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.store.BaseDirectory -> org.gnit.lucenekmp.store.BaseDirectory

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 89%

### Missing Core Methods:
- `protected static java.lang.String getTempFileName(java.lang.String, java.lang.String, long)`


## Detailed Analysis: org.apache.lucene.store.BufferedChecksum -> org.gnit.lucenekmp.store.BufferedChecksum

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public void update(int)`
- `public void update(byte[], int, int)`
- `package-private void updateShort(short)`
- `package-private void updateInt(int)`
- `package-private void updateLong(long)`
- `package-private void updateLongs(long[], int, int)`
- `public void reset()`
- `private void flush()`


## Detailed Analysis: org.apache.lucene.store.BufferedChecksumIndexInput -> org.gnit.lucenekmp.store.BufferedChecksumIndexInput

### Method Categories:
- **Java Core Business Logic**: 27
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 2
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 27
- **KMP Property Accessors**: 6
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 2
- **Semantic Completion**: 96%


## Detailed Analysis: org.apache.lucene.store.BufferedIndexInput -> org.gnit.lucenekmp.store.BufferedIndexInput

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 37
- **KMP Property Accessors**: 10
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 6
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.store.ByteArrayDataInput -> org.gnit.lucenekmp.store.ByteArrayDataInput

### Method Categories:
- **Java Core Business Logic**: 21
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 22
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 2
- **Semantic Completion**: 95%


## Detailed Analysis: org.apache.lucene.store.ByteArrayDataOutput -> org.gnit.lucenekmp.store.ByteArrayDataOutput

### Method Categories:
- **Java Core Business Logic**: 19
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 20
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 94%


## Detailed Analysis: org.apache.lucene.store.ByteBuffersDataInput -> org.gnit.lucenekmp.store.ByteBuffersDataInput

### Method Categories:
- **Java Core Business Logic**: 37
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 2
- **Java Synthetic**: 6
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 90%

### Missing Core Methods:
- `public long ramBytesUsed()`
- `public byte readByte()`
- `public void readBytes(java.nio.ByteBuffer, int)`
- `public void readBytes(byte[], int, int)`
- `public short readShort()`
- `public int readInt()`
- `public long readLong()`
- `public void readGroupVInt(int[], int)`
- `public byte readByte(long)`
- `public void readBytes(long, byte[], int, int)`
- `public short readShort(long)`
- `public int readInt(long)`
- `public long readLong(long)`
- `public void readFloats(float[], int, int)`
- `public void readLongs(long[], int, int)`
- `private java.nio.FloatBuffer getFloatBuffer(long)`
- `private java.nio.LongBuffer getLongBuffer(long)`
- `public void seek(long)`
- `public void skipBytes(long)`
- `public org.apache.lucene.store.ByteBuffersDataInput slice(long, long)`
- `private int blockIndex(long)`
- `private int blockOffset(long)`
- `private int blockSize()`
- `public void readBytes(byte[], int, int, boolean)`
- `public int readVInt()`
- `public int readZInt()`
- `public void readInts(int[], int, int)`
- `public long readVLong()`
- `public long readZLong()`
- `public java.lang.String readString()`
- `public java.util.Map<java.lang.String, java.lang.String> readMapOfStrings()`
- `public java.util.Set<java.lang.String> readSetOfStrings()`
- `public void prefetch(long, long)`


## Detailed Analysis: org.apache.lucene.store.ByteBuffersDataOutput -> org.gnit.lucenekmp.store.ByteBuffersDataOutput

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public java.nio.ByteBuffer allocate(int)`
- `public void reuse(java.nio.ByteBuffer)`


## Detailed Analysis: org.apache.lucene.store.ChecksumIndexInput -> org.gnit.lucenekmp.store.ChecksumIndexInput

### Method Categories:
- **Java Core Business Logic**: 27
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 2
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public void seek(long)`
- `private void skipByReading(long)`
- `public void close()`
- `public void skipBytes(long)`
- `public org.apache.lucene.store.IndexInput slice(java.lang.String, long, long)`
- `public org.apache.lucene.store.IndexInput slice(java.lang.String, long, long, org.apache.lucene.store.ReadAdvice)`
- `protected java.lang.String getFullSliceDescription(java.lang.String)`
- `public org.apache.lucene.store.RandomAccessInput randomAccessSlice(long, long)`
- `public void prefetch(long, long)`
- `public void updateReadAdvice(org.apache.lucene.store.ReadAdvice)`
- `public byte readByte()`
- `public void readBytes(byte[], int, int)`
- `public void readBytes(byte[], int, int, boolean)`
- `public short readShort()`
- `public int readInt()`
- `public void readGroupVInt(int[], int)`
- `public int readVInt()`
- `public int readZInt()`
- `public long readLong()`
- `public void readLongs(long[], int, int)`
- `public void readInts(int[], int, int)`
- `public void readFloats(float[], int, int)`
- `public long readVLong()`
- `public long readZLong()`
- `public java.lang.String readString()`
- `public java.util.Map<java.lang.String, java.lang.String> readMapOfStrings()`
- `public java.util.Set<java.lang.String> readSetOfStrings()`


## Detailed Analysis: org.apache.lucene.store.DataOutput -> org.gnit.lucenekmp.store.DataOutput

### Method Categories:
- **Java Core Business Logic**: 17
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public void writeByte(byte)`
- `public void writeBytes(byte[], int)`
- `public void writeBytes(byte[], int, int)`
- `public void writeInt(int)`
- `public void writeShort(short)`
- `public void writeVInt(int)`
- `public void writeZInt(int)`
- `public void writeLong(long)`
- `public void writeVLong(long)`
- `private void writeSignedVLong(long)`
- `public void writeZLong(long)`
- `public void writeString(java.lang.String)`
- `public void copyBytes(org.apache.lucene.store.DataInput, long)`
- `public void writeMapOfStrings(java.util.Map<java.lang.String, java.lang.String>)`
- `public void writeSetOfStrings(java.util.Set<java.lang.String>)`
- `public void writeGroupVInts(long[], int)`
- `public void writeGroupVInts(int[], int)`


## Detailed Analysis: org.apache.lucene.store.Directory -> org.gnit.lucenekmp.store.Directory

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public java.lang.String[] listAll()`
- `public void deleteFile(java.lang.String)`
- `public long fileLength(java.lang.String)`
- `public org.apache.lucene.store.IndexOutput createOutput(java.lang.String, org.apache.lucene.store.IOContext)`
- `public org.apache.lucene.store.IndexOutput createTempOutput(java.lang.String, java.lang.String, org.apache.lucene.store.IOContext)`
- `public void sync(java.util.Collection<java.lang.String>)`
- `public void syncMetaData()`
- `public void rename(java.lang.String, java.lang.String)`
- `public org.apache.lucene.store.IndexInput openInput(java.lang.String, org.apache.lucene.store.IOContext)`
- `public org.apache.lucene.store.ChecksumIndexInput openChecksumInput(java.lang.String)`
- `public org.apache.lucene.store.Lock obtainLock(java.lang.String)`
- `public void close()`
- `public void copyFrom(org.apache.lucene.store.Directory, java.lang.String, java.lang.String, org.apache.lucene.store.IOContext)`
- `protected void ensureOpen()`


## Detailed Analysis: org.apache.lucene.store.FSDirectory -> org.gnit.lucenekmp.store.FSDirectory

### Method Categories:
- **Java Core Business Logic**: 20
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 14%

### Missing Core Methods:
- `public void writeByte(byte)`
- `public void writeBytes(byte[], int, int)`
- `public void writeShort(short)`
- `public void writeInt(int)`
- `public void writeLong(long)`
- `public long alignFilePointer(int)`
- `public static long alignOffset(long, int)`
- `public void writeBytes(byte[], int)`
- `public void writeVInt(int)`
- `public void writeZInt(int)`
- `public void writeVLong(long)`
- `private void writeSignedVLong(long)`
- `public void writeZLong(long)`
- `public void writeString(java.lang.String)`
- `public void copyBytes(org.apache.lucene.store.DataInput, long)`
- `public void writeMapOfStrings(java.util.Map<java.lang.String, java.lang.String>)`
- `public void writeSetOfStrings(java.util.Set<java.lang.String>)`
- `public void writeGroupVInts(long[], int)`
- `public void writeGroupVInts(int[], int)`


## Detailed Analysis: org.apache.lucene.store.FSLockFactory -> org.gnit.lucenekmp.store.FSLockFactory

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public org.apache.lucene.store.Lock obtainLock(org.apache.lucene.store.Directory, java.lang.String)`
- `protected org.apache.lucene.store.Lock obtainFSLock(org.apache.lucene.store.FSDirectory, java.lang.String)`


## Detailed Analysis: org.apache.lucene.store.FilterDirectory -> org.gnit.lucenekmp.store.FilterDirectory

### Method Categories:
- **Java Core Business Logic**: 16
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public java.lang.String[] listAll()`
- `public void deleteFile(java.lang.String)`
- `public long fileLength(java.lang.String)`
- `public org.apache.lucene.store.IndexOutput createOutput(java.lang.String, org.apache.lucene.store.IOContext)`
- `public org.apache.lucene.store.IndexOutput createTempOutput(java.lang.String, java.lang.String, org.apache.lucene.store.IOContext)`
- `public void sync(java.util.Collection<java.lang.String>)`
- `public void rename(java.lang.String, java.lang.String)`
- `public void syncMetaData()`
- `public org.apache.lucene.store.IndexInput openInput(java.lang.String, org.apache.lucene.store.IOContext)`
- `public org.apache.lucene.store.Lock obtainLock(java.lang.String)`
- `public void close()`
- `protected void ensureOpen()`
- `public org.apache.lucene.store.ChecksumIndexInput openChecksumInput(java.lang.String)`
- `public void copyFrom(org.apache.lucene.store.Directory, java.lang.String, java.lang.String, org.apache.lucene.store.IOContext)`
- `protected static java.lang.String getTempFileName(java.lang.String, java.lang.String, long)`


## Detailed Analysis: org.apache.lucene.store.FilterIndexInput -> org.gnit.lucenekmp.store.FilterIndexInput

### Method Categories:
- **Java Core Business Logic**: 28
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 2
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 76%

### Missing Core Methods:
- `public void close()`
- `public void seek(long)`
- `public org.apache.lucene.store.IndexInput slice(java.lang.String, long, long)`
- `public byte readByte()`
- `public void readBytes(byte[], int, int)`
- `public void skipBytes(long)`
- `public org.apache.lucene.store.IndexInput slice(java.lang.String, long, long, org.apache.lucene.store.ReadAdvice)`
- `protected java.lang.String getFullSliceDescription(java.lang.String)`
- `public org.apache.lucene.store.RandomAccessInput randomAccessSlice(long, long)`
- `public void prefetch(long, long)`
- `public void updateReadAdvice(org.apache.lucene.store.ReadAdvice)`
- `public void readBytes(byte[], int, int, boolean)`
- `public short readShort()`
- `public int readInt()`
- `public void readGroupVInt(int[], int)`
- `public int readVInt()`
- `public int readZInt()`
- `public long readLong()`
- `public void readLongs(long[], int, int)`
- `public void readInts(int[], int, int)`
- `public void readFloats(float[], int, int)`
- `public long readVLong()`
- `public long readZLong()`
- `public java.lang.String readString()`
- `public java.util.Map<java.lang.String, java.lang.String> readMapOfStrings()`
- `public java.util.Set<java.lang.String> readSetOfStrings()`


## Detailed Analysis: org.apache.lucene.store.FilterIndexOutput -> org.gnit.lucenekmp.store.FilterIndexOutput

### Method Categories:
- **Java Core Business Logic**: 21
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public void close()`
- `public void writeByte(byte)`
- `public void writeBytes(byte[], int, int)`
- `public long alignFilePointer(int)`
- `public static long alignOffset(long, int)`
- `public void writeBytes(byte[], int)`
- `public void writeInt(int)`
- `public void writeShort(short)`
- `public void writeVInt(int)`
- `public void writeZInt(int)`
- `public void writeLong(long)`
- `public void writeVLong(long)`
- `private void writeSignedVLong(long)`
- `public void writeZLong(long)`
- `public void writeString(java.lang.String)`
- `public void copyBytes(org.apache.lucene.store.DataInput, long)`
- `public void writeMapOfStrings(java.util.Map<java.lang.String, java.lang.String>)`
- `public void writeSetOfStrings(java.util.Set<java.lang.String>)`
- `public void writeGroupVInts(long[], int)`
- `public void writeGroupVInts(int[], int)`


## Detailed Analysis: org.apache.lucene.store.FlushInfo -> org.gnit.lucenekmp.store.FlushInfo

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 6
- **KMP Synthetic**: 1
- **Semantic Completion**: 23%

### Missing Core Methods:
- `public int numDocs()`
- `public long estimatedSegmentSize()`


## Detailed Analysis: org.apache.lucene.store.IOContext -> org.gnit.lucenekmp.store.IOContext

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static org.apache.lucene.store.IOContext$Context valueOf(java.lang.String)`


## Detailed Analysis: org.apache.lucene.store.IndexInput -> org.gnit.lucenekmp.store.IndexInput

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 2
- **Semantic Completion**: 94%


## Detailed Analysis: org.apache.lucene.store.IndexOutput -> org.gnit.lucenekmp.store.IndexOutput

### Method Categories:
- **Java Core Business Logic**: 20
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public void close()`
- `public long alignFilePointer(int)`
- `public void writeByte(byte)`
- `public void writeBytes(byte[], int)`
- `public void writeBytes(byte[], int, int)`
- `public void writeInt(int)`
- `public void writeShort(short)`
- `public void writeVInt(int)`
- `public void writeZInt(int)`
- `public void writeLong(long)`
- `public void writeVLong(long)`
- `private void writeSignedVLong(long)`
- `public void writeZLong(long)`
- `public void writeString(java.lang.String)`
- `public void copyBytes(org.apache.lucene.store.DataInput, long)`
- `public void writeMapOfStrings(java.util.Map<java.lang.String, java.lang.String>)`
- `public void writeSetOfStrings(java.util.Set<java.lang.String>)`
- `public void writeGroupVInts(long[], int)`
- `public void writeGroupVInts(int[], int)`


## Detailed Analysis: org.apache.lucene.store.LockObtainFailedException -> org.gnit.lucenekmp.store.LockObtainFailedException

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.store.LockValidatingDirectoryWrapper -> org.gnit.lucenekmp.store.LockValidatingDirectoryWrapper

### Method Categories:
- **Java Core Business Logic**: 16
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 96%

### Missing Core Methods:
- `public static org.apache.lucene.store.Directory unwrap(org.apache.lucene.store.Directory)`
- `protected static java.lang.String getTempFileName(java.lang.String, java.lang.String, long)`


## Detailed Analysis: org.apache.lucene.store.MMapDirectory -> org.gnit.lucenekmp.store.MMapDirectory

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.store.IndexInput openInput(java.nio.file.Path, org.apache.lucene.store.IOContext, int, boolean, java.util.Optional<java.lang.String>, A)`
- `public boolean supportsMadvise()`
- `public java.io.IOException convertMapFailedIOException(java.io.IOException, java.lang.String, long)`


## Detailed Analysis: org.apache.lucene.store.MergeInfo -> org.gnit.lucenekmp.store.MergeInfo

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 8
- **KMP Synthetic**: 1
- **Semantic Completion**: 28%

### Missing Core Methods:
- `public int totalMaxDoc()`
- `public long estimatedMergeBytes()`
- `public int mergeMaxNumSegments()`


## Detailed Analysis: org.apache.lucene.store.NIOFSDirectory -> org.gnit.lucenekmp.store.NIOFSDirectory

### Method Categories:
- **Java Core Business Logic**: 24
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public org.apache.lucene.store.IndexInput openInput(java.lang.String, org.apache.lucene.store.IOContext)`
- `public static org.apache.lucene.store.FSDirectory open(java.nio.file.Path)`
- `public static org.apache.lucene.store.FSDirectory open(java.nio.file.Path, org.apache.lucene.store.LockFactory)`
- `public static java.lang.String[] listAll(java.nio.file.Path)`
- `private static java.lang.String[] listAll(java.nio.file.Path, java.util.Set<java.lang.String>)`
- `public java.lang.String[] listAll()`
- `public long fileLength(java.lang.String)`
- `public org.apache.lucene.store.IndexOutput createOutput(java.lang.String, org.apache.lucene.store.IOContext)`
- `public org.apache.lucene.store.IndexOutput createTempOutput(java.lang.String, java.lang.String, org.apache.lucene.store.IOContext)`
- `protected void ensureCanRead(java.lang.String)`
- `public void sync(java.util.Collection<java.lang.String>)`
- `public void rename(java.lang.String, java.lang.String)`
- `public void syncMetaData()`
- `public void close()`
- `protected void fsync(java.lang.String)`
- `public void deleteFile(java.lang.String)`
- `public void deletePendingFiles()`
- `private void maybeDeletePendingFiles()`
- `private void privateDeleteFile(java.lang.String, boolean)`
- `public org.apache.lucene.store.Lock obtainLock(java.lang.String)`
- `protected void ensureOpen()`
- `public org.apache.lucene.store.ChecksumIndexInput openChecksumInput(java.lang.String)`
- `public void copyFrom(org.apache.lucene.store.Directory, java.lang.String, java.lang.String, org.apache.lucene.store.IOContext)`
- `protected static java.lang.String getTempFileName(java.lang.String, java.lang.String, long)`


## Detailed Analysis: org.apache.lucene.store.NativeFSLockFactory -> org.gnit.lucenekmp.store.NativeFSLockFactory

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 47%


## Detailed Analysis: org.apache.lucene.store.OutputStreamIndexOutput -> org.gnit.lucenekmp.store.OutputStreamIndexOutput

### Method Categories:
- **Java Core Business Logic**: 20
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 11
- **KMP Property Accessors**: 7
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 26%

### Missing Core Methods:
- `public void writeByte(byte)`
- `public void writeBytes(byte[], int, int)`
- `public long alignFilePointer(int)`
- `public static long alignOffset(long, int)`
- `public void writeBytes(byte[], int)`
- `public void writeVInt(int)`
- `public void writeZInt(int)`
- `public void writeVLong(long)`
- `private void writeSignedVLong(long)`
- `public void writeZLong(long)`
- `public void writeString(java.lang.String)`
- `public void copyBytes(org.apache.lucene.store.DataInput, long)`
- `public void writeMapOfStrings(java.util.Map<java.lang.String, java.lang.String>)`
- `public void writeSetOfStrings(java.util.Set<java.lang.String>)`
- `public void writeGroupVInts(long[], int)`
- `public void writeGroupVInts(int[], int)`


## Detailed Analysis: org.apache.lucene.store.RandomAccessInput -> org.gnit.lucenekmp.store.RandomAccessInput

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public byte readByte(long)`
- `public void readBytes(long, byte[], int, int)`
- `public short readShort(long)`
- `public int readInt(long)`
- `public long readLong(long)`
- `public void prefetch(long, long)`


## Detailed Analysis: org.apache.lucene.store.RateLimitedIndexOutput -> org.gnit.lucenekmp.store.RateLimitedIndexOutput

### Method Categories:
- **Java Core Business Logic**: 22
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void writeByte(byte)`
- `public void writeBytes(byte[], int, int)`
- `public void writeInt(int)`
- `public void writeShort(short)`
- `public void writeLong(long)`
- `private void checkRate()`
- `public static org.apache.lucene.store.IndexOutput unwrap(org.apache.lucene.store.IndexOutput)`
- `public void close()`
- `public long alignFilePointer(int)`
- `public static long alignOffset(long, int)`
- `public void writeBytes(byte[], int)`
- `public void writeVInt(int)`
- `public void writeZInt(int)`
- `public void writeVLong(long)`
- `private void writeSignedVLong(long)`
- `public void writeZLong(long)`
- `public void writeString(java.lang.String)`
- `public void copyBytes(org.apache.lucene.store.DataInput, long)`
- `public void writeMapOfStrings(java.util.Map<java.lang.String, java.lang.String>)`
- `public void writeSetOfStrings(java.util.Set<java.lang.String>)`
- `public void writeGroupVInts(long[], int)`
- `public void writeGroupVInts(int[], int)`


## Detailed Analysis: org.apache.lucene.store.RateLimiter -> org.gnit.lucenekmp.store.RateLimiter

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public long pause(long)`


## Detailed Analysis: org.apache.lucene.store.ReadAdvice -> org.gnit.lucenekmp.store.ReadAdvice

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 75%


## Detailed Analysis: org.apache.lucene.store.TrackingDirectoryWrapper -> org.gnit.lucenekmp.store.TrackingDirectoryWrapper

### Method Categories:
- **Java Core Business Logic**: 17
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 15
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 96%

### Missing Core Methods:
- `public static org.apache.lucene.store.Directory unwrap(org.apache.lucene.store.Directory)`
- `protected static java.lang.String getTempFileName(java.lang.String, java.lang.String, long)`


## Detailed Analysis: org.apache.lucene.util.Accountable -> org.gnit.lucenekmp.util.Accountable

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public long ramBytesUsed()`


## Detailed Analysis: org.apache.lucene.util.Accountables -> org.gnit.lucenekmp.util.Accountables

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 1
- **Semantic Completion**: 90%


## Detailed Analysis: org.apache.lucene.util.ArrayIntroSorter -> org.gnit.lucenekmp.util.ArrayIntroSorter

### Method Categories:
- **Java Core Business Logic**: 23
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 21
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 99%

### Missing Core Methods:
- `package-private static int heapParent(int, int)`
- `package-private static int heapChild(int, int)`


## Detailed Analysis: org.apache.lucene.util.ArrayTimSorter -> org.gnit.lucenekmp.util.ArrayTimSorter

### Method Categories:
- **Java Core Business Logic**: 42
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 39
- **KMP Property Accessors**: 10
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 1
- **Semantic Completion**: 87%

### Missing Core Methods:
- `package-private static int minRun(int)`
- `package-private static int heapParent(int, int)`
- `package-private static int heapChild(int, int)`


## Detailed Analysis: org.apache.lucene.util.ArrayUtil -> org.gnit.lucenekmp.util.ArrayUtil

### Method Categories:
- **Java Core Business Logic**: 11
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 11
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 90%


## Detailed Analysis: org.apache.lucene.util.Attribute -> org.gnit.lucenekmp.util.Attribute

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.util.AttributeFactory -> org.gnit.lucenekmp.util.AttributeFactory

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 2
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 2
- **Semantic Completion**: 85%

### Missing Core Methods:
- `package-private static java.lang.invoke.MethodHandle findAttributeImplCtor(java.lang.Class<? extends org.apache.lucene.util.AttributeImpl>)`
- `public static org.apache.lucene.util.AttributeFactory getStaticImplementation(org.apache.lucene.util.AttributeFactory, java.lang.Class<A>)`


## Detailed Analysis: org.apache.lucene.util.AttributeImpl -> org.gnit.lucenekmp.util.AttributeImpl

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 1
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 7
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 1
- **Semantic Completion**: 72%


## Detailed Analysis: org.apache.lucene.util.AttributeSource -> org.gnit.lucenekmp.util.AttributeSource

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void remove()`
- `public boolean hasNext()`


## Detailed Analysis: org.apache.lucene.util.BitDocIdSet -> org.gnit.lucenekmp.util.BitDocIdSet

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public long ramBytesUsed()`


## Detailed Analysis: org.apache.lucene.util.BitSet -> org.gnit.lucenekmp.util.BitSet

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public void clear()`
- `public void set(int)`
- `public boolean getAndSet(int)`
- `public void clear(int)`
- `public void clear(int, int)`
- `public int approximateCardinality()`
- `public int prevSetBit(int)`
- `public int nextSetBit(int)`
- `public int nextSetBit(int, int)`
- `protected void checkUnpositioned(org.apache.lucene.search.DocIdSetIterator)`
- `public void or(org.apache.lucene.search.DocIdSetIterator)`
- `public boolean get(int)`
- `public void applyMask(org.apache.lucene.util.FixedBitSet, int)`
- `public long ramBytesUsed()`


## Detailed Analysis: org.apache.lucene.util.BitSetIterator -> org.gnit.lucenekmp.util.BitSetIterator

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 66%

### Missing Core Methods:
- `private static T getBitSet(org.apache.lucene.search.DocIdSetIterator, java.lang.Class<? extends T>)`
- `public int docID()`
- `public int nextDoc()`
- `public int advance(int)`
- `public void intoBitSet(int, org.apache.lucene.util.FixedBitSet, int)`
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`
- `protected int slowAdvance(int)`


## Detailed Analysis: org.apache.lucene.util.BitUtil -> org.gnit.lucenekmp.util.BitUtil

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public static int nextHighestPowerOfTwo(int)`
- `public static long nextHighestPowerOfTwo(long)`
- `public static long interleave(int, int)`
- `public static long deinterleave(long)`
- `public static long flipFlop(long)`
- `public static int zigZagEncode(int)`
- `public static long zigZagEncode(long)`
- `public static int zigZagDecode(int)`
- `public static long zigZagDecode(long)`
- `public static boolean isZeroOrPowerOfTwo(int)`


## Detailed Analysis: org.apache.lucene.util.Bits -> org.gnit.lucenekmp.util.Bits

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 61%

### Missing Core Methods:
- `public void applyMask(org.apache.lucene.util.FixedBitSet, int)`


## Detailed Analysis: org.apache.lucene.util.ByteBlockPool -> org.gnit.lucenekmp.util.ByteBlockPool

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 72%


## Detailed Analysis: org.apache.lucene.util.BytesRef -> org.gnit.lucenekmp.util.BytesRef

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 4
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 38%

### Missing Core Methods:
- `public boolean bytesEquals(org.apache.lucene.util.BytesRef)`
- `public java.lang.String utf8ToString()`
- `public int compareTo(org.apache.lucene.util.BytesRef)`


## Detailed Analysis: org.apache.lucene.util.BytesRefArray -> org.gnit.lucenekmp.util.BytesRefArray

### Method Categories:
- **Java Core Business Logic**: 26
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 22
- **KMP Property Accessors**: 7
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 85%

### Missing Core Methods:
- `protected void save(int, int)`
- `protected void restore(int, int)`
- `package-private static int heapParent(int, int)`
- `package-private static int heapChild(int, int)`


## Detailed Analysis: org.apache.lucene.util.BytesRefBlockPool -> org.gnit.lucenekmp.util.BytesRefBlockPool

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `package-private void reset()`
- `public void fillBytesRef(org.apache.lucene.util.BytesRef, int)`
- `public int addBytesRef(org.apache.lucene.util.BytesRef)`
- `package-private int hash(int)`
- `public long ramBytesUsed()`


## Detailed Analysis: org.apache.lucene.util.BytesRefBuilder -> org.gnit.lucenekmp.util.BytesRefBuilder

### Method Categories:
- **Java Core Business Logic**: 16
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 2
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 17
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 1
- **Semantic Completion**: 94%


## Detailed Analysis: org.apache.lucene.util.BytesRefComparator -> org.gnit.lucenekmp.util.BytesRefComparator

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 80%


## Detailed Analysis: org.apache.lucene.util.BytesRefHash -> org.gnit.lucenekmp.util.BytesRefHash

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 34
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.util.Counter bytesUsed()`


## Detailed Analysis: org.apache.lucene.util.BytesRefIterator -> org.gnit.lucenekmp.util.BytesRefIterator

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.util.CharsRef -> org.gnit.lucenekmp.util.CharsRef

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public int compare(org.apache.lucene.util.CharsRef, org.apache.lucene.util.CharsRef)`


## Detailed Analysis: org.apache.lucene.util.CloseableThreadLocal -> org.gnit.lucenekmp.util.CloseableThreadLocal

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `protected T initialValue()`
- `public void set(T)`
- `private void maybePurge()`
- `private void purge()`
- `public void close()`


## Detailed Analysis: org.apache.lucene.util.CollectionUtil -> org.gnit.lucenekmp.util.CollectionUtil

### Method Categories:
- **Java Core Business Logic**: 23
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 39
- **KMP Property Accessors**: 13
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 1
- **Semantic Completion**: 41%

### Missing Core Methods:
- `package-private void sort(int, int, int)`
- `private int median(int, int, int)`
- `package-private static int heapParent(int, int)`
- `package-private static int heapChild(int, int)`


## Detailed Analysis: org.apache.lucene.util.CommandLineUtil -> org.gnit.lucenekmp.util.CommandLineUtil

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 7
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 94%


## Detailed Analysis: org.apache.lucene.util.Constants -> org.gnit.lucenekmp.util.Constants

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 3
- **KMP Core Business Logic**: 8
- **KMP Property Accessors**: 19
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 34%


## Detailed Analysis: org.apache.lucene.util.Counter -> org.gnit.lucenekmp.util.Counter

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static org.apache.lucene.util.Counter newCounter()`
- `public static org.apache.lucene.util.Counter newCounter(boolean)`


## Detailed Analysis: org.apache.lucene.util.DocBaseBitSetIterator -> org.gnit.lucenekmp.util.DocBaseBitSetIterator

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.util.DocIdSetBuilder -> org.gnit.lucenekmp.util.DocIdSetBuilder

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 5
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.util.FileDeleter -> org.gnit.lucenekmp.util.FileDeleter

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 40%


## Detailed Analysis: org.apache.lucene.util.FixedBitSet -> org.gnit.lucenekmp.util.FixedBitSet

### Method Categories:
- **Java Core Business Logic**: 43
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 3
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 9
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public void clear()`
- `private boolean verifyGhostBitsClear()`
- `public long ramBytesUsed()`
- `public int approximateCardinality()`
- `public boolean get(int)`
- `public void set(int)`
- `public boolean getAndSet(int)`
- `public void clear(int)`
- `public boolean getAndClear(int)`
- `public int nextSetBit(int)`
- `public int nextSetBit(int, int)`
- `private int nextSetBitInRange(int, int)`
- `public int prevSetBit(int)`
- `public void or(org.apache.lucene.search.DocIdSetIterator)`
- `public void or(org.apache.lucene.util.FixedBitSet)`
- `public void xor(org.apache.lucene.util.FixedBitSet)`
- `public void xor(org.apache.lucene.search.DocIdSetIterator)`
- `private void xor(long[], int)`
- `public boolean intersects(org.apache.lucene.util.FixedBitSet)`
- `public void and(org.apache.lucene.util.FixedBitSet)`
- `private void and(long[], int)`
- `public void andNot(org.apache.lucene.search.DocIdSetIterator)`
- `public void andNot(org.apache.lucene.util.FixedBitSet)`
- `private void andNot(int, org.apache.lucene.util.FixedBitSet)`
- `private void andNot(int, long[], int)`
- `public boolean scanIsEmpty()`
- `public void flip(int, int)`
- `public void flip(int)`
- `public void set(int, int)`
- `public void clear(int, int)`
- `public org.apache.lucene.util.Bits asReadOnlyBits()`
- `public void applyMask(org.apache.lucene.util.FixedBitSet, int)`
- `public static org.apache.lucene.util.BitSet of(org.apache.lucene.search.DocIdSetIterator, int)`
- `protected void checkUnpositioned(org.apache.lucene.search.DocIdSetIterator)`


## Detailed Analysis: org.apache.lucene.util.FixedBits -> org.gnit.lucenekmp.util.FixedBits

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 76%


## Detailed Analysis: org.apache.lucene.util.FrequencyTrackingRingBuffer -> org.gnit.lucenekmp.util.FrequencyTrackingRingBuffer

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 83%

### Missing Core Methods:
- `public long ramBytesUsed()`
- `package-private int frequency(int)`
- `package-private int add(int)`
- `package-private boolean remove(int)`
- `private void relocateAdjacentKeys(int)`
- `package-private java.util.Map<java.lang.Integer, java.lang.Integer> asMap()`


## Detailed Analysis: org.apache.lucene.util.IOBooleanSupplier -> org.gnit.lucenekmp.util.IOBooleanSupplier

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.util.IOSupplier -> org.gnit.lucenekmp.util.IOSupplier

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.util.IOUtils -> org.gnit.lucenekmp.util.IOUtils

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 4
- **KMP Core Business Logic**: 25
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public java.nio.file.FileVisitResult preVisitDirectory(java.nio.file.Path, java.nio.file.attribute.BasicFileAttributes)`
- `public java.nio.file.FileVisitResult postVisitDirectory(java.nio.file.Path, java.io.IOException)`
- `public java.nio.file.FileVisitResult visitFile(java.nio.file.Path, java.nio.file.attribute.BasicFileAttributes)`
- `public java.nio.file.FileVisitResult visitFileFailed(java.nio.file.Path, java.io.IOException)`


## Detailed Analysis: org.apache.lucene.util.InPlaceMergeSorter -> org.gnit.lucenekmp.util.InPlaceMergeSorter

### Method Categories:
- **Java Core Business Logic**: 22
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 20
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 99%

### Missing Core Methods:
- `package-private static int heapParent(int, int)`
- `package-private static int heapChild(int, int)`


## Detailed Analysis: org.apache.lucene.util.InfoStream -> org.gnit.lucenekmp.util.InfoStream

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 3
- **Semantic Completion**: 83%


## Detailed Analysis: org.apache.lucene.util.IntArrayDocIdSet -> org.gnit.lucenekmp.util.IntArrayDocIdSet

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.util.IntBlockPool -> org.gnit.lucenekmp.util.IntBlockPool

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 72%


## Detailed Analysis: org.apache.lucene.util.IntroSorter -> org.gnit.lucenekmp.util.IntroSorter

### Method Categories:
- **Java Core Business Logic**: 23
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public void sort(int, int)`
- `package-private void sort(int, int, int)`
- `private int median(int, int, int)`
- `protected int comparePivot(int)`
- `protected int compare(int, int)`
- `protected void swap(int, int)`
- `package-private void checkRange(int, int)`
- `package-private void mergeInPlace(int, int, int)`
- `package-private int lower(int, int, int)`
- `package-private int upper(int, int, int)`
- `package-private int lower2(int, int, int)`
- `package-private int upper2(int, int, int)`
- `package-private void reverse(int, int)`
- `package-private void rotate(int, int, int)`
- `package-private void doRotate(int, int, int)`
- `package-private void binarySort(int, int)`
- `package-private void binarySort(int, int, int)`
- `package-private void insertionSort(int, int)`
- `package-private void heapSort(int, int)`
- `package-private void heapify(int, int)`
- `package-private void siftDown(int, int, int)`
- `package-private static int heapParent(int, int)`
- `package-private static int heapChild(int, int)`


## Detailed Analysis: org.apache.lucene.util.IntsRef -> org.gnit.lucenekmp.util.IntsRef

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 4
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 62%

### Missing Core Methods:
- `public boolean intsEquals(org.apache.lucene.util.IntsRef)`
- `public int compareTo(org.apache.lucene.util.IntsRef)`


## Detailed Analysis: org.apache.lucene.util.LSBRadixSorter -> org.gnit.lucenekmp.util.LSBRadixSorter

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 92%

### Missing Core Methods:
- `public void sort(int, int[], int)`


## Detailed Analysis: org.apache.lucene.util.LongBitSet -> org.gnit.lucenekmp.util.LongBitSet

### Method Categories:
- **Java Core Business Logic**: 21
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 3
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 76%

### Missing Core Methods:
- `private boolean verifyGhostBitsClear()`
- `public boolean get(long)`
- `public void set(long)`
- `public boolean getAndSet(long)`
- `public void clear(long)`
- `public boolean getAndClear(long)`
- `public long nextSetBit(long)`
- `public long prevSetBit(long)`
- `public void or(org.apache.lucene.util.LongBitSet)`
- `public void xor(org.apache.lucene.util.LongBitSet)`
- `public boolean intersects(org.apache.lucene.util.LongBitSet)`
- `public void and(org.apache.lucene.util.LongBitSet)`
- `public void andNot(org.apache.lucene.util.LongBitSet)`
- `public boolean scanIsEmpty()`
- `public void flip(long, long)`
- `public void flip(long)`
- `public void set(long, long)`
- `public void clear(long, long)`
- `public long ramBytesUsed()`


## Detailed Analysis: org.apache.lucene.util.LongValues -> org.gnit.lucenekmp.util.LongValues

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 71%


## Detailed Analysis: org.apache.lucene.util.LongsRef -> org.gnit.lucenekmp.util.LongsRef

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 4
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 62%

### Missing Core Methods:
- `public boolean longsEquals(org.apache.lucene.util.LongsRef)`
- `public int compareTo(org.apache.lucene.util.LongsRef)`


## Detailed Analysis: org.apache.lucene.util.MSBRadixSorter -> org.gnit.lucenekmp.util.MSBRadixSorter

### Method Categories:
- **Java Core Business Logic**: 23
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 21
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 99%

### Missing Core Methods:
- `package-private static int heapParent(int, int)`
- `package-private static int heapChild(int, int)`


## Detailed Analysis: org.apache.lucene.util.MathUtil -> org.gnit.lucenekmp.util.MathUtil

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 9
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 88%


## Detailed Analysis: org.apache.lucene.util.MergedIterator -> org.gnit.lucenekmp.util.MergedIterator

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 15
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 3
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.util.NamedSPILoader -> org.gnit.lucenekmp.util.NamedSPILoader

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void reload(java.lang.ClassLoader)`
- `public static void checkServiceName(java.lang.String)`
- `private static boolean isLetterOrDigit(char)`
- `public S lookup(java.lang.String)`
- `public java.util.Set<java.lang.String> availableServices()`


## Detailed Analysis: org.apache.lucene.util.NamedThreadFactory -> org.gnit.lucenekmp.util.NamedThreadFactory

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `private static java.lang.String checkPrefix(java.lang.String)`
- `public java.lang.Thread newThread(java.lang.Runnable)`


## Detailed Analysis: org.apache.lucene.util.NotDocIdSet -> org.gnit.lucenekmp.util.NotDocIdSet

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 70%

### Missing Core Methods:
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.util.NumericUtils -> org.gnit.lucenekmp.util.NumericUtils

### Method Categories:
- **Java Core Business Logic**: 14
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 16
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 87%


## Detailed Analysis: org.apache.lucene.util.PagedBytes -> org.gnit.lucenekmp.util.PagedBytes

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 2
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `private void addBlock(byte[])`
- `public org.apache.lucene.util.PagedBytes$Reader freeze(boolean)`
- `public long ramBytesUsed()`
- `public long copyUsingLengthPrefix(org.apache.lucene.util.BytesRef)`


## Detailed Analysis: org.apache.lucene.util.PrintStreamInfoStream -> org.gnit.lucenekmp.util.PrintStreamInfoStream

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void message(java.lang.String, java.lang.String)`
- `public boolean isEnabled(java.lang.String)`
- `public void close()`


## Detailed Analysis: org.apache.lucene.util.PriorityQueue -> org.gnit.lucenekmp.util.PriorityQueue

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 42%


## Detailed Analysis: org.apache.lucene.util.RamUsageEstimator -> org.gnit.lucenekmp.util.RamUsageEstimator

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 6
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 56%

### Missing Core Methods:
- `public static org.apache.lucene.search.QueryVisitor termCollector(java.util.Set<org.apache.lucene.index.Term>)`


## Detailed Analysis: org.apache.lucene.util.RefCount -> org.gnit.lucenekmp.util.RefCount

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 84%


## Detailed Analysis: org.apache.lucene.util.RoaringDocIdSet -> org.gnit.lucenekmp.util.RoaringDocIdSet

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 62%

### Missing Core Methods:
- `private int firstDocFromNextBlock()`
- `public static org.apache.lucene.search.DocIdSetIterator all(int)`
- `public static org.apache.lucene.search.DocIdSetIterator range(int, int)`


## Detailed Analysis: org.apache.lucene.util.SameThreadExecutorService -> org.gnit.lucenekmp.util.SameThreadExecutorService

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 15
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 7
- **Semantic Completion**: 23%

### Missing Core Methods:
- `public java.util.List<java.lang.Runnable> shutdownNow()`
- `public boolean awaitTermination(long, java.util.concurrent.TimeUnit)`


## Detailed Analysis: org.apache.lucene.util.SetOnce -> org.gnit.lucenekmp.util.SetOnce

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.util.Sorter -> org.gnit.lucenekmp.util.Sorter

### Method Categories:
- **Java Core Business Logic**: 21
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `protected int compare(int, int)`
- `protected void swap(int, int)`
- `protected int comparePivot(int)`
- `public void sort(int, int)`
- `package-private void checkRange(int, int)`
- `package-private void mergeInPlace(int, int, int)`
- `package-private int lower(int, int, int)`
- `package-private int upper(int, int, int)`
- `package-private int lower2(int, int, int)`
- `package-private int upper2(int, int, int)`
- `package-private void reverse(int, int)`
- `package-private void rotate(int, int, int)`
- `package-private void doRotate(int, int, int)`
- `package-private void binarySort(int, int)`
- `package-private void binarySort(int, int, int)`
- `package-private void insertionSort(int, int)`
- `package-private void heapSort(int, int)`
- `package-private void heapify(int, int)`
- `package-private void siftDown(int, int, int)`


## Detailed Analysis: org.apache.lucene.util.SparseFixedBitSet -> org.gnit.lucenekmp.util.SparseFixedBitSet

### Method Categories:
- **Java Core Business Logic**: 31
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 3
- **Semantic Completion**: 83%

### Missing Core Methods:
- `public void clear()`
- `private boolean consistent(int)`
- `public int approximateCardinality()`
- `public boolean get(int)`
- `public boolean getAndSet(int)`
- `public void set(int)`
- `private void insertBlock(int, long, int)`
- `private void insertLong(int, long, int, long)`
- `public void clear(int)`
- `private void and(int, int, long)`
- `private void removeLong(int, int, long, int)`
- `public void clear(int, int)`
- `private void clearWithinBlock(int, int, int)`
- `private int firstDoc(int, int)`
- `public int nextSetBit(int)`
- `public int nextSetBit(int, int)`
- `private int nextSetBitInRange(int, int)`
- `private int lastDoc(int)`
- `public int prevSetBit(int)`
- `private long longBits(long, long[], int)`
- `private void or(int, long, long[], int)`
- `private void or(org.apache.lucene.util.SparseFixedBitSet)`
- `private void orDense(org.apache.lucene.search.DocIdSetIterator)`
- `public void or(org.apache.lucene.search.DocIdSetIterator)`
- `public long ramBytesUsed()`
- `public static org.apache.lucene.util.BitSet of(org.apache.lucene.search.DocIdSetIterator, int)`
- `protected void checkUnpositioned(org.apache.lucene.search.DocIdSetIterator)`
- `public void applyMask(org.apache.lucene.util.FixedBitSet, int)`


## Detailed Analysis: org.apache.lucene.util.StableMSBRadixSorter -> org.gnit.lucenekmp.util.StableMSBRadixSorter

### Method Categories:
- **Java Core Business Logic**: 26
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 24
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 99%

### Missing Core Methods:
- `package-private static int heapParent(int, int)`
- `package-private static int heapChild(int, int)`


## Detailed Analysis: org.apache.lucene.util.StableStringSorter -> org.gnit.lucenekmp.util.StableStringSorter

### Method Categories:
- **Java Core Business Logic**: 37
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 35
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 95%

### Missing Core Methods:
- `private static void sumHistogram(int[], int[])`
- `package-private static int heapParent(int, int)`
- `package-private static int heapChild(int, int)`


## Detailed Analysis: org.apache.lucene.util.StringHelper -> org.gnit.lucenekmp.util.StringHelper

### Method Categories:
- **Java Core Business Logic**: 14
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 92%


## Detailed Analysis: org.apache.lucene.util.StringSorter -> org.gnit.lucenekmp.util.StringSorter

### Method Categories:
- **Java Core Business Logic**: 35
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 21
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 89%

### Missing Core Methods:
- `protected int byteAt(int, int)`
- `protected org.apache.lucene.util.Sorter getFallbackSorter(int)`
- `protected void sort(int, int, int, int)`
- `protected boolean shouldFallback(int, int, int)`
- `private void radixSort(int, int, int, int)`
- `private boolean assertHistogram(int, int[])`
- `protected int getBucket(int, int)`
- `private int computeCommonPrefixLengthAndBuildHistogram(int, int, int, int[])`
- `private int computeInitialCommonPrefixLength(int, int)`
- `private int computeCommonPrefixLengthAndBuildHistogramPart1(int, int, int, int[], int)`
- `private int computeCommonPrefixLengthAndBuildHistogramPart2(int, int, int, int[], int, int)`
- `protected void buildHistogram(int, int, int, int, int, int[])`
- `private static void sumHistogram(int[], int[])`
- `protected void reorder(int, int, int[], int[], int)`
- `package-private static int heapParent(int, int)`
- `package-private static int heapChild(int, int)`


## Detailed Analysis: org.apache.lucene.util.ThreadInterruptedException -> org.gnit.lucenekmp.util.ThreadInterruptedException

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.util.TimSorter -> org.gnit.lucenekmp.util.TimSorter

### Method Categories:
- **Java Core Business Logic**: 42
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `package-private int runLen(int)`
- `package-private int runBase(int)`
- `package-private int runEnd(int)`
- `package-private void setRunEnd(int, int)`
- `package-private void pushRunLen(int)`
- `package-private int nextRun()`
- `package-private void ensureInvariants()`
- `package-private void exhaustStack()`
- `package-private void reset(int, int)`
- `package-private void mergeAt(int)`
- `package-private void merge(int, int, int)`
- `public void sort(int, int)`
- `package-private void doRotate(int, int, int)`
- `package-private void mergeLo(int, int, int)`
- `package-private void mergeHi(int, int, int)`
- `package-private int lowerSaved(int, int, int)`
- `package-private int upperSaved(int, int, int)`
- `package-private int lowerSaved3(int, int, int)`
- `package-private int upperSaved3(int, int, int)`
- `protected void save(int, int)`
- `protected void restore(int, int)`
- `protected int compareSaved(int, int)`
- `protected int compare(int, int)`
- `protected void swap(int, int)`
- `protected int comparePivot(int)`
- `package-private void checkRange(int, int)`
- `package-private void mergeInPlace(int, int, int)`
- `package-private int lower(int, int, int)`
- `package-private int upper(int, int, int)`
- `package-private int lower2(int, int, int)`
- `package-private int upper2(int, int, int)`
- `package-private void reverse(int, int)`
- `package-private void rotate(int, int, int)`
- `package-private void binarySort(int, int)`
- `package-private void binarySort(int, int, int)`
- `package-private void insertionSort(int, int)`
- `package-private void heapSort(int, int)`
- `package-private void heapify(int, int)`
- `package-private void siftDown(int, int, int)`
- `package-private static int heapParent(int, int)`
- `package-private static int heapChild(int, int)`


## Detailed Analysis: org.apache.lucene.util.UnicodeUtil -> org.gnit.lucenekmp.util.UnicodeUtil

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.util.VectorUtil -> org.gnit.lucenekmp.util.VectorUtil

### Method Categories:
- **Java Core Business Logic**: 20
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 20
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 97%


## Detailed Analysis: org.apache.lucene.util.Version -> org.gnit.lucenekmp.util.Version

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 8
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 4
- **Semantic Completion**: 41%

### Missing Core Methods:
- `public boolean onOrAfter(org.apache.lucene.util.Version)`
- `private boolean encodedIsValid()`


## Detailed Analysis: org.apache.lucene.util.automaton.Automaton -> org.gnit.lucenekmp.util.automaton.Automaton

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 21
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void addTransition(int, int, int)`
- `public void addTransition(int, int, int, int)`
- `public void addEpsilon(int, int)`
- `public int createState()`
- `public void setAccept(int, boolean)`
- `public boolean isAccept(int)`
- `public void copyStates(org.apache.lucene.util.automaton.Automaton)`


## Detailed Analysis: org.apache.lucene.util.automaton.ByteRunAutomaton -> org.gnit.lucenekmp.util.automaton.ByteRunAutomaton

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 3
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public boolean isAccept(int)`
- `package-private int getCharClass(int)`
- `public int step(int, int)`
- `public long ramBytesUsed()`
- `public boolean run(byte[], int, int)`


## Detailed Analysis: org.apache.lucene.util.automaton.ByteRunnable -> org.gnit.lucenekmp.util.automaton.ByteRunnable

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public int step(int, int)`
- `public boolean isAccept(int)`
- `public boolean run(byte[], int, int)`


## Detailed Analysis: org.apache.lucene.util.automaton.CompiledAutomaton -> org.gnit.lucenekmp.util.automaton.CompiledAutomaton

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 2
- **Java Synthetic**: 3
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `private static int findSinkState(org.apache.lucene.util.automaton.Automaton)`
- `private org.apache.lucene.util.BytesRef addTail(int, org.apache.lucene.util.BytesRefBuilder, int, int)`
- `public org.apache.lucene.index.TermsEnum getTermsEnum(org.apache.lucene.index.Terms)`
- `public void visit(org.apache.lucene.search.QueryVisitor, org.apache.lucene.search.Query, java.lang.String)`
- `public org.apache.lucene.util.BytesRef floor(org.apache.lucene.util.BytesRef, org.apache.lucene.util.BytesRefBuilder)`
- `public long ramBytesUsed()`


## Detailed Analysis: org.apache.lucene.util.automaton.FrozenIntSet -> org.gnit.lucenekmp.util.automaton.FrozenIntSet

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 3
- **KMP Synthetic**: 0
- **Semantic Completion**: 60%


## Detailed Analysis: org.apache.lucene.util.automaton.NFARunAutomaton -> org.gnit.lucenekmp.util.automaton.NFARunAutomaton

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 2
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 2
- **Semantic Completion**: 65%


## Detailed Analysis: org.apache.lucene.util.automaton.Operations -> org.gnit.lucenekmp.util.automaton.Operations

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 29%

### Missing Core Methods:
- `private org.apache.lucene.util.automaton.Operations$PointTransitions next(int)`
- `private org.apache.lucene.util.automaton.Operations$PointTransitions find(int)`
- `public void reset()`
- `public void sort()`


## Detailed Analysis: org.apache.lucene.util.automaton.RegExp -> org.gnit.lucenekmp.util.automaton.RegExp

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public static org.apache.lucene.util.automaton.RegExp$Kind valueOf(java.lang.String)`


## Detailed Analysis: org.apache.lucene.util.automaton.RunAutomaton -> org.gnit.lucenekmp.util.automaton.RunAutomaton

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 3
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public boolean isAccept(int)`
- `package-private int getCharClass(int)`
- `public int step(int, int)`
- `public long ramBytesUsed()`


## Detailed Analysis: org.apache.lucene.util.automaton.StatePair -> org.gnit.lucenekmp.util.automaton.StatePair

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 3
- **KMP Synthetic**: 0
- **Semantic Completion**: 20%


## Detailed Analysis: org.apache.lucene.util.automaton.StringsToAutomaton -> org.gnit.lucenekmp.util.automaton.StringsToAutomaton

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 2
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 45%

### Missing Core Methods:
- `package-private org.apache.lucene.util.automaton.StringsToAutomaton$State getState(int)`
- `package-private boolean hasChildren()`
- `package-private org.apache.lucene.util.automaton.StringsToAutomaton$State newState(int)`
- `package-private org.apache.lucene.util.automaton.StringsToAutomaton$State lastChild()`
- `package-private org.apache.lucene.util.automaton.StringsToAutomaton$State lastChild(int)`
- `package-private void replaceLastChild(org.apache.lucene.util.automaton.StringsToAutomaton$State)`


## Detailed Analysis: org.apache.lucene.util.automaton.TooComplexToDeterminizeException -> org.gnit.lucenekmp.util.automaton.TooComplexToDeterminizeException

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.util.automaton.Transition -> org.gnit.lucenekmp.util.automaton.Transition

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public long ramBytesUsed()`


## Detailed Analysis: org.apache.lucene.util.automaton.UTF32ToUTF8 -> org.gnit.lucenekmp.util.automaton.UTF32ToUTF8

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.util.bkd.BKDConfig -> org.gnit.lucenekmp.util.bkd.BKDConfig

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public int packedBytesLength()`
- `public int packedIndexBytesLength()`
- `public int bytesPerDoc()`
- `public int numDims()`
- `public int numIndexDims()`
- `public int bytesPerDim()`
- `public int maxPointsInLeafNode()`


## Detailed Analysis: org.apache.lucene.util.compress.LZ4 -> org.gnit.lucenekmp.util.compress.LZ4

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `package-private void reset(byte[], int, int)`
- `package-private void initDictionary(int)`
- `package-private int get(int)`
- `public int previous(int)`
- `package-private boolean assertReset()`


## Detailed Analysis: org.apache.lucene.util.fst.ByteSequenceOutputs -> org.gnit.lucenekmp.util.fst.ByteSequenceOutputs

### Method Categories:
- **Java Core Business Logic**: 12
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 1
- **Java Synthetic**: 8
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public org.apache.lucene.util.BytesRef common(org.apache.lucene.util.BytesRef, org.apache.lucene.util.BytesRef)`
- `public org.apache.lucene.util.BytesRef subtract(org.apache.lucene.util.BytesRef, org.apache.lucene.util.BytesRef)`
- `public org.apache.lucene.util.BytesRef add(org.apache.lucene.util.BytesRef, org.apache.lucene.util.BytesRef)`
- `public void write(org.apache.lucene.util.BytesRef, org.apache.lucene.store.DataOutput)`
- `public org.apache.lucene.util.BytesRef read(org.apache.lucene.store.DataInput)`
- `public void skipOutput(org.apache.lucene.store.DataInput)`
- `public java.lang.String outputToString(org.apache.lucene.util.BytesRef)`
- `public long ramBytesUsed(org.apache.lucene.util.BytesRef)`
- `public void writeFinalOutput(T, org.apache.lucene.store.DataOutput)`
- `public T readFinalOutput(org.apache.lucene.store.DataInput)`
- `public void skipFinalOutput(org.apache.lucene.store.DataInput)`
- `public T merge(T, T)`


## Detailed Analysis: org.apache.lucene.util.fst.BytesRefFSTEnum -> org.gnit.lucenekmp.util.fst.BytesRefFSTEnum

### Method Categories:
- **Java Core Business Logic**: 27
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.util.fst.BytesRefFSTEnum$InputOutput<T> seekCeil(org.apache.lucene.util.BytesRef)`
- `public org.apache.lucene.util.fst.BytesRefFSTEnum$InputOutput<T> seekFloor(org.apache.lucene.util.BytesRef)`
- `public org.apache.lucene.util.fst.BytesRefFSTEnum$InputOutput<T> seekExact(org.apache.lucene.util.BytesRef)`
- `protected void grow()`
- `private org.apache.lucene.util.fst.BytesRefFSTEnum$InputOutput<T> setResult()`
- `private void rewindPrefix()`
- `protected void doNext()`
- `protected void doSeekCeil()`
- `private org.apache.lucene.util.fst.FST$Arc<T> doSeekCeilArrayContinuous(org.apache.lucene.util.fst.FST$Arc<T>, int, org.apache.lucene.util.fst.FST$BytesReader)`
- `private org.apache.lucene.util.fst.FST$Arc<T> doSeekCeilArrayDirectAddressing(org.apache.lucene.util.fst.FST$Arc<T>, int, org.apache.lucene.util.fst.FST$BytesReader)`
- `private org.apache.lucene.util.fst.FST$Arc<T> doSeekCeilArrayPacked(org.apache.lucene.util.fst.FST$Arc<T>, int, org.apache.lucene.util.fst.FST$BytesReader)`
- `private org.apache.lucene.util.fst.FST$Arc<T> doSeekCeilList(org.apache.lucene.util.fst.FST$Arc<T>, int)`
- `package-private void doSeekFloor()`
- `private org.apache.lucene.util.fst.FST$Arc<T> doSeekFloorContinuous(org.apache.lucene.util.fst.FST$Arc<T>, int, org.apache.lucene.util.fst.FST$BytesReader)`
- `private org.apache.lucene.util.fst.FST$Arc<T> doSeekFloorArrayDirectAddressing(org.apache.lucene.util.fst.FST$Arc<T>, int, org.apache.lucene.util.fst.FST$BytesReader)`
- `private void rollbackToLastForkThenPush()`
- `private org.apache.lucene.util.fst.FST$Arc<T> backtrackToFloorArc(org.apache.lucene.util.fst.FST$Arc<T>, int, org.apache.lucene.util.fst.FST$BytesReader)`
- `private void findNextFloorArcDirectAddressing(org.apache.lucene.util.fst.FST$Arc<T>, int, org.apache.lucene.util.fst.FST$BytesReader)`
- `private void findNextFloorArcContinuous(org.apache.lucene.util.fst.FST$Arc<T>, int, org.apache.lucene.util.fst.FST$BytesReader)`
- `private void findNextFloorArcBinarySearch(org.apache.lucene.util.fst.FST$Arc<T>, int, org.apache.lucene.util.fst.FST$BytesReader)`
- `private org.apache.lucene.util.fst.FST$Arc<T> doSeekFloorArrayPacked(org.apache.lucene.util.fst.FST$Arc<T>, int, org.apache.lucene.util.fst.FST$BytesReader)`
- `private org.apache.lucene.util.fst.FST$Arc<T> doSeekFloorList(org.apache.lucene.util.fst.FST$Arc<T>, int)`
- `package-private boolean doSeekExact()`
- `private void incr()`
- `private void pushFirst()`
- `private void pushLast()`
- `private org.apache.lucene.util.fst.FST$Arc<T> getArc(int)`


## Detailed Analysis: org.apache.lucene.util.fst.FST -> org.gnit.lucenekmp.util.fst.FST

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 6
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public org.apache.lucene.util.fst.FST$Arc<T> copyFrom(org.apache.lucene.util.fst.FST$Arc<T>)`
- `package-private boolean flag(int)`
- `public T nextFinalOutput()`
- `package-private long nextArc()`
- `public int arcIdx()`
- `public byte nodeFlags()`
- `public long posArcsStart()`
- `public int bytesPerArc()`
- `public int numArcs()`
- `package-private int firstLabel()`


## Detailed Analysis: org.apache.lucene.util.fst.FSTCompiler -> org.gnit.lucenekmp.util.fst.FSTCompiler

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 11
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%


## Detailed Analysis: org.apache.lucene.util.fst.FSTEnum -> org.gnit.lucenekmp.util.fst.FSTEnum

### Method Categories:
- **Java Core Business Logic**: 23
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 23
- **KMP Property Accessors**: 14
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 78%


## Detailed Analysis: org.apache.lucene.util.fst.FSTReader -> org.gnit.lucenekmp.util.fst.FSTReader

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public void writeTo(org.apache.lucene.store.DataOutput)`
- `public long ramBytesUsed()`


## Detailed Analysis: org.apache.lucene.util.fst.FSTSuffixNodeCache -> org.gnit.lucenekmp.util.fst.FSTSuffixNodeCache

### Method Categories:
- **Java Core Business Logic**: 9
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 9
- **KMP Property Accessors**: 7
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 68%


## Detailed Analysis: org.apache.lucene.util.fst.GrowableByteArrayDataOutput -> org.gnit.lucenekmp.util.fst.GrowableByteArrayDataOutput

### Method Categories:
- **Java Core Business Logic**: 21
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public void writeByte(byte)`
- `public void writeBytes(byte[], int, int)`
- `private void ensureCapacity(int)`
- `public void writeTo(org.apache.lucene.store.DataOutput)`
- `public void writeTo(int, byte[], int, int)`
- `public long ramBytesUsed()`
- `public void writeBytes(byte[], int)`
- `public void writeInt(int)`
- `public void writeShort(short)`
- `public void writeVInt(int)`
- `public void writeZInt(int)`
- `public void writeLong(long)`
- `public void writeVLong(long)`
- `private void writeSignedVLong(long)`
- `public void writeZLong(long)`
- `public void writeString(java.lang.String)`
- `public void copyBytes(org.apache.lucene.store.DataInput, long)`
- `public void writeMapOfStrings(java.util.Map<java.lang.String, java.lang.String>)`
- `public void writeSetOfStrings(java.util.Set<java.lang.String>)`
- `public void writeGroupVInts(long[], int)`
- `public void writeGroupVInts(int[], int)`


## Detailed Analysis: org.apache.lucene.util.fst.OffHeapFSTStore -> org.gnit.lucenekmp.util.fst.OffHeapFSTStore

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public long ramBytesUsed()`
- `public void writeTo(org.apache.lucene.store.DataOutput)`


## Detailed Analysis: org.apache.lucene.util.fst.OnHeapFSTStore -> org.gnit.lucenekmp.util.fst.OnHeapFSTStore

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public long ramBytesUsed()`
- `public void writeTo(org.apache.lucene.store.DataOutput)`


## Detailed Analysis: org.apache.lucene.util.fst.Util -> org.gnit.lucenekmp.util.fst.Util

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `package-private org.apache.lucene.util.fst.Util$FSTPath<T> newPath(T, org.apache.lucene.util.IntsRefBuilder)`


## Detailed Analysis: org.apache.lucene.util.hnsw.BlockingFloatHeap -> org.gnit.lucenekmp.util.hnsw.BlockingFloatHeap

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public float offer(float)`
- `public float offer(float[], int)`
- `private void push(float)`
- `private float updateTop(float)`
- `private void downHeap(int)`
- `private void upHeap(int)`


## Detailed Analysis: org.apache.lucene.util.hnsw.HnswGraph -> org.gnit.lucenekmp.util.hnsw.HnswGraph

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public int nextNeighbor()`
- `public void seek(int, int)`
- `public int numLevels()`
- `public int entryNode()`
- `public int neighborCount()`
- `public int maxConn()`
- `public org.apache.lucene.util.hnsw.HnswGraph$NodesIterator getNodesOnLevel(int)`
- `public int maxNodeId()`


## Detailed Analysis: org.apache.lucene.util.hnsw.NeighborQueue -> org.gnit.lucenekmp.util.hnsw.NeighborQueue

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 82%


## Detailed Analysis: org.apache.lucene.util.hnsw.RandomVectorScorer -> org.gnit.lucenekmp.util.hnsw.RandomVectorScorer

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public int maxOrd()`
- `public int ordToDoc(int)`
- `public org.apache.lucene.util.Bits getAcceptOrds(org.apache.lucene.util.Bits)`
- `public float score(int)`


## Detailed Analysis: org.apache.lucene.util.hnsw.RandomVectorScorerSupplier -> org.gnit.lucenekmp.util.hnsw.RandomVectorScorerSupplier

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.util.hnsw.UpdateableRandomVectorScorer -> org.gnit.lucenekmp.util.hnsw.UpdateableRandomVectorScorer

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public int maxOrd()`
- `public int ordToDoc(int)`
- `public org.apache.lucene.util.Bits getAcceptOrds(org.apache.lucene.util.Bits)`
- `public float score(int)`


## Detailed Analysis: org.apache.lucene.util.packed.AbstractBlockPackedWriter -> org.gnit.lucenekmp.util.packed.AbstractBlockPackedWriter

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public void reset(org.apache.lucene.store.DataOutput)`
- `private void checkNotFinished()`
- `public void add(long)`
- `package-private void addBlockOfZeros()`
- `public void finish()`
- `protected void flush()`
- `protected void writeValues(int)`


## Detailed Analysis: org.apache.lucene.util.packed.AbstractPagedMutable -> org.gnit.lucenekmp.util.packed.AbstractPagedMutable

### Method Categories:
- **Java Core Business Logic**: 13
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `protected void fillPages()`
- `protected org.apache.lucene.util.packed.PackedInts$Mutable newMutable(int, int)`
- `package-private int lastPageSize(long)`
- `package-private int pageSize()`
- `package-private int pageIndex(long)`
- `package-private int indexInPage(long)`
- `public long get(long)`
- `public void set(long, long)`
- `protected long baseRamBytesUsed()`
- `public long ramBytesUsed()`
- `protected T newUnfilledCopy(long)`
- `public T resize(long)`
- `public T grow(long)`


## Detailed Analysis: org.apache.lucene.util.packed.BlockPackedReaderIterator -> org.gnit.lucenekmp.util.packed.BlockPackedReaderIterator

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `public void reset(org.apache.lucene.store.DataInput, long)`
- `public void skip(long)`
- `private void skipBytes(long)`
- `public org.apache.lucene.util.LongsRef next(int)`
- `private void refill()`


## Detailed Analysis: org.apache.lucene.util.packed.BlockPackedWriter -> org.gnit.lucenekmp.util.packed.BlockPackedWriter

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 4
- **KMP Core Business Logic**: 7
- **KMP Property Accessors**: 12
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 28%

### Missing Core Methods:
- `package-private static void writeVLong(org.apache.lucene.store.DataOutput, long)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperation -> org.gnit.lucenekmp.util.packed.BulkOperation

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked -> org.gnit.lucenekmp.util.packed.BulkOperationPacked

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked1 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked1

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked10 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked10

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked11 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked11

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked12 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked12

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked13 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked13

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked14 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked14

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked15 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked15

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked16 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked16

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked17 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked17

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked18 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked18

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked19 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked19

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked2 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked2

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked20 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked20

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked21 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked21

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked22 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked22

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked23 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked23

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked24 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked24

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked3 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked3

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked4 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked4

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked5 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked5

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked6 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked6

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked7 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked7

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked8 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked8

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPacked9 -> org.gnit.lucenekmp.util.packed.BulkOperationPacked9

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 14
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 98%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`


## Detailed Analysis: org.apache.lucene.util.packed.BulkOperationPackedSingleBlock -> org.gnit.lucenekmp.util.packed.BulkOperationPackedSingleBlock

### Method Categories:
- **Java Core Business Logic**: 20
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 83%

### Missing Core Methods:
- `public int longBlockCount()`
- `public int byteBlockCount()`
- `public int longValueCount()`
- `public int byteValueCount()`
- `private int decode(long, long[], int)`
- `private int decode(long, int[], int)`
- `private long encode(long[], int)`
- `private long encode(int[], int)`
- `public void decode(long[], int, long[], int, int)`
- `public void decode(byte[], int, long[], int, int)`
- `public void decode(long[], int, int[], int, int)`
- `public void decode(byte[], int, int[], int, int)`
- `public void encode(long[], int, long[], int, int)`
- `public void encode(int[], int, long[], int, int)`
- `public void encode(long[], int, byte[], int, int)`
- `public void encode(int[], int, byte[], int, int)`
- `public static org.apache.lucene.util.packed.BulkOperation of(org.apache.lucene.util.packed.PackedInts$Format, int)`
- `protected int writeLong(long, byte[], int)`
- `public int computeIterations(int, int)`


## Detailed Analysis: org.apache.lucene.util.packed.DeltaPackedLongValues -> org.gnit.lucenekmp.util.packed.DeltaPackedLongValues

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `package-private long get(int, int)`
- `package-private int decodeBlock(int, long[])`
- `public static org.apache.lucene.util.packed.PackedLongValues$Builder packedBuilder(int, float)`
- `public static org.apache.lucene.util.packed.PackedLongValues$Builder packedBuilder(float)`
- `public static org.apache.lucene.util.packed.PackedLongValues$Builder deltaPackedBuilder(int, float)`
- `public static org.apache.lucene.util.packed.PackedLongValues$Builder deltaPackedBuilder(float)`
- `public static org.apache.lucene.util.packed.PackedLongValues$Builder monotonicBuilder(int, float)`
- `public static org.apache.lucene.util.packed.PackedLongValues$Builder monotonicBuilder(float)`
- `public long get(long)`
- `public long ramBytesUsed()`


## Detailed Analysis: org.apache.lucene.util.packed.DirectMonotonicReader -> org.gnit.lucenekmp.util.packed.DirectMonotonicReader

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public static org.apache.lucene.util.packed.DirectMonotonicReader$Meta loadMeta(org.apache.lucene.store.IndexInput, long, int)`
- `public static org.apache.lucene.util.packed.DirectMonotonicReader getInstance(org.apache.lucene.util.packed.DirectMonotonicReader$Meta, org.apache.lucene.store.RandomAccessInput)`
- `public static org.apache.lucene.util.packed.DirectMonotonicReader getInstance(org.apache.lucene.util.packed.DirectMonotonicReader$Meta, org.apache.lucene.store.RandomAccessInput, boolean)`
- `public long get(long)`
- `private long[] getBounds(long)`
- `public long binarySearch(long, long, long)`


## Detailed Analysis: org.apache.lucene.util.packed.DirectMonotonicWriter -> org.gnit.lucenekmp.util.packed.DirectMonotonicWriter

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `private void flush()`
- `public void add(long)`
- `public void finish()`


## Detailed Analysis: org.apache.lucene.util.packed.DirectReader -> org.gnit.lucenekmp.util.packed.DirectReader

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 83%


## Detailed Analysis: org.apache.lucene.util.packed.DirectWriter -> org.gnit.lucenekmp.util.packed.DirectWriter

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 86%

### Missing Core Methods:
- `public void add(long)`
- `private void flush()`
- `public void finish()`


## Detailed Analysis: org.apache.lucene.util.packed.GrowableWriter -> org.gnit.lucenekmp.util.packed.GrowableWriter

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 83%

### Missing Core Methods:
- `public long get(int)`
- `private void ensureCapacity(long)`
- `public void set(int, long)`
- `public void clear()`
- `public org.apache.lucene.util.packed.GrowableWriter resize(int)`
- `public int get(int, long[], int, int)`
- `public int set(int, long[], int, int)`
- `public void fill(int, int, long)`
- `public long ramBytesUsed()`


## Detailed Analysis: org.apache.lucene.util.packed.MonotonicBlockPackedReader -> org.gnit.lucenekmp.util.packed.MonotonicBlockPackedReader

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public long get(long)`


## Detailed Analysis: org.apache.lucene.util.packed.MonotonicLongValues -> org.gnit.lucenekmp.util.packed.MonotonicLongValues

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 100%

### Missing Core Methods:
- `package-private long get(int, int)`
- `package-private int decodeBlock(int, long[])`
- `public static org.apache.lucene.util.packed.PackedLongValues$Builder packedBuilder(int, float)`
- `public static org.apache.lucene.util.packed.PackedLongValues$Builder packedBuilder(float)`
- `public static org.apache.lucene.util.packed.PackedLongValues$Builder deltaPackedBuilder(int, float)`
- `public static org.apache.lucene.util.packed.PackedLongValues$Builder deltaPackedBuilder(float)`
- `public static org.apache.lucene.util.packed.PackedLongValues$Builder monotonicBuilder(int, float)`
- `public static org.apache.lucene.util.packed.PackedLongValues$Builder monotonicBuilder(float)`
- `public long get(long)`
- `public long ramBytesUsed()`


## Detailed Analysis: org.apache.lucene.util.packed.Packed64 -> org.gnit.lucenekmp.util.packed.Packed64

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 83%

### Missing Core Methods:
- `public long get(int)`
- `public int get(int, long[], int, int)`
- `public void set(int, long)`
- `public int set(int, long[], int, int)`
- `public long ramBytesUsed()`
- `public void fill(int, int, long)`
- `public void clear()`


## Detailed Analysis: org.apache.lucene.util.packed.Packed64SingleBlock -> org.gnit.lucenekmp.util.packed.Packed64SingleBlock

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 7
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 3
- **Semantic Completion**: 82%

### Missing Core Methods:
- `public static boolean isSupported(int)`
- `private static int requiredCapacity(int, int)`
- `public static org.apache.lucene.util.packed.Packed64SingleBlock create(int, int)`


## Detailed Analysis: org.apache.lucene.util.packed.PackedInts -> org.gnit.lucenekmp.util.packed.PackedInts

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public long get(int)`
- `public int get(int, long[], int, int)`
- `public long ramBytesUsed()`


## Detailed Analysis: org.apache.lucene.util.packed.PackedLongValues -> org.gnit.lucenekmp.util.packed.PackedLongValues

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 8
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 38%


## Detailed Analysis: org.apache.lucene.util.packed.PackedReaderIterator -> org.gnit.lucenekmp.util.packed.PackedReaderIterator

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 14
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 36%


## Detailed Analysis: org.apache.lucene.util.packed.PackedWriter -> org.gnit.lucenekmp.util.packed.PackedWriter

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 15
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 40%


## Detailed Analysis: org.apache.lucene.util.packed.PagedGrowableWriter -> org.gnit.lucenekmp.util.packed.PagedGrowableWriter

### Method Categories:
- **Java Core Business Logic**: 13
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 1
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 13
- **KMP Property Accessors**: 8
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 6
- **Semantic Completion**: 79%


## Detailed Analysis: org.apache.lucene.util.packed.PagedMutable -> org.gnit.lucenekmp.util.packed.PagedMutable

### Method Categories:
- **Java Core Business Logic**: 13
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 1
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 13
- **KMP Property Accessors**: 8
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 6
- **Semantic Completion**: 79%



## Progress Table for Unit Test Classes
| Java Unit Test Class | KMP Unit Test Class | Depth | Class Ported | Java Core Methods | KMP Core Methods | Semantic Progress |
| --- | --- | --- | --- | --- | --- | --- |
| [org.apache.lucene.analysis.Analyzer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/Analyzer.java) | org.gnit.lucenekmp.analysis.Analyzer | Depth 2 | [x] | 4 | 2 | 0% |
| [org.apache.lucene.analysis.CharArrayMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/CharArrayMap.java) | org.gnit.lucenekmp.analysis.CharArrayMap | Depth 3 | [x] | 9 | 8 | 95% |
| [org.apache.lucene.analysis.CharArraySet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/CharArraySet.java) | org.gnit.lucenekmp.analysis.CharArraySet | Depth 1 | [x] | 9 | 1 | 55% |
| [org.apache.lucene.analysis.CharFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/CharFilter.java) | org.gnit.lucenekmp.analysis.CharFilter | Depth 1 | [x] | 3 | 7 | 34% |
| [org.apache.lucene.analysis.CharacterUtils](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/CharacterUtils.java) | org.gnit.lucenekmp.analysis.CharacterUtils | Depth 4 | [x] | 1 | 1 | 53% |
| [org.apache.lucene.analysis.FilteringTokenFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/FilteringTokenFilter.java) | org.gnit.lucenekmp.analysis.FilteringTokenFilter | Depth 2 | [x] | 21 | 19 | 84% |
| [org.apache.lucene.analysis.LowerCaseFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/LowerCaseFilter.java) | org.gnit.lucenekmp.analysis.LowerCaseFilter | Depth 2 | [x] | 21 | 19 | 84% |
| [org.apache.lucene.analysis.ReusableStringReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/ReusableStringReader.java) | org.gnit.lucenekmp.analysis.ReusableStringReader | Depth 1 | [x] | 2 | 5 | 47% |
| [org.apache.lucene.analysis.TestCharFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/analysis/TestCharFilter.java) | org.gnit.lucenekmp.analysis.TestCharFilter | Depth 1 | [x] | 4 | 7 | 45% |
| [org.apache.lucene.analysis.TokenFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/TokenFilter.java) | org.gnit.lucenekmp.analysis.TokenFilter | Depth 2 | [x] | 21 | 19 | 84% |
| [org.apache.lucene.analysis.TokenStream](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/TokenStream.java) | org.gnit.lucenekmp.analysis.TokenStream | Depth 1 | [x] | 20 | 0 | 0% |
| [org.apache.lucene.analysis.TokenStreamToAutomaton](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/TokenStreamToAutomaton.java) | org.gnit.lucenekmp.analysis.TokenStreamToAutomaton | Depth 3 | [x] | 1 | 6 | 13% |
| [org.apache.lucene.analysis.Tokenizer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/Tokenizer.java) | org.gnit.lucenekmp.analysis.Tokenizer | Depth 2 | [x] | 2 | 5 | 32% |
| [org.apache.lucene.analysis.standard.SpoonFeedMaxCharsReaderWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/analysis/standard/SpoonFeedMaxCharsReaderWrapper.java) | org.gnit.lucenekmp.analysis.standard.SpoonFeedMaxCharsReaderWrapper | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.analysis.standard.StandardTokenizer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/standard/StandardTokenizer.java) | org.gnit.lucenekmp.analysis.standard.StandardTokenizer | Depth 1 | [x] | 23 | 0 | 0% |
| [org.apache.lucene.analysis.standard.StandardTokenizerImpl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/standard/StandardTokenizerImpl.java) | org.gnit.lucenekmp.analysis.standard.StandardTokenizerImpl | Depth 2 | [x] | 23 | 14 | 89% |
| [org.apache.lucene.analysis.standard.TestStandardAnalyzer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/analysis/standard/TestStandardAnalyzer.java) | org.gnit.lucenekmp.analysis.standard.TestStandardAnalyzer | Depth 1 | [x] | 11 | 15 | 0% |
| [org.apache.lucene.analysis.tokenattributes.BytesTermAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/BytesTermAttribute.java) | org.gnit.lucenekmp.analysis.tokenattributes.BytesTermAttribute | Depth 3 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.analysis.tokenattributes.FlagsAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/FlagsAttribute.java) | org.gnit.lucenekmp.analysis.tokenattributes.FlagsAttribute | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.analysis.tokenattributes.FlagsAttributeImpl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/FlagsAttributeImpl.java) | org.gnit.lucenekmp.analysis.tokenattributes.FlagsAttributeImpl | Depth 1 | [x] | 5 | 7 | 77% |
| [org.apache.lucene.analysis.tokenattributes.KeywordAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/KeywordAttribute.java) | org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.analysis.tokenattributes.PackedTokenAttributeImpl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/PackedTokenAttributeImpl.java) | org.gnit.lucenekmp.analysis.tokenattributes.PackedTokenAttributeImpl | Depth 1 | [x] | 22 | 24 | 86% |
| [org.apache.lucene.analysis.tokenattributes.PayloadAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/PayloadAttribute.java) | org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/PositionIncrementAttribute.java) | org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/PositionLengthAttribute.java) | org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.analysis.tokenattributes.TermFrequencyAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/TermFrequencyAttribute.java) | org.gnit.lucenekmp.analysis.tokenattributes.TermFrequencyAttribute | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/TermToBytesRefAttribute.java) | org.gnit.lucenekmp.analysis.tokenattributes.TermToBytesRefAttribute | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.analysis.tokenattributes.TestCharTermAttributeImpl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/analysis/tokenattributes/TestCharTermAttributeImpl.java) | org.gnit.lucenekmp.analysis.tokenattributes.TestCharTermAttributeImpl | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.analysis.tokenattributes.TestPackedTokenAttributeImpl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/analysis/tokenattributes/TestPackedTokenAttributeImpl.java) | org.gnit.lucenekmp.analysis.tokenattributes.TestPackedTokenAttributeImpl | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.analysis.tokenattributes.TypeAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/TypeAttribute.java) | org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.BlockTermState](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/BlockTermState.java) | org.gnit.lucenekmp.codecs.BlockTermState | Depth 2 | [x] | 1 | 1 | 21% |
| [org.apache.lucene.codecs.Codec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/Codec.java) | org.gnit.lucenekmp.codecs.Codec | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.CompetitiveImpactAccumulator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/CompetitiveImpactAccumulator.java) | org.gnit.lucenekmp.codecs.CompetitiveImpactAccumulator | Depth 4 | [x] | 5 | 6 | 85% |
| [org.apache.lucene.codecs.DocValuesConsumer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/DocValuesConsumer.java) | org.gnit.lucenekmp.codecs.DocValuesConsumer | Depth 4 | [x] | 8 | 12 | 0% |
| [org.apache.lucene.codecs.DocValuesFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/DocValuesFormat.java) | org.gnit.lucenekmp.codecs.DocValuesFormat | Depth 3 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.FieldsProducer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/FieldsProducer.java) | org.gnit.lucenekmp.codecs.FieldsProducer | Depth 2 | [x] | 3 | 3 | 96% |
| [org.apache.lucene.codecs.FilterCodec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/FilterCodec.java) | org.gnit.lucenekmp.codecs.FilterCodec | Depth 3 | [] | 14 | 0 | 0% |
| [org.apache.lucene.codecs.KnnFieldVectorsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/KnnFieldVectorsWriter.java) | org.gnit.lucenekmp.codecs.KnnFieldVectorsWriter | Depth 2 | [x] | 3 | 3 | 88% |
| [org.apache.lucene.codecs.KnnVectorsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/KnnVectorsFormat.java) | org.gnit.lucenekmp.codecs.KnnVectorsFormat | Depth 2 | [x] | 6 | 0 | 0% |
| [org.apache.lucene.codecs.KnnVectorsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/KnnVectorsWriter.java) | org.gnit.lucenekmp.codecs.KnnVectorsWriter | Depth 3 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.NormsConsumer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/NormsConsumer.java) | org.gnit.lucenekmp.codecs.NormsConsumer | Depth 4 | [x] | 3 | 7 | 0% |
| [org.apache.lucene.codecs.PointsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/PointsFormat.java) | org.gnit.lucenekmp.codecs.PointsFormat | Depth 3 | [x] | 2 | 3 | 0% |
| [org.apache.lucene.codecs.PointsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/PointsWriter.java) | org.gnit.lucenekmp.codecs.PointsWriter | Depth 4 | [x] | 3 | 3 | 0% |
| [org.apache.lucene.codecs.PostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/PostingsFormat.java) | org.gnit.lucenekmp.codecs.PostingsFormat | Depth 3 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.PushPostingsWriterBase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/PushPostingsWriterBase.java) | org.gnit.lucenekmp.codecs.PushPostingsWriterBase | Depth 4 | [x] | 10 | 10 | 59% |
| [org.apache.lucene.codecs.StoredFieldsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/StoredFieldsReader.java) | org.gnit.lucenekmp.codecs.StoredFieldsReader | Depth 2 | [x] | 5 | 5 | 96% |
| [org.apache.lucene.codecs.StoredFieldsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/StoredFieldsWriter.java) | org.gnit.lucenekmp.codecs.StoredFieldsWriter | Depth 4 | [x] | 9 | 2 | 0% |
| [org.apache.lucene.codecs.TermStats](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/TermStats.java) | org.gnit.lucenekmp.codecs.TermStats | Depth 4 | [x] | 2 | 0 | 23% |
| [org.apache.lucene.codecs.TermVectorsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/TermVectorsReader.java) | org.gnit.lucenekmp.codecs.TermVectorsReader | Depth 1 | [x] | 4 | 4 | 92% |
| [org.apache.lucene.codecs.TermVectorsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/TermVectorsWriter.java) | org.gnit.lucenekmp.codecs.TermVectorsWriter | Depth 3 | [x] | 2 | 2 | 35% |
| [org.apache.lucene.codecs.compressing.CompressionMode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/compressing/CompressionMode.java) | org.gnit.lucenekmp.codecs.compressing.CompressionMode | Depth 4 | [x] | 2 | 2 | 0% |
| [org.apache.lucene.codecs.compressing.Decompressor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/compressing/Decompressor.java) | org.gnit.lucenekmp.codecs.compressing.Decompressor | Depth 4 | [x] | 1 | 1 | 85% |
| [org.apache.lucene.codecs.compressing.MatchingReaders](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/compressing/MatchingReaders.java) | org.gnit.lucenekmp.codecs.compressing.MatchingReaders | Depth 5 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.hnsw.DefaultFlatVectorScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/hnsw/DefaultFlatVectorScorer.java) | org.gnit.lucenekmp.codecs.hnsw.DefaultFlatVectorScorer | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.hnsw.FlatFieldVectorsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/hnsw/FlatFieldVectorsWriter.java) | org.gnit.lucenekmp.codecs.hnsw.FlatFieldVectorsWriter | Depth 4 | [x] | 4 | 4 | 93% |
| [org.apache.lucene.codecs.hnsw.FlatVectorScorerUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/hnsw/FlatVectorScorerUtil.java) | org.gnit.lucenekmp.codecs.hnsw.FlatVectorScorerUtil | Depth 3 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.hnsw.FlatVectorsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/hnsw/FlatVectorsFormat.java) | org.gnit.lucenekmp.codecs.hnsw.FlatVectorsFormat | Depth 2 | [x] | 6 | 5 | 56% |
| [org.apache.lucene.codecs.hnsw.FlatVectorsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/hnsw/FlatVectorsReader.java) | org.gnit.lucenekmp.codecs.hnsw.FlatVectorsReader | Depth 2 | [x] | 9 | 9 | 91% |
| [org.apache.lucene.codecs.hnsw.FlatVectorsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/hnsw/FlatVectorsWriter.java) | org.gnit.lucenekmp.codecs.hnsw.FlatVectorsWriter | Depth 3 | [x] | 9 | 9 | 81% |
| [org.apache.lucene.codecs.hnsw.ScalarQuantizedVectorScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/hnsw/ScalarQuantizedVectorScorer.java) | org.gnit.lucenekmp.codecs.hnsw.ScalarQuantizedVectorScorer | Depth 7 | [x] | 4 | 4 | 90% |
| [org.apache.lucene.codecs.lucene101.ForDeltaUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene101/ForDeltaUtil.java) | org.gnit.lucenekmp.codecs.lucene101.ForDeltaUtil | Depth 1 | [x] | 19 | 16 | 85% |
| [org.apache.lucene.codecs.lucene101.ForUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene101/ForUtil.java) | org.gnit.lucenekmp.codecs.lucene101.ForUtil | Depth 1 | [x] | 30 | 28 | 53% |
| [org.apache.lucene.codecs.lucene101.Lucene101Codec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene101/Lucene101Codec.java) | org.gnit.lucenekmp.codecs.lucene101.Lucene101Codec | Depth 3 | [x] | 8 | 3 | 86% |
| [org.apache.lucene.codecs.lucene101.Lucene101PostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene101/Lucene101PostingsFormat.java) | org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat | Depth 2 | [x] | 5 | 1 | 1% |
| [org.apache.lucene.codecs.lucene101.Lucene101PostingsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene101/Lucene101PostingsReader.java) | org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsReader | Depth 2 | [x] | 32 | 1 | 0% |
| [org.apache.lucene.codecs.lucene101.Lucene101PostingsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene101/Lucene101PostingsWriter.java) | org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsWriter | Depth 3 | [x] | 15 | 3 | 83% |
| [org.apache.lucene.codecs.lucene90.IndexedDISI](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/IndexedDISI.java) | org.gnit.lucenekmp.codecs.lucene90.IndexedDISI | Depth 4 | [x] | 7 | 3 | 0% |
| [org.apache.lucene.codecs.lucene90.LZ4WithPresetDictCompressionMode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/LZ4WithPresetDictCompressionMode.java) | org.gnit.lucenekmp.codecs.lucene90.LZ4WithPresetDictCompressionMode | Depth 5 | [x] | 3 | 2 | 0% |
| [org.apache.lucene.codecs.lucene90.Lucene90CompoundFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/Lucene90CompoundFormat.java) | org.gnit.lucenekmp.codecs.lucene90.Lucene90CompoundFormat | Depth 4 | [x] | 0 | 15 | 0% |
| [org.apache.lucene.codecs.lucene90.Lucene90CompoundReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/Lucene90CompoundReader.java) | org.gnit.lucenekmp.codecs.lucene90.Lucene90CompoundReader | Depth 5 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.lucene90.Lucene90DocValuesConsumer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/Lucene90DocValuesConsumer.java) | org.gnit.lucenekmp.codecs.lucene90.Lucene90DocValuesConsumer | Depth 4 | [x] | 8 | 7 | 0% |
| [org.apache.lucene.codecs.lucene90.Lucene90DocValuesFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/Lucene90DocValuesFormat.java) | org.gnit.lucenekmp.codecs.lucene90.Lucene90DocValuesFormat | Depth 2 | [x] | 5 | 0 | 0% |
| [org.apache.lucene.codecs.lucene90.Lucene90DocValuesProducer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/Lucene90DocValuesProducer.java) | org.gnit.lucenekmp.codecs.lucene90.Lucene90DocValuesProducer | Depth 4 | [x] | 9 | 12 | 44% |
| [org.apache.lucene.codecs.lucene90.Lucene90NormsConsumer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/Lucene90NormsConsumer.java) | org.gnit.lucenekmp.codecs.lucene90.Lucene90NormsConsumer | Depth 4 | [x] | 6 | 6 | 62% |
| [org.apache.lucene.codecs.lucene90.Lucene90NormsProducer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/Lucene90NormsProducer.java) | org.gnit.lucenekmp.codecs.lucene90.Lucene90NormsProducer | Depth 5 | [x] | 26 | 7 | 0% |
| [org.apache.lucene.codecs.lucene90.Lucene90PointsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/Lucene90PointsWriter.java) | org.gnit.lucenekmp.codecs.lucene90.Lucene90PointsWriter | Depth 5 | [x] | 7 | 3 | 65% |
| [org.apache.lucene.codecs.lucene90.blocktree.CompressionAlgorithm](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/blocktree/CompressionAlgorithm.java) | org.gnit.lucenekmp.codecs.lucene90.blocktree.CompressionAlgorithm | Depth 6 | [x] | 3 | 2 | 66% |
| [org.apache.lucene.codecs.lucene90.blocktree.IntersectTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/blocktree/IntersectTermsEnum.java) | org.gnit.lucenekmp.codecs.lucene90.blocktree.IntersectTermsEnum | Depth 6 | [x] | 1 | 0 | 0% |
| [org.apache.lucene.codecs.lucene90.blocktree.IntersectTermsEnumFrame](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/blocktree/IntersectTermsEnumFrame.java) | org.gnit.lucenekmp.codecs.lucene90.blocktree.IntersectTermsEnumFrame | Depth 6 | [x] | 7 | 7 | 20% |
| [org.apache.lucene.codecs.lucene90.blocktree.Lucene90BlockTreeTermsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/blocktree/Lucene90BlockTreeTermsReader.java) | org.gnit.lucenekmp.codecs.lucene90.blocktree.Lucene90BlockTreeTermsReader | Depth 3 | [x] | 5 | 2 | 55% |
| [org.apache.lucene.codecs.lucene90.blocktree.Lucene90BlockTreeTermsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/blocktree/Lucene90BlockTreeTermsWriter.java) | org.gnit.lucenekmp.codecs.lucene90.blocktree.Lucene90BlockTreeTermsWriter | Depth 4 | [x] | 2 | 6 | 0% |
| [org.apache.lucene.codecs.lucene90.blocktree.SegmentTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/blocktree/SegmentTermsEnum.java) | org.gnit.lucenekmp.codecs.lucene90.blocktree.SegmentTermsEnum | Depth 6 | [x] | 24 | 24 | 80% |
| [org.apache.lucene.codecs.lucene90.blocktree.SegmentTermsEnumFrame](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/blocktree/SegmentTermsEnumFrame.java) | org.gnit.lucenekmp.codecs.lucene90.blocktree.SegmentTermsEnumFrame | Depth 6 | [x] | 15 | 15 | 32% |
| [org.apache.lucene.codecs.lucene90.blocktree.Stats](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/blocktree/Stats.java) | org.gnit.lucenekmp.codecs.lucene90.blocktree.Stats | Depth 5 | [x] | 4 | 1 | 14% |
| [org.apache.lucene.codecs.lucene90.compressing.FieldsIndex](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/compressing/FieldsIndex.java) | org.gnit.lucenekmp.codecs.lucene90.compressing.FieldsIndex | Depth 5 | [x] | 5 | 5 | 96% |
| [org.apache.lucene.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/compressing/Lucene90CompressingStoredFieldsReader.java) | org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsReader | Depth 5 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/compressing/Lucene90CompressingStoredFieldsWriter.java) | org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter | Depth 5 | [x] | 2 | 1 | 0% |
| [org.apache.lucene.codecs.lucene90.compressing.Lucene90CompressingTermVectorsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/compressing/Lucene90CompressingTermVectorsReader.java) | org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsReader | Depth 5 | [x] | 3 | 14 | 0% |
| [org.apache.lucene.codecs.lucene90.compressing.Lucene90CompressingTermVectorsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene90/compressing/Lucene90CompressingTermVectorsWriter.java) | org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsWriter | Depth 5 | [x] | 2 | 2 | 0% |
| [org.apache.lucene.codecs.lucene94.Lucene94FieldInfosFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene94/Lucene94FieldInfosFormat.java) | org.gnit.lucenekmp.codecs.lucene94.Lucene94FieldInfosFormat | Depth 4 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.lucene95.HasIndexSlice](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene95/HasIndexSlice.java) | org.gnit.lucenekmp.codecs.lucene95.HasIndexSlice | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.lucene95.OffHeapByteVectorValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene95/OffHeapByteVectorValues.java) | org.gnit.lucenekmp.codecs.lucene95.OffHeapByteVectorValues | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.lucene95.OffHeapFloatVectorValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene95/OffHeapFloatVectorValues.java) | org.gnit.lucenekmp.codecs.lucene95.OffHeapFloatVectorValues | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.lucene99.Lucene99FlatVectorsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene99/Lucene99FlatVectorsReader.java) | org.gnit.lucenekmp.codecs.lucene99.Lucene99FlatVectorsReader | Depth 5 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.lucene99.Lucene99FlatVectorsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene99/Lucene99FlatVectorsWriter.java) | org.gnit.lucenekmp.codecs.lucene99.Lucene99FlatVectorsWriter | Depth 5 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene99/Lucene99HnswVectorsReader.java) | org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswVectorsReader | Depth 4 | [x] | 12 | 8 | 14% |
| [org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene99/Lucene99HnswVectorsWriter.java) | org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswVectorsWriter | Depth 4 | [x] | 8 | 8 | 97% |
| [org.apache.lucene.codecs.lucene99.Lucene99ScalarQuantizedVectorScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene99/Lucene99ScalarQuantizedVectorScorer.java) | org.gnit.lucenekmp.codecs.lucene99.Lucene99ScalarQuantizedVectorScorer | Depth 6 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.lucene99.Lucene99ScalarQuantizedVectorsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene99/Lucene99ScalarQuantizedVectorsReader.java) | org.gnit.lucenekmp.codecs.lucene99.Lucene99ScalarQuantizedVectorsReader | Depth 6 | [x] | 7 | 6 | 12% |
| [org.apache.lucene.codecs.lucene99.Lucene99ScalarQuantizedVectorsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene99/Lucene99ScalarQuantizedVectorsWriter.java) | org.gnit.lucenekmp.codecs.lucene99.Lucene99ScalarQuantizedVectorsWriter | Depth 6 | [x] | 5 | 2 | 0% |
| [org.apache.lucene.codecs.lucene99.OffHeapQuantizedByteVectorValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene99/OffHeapQuantizedByteVectorValues.java) | org.gnit.lucenekmp.codecs.lucene99.OffHeapQuantizedByteVectorValues | Depth 7 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.perfield.PerFieldDocValuesFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/perfield/PerFieldDocValuesFormat.java) | org.gnit.lucenekmp.codecs.perfield.PerFieldDocValuesFormat | Depth 3 | [x] | 1 | 14 | 7% |
| [org.apache.lucene.codecs.perfield.PerFieldKnnVectorsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/perfield/PerFieldKnnVectorsFormat.java) | org.gnit.lucenekmp.codecs.perfield.PerFieldKnnVectorsFormat | Depth 3 | [x] | 10 | 1 | 45% |
| [org.apache.lucene.codecs.perfield.PerFieldMergeState](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/perfield/PerFieldMergeState.java) | org.gnit.lucenekmp.codecs.perfield.PerFieldMergeState | Depth 5 | [x] | 16 | 3 | 12% |
| [org.apache.lucene.codecs.perfield.PerFieldPostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/perfield/PerFieldPostingsFormat.java) | org.gnit.lucenekmp.codecs.perfield.PerFieldPostingsFormat | Depth 3 | [x] | 0 | 1 | 0% |
| [org.apache.lucene.document.BinaryDocValuesField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/BinaryDocValuesField.java) | org.gnit.lucenekmp.document.BinaryDocValuesField | Depth 1 | [x] | 9 | 0 | 0% |
| [org.apache.lucene.document.BinaryPoint](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/BinaryPoint.java) | org.gnit.lucenekmp.document.BinaryPoint | Depth 3 | [x] | 7 | 0 | 0% |
| [org.apache.lucene.document.DoubleDocValuesField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/DoubleDocValuesField.java) | org.gnit.lucenekmp.document.DoubleDocValuesField | Depth 1 | [x] | 13 | 9 | 85% |
| [org.apache.lucene.document.DoublePoint](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/DoublePoint.java) | org.gnit.lucenekmp.document.DoublePoint | Depth 2 | [x] | 7 | 0 | 0% |
| [org.apache.lucene.document.Field](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/Field.java) | org.gnit.lucenekmp.document.Field | Depth 2 | [x] | 20 | 19 | 83% |
| [org.apache.lucene.document.FloatDocValuesField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/FloatDocValuesField.java) | org.gnit.lucenekmp.document.FloatDocValuesField | Depth 1 | [x] | 13 | 9 | 85% |
| [org.apache.lucene.document.FloatPoint](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/FloatPoint.java) | org.gnit.lucenekmp.document.FloatPoint | Depth 2 | [x] | 7 | 0 | 0% |
| [org.apache.lucene.document.IntPoint](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/IntPoint.java) | org.gnit.lucenekmp.document.IntPoint | Depth 2 | [x] | 7 | 0 | 0% |
| [org.apache.lucene.document.InvertableType](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/InvertableType.java) | org.gnit.lucenekmp.document.InvertableType | Depth 2 | [x] | 1 | 1 | 75% |
| [org.apache.lucene.document.KeywordField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/KeywordField.java) | org.gnit.lucenekmp.document.KeywordField | Depth 2 | [] | 13 | 0 | 0% |
| [org.apache.lucene.document.KnnByteVectorField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/KnnByteVectorField.java) | org.gnit.lucenekmp.document.KnnByteVectorField | Depth 1 | [x] | 13 | 3 | 93% |
| [org.apache.lucene.document.KnnFloatVectorField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/KnnFloatVectorField.java) | org.gnit.lucenekmp.document.KnnFloatVectorField | Depth 1 | [x] | 13 | 3 | 93% |
| [org.apache.lucene.document.LongDistanceFeatureQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/LongDistanceFeatureQuery.java) | org.gnit.lucenekmp.document.LongDistanceFeatureQuery | Depth 3 | [x] | 9 | 7 | 0% |
| [org.apache.lucene.document.LongPoint](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/LongPoint.java) | org.gnit.lucenekmp.document.LongPoint | Depth 2 | [x] | 7 | 0 | 0% |
| [org.apache.lucene.document.NumericDocValuesField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/NumericDocValuesField.java) | org.gnit.lucenekmp.document.NumericDocValuesField | Depth 1 | [x] | 13 | 4 | 86% |
| [org.apache.lucene.document.SortedDocValuesField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/SortedDocValuesField.java) | org.gnit.lucenekmp.document.SortedDocValuesField | Depth 1 | [x] | 13 | 4 | 86% |
| [org.apache.lucene.document.SortedNumericDocValuesField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/SortedNumericDocValuesField.java) | org.gnit.lucenekmp.document.SortedNumericDocValuesField | Depth 1 | [x] | 13 | 4 | 86% |
| [org.apache.lucene.document.SortedNumericDocValuesRangeQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/SortedNumericDocValuesRangeQuery.java) | org.gnit.lucenekmp.document.SortedNumericDocValuesRangeQuery | Depth 3 | [x] | 7 | 1 | 0% |
| [org.apache.lucene.document.SortedNumericDocValuesSetQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/SortedNumericDocValuesSetQuery.java) | org.gnit.lucenekmp.document.SortedNumericDocValuesSetQuery | Depth 3 | [x] | 7 | 1 | 0% |
| [org.apache.lucene.document.SortedSetDocValuesField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/SortedSetDocValuesField.java) | org.gnit.lucenekmp.document.SortedSetDocValuesField | Depth 1 | [] | 13 | 0 | 0% |
| [org.apache.lucene.document.SortedSetDocValuesRangeQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/SortedSetDocValuesRangeQuery.java) | org.gnit.lucenekmp.document.SortedSetDocValuesRangeQuery | Depth 3 | [x] | 7 | 1 | 0% |
| [org.apache.lucene.document.StoredField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/StoredField.java) | org.gnit.lucenekmp.document.StoredField | Depth 1 | [x] | 9 | 0 | 0% |
| [org.apache.lucene.document.StoredValue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/StoredValue.java) | org.gnit.lucenekmp.document.StoredValue | Depth 2 | [x] | 1 | 1 | 75% |
| [org.apache.lucene.document.StringField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/StringField.java) | org.gnit.lucenekmp.document.StringField | Depth 1 | [x] | 9 | 0 | 0% |
| [org.apache.lucene.document.TextField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/TextField.java) | org.gnit.lucenekmp.document.TextField | Depth 1 | [x] | 9 | 0 | 0% |
| [org.apache.lucene.index.ApproximatePriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ApproximatePriorityQueue.java) | org.gnit.lucenekmp.index.ApproximatePriorityQueue | Depth 1 | [x] | 4 | 4 | 34% |
| [org.apache.lucene.index.AutomatonTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/AutomatonTermsEnum.java) | org.gnit.lucenekmp.index.AutomatonTermsEnum | Depth 2 | [x] | 17 | 17 | 91% |
| [org.apache.lucene.index.BaseCompositeReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/BaseCompositeReader.java) | org.gnit.lucenekmp.index.BaseCompositeReader | Depth 3 | [x] | 3 | 3 | 93% |
| [org.apache.lucene.index.BinaryDocValuesWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/BinaryDocValuesWriter.java) | org.gnit.lucenekmp.index.BinaryDocValuesWriter | Depth 4 | [x] | 0 | 8 | 0% |
| [org.apache.lucene.index.BufferedUpdates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/BufferedUpdates.java) | org.gnit.lucenekmp.index.BufferedUpdates | Depth 3 | [x] | 1 | 1 | 0% |
| [org.apache.lucene.index.BufferedUpdatesStream](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/BufferedUpdatesStream.java) | org.gnit.lucenekmp.index.BufferedUpdatesStream | Depth 3 | [x] | 3 | 3 | 0% |
| [org.apache.lucene.index.ByteSlicePool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ByteSlicePool.java) | org.gnit.lucenekmp.index.ByteSlicePool | Depth 1 | [x] | 3 | 0 | 0% |
| [org.apache.lucene.index.ByteSliceReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ByteSliceReader.java) | org.gnit.lucenekmp.index.ByteSliceReader | Depth 1 | [x] | 21 | 21 | 69% |
| [org.apache.lucene.index.CachingMergeContext](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/CachingMergeContext.java) | org.gnit.lucenekmp.index.CachingMergeContext | Depth 1 | [x] | 2 | 2 | 72% |
| [org.apache.lucene.index.CheckIndex](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/CheckIndex.java) | org.gnit.lucenekmp.index.CheckIndex | Depth 2 | [x] | 0 | 3 | 0% |
| [org.apache.lucene.index.CompositeReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/CompositeReader.java) | org.gnit.lucenekmp.index.CompositeReader | Depth 2 | [x] | 20 | 20 | 89% |
| [org.apache.lucene.index.CompositeReaderContext](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/CompositeReaderContext.java) | org.gnit.lucenekmp.index.CompositeReaderContext | Depth 3 | [x] | 1 | 1 | 0% |
| [org.apache.lucene.index.ConcurrentApproximatePriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ConcurrentApproximatePriorityQueue.java) | org.gnit.lucenekmp.index.ConcurrentApproximatePriorityQueue | Depth 1 | [x] | 4 | 0 | 75% |
| [org.apache.lucene.index.ConcurrentMergeScheduler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ConcurrentMergeScheduler.java) | org.gnit.lucenekmp.index.ConcurrentMergeScheduler | Depth 2 | [x] | 16 | 3 | 0% |
| [org.apache.lucene.index.CorruptIndexException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/CorruptIndexException.java) | org.gnit.lucenekmp.index.CorruptIndexException | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.index.DirectoryReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DirectoryReader.java) | org.gnit.lucenekmp.index.DirectoryReader | Depth 1 | [x] | 37 | 13 | 89% |
| [org.apache.lucene.index.DocIDMerger](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocIDMerger.java) | org.gnit.lucenekmp.index.DocIDMerger | Depth 4 | [x] | 3 | 2 | 0% |
| [org.apache.lucene.index.DocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocValues.java) | org.gnit.lucenekmp.index.DocValues | Depth 3 | [x] | 9 | 11 | 54% |
| [org.apache.lucene.index.DocValuesFieldUpdates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocValuesFieldUpdates.java) | org.gnit.lucenekmp.index.DocValuesFieldUpdates | Depth 2 | [x] | 10 | 21 | 0% |
| [org.apache.lucene.index.DocValuesLeafReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocValuesLeafReader.java) | org.gnit.lucenekmp.index.DocValuesLeafReader | Depth 4 | [x] | 38 | 42 | 86% |
| [org.apache.lucene.index.DocValuesSkipIndexType](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocValuesSkipIndexType.java) | org.gnit.lucenekmp.index.DocValuesSkipIndexType | Depth 2 | [x] | 2 | 2 | 82% |
| [org.apache.lucene.index.DocValuesType](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocValuesType.java) | org.gnit.lucenekmp.index.DocValuesType | Depth 1 | [x] | 1 | 1 | 75% |
| [org.apache.lucene.index.DocValuesUpdate](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocValuesUpdate.java) | org.gnit.lucenekmp.index.DocValuesUpdate | Depth 1 | [x] | 5 | 1 | 0% |
| [org.apache.lucene.index.DocumentsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriter.java) | org.gnit.lucenekmp.index.DocumentsWriter | Depth 2 | [x] | 6 | 1 | 0% |
| [org.apache.lucene.index.DocumentsWriterDeleteQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriterDeleteQueue.java) | org.gnit.lucenekmp.index.DocumentsWriterDeleteQueue | Depth 2 | [x] | 1 | 1 | 44% |
| [org.apache.lucene.index.DocumentsWriterFlushControl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriterFlushControl.java) | org.gnit.lucenekmp.index.DocumentsWriterFlushControl | Depth 1 | [x] | 37 | 1 | 0% |
| [org.apache.lucene.index.DocumentsWriterFlushQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriterFlushQueue.java) | org.gnit.lucenekmp.index.DocumentsWriterFlushQueue | Depth 2 | [x] | 9 | 3 | 0% |
| [org.apache.lucene.index.DocumentsWriterPerThread](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriterPerThread.java) | org.gnit.lucenekmp.index.DocumentsWriterPerThread | Depth 2 | [x] | 0 | 2 | 0% |
| [org.apache.lucene.index.DocumentsWriterPerThreadPool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriterPerThreadPool.java) | org.gnit.lucenekmp.index.DocumentsWriterPerThreadPool | Depth 1 | [x] | 10 | 1 | 0% |
| [org.apache.lucene.index.ExitableDirectoryReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ExitableDirectoryReader.java) | org.gnit.lucenekmp.index.ExitableDirectoryReader | Depth 3 | [] | 8 | 0 | 0% |
| [org.apache.lucene.index.FieldInfos](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FieldInfos.java) | org.gnit.lucenekmp.index.FieldInfos | Depth 2 | [x] | 3 | 0 | 23% |
| [org.apache.lucene.index.FieldInvertState](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FieldInvertState.java) | org.gnit.lucenekmp.index.FieldInvertState | Depth 1 | [x] | 1 | 1 | 44% |
| [org.apache.lucene.index.FieldUpdatesBuffer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FieldUpdatesBuffer.java) | org.gnit.lucenekmp.index.FieldUpdatesBuffer | Depth 1 | [x] | 11 | 2 | 83% |
| [org.apache.lucene.index.Fields](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/Fields.java) | org.gnit.lucenekmp.index.Fields | Depth 1 | [x] | 1 | 0 | 0% |
| [org.apache.lucene.index.FilterBinaryDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FilterBinaryDocValues.java) | org.gnit.lucenekmp.index.FilterBinaryDocValues | Depth 4 | [x] | 9 | 7 | 92% |
| [org.apache.lucene.index.FilterCodecReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FilterCodecReader.java) | org.gnit.lucenekmp.index.FilterCodecReader | Depth 3 | [x] | 41 | 43 | 86% |
| [org.apache.lucene.index.FilterDirectoryReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FilterDirectoryReader.java) | org.gnit.lucenekmp.index.FilterDirectoryReader | Depth 3 | [x] | 40 | 2 | 0% |
| [org.apache.lucene.index.FilterLeafReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FilterLeafReader.java) | org.gnit.lucenekmp.index.FilterLeafReader | Depth 2 | [x] | 6 | 11 | 0% |
| [org.apache.lucene.index.FilterMergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FilterMergePolicy.java) | org.gnit.lucenekmp.index.FilterMergePolicy | Depth 1 | [x] | 16 | 16 | 86% |
| [org.apache.lucene.index.FilterNumericDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FilterNumericDocValues.java) | org.gnit.lucenekmp.index.FilterNumericDocValues | Depth 2 | [x] | 9 | 7 | 92% |
| [org.apache.lucene.index.FilterSortedDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FilterSortedDocValues.java) | org.gnit.lucenekmp.index.FilterSortedDocValues | Depth 4 | [x] | 13 | 11 | 95% |
| [org.apache.lucene.index.FilterSortedNumericDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FilterSortedNumericDocValues.java) | org.gnit.lucenekmp.index.FilterSortedNumericDocValues | Depth 4 | [x] | 10 | 8 | 93% |
| [org.apache.lucene.index.FilterSortedSetDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FilterSortedSetDocValues.java) | org.gnit.lucenekmp.index.FilterSortedSetDocValues | Depth 4 | [x] | 14 | 12 | 95% |
| [org.apache.lucene.index.FlushByRamOrCountsPolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FlushByRamOrCountsPolicy.java) | org.gnit.lucenekmp.index.FlushByRamOrCountsPolicy | Depth 1 | [x] | 9 | 9 | 78% |
| [org.apache.lucene.index.FlushPolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FlushPolicy.java) | org.gnit.lucenekmp.index.FlushPolicy | Depth 1 | [x] | 4 | 4 | 62% |
| [org.apache.lucene.index.FreqProxFields](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FreqProxFields.java) | org.gnit.lucenekmp.index.FreqProxFields | Depth 5 | [x] | 12 | 1 | 0% |
| [org.apache.lucene.index.FreqProxTermsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FreqProxTermsWriter.java) | org.gnit.lucenekmp.index.FreqProxTermsWriter | Depth 4 | [x] | 6 | 1 | 0% |
| [org.apache.lucene.index.FreqProxTermsWriterPerField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FreqProxTermsWriterPerField.java) | org.gnit.lucenekmp.index.FreqProxTermsWriterPerField | Depth 5 | [x] | 3 | 3 | 30% |
| [org.apache.lucene.index.FrozenBufferedUpdates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FrozenBufferedUpdates.java) | org.gnit.lucenekmp.index.FrozenBufferedUpdates | Depth 3 | [x] | 0 | 1 | 0% |
| [org.apache.lucene.index.Impact](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/Impact.java) | org.gnit.lucenekmp.index.Impact | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.index.ImpactsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ImpactsEnum.java) | org.gnit.lucenekmp.index.ImpactsEnum | Depth 2 | [x] | 12 | 9 | 91% |
| [org.apache.lucene.index.IndexFileDeleter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexFileDeleter.java) | org.gnit.lucenekmp.index.IndexFileDeleter | Depth 3 | [x] | 2 | 1 | 0% |
| [org.apache.lucene.index.IndexFileNames](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexFileNames.java) | org.gnit.lucenekmp.index.IndexFileNames | Depth 1 | [x] | 9 | 9 | 93% |
| [org.apache.lucene.index.IndexFormatTooNewException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexFormatTooNewException.java) | org.gnit.lucenekmp.index.IndexFormatTooNewException | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.index.IndexFormatTooOldException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexFormatTooOldException.java) | org.gnit.lucenekmp.index.IndexFormatTooOldException | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.index.IndexNotFoundException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexNotFoundException.java) | org.gnit.lucenekmp.index.IndexNotFoundException | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.index.IndexOptions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexOptions.java) | org.gnit.lucenekmp.index.IndexOptions | Depth 1 | [x] | 1 | 1 | 75% |
| [org.apache.lucene.index.IndexReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexReader.java) | org.gnit.lucenekmp.index.IndexReader | Depth 1 | [x] | 20 | 1 | 0% |
| [org.apache.lucene.index.IndexReaderContext](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexReaderContext.java) | org.gnit.lucenekmp.index.IndexReaderContext | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.index.IndexSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexSorter.java) | org.gnit.lucenekmp.index.IndexSorter | Depth 3 | [x] | 1 | 1 | 0% |
| [org.apache.lucene.index.IndexWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexWriter.java) | org.gnit.lucenekmp.index.IndexWriter | Depth 2 | [x] | 6 | 3 | 0% |
| [org.apache.lucene.index.IndexWriterConfig](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexWriterConfig.java) | org.gnit.lucenekmp.index.IndexWriterConfig | Depth 1 | [x] | 26 | 1 | 0% |
| [org.apache.lucene.index.IndexableField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexableField.java) | org.gnit.lucenekmp.index.IndexableField | Depth 1 | [x] | 8 | 1 | 0% |
| [org.apache.lucene.index.IndexingChain](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexingChain.java) | org.gnit.lucenekmp.index.IndexingChain | Depth 3 | [x] | 38 | 42 | 86% |
| [org.apache.lucene.index.KnnVectorValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/KnnVectorValues.java) | org.gnit.lucenekmp.index.KnnVectorValues | Depth 2 | [x] | 2 | 1 | 80% |
| [org.apache.lucene.index.LeafMetaData](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/LeafMetaData.java) | org.gnit.lucenekmp.index.LeafMetaData | Depth 1 | [x] | 3 | 0 | 25% |
| [org.apache.lucene.index.LeafReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/LeafReader.java) | org.gnit.lucenekmp.index.LeafReader | Depth 1 | [x] | 38 | 42 | 86% |
| [org.apache.lucene.index.LeafReaderContext](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/LeafReaderContext.java) | org.gnit.lucenekmp.index.LeafReaderContext | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.index.LiveIndexWriterConfig](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/LiveIndexWriterConfig.java) | org.gnit.lucenekmp.index.LiveIndexWriterConfig | Depth 1 | [x] | 6 | 6 | 52% |
| [org.apache.lucene.index.LockableConcurrentApproximatePriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/LockableConcurrentApproximatePriorityQueue.java) | org.gnit.lucenekmp.index.LockableConcurrentApproximatePriorityQueue | Depth 1 | [x] | 4 | 1 | 0% |
| [org.apache.lucene.index.MappedMultiFields](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MappedMultiFields.java) | org.gnit.lucenekmp.index.MappedMultiFields | Depth 5 | [x] | 6 | 11 | 0% |
| [org.apache.lucene.index.MappingMultiPostingsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MappingMultiPostingsEnum.java) | org.gnit.lucenekmp.index.MappingMultiPostingsEnum | Depth 7 | [x] | 2 | 2 | 40% |
| [org.apache.lucene.index.MergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MergePolicy.java) | org.gnit.lucenekmp.index.MergePolicy | Depth 2 | [x] | 0 | 1 | 0% |
| [org.apache.lucene.index.MergeRateLimiter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MergeRateLimiter.java) | org.gnit.lucenekmp.index.MergeRateLimiter | Depth 1 | [x] | 2 | 1 | 0% |
| [org.apache.lucene.index.MergeScheduler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MergeScheduler.java) | org.gnit.lucenekmp.index.MergeScheduler | Depth 1 | [x] | 6 | 3 | 0% |
| [org.apache.lucene.index.MergeState](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MergeState.java) | org.gnit.lucenekmp.index.MergeState | Depth 2 | [x] | 4 | 1 | 0% |
| [org.apache.lucene.index.MergeTrigger](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MergeTrigger.java) | org.gnit.lucenekmp.index.MergeTrigger | Depth 1 | [x] | 1 | 1 | 75% |
| [org.apache.lucene.index.MultiDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MultiDocValues.java) | org.gnit.lucenekmp.index.MultiDocValues | Depth 2 | [x] | 9 | 8 | 76% |
| [org.apache.lucene.index.MultiFields](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MultiFields.java) | org.gnit.lucenekmp.index.MultiFields | Depth 4 | [x] | 1 | 1 | 91% |
| [org.apache.lucene.index.MultiPostingsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MultiPostingsEnum.java) | org.gnit.lucenekmp.index.MultiPostingsEnum | Depth 3 | [x] | 13 | 0 | 7% |
| [org.apache.lucene.index.MultiReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MultiReader.java) | org.gnit.lucenekmp.index.MultiReader | Depth 1 | [x] | 22 | 3 | 0% |
| [org.apache.lucene.index.MultiSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MultiSorter.java) | org.gnit.lucenekmp.index.MultiSorter | Depth 4 | [x] | 10 | 15 | 70% |
| [org.apache.lucene.index.MultiTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MultiTermsEnum.java) | org.gnit.lucenekmp.index.MultiTermsEnum | Depth 3 | [x] | 12 | 6 | 0% |
| [org.apache.lucene.index.NoDeletionPolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/NoDeletionPolicy.java) | org.gnit.lucenekmp.index.NoDeletionPolicy | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.index.NoMergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/NoMergePolicy.java) | org.gnit.lucenekmp.index.NoMergePolicy | Depth 1 | [x] | 15 | 0 | 0% |
| [org.apache.lucene.index.NumericDocValuesFieldUpdates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/NumericDocValuesFieldUpdates.java) | org.gnit.lucenekmp.index.NumericDocValuesFieldUpdates | Depth 3 | [x] | 14 | 10 | 9% |
| [org.apache.lucene.index.NumericDocValuesWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/NumericDocValuesWriter.java) | org.gnit.lucenekmp.index.NumericDocValuesWriter | Depth 4 | [x] | 8 | 7 | 0% |
| [org.apache.lucene.index.OneMergeWrappingMergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/OneMergeWrappingMergePolicy.java) | org.gnit.lucenekmp.index.OneMergeWrappingMergePolicy | Depth 1 | [x] | 17 | 17 | 87% |
| [org.apache.lucene.index.OrdTermState](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/OrdTermState.java) | org.gnit.lucenekmp.index.OrdTermState | Depth 3 | [x] | 1 | 1 | 57% |
| [org.apache.lucene.index.OrdinalMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/OrdinalMap.java) | org.gnit.lucenekmp.index.OrdinalMap | Depth 3 | [x] | 1 | 15 | 0% |
| [org.apache.lucene.index.ParallelCompositeReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ParallelCompositeReader.java) | org.gnit.lucenekmp.index.ParallelCompositeReader | Depth 3 | [x] | 38 | 3 | 0% |
| [org.apache.lucene.index.ParallelLeafReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ParallelLeafReader.java) | org.gnit.lucenekmp.index.ParallelLeafReader | Depth 3 | [x] | 4 | 3 | 31% |
| [org.apache.lucene.index.PendingDeletes](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/PendingDeletes.java) | org.gnit.lucenekmp.index.PendingDeletes | Depth 1 | [x] | 13 | 13 | 83% |
| [org.apache.lucene.index.PointValuesWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/PointValuesWriter.java) | org.gnit.lucenekmp.index.PointValuesWriter | Depth 4 | [x] | 11 | 4 | 19% |
| [org.apache.lucene.index.PostingsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/PostingsEnum.java) | org.gnit.lucenekmp.index.PostingsEnum | Depth 1 | [x] | 11 | 1 | 25% |
| [org.apache.lucene.index.PrefixCodedTerms](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/PrefixCodedTerms.java) | org.gnit.lucenekmp.index.PrefixCodedTerms | Depth 1 | [x] | 1 | 2 | 0% |
| [org.apache.lucene.index.QueryTimeoutImpl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/QueryTimeoutImpl.java) | org.gnit.lucenekmp.index.QueryTimeoutImpl | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.index.ReaderPool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ReaderPool.java) | org.gnit.lucenekmp.index.ReaderPool | Depth 3 | [x] | 0 | 1 | 0% |
| [org.apache.lucene.index.ReaderSlice](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ReaderSlice.java) | org.gnit.lucenekmp.index.ReaderSlice | Depth 2 | [x] | 1 | 0 | 0% |
| [org.apache.lucene.index.ReadersAndUpdates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ReadersAndUpdates.java) | org.gnit.lucenekmp.index.ReadersAndUpdates | Depth 3 | [x] | 8 | 1 | 0% |
| [org.apache.lucene.index.SegmentCommitInfo](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentCommitInfo.java) | org.gnit.lucenekmp.index.SegmentCommitInfo | Depth 1 | [x] | 11 | 11 | 97% |
| [org.apache.lucene.index.SegmentCoreReaders](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentCoreReaders.java) | org.gnit.lucenekmp.index.SegmentCoreReaders | Depth 3 | [x] | 1 | 1 | 0% |
| [org.apache.lucene.index.SegmentDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentDocValues.java) | org.gnit.lucenekmp.index.SegmentDocValues | Depth 3 | [x] | 3 | 3 | 84% |
| [org.apache.lucene.index.SegmentDocValuesProducer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentDocValuesProducer.java) | org.gnit.lucenekmp.index.SegmentDocValuesProducer | Depth 2 | [x] | 8 | 8 | 83% |
| [org.apache.lucene.index.SegmentInfos](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentInfos.java) | org.gnit.lucenekmp.index.SegmentInfos | Depth 2 | [x] | 2 | 2 | 47% |
| [org.apache.lucene.index.SegmentMerger](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentMerger.java) | org.gnit.lucenekmp.index.SegmentMerger | Depth 3 | [x] | 0 | 1 | 0% |
| [org.apache.lucene.index.SegmentReadState](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentReadState.java) | org.gnit.lucenekmp.index.SegmentReadState | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.index.SegmentReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentReader.java) | org.gnit.lucenekmp.index.SegmentReader | Depth 2 | [x] | 1 | 1 | 37% |
| [org.apache.lucene.index.SegmentWriteState](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SegmentWriteState.java) | org.gnit.lucenekmp.index.SegmentWriteState | Depth 2 | [x] | 1 | 1 | 11% |
| [org.apache.lucene.index.SerialMergeScheduler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SerialMergeScheduler.java) | org.gnit.lucenekmp.index.SerialMergeScheduler | Depth 1 | [x] | 6 | 1 | 0% |
| [org.apache.lucene.index.SimpleMergedSegmentWarmer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SimpleMergedSegmentWarmer.java) | org.gnit.lucenekmp.index.SimpleMergedSegmentWarmer | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.index.SingleTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SingleTermsEnum.java) | org.gnit.lucenekmp.index.SingleTermsEnum | Depth 2 | [x] | 13 | 13 | 88% |
| [org.apache.lucene.index.SlowCodecReaderWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SlowCodecReaderWrapper.java) | org.gnit.lucenekmp.index.SlowCodecReaderWrapper | Depth 3 | [x] | 39 | 43 | 87% |
| [org.apache.lucene.index.SlowCompositeCodecReaderWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SlowCompositeCodecReaderWrapper.java) | org.gnit.lucenekmp.index.SlowCompositeCodecReaderWrapper | Depth 3 | [x] | 2 | 5 | 0% |
| [org.apache.lucene.index.SnapshotDeletionPolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SnapshotDeletionPolicy.java) | org.gnit.lucenekmp.index.SnapshotDeletionPolicy | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.index.SoftDeletesDirectoryReaderWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SoftDeletesDirectoryReaderWrapper.java) | org.gnit.lucenekmp.index.SoftDeletesDirectoryReaderWrapper | Depth 3 | [x] | 41 | 2 | 0% |
| [org.apache.lucene.index.SoftDeletesRetentionMergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SoftDeletesRetentionMergePolicy.java) | org.gnit.lucenekmp.index.SoftDeletesRetentionMergePolicy | Depth 2 | [] | 15 | 0 | 0% |
| [org.apache.lucene.index.SortFieldProvider](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortFieldProvider.java) | org.gnit.lucenekmp.index.SortFieldProvider | Depth 4 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.index.SortedDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortedDocValues.java) | org.gnit.lucenekmp.index.SortedDocValues | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.index.SortedDocValuesTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortedDocValuesTermsEnum.java) | org.gnit.lucenekmp.index.SortedDocValuesTermsEnum | Depth 2 | [x] | 11 | 12 | 91% |
| [org.apache.lucene.index.SortedNumericDocValuesWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortedNumericDocValuesWriter.java) | org.gnit.lucenekmp.index.SortedNumericDocValuesWriter | Depth 4 | [x] | 0 | 8 | 0% |
| [org.apache.lucene.index.SortedSetDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortedSetDocValues.java) | org.gnit.lucenekmp.index.SortedSetDocValues | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.index.SortedSetDocValuesTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortedSetDocValuesTermsEnum.java) | org.gnit.lucenekmp.index.SortedSetDocValuesTermsEnum | Depth 2 | [x] | 11 | 12 | 91% |
| [org.apache.lucene.index.SortedSetDocValuesWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortedSetDocValuesWriter.java) | org.gnit.lucenekmp.index.SortedSetDocValuesWriter | Depth 4 | [x] | 0 | 8 | 0% |
| [org.apache.lucene.index.Sorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/Sorter.java) | org.gnit.lucenekmp.index.Sorter | Depth 3 | [x] | 2 | 1 | 0% |
| [org.apache.lucene.index.SortingCodecReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortingCodecReader.java) | org.gnit.lucenekmp.index.SortingCodecReader | Depth 3 | [x] | 41 | 7 | 52% |
| [org.apache.lucene.index.SortingStoredFieldsConsumer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortingStoredFieldsConsumer.java) | org.gnit.lucenekmp.index.SortingStoredFieldsConsumer | Depth 4 | [x] | 2 | 8 | 0% |
| [org.apache.lucene.index.SortingTermVectorsConsumer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortingTermVectorsConsumer.java) | org.gnit.lucenekmp.index.SortingTermVectorsConsumer | Depth 3 | [x] | 12 | 1 | 83% |
| [org.apache.lucene.index.StandardDirectoryReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/StandardDirectoryReader.java) | org.gnit.lucenekmp.index.StandardDirectoryReader | Depth 2 | [x] | 2 | 1 | 0% |
| [org.apache.lucene.index.StoredFieldDataInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/StoredFieldDataInput.java) | org.gnit.lucenekmp.index.StoredFieldDataInput | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.index.StoredFieldVisitor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/StoredFieldVisitor.java) | org.gnit.lucenekmp.index.StoredFieldVisitor | Depth 3 | [x] | 1 | 1 | 75% |
| [org.apache.lucene.index.StoredFieldsConsumer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/StoredFieldsConsumer.java) | org.gnit.lucenekmp.index.StoredFieldsConsumer | Depth 4 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.index.TermStates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/TermStates.java) | org.gnit.lucenekmp.index.TermStates | Depth 3 | [x] | 1 | 0 | 0% |
| [org.apache.lucene.index.TermVectors](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/TermVectors.java) | org.gnit.lucenekmp.index.TermVectors | Depth 2 | [x] | 3 | 3 | 93% |
| [org.apache.lucene.index.TermVectorsConsumer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/TermVectorsConsumer.java) | org.gnit.lucenekmp.index.TermVectorsConsumer | Depth 3 | [x] | 11 | 11 | 50% |
| [org.apache.lucene.index.TermVectorsConsumerPerField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/TermVectorsConsumerPerField.java) | org.gnit.lucenekmp.index.TermVectorsConsumerPerField | Depth 5 | [x] | 3 | 3 | 37% |
| [org.apache.lucene.index.Terms](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/Terms.java) | org.gnit.lucenekmp.index.Terms | Depth 2 | [x] | 17 | 17 | 91% |
| [org.apache.lucene.index.TermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/TermsEnum.java) | org.gnit.lucenekmp.index.TermsEnum | Depth 2 | [x] | 11 | 1 | 0% |
| [org.apache.lucene.index.TermsEnumIndex](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/TermsEnumIndex.java) | org.gnit.lucenekmp.index.TermsEnumIndex | Depth 2 | [x] | 1 | 1 | 35% |
| [org.apache.lucene.index.TermsHash](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/TermsHash.java) | org.gnit.lucenekmp.index.TermsHash | Depth 3 | [x] | 6 | 6 | 62% |
| [org.apache.lucene.index.TestByteSlicePool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestByteSlicePool.java) | org.gnit.lucenekmp.index.TestByteSlicePool | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.index.TestCachingMergeContext](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestCachingMergeContext.java) | org.gnit.lucenekmp.index.TestCachingMergeContext | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.index.TestConcurrentApproximatePriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestConcurrentApproximatePriorityQueue.java) | org.gnit.lucenekmp.index.TestConcurrentApproximatePriorityQueue | Depth 1 | [x] | 1 | 7 | 0% |
| [org.apache.lucene.index.TestDirectoryReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestDirectoryReader.java) | org.gnit.lucenekmp.index.TestDirectoryReader | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.index.TestDocumentsWriterStallControl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestDocumentsWriterStallControl.java) | org.gnit.lucenekmp.index.TestDocumentsWriterStallControl | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.index.TestExitableDirectoryReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestExitableDirectoryReader.java) | org.gnit.lucenekmp.index.TestExitableDirectoryReader | Depth 2 | [] | 6 | 0 | 0% |
| [org.apache.lucene.index.TestFieldInvertState](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestFieldInvertState.java) | org.gnit.lucenekmp.index.TestFieldInvertState | Depth 1 | [x] | 2 | 0 | 0% |
| [org.apache.lucene.index.TestFilterLeafReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestFilterLeafReader.java) | org.gnit.lucenekmp.index.TestFilterLeafReader | Depth 2 | [] | 39 | 0 | 0% |
| [org.apache.lucene.index.TestFlushByRamOrCountsPolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestFlushByRamOrCountsPolicy.java) | org.gnit.lucenekmp.index.TestFlushByRamOrCountsPolicy | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.index.TestIndexCommit](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexCommit.java) | org.gnit.lucenekmp.index.TestIndexCommit | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.index.TestIndexInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexInput.java) | org.gnit.lucenekmp.index.TestIndexInput | Depth 1 | [x] | 148 | 26 | 0% |
| [org.apache.lucene.index.TestIndexWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriter.java) | org.gnit.lucenekmp.index.TestIndexWriter | Depth 1 | [] | 16 | 0 | 0% |
| [org.apache.lucene.index.TestIndexWriterConfig](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterConfig.java) | org.gnit.lucenekmp.index.TestIndexWriterConfig | Depth 1 | [x] | 7 | 16 | 0% |
| [org.apache.lucene.index.TestIndexableField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexableField.java) | org.gnit.lucenekmp.index.TestIndexableField | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.index.TestLockableConcurrentApproximatePriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestLockableConcurrentApproximatePriorityQueue.java) | org.gnit.lucenekmp.index.TestLockableConcurrentApproximatePriorityQueue | Depth 1 | [x] | 6 | 3 | 0% |
| [org.apache.lucene.index.TestMultiFields](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestMultiFields.java) | org.gnit.lucenekmp.index.TestMultiFields | Depth 1 | [] | 16 | 0 | 0% |
| [org.apache.lucene.index.TestOneMergeWrappingMergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestOneMergeWrappingMergePolicy.java) | org.gnit.lucenekmp.index.TestOneMergeWrappingMergePolicy | Depth 1 | [x] | 15 | 2 | 0% |
| [org.apache.lucene.index.TestPendingDeletes](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestPendingDeletes.java) | org.gnit.lucenekmp.index.TestPendingDeletes | Depth 1 | [x] | 143 | 5 | 50% |
| [org.apache.lucene.index.TestPendingSoftDeletes](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestPendingSoftDeletes.java) | org.gnit.lucenekmp.index.TestPendingSoftDeletes | Depth 1 | [] | 13 | 0 | 0% |
| [org.apache.lucene.index.TestTermVectorsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestTermVectorsReader.java) | org.gnit.lucenekmp.index.TestTermVectorsReader | Depth 1 | [x] | 11 | 1 | 0% |
| [org.apache.lucene.index.TieredMergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/TieredMergePolicy.java) | org.gnit.lucenekmp.index.TieredMergePolicy | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.index.TrackingTmpOutputDirectoryWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/TrackingTmpOutputDirectoryWrapper.java) | org.gnit.lucenekmp.index.TrackingTmpOutputDirectoryWrapper | Depth 4 | [x] | 16 | 14 | 93% |
| [org.apache.lucene.index.VectorEncoding](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/VectorEncoding.java) | org.gnit.lucenekmp.index.VectorEncoding | Depth 1 | [x] | 1 | 1 | 60% |
| [org.apache.lucene.index.VectorSimilarityFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/VectorSimilarityFunction.java) | org.gnit.lucenekmp.index.VectorSimilarityFunction | Depth 2 | [x] | 3 | 3 | 86% |
| [org.apache.lucene.internal.hppc.BufferAllocationException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/BufferAllocationException.java) | org.gnit.lucenekmp.internal.hppc.BufferAllocationException | Depth 3 | [x] | 1 | 1 | 83% |
| [org.apache.lucene.internal.hppc.HashContainers](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/HashContainers.java) | org.gnit.lucenekmp.internal.hppc.HashContainers | Depth 3 | [x] | 6 | 6 | 69% |
| [org.apache.lucene.internal.hppc.IntCursor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/IntCursor.java) | org.gnit.lucenekmp.internal.hppc.IntCursor | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.internal.hppc.IntIntHashMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/IntIntHashMap.java) | org.gnit.lucenekmp.internal.hppc.IntIntHashMap | Depth 3 | [x] | 2 | 2 | 85% |
| [org.apache.lucene.internal.hppc.IntObjectHashMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/IntObjectHashMap.java) | org.gnit.lucenekmp.internal.hppc.IntObjectHashMap | Depth 4 | [x] | 1 | 2 | 0% |
| [org.apache.lucene.internal.hppc.LongCursor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/LongCursor.java) | org.gnit.lucenekmp.internal.hppc.LongCursor | Depth 3 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.internal.hppc.LongIntHashMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/LongIntHashMap.java) | org.gnit.lucenekmp.internal.hppc.LongIntHashMap | Depth 5 | [x] | 2 | 2 | 85% |
| [org.apache.lucene.internal.hppc.LongObjectHashMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/LongObjectHashMap.java) | org.gnit.lucenekmp.internal.hppc.LongObjectHashMap | Depth 3 | [x] | 2 | 2 | 85% |
| [org.apache.lucene.internal.hppc.ObjectCursor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/ObjectCursor.java) | org.gnit.lucenekmp.internal.hppc.ObjectCursor | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.internal.tests.ConcurrentMergeSchedulerAccess](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/tests/ConcurrentMergeSchedulerAccess.java) | org.gnit.lucenekmp.internal.tests.ConcurrentMergeSchedulerAccess | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.internal.tests.TestSecrets](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/tests/TestSecrets.java) | org.gnit.lucenekmp.internal.tests.TestSecrets | Depth 1 | [x] | 2 | 4 | 77% |
| [org.apache.lucene.internal.vectorization.BaseVectorizationTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/internal/vectorization/BaseVectorizationTestCase.java) | org.gnit.lucenekmp.internal.vectorization.BaseVectorizationTestCase | Depth 1 | [] | 142 | 0 | 0% |
| [org.apache.lucene.internal.vectorization.DefaultVectorUtilSupport](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/vectorization/DefaultVectorUtilSupport.java) | org.gnit.lucenekmp.internal.vectorization.DefaultVectorUtilSupport | Depth 3 | [x] | 11 | 11 | 81% |
| [org.apache.lucene.internal.vectorization.PostingDecodingUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/vectorization/PostingDecodingUtil.java) | org.gnit.lucenekmp.internal.vectorization.PostingDecodingUtil | Depth 1 | [x] | 1 | 1 | 62% |
| [org.apache.lucene.internal.vectorization.TestVectorScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/internal/vectorization/TestVectorScorer.java) | org.gnit.lucenekmp.internal.vectorization.TestVectorScorer | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.internal.vectorization.VectorizationProvider](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/vectorization/VectorizationProvider.java) | org.gnit.lucenekmp.internal.vectorization.VectorizationProvider | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.search.AbstractKnnCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/AbstractKnnCollector.java) | org.gnit.lucenekmp.search.AbstractKnnCollector | Depth 1 | [x] | 8 | 8 | 88% |
| [org.apache.lucene.search.AbstractKnnVectorQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/AbstractKnnVectorQuery.java) | org.gnit.lucenekmp.search.AbstractKnnVectorQuery | Depth 2 | [x] | 8 | 3 | 0% |
| [org.apache.lucene.search.AbstractMultiTermQueryConstantScoreWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/AbstractMultiTermQueryConstantScoreWrapper.java) | org.gnit.lucenekmp.search.AbstractMultiTermQueryConstantScoreWrapper | Depth 4 | [x] | 12 | 0 | 0% |
| [org.apache.lucene.search.BaseKnnVectorQueryTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/BaseKnnVectorQueryTestCase.java) | org.gnit.lucenekmp.search.BaseKnnVectorQueryTestCase | Depth 2 | [] | 0 | 0 | 0% |
| [org.apache.lucene.search.BlockMaxConjunctionBulkScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/BlockMaxConjunctionBulkScorer.java) | org.gnit.lucenekmp.search.BlockMaxConjunctionBulkScorer | Depth 5 | [x] | 1 | 1 | 0% |
| [org.apache.lucene.search.BooleanClause](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/BooleanClause.java) | org.gnit.lucenekmp.search.BooleanClause | Depth 2 | [x] | 1 | 1 | 76% |
| [org.apache.lucene.search.BooleanQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/BooleanQuery.java) | org.gnit.lucenekmp.search.BooleanQuery | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.search.BooleanScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/BooleanScorer.java) | org.gnit.lucenekmp.search.BooleanScorer | Depth 5 | [x] | 0 | 16 | 0% |
| [org.apache.lucene.search.BooleanScorerSupplier](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/BooleanScorerSupplier.java) | org.gnit.lucenekmp.search.BooleanScorerSupplier | Depth 4 | [x] | 1 | 5 | 0% |
| [org.apache.lucene.search.BooleanWeight](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/BooleanWeight.java) | org.gnit.lucenekmp.search.BooleanWeight | Depth 3 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.search.BoostAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/BoostAttribute.java) | org.gnit.lucenekmp.search.BoostAttribute | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.search.ByteVectorSimilarityValuesSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ByteVectorSimilarityValuesSource.java) | org.gnit.lucenekmp.search.ByteVectorSimilarityValuesSource | Depth 2 | [x] | 20 | 9 | 92% |
| [org.apache.lucene.search.CollectionStatistics](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/CollectionStatistics.java) | org.gnit.lucenekmp.search.CollectionStatistics | Depth 2 | [x] | 4 | 0 | 24% |
| [org.apache.lucene.search.Collector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Collector.java) | org.gnit.lucenekmp.search.Collector | Depth 1 | [x] | 2 | 2 | 81% |
| [org.apache.lucene.search.ConjunctionBulkScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ConjunctionBulkScorer.java) | org.gnit.lucenekmp.search.ConjunctionBulkScorer | Depth 5 | [x] | 1 | 1 | 0% |
| [org.apache.lucene.search.ConjunctionDISI](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ConjunctionDISI.java) | org.gnit.lucenekmp.search.ConjunctionDISI | Depth 3 | [x] | 9 | 1 | 0% |
| [org.apache.lucene.search.ConjunctionScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ConjunctionScorer.java) | org.gnit.lucenekmp.search.ConjunctionScorer | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.search.ConstantScoreQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ConstantScoreQuery.java) | org.gnit.lucenekmp.search.ConstantScoreQuery | Depth 2 | [x] | 7 | 3 | 0% |
| [org.apache.lucene.search.ConstantScoreScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ConstantScoreScorer.java) | org.gnit.lucenekmp.search.ConstantScoreScorer | Depth 3 | [x] | 3 | 5 | 0% |
| [org.apache.lucene.search.DenseConjunctionBulkScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DenseConjunctionBulkScorer.java) | org.gnit.lucenekmp.search.DenseConjunctionBulkScorer | Depth 5 | [x] | 1 | 1 | 0% |
| [org.apache.lucene.search.DisiWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisiWrapper.java) | org.gnit.lucenekmp.search.DisiWrapper | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.search.DisjunctionDISIApproximation](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisjunctionDISIApproximation.java) | org.gnit.lucenekmp.search.DisjunctionDISIApproximation | Depth 2 | [x] | 10 | 1 | 0% |
| [org.apache.lucene.search.DisjunctionMatchesIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisjunctionMatchesIterator.java) | org.gnit.lucenekmp.search.DisjunctionMatchesIterator | Depth 2 | [x] | 0 | 5 | 0% |
| [org.apache.lucene.search.DisjunctionMaxBulkScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisjunctionMaxBulkScorer.java) | org.gnit.lucenekmp.search.DisjunctionMaxBulkScorer | Depth 4 | [x] | 10 | 1 | 0% |
| [org.apache.lucene.search.DisjunctionMaxQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisjunctionMaxQuery.java) | org.gnit.lucenekmp.search.DisjunctionMaxQuery | Depth 2 | [x] | 7 | 3 | 0% |
| [org.apache.lucene.search.DisjunctionMaxScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisjunctionMaxScorer.java) | org.gnit.lucenekmp.search.DisjunctionMaxScorer | Depth 3 | [x] | 6 | 6 | 83% |
| [org.apache.lucene.search.DisjunctionScoreBlockBoundaryPropagator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisjunctionScoreBlockBoundaryPropagator.java) | org.gnit.lucenekmp.search.DisjunctionScoreBlockBoundaryPropagator | Depth 1 | [x] | 1 | 1 | 0% |
| [org.apache.lucene.search.DisjunctionScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisjunctionScorer.java) | org.gnit.lucenekmp.search.DisjunctionScorer | Depth 2 | [x] | 3 | 15 | 0% |
| [org.apache.lucene.search.DisjunctionSumScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisjunctionSumScorer.java) | org.gnit.lucenekmp.search.DisjunctionSumScorer | Depth 4 | [x] | 6 | 6 | 88% |
| [org.apache.lucene.search.DocIdSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DocIdSet.java) | org.gnit.lucenekmp.search.DocIdSet | Depth 2 | [x] | 1 | 1 | 72% |
| [org.apache.lucene.search.DocIdStream](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DocIdStream.java) | org.gnit.lucenekmp.search.DocIdStream | Depth 1 | [x] | 1 | 2 | 61% |
| [org.apache.lucene.search.DocValuesRewriteMethod](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DocValuesRewriteMethod.java) | org.gnit.lucenekmp.search.DocValuesRewriteMethod | Depth 3 | [x] | 5 | 1 | 0% |
| [org.apache.lucene.search.DoubleValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DoubleValues.java) | org.gnit.lucenekmp.search.DoubleValues | Depth 3 | [x] | 3 | 2 | 58% |
| [org.apache.lucene.search.DoubleValuesSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DoubleValuesSource.java) | org.gnit.lucenekmp.search.DoubleValuesSource | Depth 2 | [x] | 19 | 2 | 0% |
| [org.apache.lucene.search.ExactPhraseMatcher](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ExactPhraseMatcher.java) | org.gnit.lucenekmp.search.ExactPhraseMatcher | Depth 4 | [x] | 1 | 0 | 0% |
| [org.apache.lucene.search.FieldComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FieldComparator.java) | org.gnit.lucenekmp.search.FieldComparator | Depth 2 | [x] | 6 | 10 | 29% |
| [org.apache.lucene.search.FieldDoc](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FieldDoc.java) | org.gnit.lucenekmp.search.FieldDoc | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.search.FieldValueHitQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FieldValueHitQueue.java) | org.gnit.lucenekmp.search.FieldValueHitQueue | Depth 4 | [x] | 13 | 20 | 62% |
| [org.apache.lucene.search.FilterCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FilterCollector.java) | org.gnit.lucenekmp.search.FilterCollector | Depth 4 | [x] | 2 | 3 | 56% |
| [org.apache.lucene.search.FilterDocIdSetIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FilterDocIdSetIterator.java) | org.gnit.lucenekmp.search.FilterDocIdSetIterator | Depth 3 | [x] | 7 | 5 | 90% |
| [org.apache.lucene.search.FilterLeafCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FilterLeafCollector.java) | org.gnit.lucenekmp.search.FilterLeafCollector | Depth 3 | [x] | 4 | 2 | 56% |
| [org.apache.lucene.search.FilterScorable](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FilterScorable.java) | org.gnit.lucenekmp.search.FilterScorable | Depth 3 | [x] | 1 | 1 | 70% |
| [org.apache.lucene.search.FilterScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FilterScorer.java) | org.gnit.lucenekmp.search.FilterScorer | Depth 3 | [x] | 6 | 5 | 87% |
| [org.apache.lucene.search.FilterWeight](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FilterWeight.java) | org.gnit.lucenekmp.search.FilterWeight | Depth 3 | [x] | 7 | 7 | 92% |
| [org.apache.lucene.search.FilteredDocIdSetIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FilteredDocIdSetIterator.java) | org.gnit.lucenekmp.search.FilteredDocIdSetIterator | Depth 3 | [x] | 8 | 6 | 92% |
| [org.apache.lucene.search.FloatVectorSimilarityValuesSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FloatVectorSimilarityValuesSource.java) | org.gnit.lucenekmp.search.FloatVectorSimilarityValuesSource | Depth 2 | [x] | 20 | 9 | 92% |
| [org.apache.lucene.search.HitQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/HitQueue.java) | org.gnit.lucenekmp.search.HitQueue | Depth 2 | [x] | 10 | 17 | 64% |
| [org.apache.lucene.search.IndexOrDocValuesQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/IndexOrDocValuesQuery.java) | org.gnit.lucenekmp.search.IndexOrDocValuesQuery | Depth 2 | [x] | 7 | 3 | 0% |
| [org.apache.lucene.search.IndexSearcher](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/IndexSearcher.java) | org.gnit.lucenekmp.search.IndexSearcher | Depth 2 | [x] | 6 | 3 | 0% |
| [org.apache.lucene.search.IndexSortSortedNumericDocValuesRangeQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/IndexSortSortedNumericDocValuesRangeQuery.java) | org.gnit.lucenekmp.search.IndexSortSortedNumericDocValuesRangeQuery | Depth 3 | [x] | 7 | 3 | 0% |
| [org.apache.lucene.search.KnnCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/KnnCollector.java) | org.gnit.lucenekmp.search.KnnCollector | Depth 2 | [x] | 7 | 7 | 93% |
| [org.apache.lucene.search.LRUQueryCache](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/LRUQueryCache.java) | org.gnit.lucenekmp.search.LRUQueryCache | Depth 3 | [x] | 4 | 3 | 0% |
| [org.apache.lucene.search.LeafCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/LeafCollector.java) | org.gnit.lucenekmp.search.LeafCollector | Depth 1 | [x] | 4 | 1 | 0% |
| [org.apache.lucene.search.LeafFieldComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/LeafFieldComparator.java) | org.gnit.lucenekmp.search.LeafFieldComparator | Depth 2 | [x] | 4 | 1 | 0% |
| [org.apache.lucene.search.LongValuesSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/LongValuesSource.java) | org.gnit.lucenekmp.search.LongValuesSource | Depth 2 | [x] | 2 | 5 | 0% |
| [org.apache.lucene.search.MaxNonCompetitiveBoostAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/MaxNonCompetitiveBoostAttribute.java) | org.gnit.lucenekmp.search.MaxNonCompetitiveBoostAttribute | Depth 5 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.search.MaxScoreAccumulator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/MaxScoreAccumulator.java) | org.gnit.lucenekmp.search.MaxScoreAccumulator | Depth 1 | [x] | 4 | 3 | 93% |
| [org.apache.lucene.search.MaxScoreBulkScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/MaxScoreBulkScorer.java) | org.gnit.lucenekmp.search.MaxScoreBulkScorer | Depth 5 | [x] | 1 | 1 | 65% |
| [org.apache.lucene.search.MultiCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/MultiCollector.java) | org.gnit.lucenekmp.search.MultiCollector | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.MultiLeafFieldComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/MultiLeafFieldComparator.java) | org.gnit.lucenekmp.search.MultiLeafFieldComparator | Depth 4 | [x] | 4 | 4 | 93% |
| [org.apache.lucene.search.MultiPhraseQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/MultiPhraseQuery.java) | org.gnit.lucenekmp.search.MultiPhraseQuery | Depth 2 | [x] | 9 | 10 | 73% |
| [org.apache.lucene.search.MultiTermQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/MultiTermQuery.java) | org.gnit.lucenekmp.search.MultiTermQuery | Depth 2 | [x] | 2 | 8 | 18% |
| [org.apache.lucene.search.MultiTermQueryConstantScoreBlendedWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/MultiTermQueryConstantScoreBlendedWrapper.java) | org.gnit.lucenekmp.search.MultiTermQueryConstantScoreBlendedWrapper | Depth 4 | [x] | 12 | 15 | 0% |
| [org.apache.lucene.search.MultiTermQueryConstantScoreWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/MultiTermQueryConstantScoreWrapper.java) | org.gnit.lucenekmp.search.MultiTermQueryConstantScoreWrapper | Depth 4 | [x] | 12 | 12 | 88% |
| [org.apache.lucene.search.Multiset](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Multiset.java) | org.gnit.lucenekmp.search.Multiset | Depth 3 | [x] | 1 | 2 | 32% |
| [org.apache.lucene.search.NamedMatches](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/NamedMatches.java) | org.gnit.lucenekmp.search.NamedMatches | Depth 2 | [] | 5 | 0 | 0% |
| [org.apache.lucene.search.PhrasePositions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/PhrasePositions.java) | org.gnit.lucenekmp.search.PhrasePositions | Depth 4 | [x] | 2 | 2 | 19% |
| [org.apache.lucene.search.PhraseQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/PhraseQuery.java) | org.gnit.lucenekmp.search.PhraseQuery | Depth 2 | [x] | 9 | 10 | 73% |
| [org.apache.lucene.search.PhraseQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/PhraseQueue.java) | org.gnit.lucenekmp.search.PhraseQueue | Depth 4 | [x] | 10 | 15 | 70% |
| [org.apache.lucene.search.PhraseWeight](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/PhraseWeight.java) | org.gnit.lucenekmp.search.PhraseWeight | Depth 4 | [x] | 2 | 4 | 0% |
| [org.apache.lucene.search.PointInSetQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/PointInSetQuery.java) | org.gnit.lucenekmp.search.PointInSetQuery | Depth 3 | [x] | 7 | 2 | 0% |
| [org.apache.lucene.search.PointRangeQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/PointRangeQuery.java) | org.gnit.lucenekmp.search.PointRangeQuery | Depth 3 | [x] | 14 | 4 | 0% |
| [org.apache.lucene.search.Pruning](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Pruning.java) | org.gnit.lucenekmp.search.Pruning | Depth 2 | [x] | 1 | 1 | 75% |
| [org.apache.lucene.search.QueryVisitor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/QueryVisitor.java) | org.gnit.lucenekmp.search.QueryVisitor | Depth 2 | [x] | 6 | 5 | 96% |
| [org.apache.lucene.search.ReferenceManager](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ReferenceManager.java) | org.gnit.lucenekmp.search.ReferenceManager | Depth 2 | [] | 17 | 0 | 0% |
| [org.apache.lucene.search.RegexpQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/RegexpQuery.java) | org.gnit.lucenekmp.search.RegexpQuery | Depth 1 | [x] | 10 | 1 | 0% |
| [org.apache.lucene.search.ReqOptSumScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ReqOptSumScorer.java) | org.gnit.lucenekmp.search.ReqOptSumScorer | Depth 2 | [x] | 10 | 1 | 0% |
| [org.apache.lucene.search.Scorable](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Scorable.java) | org.gnit.lucenekmp.search.Scorable | Depth 1 | [x] | 1 | 0 | 0% |
| [org.apache.lucene.search.Score](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Score.java) | org.gnit.lucenekmp.search.Score | Depth 5 | [x] | 1 | 1 | 73% |
| [org.apache.lucene.search.ScoreCachingWrappingScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ScoreCachingWrappingScorer.java) | org.gnit.lucenekmp.search.ScoreCachingWrappingScorer | Depth 4 | [x] | 4 | 3 | 65% |
| [org.apache.lucene.search.ScoreDoc](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ScoreDoc.java) | org.gnit.lucenekmp.search.ScoreDoc | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.search.ScoreMode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ScoreMode.java) | org.gnit.lucenekmp.search.ScoreMode | Depth 1 | [x] | 2 | 2 | 85% |
| [org.apache.lucene.search.Scorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Scorer.java) | org.gnit.lucenekmp.search.Scorer | Depth 1 | [x] | 5 | 5 | 92% |
| [org.apache.lucene.search.ScorerUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ScorerUtil.java) | org.gnit.lucenekmp.search.ScorerUtil | Depth 3 | [x] | 10 | 15 | 70% |
| [org.apache.lucene.search.ScoringRewrite](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ScoringRewrite.java) | org.gnit.lucenekmp.search.ScoringRewrite | Depth 3 | [x] | 7 | 1 | 0% |
| [org.apache.lucene.search.SearcherFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/SearcherFactory.java) | org.gnit.lucenekmp.search.SearcherFactory | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.search.SearcherManager](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/SearcherManager.java) | org.gnit.lucenekmp.search.SearcherManager | Depth 1 | [] | 18 | 0 | 0% |
| [org.apache.lucene.search.SimpleCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/SimpleCollector.java) | org.gnit.lucenekmp.search.SimpleCollector | Depth 1 | [x] | 7 | 4 | 68% |
| [org.apache.lucene.search.SimpleScorable](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/SimpleScorable.java) | org.gnit.lucenekmp.search.SimpleScorable | Depth 4 | [x] | 2 | 2 | 89% |
| [org.apache.lucene.search.SloppyPhraseMatcher](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/SloppyPhraseMatcher.java) | org.gnit.lucenekmp.search.SloppyPhraseMatcher | Depth 4 | [x] | 1 | 1 | 0% |
| [org.apache.lucene.search.Sort](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Sort.java) | org.gnit.lucenekmp.search.Sort | Depth 1 | [x] | 2 | 0 | 0% |
| [org.apache.lucene.search.SortField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/SortField.java) | org.gnit.lucenekmp.search.SortField | Depth 2 | [x] | 0 | 1 | 0% |
| [org.apache.lucene.search.SortedNumericSelector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/SortedNumericSelector.java) | org.gnit.lucenekmp.search.SortedNumericSelector | Depth 3 | [x] | 9 | 7 | 92% |
| [org.apache.lucene.search.SortedNumericSortField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/SortedNumericSortField.java) | org.gnit.lucenekmp.search.SortedNumericSortField | Depth 3 | [x] | 8 | 12 | 0% |
| [org.apache.lucene.search.SortedSetSortField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/SortedSetSortField.java) | org.gnit.lucenekmp.search.SortedSetSortField | Depth 4 | [x] | 7 | 7 | 46% |
| [org.apache.lucene.search.SynonymQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/SynonymQuery.java) | org.gnit.lucenekmp.search.SynonymQuery | Depth 2 | [x] | 1 | 0 | 0% |
| [org.apache.lucene.search.TaskExecutor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TaskExecutor.java) | org.gnit.lucenekmp.search.TaskExecutor | Depth 2 | [x] | 2 | 1 | 0% |
| [org.apache.lucene.search.TermCollectingRewrite](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TermCollectingRewrite.java) | org.gnit.lucenekmp.search.TermCollectingRewrite | Depth 3 | [x] | 2 | 2 | 46% |
| [org.apache.lucene.search.TermInSetQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TermInSetQuery.java) | org.gnit.lucenekmp.search.TermInSetQuery | Depth 3 | [x] | 24 | 0 | 0% |
| [org.apache.lucene.search.TermQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TermQuery.java) | org.gnit.lucenekmp.search.TermQuery | Depth 2 | [x] | 9 | 8 | 0% |
| [org.apache.lucene.search.TermScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TermScorer.java) | org.gnit.lucenekmp.search.TermScorer | Depth 3 | [x] | 5 | 5 | 86% |
| [org.apache.lucene.search.TermStatistics](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TermStatistics.java) | org.gnit.lucenekmp.search.TermStatistics | Depth 2 | [x] | 2 | 0 | 33% |
| [org.apache.lucene.search.TestBooleanQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestBooleanQuery.java) | org.gnit.lucenekmp.search.TestBooleanQuery | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.TestCollectorManager](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestCollectorManager.java) | org.gnit.lucenekmp.search.TestCollectorManager | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.TestConjunctions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestConjunctions.java) | org.gnit.lucenekmp.search.TestConjunctions | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.TestDisiPriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestDisiPriorityQueue.java) | org.gnit.lucenekmp.search.TestDisiPriorityQueue | Depth 1 | [x] | 7 | 1 | 0% |
| [org.apache.lucene.search.TestDisjunctionScoreBlockBoundaryPropagator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestDisjunctionScoreBlockBoundaryPropagator.java) | org.gnit.lucenekmp.search.TestDisjunctionScoreBlockBoundaryPropagator | Depth 1 | [x] | 5 | 5 | 92% |
| [org.apache.lucene.search.TestIndexSearcher](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestIndexSearcher.java) | org.gnit.lucenekmp.search.TestIndexSearcher | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.search.TestKnnByteVectorQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestKnnByteVectorQuery.java) | org.gnit.lucenekmp.search.TestKnnByteVectorQuery | Depth 1 | [] | 13 | 0 | 0% |
| [org.apache.lucene.search.TestKnnFloatVectorQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestKnnFloatVectorQuery.java) | org.gnit.lucenekmp.search.TestKnnFloatVectorQuery | Depth 1 | [] | 13 | 0 | 0% |
| [org.apache.lucene.search.TestMatchesIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMatchesIterator.java) | org.gnit.lucenekmp.search.TestMatchesIterator | Depth 1 | [] | 4 | 0 | 0% |
| [org.apache.lucene.search.TestTermScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestTermScorer.java) | org.gnit.lucenekmp.search.TestTermScorer | Depth 1 | [] | 7 | 0 | 0% |
| [org.apache.lucene.search.TestTimeLimitingBulkScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestTimeLimitingBulkScorer.java) | org.gnit.lucenekmp.search.TestTimeLimitingBulkScorer | Depth 1 | [x] | 1 | 1 | 0% |
| [org.apache.lucene.search.TestTopDocsCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestTopDocsCollector.java) | org.gnit.lucenekmp.search.TestTopDocsCollector | Depth 1 | [] | 8 | 0 | 0% |
| [org.apache.lucene.search.TestVectorScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestVectorScorer.java) | org.gnit.lucenekmp.search.TestVectorScorer | Depth 1 | [x] | 0 | 5 | 0% |
| [org.apache.lucene.search.TimeLimitingKnnCollectorManager](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TimeLimitingKnnCollectorManager.java) | org.gnit.lucenekmp.search.TimeLimitingKnnCollectorManager | Depth 2 | [x] | 1 | 7 | 0% |
| [org.apache.lucene.search.TopDocs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TopDocs.java) | org.gnit.lucenekmp.search.TopDocs | Depth 2 | [x] | 10 | 0 | 0% |
| [org.apache.lucene.search.TopDocsCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TopDocsCollector.java) | org.gnit.lucenekmp.search.TopDocsCollector | Depth 1 | [x] | 8 | 0 | 0% |
| [org.apache.lucene.search.TopFieldCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TopFieldCollector.java) | org.gnit.lucenekmp.search.TopFieldCollector | Depth 3 | [x] | 16 | 6 | 0% |
| [org.apache.lucene.search.TopFieldDocs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TopFieldDocs.java) | org.gnit.lucenekmp.search.TopFieldDocs | Depth 1 | [x] | 9 | 3 | 0% |
| [org.apache.lucene.search.TopKnnCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TopKnnCollector.java) | org.gnit.lucenekmp.search.TopKnnCollector | Depth 1 | [x] | 8 | 8 | 83% |
| [org.apache.lucene.search.TopScoreDocCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TopScoreDocCollector.java) | org.gnit.lucenekmp.search.TopScoreDocCollector | Depth 2 | [x] | 7 | 4 | 71% |
| [org.apache.lucene.search.TopTermsRewrite](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TopTermsRewrite.java) | org.gnit.lucenekmp.search.TopTermsRewrite | Depth 4 | [x] | 3 | 3 | 54% |
| [org.apache.lucene.search.TotalHitCountCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TotalHitCountCollector.java) | org.gnit.lucenekmp.search.TotalHitCountCollector | Depth 4 | [x] | 4 | 2 | 61% |
| [org.apache.lucene.search.TotalHitCountCollectorManager](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TotalHitCountCollectorManager.java) | org.gnit.lucenekmp.search.TotalHitCountCollectorManager | Depth 3 | [x] | 3 | 3 | 0% |
| [org.apache.lucene.search.TotalHits](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TotalHits.java) | org.gnit.lucenekmp.search.TotalHits | Depth 1 | [x] | 0 | 1 | 0% |
| [org.apache.lucene.search.TwoPhaseIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TwoPhaseIterator.java) | org.gnit.lucenekmp.search.TwoPhaseIterator | Depth 2 | [x] | 8 | 6 | 84% |
| [org.apache.lucene.search.UsageTrackingQueryCachingPolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/UsageTrackingQueryCachingPolicy.java) | org.gnit.lucenekmp.search.UsageTrackingQueryCachingPolicy | Depth 2 | [x] | 7 | 3 | 93% |
| [org.apache.lucene.search.VectorScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/VectorScorer.java) | org.gnit.lucenekmp.search.VectorScorer | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.search.VectorSimilarityValuesSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/VectorSimilarityValuesSource.java) | org.gnit.lucenekmp.search.VectorSimilarityValuesSource | Depth 4 | [x] | 3 | 2 | 90% |
| [org.apache.lucene.search.Weight](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Weight.java) | org.gnit.lucenekmp.search.Weight | Depth 2 | [x] | 5 | 3 | 16% |
| [org.apache.lucene.search.comparators.DocComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/comparators/DocComparator.java) | org.gnit.lucenekmp.search.comparators.DocComparator | Depth 3 | [x] | 5 | 5 | 0% |
| [org.apache.lucene.search.comparators.DoubleComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/comparators/DoubleComparator.java) | org.gnit.lucenekmp.search.comparators.DoubleComparator | Depth 3 | [x] | 12 | 12 | 89% |
| [org.apache.lucene.search.comparators.FloatComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/comparators/FloatComparator.java) | org.gnit.lucenekmp.search.comparators.FloatComparator | Depth 3 | [x] | 12 | 12 | 89% |
| [org.apache.lucene.search.comparators.IntComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/comparators/IntComparator.java) | org.gnit.lucenekmp.search.comparators.IntComparator | Depth 3 | [x] | 12 | 12 | 89% |
| [org.apache.lucene.search.comparators.LongComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/comparators/LongComparator.java) | org.gnit.lucenekmp.search.comparators.LongComparator | Depth 3 | [x] | 12 | 12 | 89% |
| [org.apache.lucene.search.comparators.MinDocIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/comparators/MinDocIterator.java) | org.gnit.lucenekmp.search.comparators.MinDocIterator | Depth 4 | [x] | 7 | 5 | 70% |
| [org.apache.lucene.search.comparators.NumericComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/comparators/NumericComparator.java) | org.gnit.lucenekmp.search.comparators.NumericComparator | Depth 4 | [x] | 11 | 4 | 0% |
| [org.apache.lucene.search.comparators.TermOrdValComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/comparators/TermOrdValComparator.java) | org.gnit.lucenekmp.search.comparators.TermOrdValComparator | Depth 3 | [x] | 9 | 6 | 0% |
| [org.apache.lucene.search.knn.MultiLeafKnnCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/knn/MultiLeafKnnCollector.java) | org.gnit.lucenekmp.search.knn.MultiLeafKnnCollector | Depth 1 | [x] | 7 | 3 | 0% |
| [org.apache.lucene.search.similarities.AfterEffect](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/AfterEffect.java) | org.gnit.lucenekmp.search.similarities.AfterEffect | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.AfterEffectB](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/AfterEffectB.java) | org.gnit.lucenekmp.search.similarities.AfterEffectB | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.AfterEffectL](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/AfterEffectL.java) | org.gnit.lucenekmp.search.similarities.AfterEffectL | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.Axiomatic](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/Axiomatic.java) | org.gnit.lucenekmp.search.similarities.Axiomatic | Depth 4 | [x] | 17 | 16 | 88% |
| [org.apache.lucene.search.similarities.AxiomaticF1EXP](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/AxiomaticF1EXP.java) | org.gnit.lucenekmp.search.similarities.AxiomaticF1EXP | Depth 3 | [x] | 17 | 16 | 88% |
| [org.apache.lucene.search.similarities.AxiomaticF1LOG](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/AxiomaticF1LOG.java) | org.gnit.lucenekmp.search.similarities.AxiomaticF1LOG | Depth 3 | [x] | 17 | 16 | 88% |
| [org.apache.lucene.search.similarities.AxiomaticF2EXP](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/AxiomaticF2EXP.java) | org.gnit.lucenekmp.search.similarities.AxiomaticF2EXP | Depth 3 | [x] | 17 | 16 | 88% |
| [org.apache.lucene.search.similarities.AxiomaticF2LOG](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/AxiomaticF2LOG.java) | org.gnit.lucenekmp.search.similarities.AxiomaticF2LOG | Depth 3 | [x] | 17 | 16 | 88% |
| [org.apache.lucene.search.similarities.BasicModel](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/BasicModel.java) | org.gnit.lucenekmp.search.similarities.BasicModel | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.BasicModelG](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/BasicModelG.java) | org.gnit.lucenekmp.search.similarities.BasicModelG | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.BasicModelIF](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/BasicModelIF.java) | org.gnit.lucenekmp.search.similarities.BasicModelIF | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.BasicModelIn](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/BasicModelIn.java) | org.gnit.lucenekmp.search.similarities.BasicModelIn | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.BasicModelIne](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/BasicModelIne.java) | org.gnit.lucenekmp.search.similarities.BasicModelIne | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.BasicStats](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/BasicStats.java) | org.gnit.lucenekmp.search.similarities.BasicStats | Depth 4 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.search.similarities.BooleanSimilarity](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/BooleanSimilarity.java) | org.gnit.lucenekmp.search.similarities.BooleanSimilarity | Depth 4 | [x] | 2 | 2 | 76% |
| [org.apache.lucene.search.similarities.DFISimilarity](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/DFISimilarity.java) | org.gnit.lucenekmp.search.similarities.DFISimilarity | Depth 3 | [] | 8 | 0 | 0% |
| [org.apache.lucene.search.similarities.DFRSimilarity](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/DFRSimilarity.java) | org.gnit.lucenekmp.search.similarities.DFRSimilarity | Depth 3 | [] | 8 | 0 | 0% |
| [org.apache.lucene.search.similarities.Distribution](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/Distribution.java) | org.gnit.lucenekmp.search.similarities.Distribution | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.DistributionLL](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/DistributionLL.java) | org.gnit.lucenekmp.search.similarities.DistributionLL | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.DistributionSPL](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/DistributionSPL.java) | org.gnit.lucenekmp.search.similarities.DistributionSPL | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.IBSimilarity](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/IBSimilarity.java) | org.gnit.lucenekmp.search.similarities.IBSimilarity | Depth 3 | [] | 8 | 0 | 0% |
| [org.apache.lucene.search.similarities.Independence](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/Independence.java) | org.gnit.lucenekmp.search.similarities.Independence | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.search.similarities.IndependenceChiSquared](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/IndependenceChiSquared.java) | org.gnit.lucenekmp.search.similarities.IndependenceChiSquared | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.search.similarities.IndependenceSaturated](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/IndependenceSaturated.java) | org.gnit.lucenekmp.search.similarities.IndependenceSaturated | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.search.similarities.IndependenceStandardized](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/IndependenceStandardized.java) | org.gnit.lucenekmp.search.similarities.IndependenceStandardized | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.search.similarities.LMDirichletSimilarity](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/LMDirichletSimilarity.java) | org.gnit.lucenekmp.search.similarities.LMDirichletSimilarity | Depth 3 | [x] | 8 | 7 | 90% |
| [org.apache.lucene.search.similarities.LMJelinekMercerSimilarity](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/LMJelinekMercerSimilarity.java) | org.gnit.lucenekmp.search.similarities.LMJelinekMercerSimilarity | Depth 3 | [] | 8 | 0 | 0% |
| [org.apache.lucene.search.similarities.LMSimilarity](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/LMSimilarity.java) | org.gnit.lucenekmp.search.similarities.LMSimilarity | Depth 5 | [x] | 1 | 0 | 0% |
| [org.apache.lucene.search.similarities.Lambda](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/Lambda.java) | org.gnit.lucenekmp.search.similarities.Lambda | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.LambdaDF](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/LambdaDF.java) | org.gnit.lucenekmp.search.similarities.LambdaDF | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.LambdaTTF](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/LambdaTTF.java) | org.gnit.lucenekmp.search.similarities.LambdaTTF | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.MultiSimilarity](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/MultiSimilarity.java) | org.gnit.lucenekmp.search.similarities.MultiSimilarity | Depth 5 | [x] | 2 | 2 | 0% |
| [org.apache.lucene.search.similarities.Normalization](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/Normalization.java) | org.gnit.lucenekmp.search.similarities.Normalization | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.NormalizationH1](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/NormalizationH1.java) | org.gnit.lucenekmp.search.similarities.NormalizationH1 | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.NormalizationH2](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/NormalizationH2.java) | org.gnit.lucenekmp.search.similarities.NormalizationH2 | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.NormalizationH3](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/NormalizationH3.java) | org.gnit.lucenekmp.search.similarities.NormalizationH3 | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.NormalizationZ](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/NormalizationZ.java) | org.gnit.lucenekmp.search.similarities.NormalizationZ | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.RawTFSimilarity](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/RawTFSimilarity.java) | org.gnit.lucenekmp.search.similarities.RawTFSimilarity | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.similarities.SimilarityBase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/SimilarityBase.java) | org.gnit.lucenekmp.search.similarities.SimilarityBase | Depth 5 | [x] | 3 | 1 | 0% |
| [org.apache.lucene.search.similarities.TFIDFSimilarity](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/similarities/TFIDFSimilarity.java) | org.gnit.lucenekmp.search.similarities.TFIDFSimilarity | Depth 3 | [x] | 3 | 3 | 83% |
| [org.apache.lucene.store.AlreadyClosedException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/AlreadyClosedException.java) | org.gnit.lucenekmp.store.AlreadyClosedException | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.store.BaseDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/BaseDirectory.java) | org.gnit.lucenekmp.store.BaseDirectory | Depth 2 | [x] | 15 | 14 | 89% |
| [org.apache.lucene.store.BufferedChecksumIndexInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/BufferedChecksumIndexInput.java) | org.gnit.lucenekmp.store.BufferedChecksumIndexInput | Depth 1 | [x] | 27 | 27 | 96% |
| [org.apache.lucene.store.BufferedIndexInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/BufferedIndexInput.java) | org.gnit.lucenekmp.store.BufferedIndexInput | Depth 4 | [x] | 0 | 37 | 0% |
| [org.apache.lucene.store.ByteArrayDataInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/ByteArrayDataInput.java) | org.gnit.lucenekmp.store.ByteArrayDataInput | Depth 1 | [x] | 21 | 22 | 95% |
| [org.apache.lucene.store.ByteArrayDataOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/ByteArrayDataOutput.java) | org.gnit.lucenekmp.store.ByteArrayDataOutput | Depth 1 | [x] | 19 | 20 | 94% |
| [org.apache.lucene.store.ByteBuffersDataInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/ByteBuffersDataInput.java) | org.gnit.lucenekmp.store.ByteBuffersDataInput | Depth 1 | [x] | 37 | 4 | 90% |
| [org.apache.lucene.store.ByteBuffersDataOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/ByteBuffersDataOutput.java) | org.gnit.lucenekmp.store.ByteBuffersDataOutput | Depth 2 | [x] | 2 | 4 | 0% |
| [org.apache.lucene.store.ByteBuffersDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/ByteBuffersDirectory.java) | org.gnit.lucenekmp.store.ByteBuffersDirectory | Depth 2 | [x] | 1 | 1 | 0% |
| [org.apache.lucene.store.ByteBuffersIndexInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/ByteBuffersIndexInput.java) | org.gnit.lucenekmp.store.ByteBuffersIndexInput | Depth 1 | [x] | 32 | 32 | 97% |
| [org.apache.lucene.store.ByteBuffersIndexOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/ByteBuffersIndexOutput.java) | org.gnit.lucenekmp.store.ByteBuffersIndexOutput | Depth 1 | [x] | 22 | 21 | 97% |
| [org.apache.lucene.store.FSDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/FSDirectory.java) | org.gnit.lucenekmp.store.FSDirectory | Depth 2 | [x] | 20 | 5 | 14% |
| [org.apache.lucene.store.FileSwitchDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/FileSwitchDirectory.java) | org.gnit.lucenekmp.store.FileSwitchDirectory | Depth 2 | [] | 17 | 0 | 0% |
| [org.apache.lucene.store.FilterIndexInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/FilterIndexInput.java) | org.gnit.lucenekmp.store.FilterIndexInput | Depth 1 | [x] | 28 | 2 | 76% |
| [org.apache.lucene.store.FlushInfo](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/FlushInfo.java) | org.gnit.lucenekmp.store.FlushInfo | Depth 2 | [x] | 2 | 0 | 23% |
| [org.apache.lucene.store.IndexInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/IndexInput.java) | org.gnit.lucenekmp.store.IndexInput | Depth 2 | [x] | 6 | 6 | 94% |
| [org.apache.lucene.store.LockObtainFailedException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/LockObtainFailedException.java) | org.gnit.lucenekmp.store.LockObtainFailedException | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.store.LockReleaseFailedException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/LockReleaseFailedException.java) | org.gnit.lucenekmp.store.LockReleaseFailedException | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.store.LockValidatingDirectoryWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/LockValidatingDirectoryWrapper.java) | org.gnit.lucenekmp.store.LockValidatingDirectoryWrapper | Depth 2 | [x] | 16 | 14 | 96% |
| [org.apache.lucene.store.MMapDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/MMapDirectory.java) | org.gnit.lucenekmp.store.MMapDirectory | Depth 2 | [x] | 3 | 2 | 0% |
| [org.apache.lucene.store.MergeInfo](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/MergeInfo.java) | org.gnit.lucenekmp.store.MergeInfo | Depth 2 | [x] | 3 | 0 | 28% |
| [org.apache.lucene.store.NRTCachingDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/NRTCachingDirectory.java) | org.gnit.lucenekmp.store.NRTCachingDirectory | Depth 2 | [] | 22 | 0 | 0% |
| [org.apache.lucene.store.NativeFSLockFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/NativeFSLockFactory.java) | org.gnit.lucenekmp.store.NativeFSLockFactory | Depth 4 | [x] | 2 | 2 | 47% |
| [org.apache.lucene.store.OutputStreamIndexOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/OutputStreamIndexOutput.java) | org.gnit.lucenekmp.store.OutputStreamIndexOutput | Depth 2 | [x] | 5 | 11 | 32% |
| [org.apache.lucene.store.RandomAccessInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/RandomAccessInput.java) | org.gnit.lucenekmp.store.RandomAccessInput | Depth 1 | [x] | 6 | 2 | 0% |
| [org.apache.lucene.store.RateLimitedIndexOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/RateLimitedIndexOutput.java) | org.gnit.lucenekmp.store.RateLimitedIndexOutput | Depth 3 | [x] | 22 | 3 | 0% |
| [org.apache.lucene.store.RateLimiter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/RateLimiter.java) | org.gnit.lucenekmp.store.RateLimiter | Depth 3 | [x] | 1 | 1 | 0% |
| [org.apache.lucene.store.ReadAdvice](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/ReadAdvice.java) | org.gnit.lucenekmp.store.ReadAdvice | Depth 2 | [x] | 1 | 1 | 75% |
| [org.apache.lucene.store.SimpleFSLockFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/SimpleFSLockFactory.java) | org.gnit.lucenekmp.store.SimpleFSLockFactory | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.store.TestByteBuffersDataOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/store/TestByteBuffersDataOutput.java) | org.gnit.lucenekmp.store.TestByteBuffersDataOutput | Depth 1 | [x] | 159 | 1 | 0% |
| [org.apache.lucene.store.TestChecksumIndexInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/store/TestChecksumIndexInput.java) | org.gnit.lucenekmp.store.TestChecksumIndexInput | Depth 1 | [x] | 27 | 27 | 98% |
| [org.apache.lucene.store.TestFilterDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/store/TestFilterDirectory.java) | org.gnit.lucenekmp.store.TestFilterDirectory | Depth 1 | [x] | 16 | 14 | 96% |
| [org.apache.lucene.store.TestLockFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/store/TestLockFactory.java) | org.gnit.lucenekmp.store.TestLockFactory | Depth 1 | [x] | 1 | 2 | 0% |
| [org.apache.lucene.store.TestNIOFSDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/store/TestNIOFSDirectory.java) | org.gnit.lucenekmp.store.TestNIOFSDirectory | Depth 1 | [x] | 31 | 6 | 0% |
| [org.apache.lucene.store.TrackingDirectoryWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/TrackingDirectoryWrapper.java) | org.gnit.lucenekmp.store.TrackingDirectoryWrapper | Depth 1 | [x] | 17 | 15 | 96% |
| [org.apache.lucene.tests.analysis.BaseTokenStreamTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/BaseTokenStreamTestCase.java) | org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase | Depth 2 | [x] | 1 | 48 | 0% |
| [org.apache.lucene.tests.analysis.CannedTokenStream](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/CannedTokenStream.java) | org.gnit.lucenekmp.tests.analysis.CannedTokenStream | Depth 1 | [x] | 20 | 19 | 85% |
| [org.apache.lucene.tests.analysis.LookaheadTokenFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/LookaheadTokenFilter.java) | org.gnit.lucenekmp.tests.analysis.LookaheadTokenFilter | Depth 4 | [x] | 6 | 6 | 97% |
| [org.apache.lucene.tests.analysis.MockAnalyzer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/MockAnalyzer.java) | org.gnit.lucenekmp.tests.analysis.MockAnalyzer | Depth 1 | [x] | 12 | 12 | 90% |
| [org.apache.lucene.tests.analysis.MockCharFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/MockCharFilter.java) | org.gnit.lucenekmp.tests.analysis.MockCharFilter | Depth 2 | [x] | 5 | 8 | 36% |
| [org.apache.lucene.tests.analysis.MockFixedLengthPayloadFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/MockFixedLengthPayloadFilter.java) | org.gnit.lucenekmp.tests.analysis.MockFixedLengthPayloadFilter | Depth 2 | [x] | 21 | 19 | 84% |
| [org.apache.lucene.tests.analysis.MockGraphTokenFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/MockGraphTokenFilter.java) | org.gnit.lucenekmp.tests.analysis.MockGraphTokenFilter | Depth 2 | [x] | 27 | 3 | 13% |
| [org.apache.lucene.tests.analysis.MockLowerCaseFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/MockLowerCaseFilter.java) | org.gnit.lucenekmp.tests.analysis.MockLowerCaseFilter | Depth 2 | [x] | 21 | 19 | 84% |
| [org.apache.lucene.tests.analysis.MockTokenFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/MockTokenFilter.java) | org.gnit.lucenekmp.tests.analysis.MockTokenFilter | Depth 2 | [x] | 21 | 0 | 0% |
| [org.apache.lucene.tests.analysis.MockTokenizer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/MockTokenizer.java) | org.gnit.lucenekmp.tests.analysis.MockTokenizer | Depth 2 | [x] | 1 | 1 | 75% |
| [org.apache.lucene.tests.analysis.Token](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/Token.java) | org.gnit.lucenekmp.tests.analysis.Token | Depth 1 | [x] | 24 | 2 | 13% |
| [org.apache.lucene.tests.analysis.standard.EmojiTokenizationTestUnicode_12_1](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/standard/EmojiTokenizationTestUnicode_12_1.java) | org.gnit.lucenekmp.tests.analysis.standard.EmojiTokenizationTestUnicode_12_1 | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.analysis.standard.WordBreakTestUnicode_12_1_0](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/standard/WordBreakTestUnicode_12_1_0.java) | org.gnit.lucenekmp.tests.analysis.standard.WordBreakTestUnicode_12_1_0 | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.codecs.asserting.AssertingCodec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/asserting/AssertingCodec.java) | org.gnit.lucenekmp.tests.codecs.asserting.AssertingCodec | Depth 3 | [] | 8 | 0 | 0% |
| [org.apache.lucene.tests.codecs.asserting.AssertingDocValuesFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/asserting/AssertingDocValuesFormat.java) | org.gnit.lucenekmp.tests.codecs.asserting.AssertingDocValuesFormat | Depth 3 | [] | 16 | 0 | 0% |
| [org.apache.lucene.tests.codecs.asserting.AssertingKnnVectorsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/asserting/AssertingKnnVectorsFormat.java) | org.gnit.lucenekmp.tests.codecs.asserting.AssertingKnnVectorsFormat | Depth 4 | [] | 8 | 0 | 0% |
| [org.apache.lucene.tests.codecs.asserting.AssertingLiveDocsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/asserting/AssertingLiveDocsFormat.java) | org.gnit.lucenekmp.tests.codecs.asserting.AssertingLiveDocsFormat | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.codecs.asserting.AssertingNormsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/asserting/AssertingNormsFormat.java) | org.gnit.lucenekmp.tests.codecs.asserting.AssertingNormsFormat | Depth 4 | [] | 4 | 0 | 0% |
| [org.apache.lucene.tests.codecs.asserting.AssertingPointsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/asserting/AssertingPointsFormat.java) | org.gnit.lucenekmp.tests.codecs.asserting.AssertingPointsFormat | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.tests.codecs.asserting.AssertingPostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/asserting/AssertingPostingsFormat.java) | org.gnit.lucenekmp.tests.codecs.asserting.AssertingPostingsFormat | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.tests.codecs.asserting.AssertingStoredFieldsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/asserting/AssertingStoredFieldsFormat.java) | org.gnit.lucenekmp.tests.codecs.asserting.AssertingStoredFieldsFormat | Depth 4 | [] | 6 | 0 | 0% |
| [org.apache.lucene.tests.codecs.asserting.AssertingTermVectorsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/asserting/AssertingTermVectorsFormat.java) | org.gnit.lucenekmp.tests.codecs.asserting.AssertingTermVectorsFormat | Depth 4 | [] | 5 | 0 | 0% |
| [org.apache.lucene.tests.codecs.blockterms.LuceneFixedGap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/blockterms/LuceneFixedGap.java) | org.gnit.lucenekmp.tests.codecs.blockterms.LuceneFixedGap | Depth 2 | [] | 5 | 0 | 0% |
| [org.apache.lucene.tests.codecs.blockterms.LuceneVarGapDocFreqInterval](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/blockterms/LuceneVarGapDocFreqInterval.java) | org.gnit.lucenekmp.tests.codecs.blockterms.LuceneVarGapDocFreqInterval | Depth 3 | [] | 5 | 0 | 0% |
| [org.apache.lucene.tests.codecs.blockterms.LuceneVarGapFixedInterval](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/blockterms/LuceneVarGapFixedInterval.java) | org.gnit.lucenekmp.tests.codecs.blockterms.LuceneVarGapFixedInterval | Depth 3 | [] | 5 | 0 | 0% |
| [org.apache.lucene.tests.codecs.bloom.TestBloomFilteredLucenePostings](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/bloom/TestBloomFilteredLucenePostings.java) | org.gnit.lucenekmp.tests.codecs.bloom.TestBloomFilteredLucenePostings | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.codecs.cheapbastard.CheapBastardCodec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/cheapbastard/CheapBastardCodec.java) | org.gnit.lucenekmp.tests.codecs.cheapbastard.CheapBastardCodec | Depth 2 | [] | 14 | 0 | 0% |
| [org.apache.lucene.tests.codecs.compressing.CompressingCodec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/compressing/CompressingCodec.java) | org.gnit.lucenekmp.tests.codecs.compressing.CompressingCodec | Depth 2 | [] | 18 | 0 | 0% |
| [org.apache.lucene.tests.codecs.compressing.DeflateWithPresetCompressingCodec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/compressing/DeflateWithPresetCompressingCodec.java) | org.gnit.lucenekmp.tests.codecs.compressing.DeflateWithPresetCompressingCodec | Depth 3 | [] | 18 | 0 | 0% |
| [org.apache.lucene.tests.codecs.compressing.FastCompressingCodec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/compressing/FastCompressingCodec.java) | org.gnit.lucenekmp.tests.codecs.compressing.FastCompressingCodec | Depth 3 | [] | 18 | 0 | 0% |
| [org.apache.lucene.tests.codecs.compressing.FastDecompressionCompressingCodec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/compressing/FastDecompressionCompressingCodec.java) | org.gnit.lucenekmp.tests.codecs.compressing.FastDecompressionCompressingCodec | Depth 3 | [] | 18 | 0 | 0% |
| [org.apache.lucene.tests.codecs.compressing.HighCompressionCompressingCodec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/compressing/HighCompressionCompressingCodec.java) | org.gnit.lucenekmp.tests.codecs.compressing.HighCompressionCompressingCodec | Depth 3 | [] | 18 | 0 | 0% |
| [org.apache.lucene.tests.codecs.compressing.LZ4WithPresetCompressingCodec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/compressing/LZ4WithPresetCompressingCodec.java) | org.gnit.lucenekmp.tests.codecs.compressing.LZ4WithPresetCompressingCodec | Depth 3 | [] | 18 | 0 | 0% |
| [org.apache.lucene.tests.codecs.compressing.dummy.DummyCompressingCodec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/compressing/dummy/DummyCompressingCodec.java) | org.gnit.lucenekmp.tests.codecs.compressing.dummy.DummyCompressingCodec | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.codecs.mockrandom.MockRandomPostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/mockrandom/MockRandomPostingsFormat.java) | org.gnit.lucenekmp.tests.codecs.mockrandom.MockRandomPostingsFormat | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.index.AlcoholicMergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/AlcoholicMergePolicy.java) | org.gnit.lucenekmp.tests.index.AlcoholicMergePolicy | Depth 2 | [x] | 1 | 1 | 50% |
| [org.apache.lucene.tests.index.AssertingLeafReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/AssertingLeafReader.java) | org.gnit.lucenekmp.tests.index.AssertingLeafReader | Depth 3 | [x] | 9 | 1 | 0% |
| [org.apache.lucene.tests.index.BaseIndexFileFormatTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/BaseIndexFileFormatTestCase.java) | org.gnit.lucenekmp.tests.index.BaseIndexFileFormatTestCase | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.tests.index.BaseKnnVectorsFormatTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/BaseKnnVectorsFormatTestCase.java) | org.gnit.lucenekmp.tests.index.BaseKnnVectorsFormatTestCase | Depth 2 | [] | 14 | 0 | 0% |
| [org.apache.lucene.tests.index.DocHelper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/DocHelper.java) | org.gnit.lucenekmp.tests.index.DocHelper | Depth 1 | [] | 5 | 0 | 0% |
| [org.apache.lucene.tests.index.FieldFilterLeafReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/FieldFilterLeafReader.java) | org.gnit.lucenekmp.tests.index.FieldFilterLeafReader | Depth 3 | [x] | 3 | 3 | 93% |
| [org.apache.lucene.tests.index.ForceMergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/ForceMergePolicy.java) | org.gnit.lucenekmp.tests.index.ForceMergePolicy | Depth 2 | [] | 16 | 0 | 0% |
| [org.apache.lucene.tests.index.MergeReaderWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/MergeReaderWrapper.java) | org.gnit.lucenekmp.tests.index.MergeReaderWrapper | Depth 4 | [x] | 38 | 42 | 80% |
| [org.apache.lucene.tests.index.MergingCodecReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/MergingCodecReader.java) | org.gnit.lucenekmp.tests.index.MergingCodecReader | Depth 3 | [x] | 5 | 3 | 73% |
| [org.apache.lucene.tests.index.MismatchedCodecReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/MismatchedCodecReader.java) | org.gnit.lucenekmp.tests.index.MismatchedCodecReader | Depth 3 | [x] | 9 | 6 | 28% |
| [org.apache.lucene.tests.index.MismatchedDirectoryReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/MismatchedDirectoryReader.java) | org.gnit.lucenekmp.tests.index.MismatchedDirectoryReader | Depth 3 | [x] | 2 | 2 | 76% |
| [org.apache.lucene.tests.index.MockIndexWriterEventListener](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/MockIndexWriterEventListener.java) | org.gnit.lucenekmp.tests.index.MockIndexWriterEventListener | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.index.MockRandomMergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/MockRandomMergePolicy.java) | org.gnit.lucenekmp.tests.index.MockRandomMergePolicy | Depth 3 | [x] | 2 | 42 | 0% |
| [org.apache.lucene.tests.index.RandomCodec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/RandomCodec.java) | org.gnit.lucenekmp.tests.index.RandomCodec | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.index.RandomIndexWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/RandomIndexWriter.java) | org.gnit.lucenekmp.tests.index.RandomIndexWriter | Depth 2 | [x] | 1 | 3 | 0% |
| [org.apache.lucene.tests.index.SuppressingConcurrentMergeScheduler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/SuppressingConcurrentMergeScheduler.java) | org.gnit.lucenekmp.tests.index.SuppressingConcurrentMergeScheduler | Depth 2 | [] | 30 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.DisableFsyncFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/DisableFsyncFS.java) | org.gnit.lucenekmp.tests.mockfile.DisableFsyncFS | Depth 3 | [] | 15 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.ExtrasFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/ExtrasFS.java) | org.gnit.lucenekmp.tests.mockfile.ExtrasFS | Depth 1 | [] | 29 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.FilterAsynchronousFileChannel](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/FilterAsynchronousFileChannel.java) | org.gnit.lucenekmp.tests.mockfile.FilterAsynchronousFileChannel | Depth 4 | [] | 10 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.FilterDirectoryStream](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/FilterDirectoryStream.java) | org.gnit.lucenekmp.tests.mockfile.FilterDirectoryStream | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.FilterFileChannel](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/FilterFileChannel.java) | org.gnit.lucenekmp.tests.mockfile.FilterFileChannel | Depth 2 | [] | 15 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.FilterFileStore](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/FilterFileStore.java) | org.gnit.lucenekmp.tests.mockfile.FilterFileStore | Depth 4 | [] | 4 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.FilterFileSystem](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/FilterFileSystem.java) | org.gnit.lucenekmp.tests.mockfile.FilterFileSystem | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.FilterFileSystemProvider](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/FilterFileSystemProvider.java) | org.gnit.lucenekmp.tests.mockfile.FilterFileSystemProvider | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.FilterInputStream2](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/FilterInputStream2.java) | org.gnit.lucenekmp.tests.mockfile.FilterInputStream2 | Depth 4 | [x] | 7 | 12 | 59% |
| [org.apache.lucene.tests.mockfile.FilterOutputStream2](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/FilterOutputStream2.java) | org.gnit.lucenekmp.tests.mockfile.FilterOutputStream2 | Depth 4 | [x] | 5 | 5 | 89% |
| [org.apache.lucene.tests.mockfile.FilterPath](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/FilterPath.java) | org.gnit.lucenekmp.tests.mockfile.FilterPath | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.FilterSeekableByteChannel](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/FilterSeekableByteChannel.java) | org.gnit.lucenekmp.tests.mockfile.FilterSeekableByteChannel | Depth 4 | [x] | 5 | 5 | 91% |
| [org.apache.lucene.tests.mockfile.HandleLimitFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/HandleLimitFS.java) | org.gnit.lucenekmp.tests.mockfile.HandleLimitFS | Depth 2 | [] | 31 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.HandleTrackingFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/HandleTrackingFS.java) | org.gnit.lucenekmp.tests.mockfile.HandleTrackingFS | Depth 3 | [] | 7 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.LeakFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/LeakFS.java) | org.gnit.lucenekmp.tests.mockfile.LeakFS | Depth 1 | [] | 31 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.ShuffleFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/ShuffleFS.java) | org.gnit.lucenekmp.tests.mockfile.ShuffleFS | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.VerboseFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/VerboseFS.java) | org.gnit.lucenekmp.tests.mockfile.VerboseFS | Depth 2 | [] | 31 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.VirusCheckingFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/VirusCheckingFS.java) | org.gnit.lucenekmp.tests.mockfile.VirusCheckingFS | Depth 2 | [] | 30 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.WindowsFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/WindowsFS.java) | org.gnit.lucenekmp.tests.mockfile.WindowsFS | Depth 1 | [] | 34 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.WindowsPath](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/WindowsPath.java) | org.gnit.lucenekmp.tests.mockfile.WindowsPath | Depth 2 | [] | 23 | 0 | 0% |
| [org.apache.lucene.tests.search.AssertingCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/search/AssertingCollector.java) | org.gnit.lucenekmp.tests.search.AssertingCollector | Depth 4 | [x] | 4 | 4 | 64% |
| [org.apache.lucene.tests.search.AssertingIndexSearcher](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/search/AssertingIndexSearcher.java) | org.gnit.lucenekmp.tests.search.AssertingIndexSearcher | Depth 2 | [x] | 26 | 36 | 67% |
| [org.apache.lucene.tests.search.AssertingMatchesIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/search/AssertingMatchesIterator.java) | org.gnit.lucenekmp.tests.search.AssertingMatchesIterator | Depth 3 | [x] | 1 | 1 | 75% |
| [org.apache.lucene.tests.search.AssertingScorable](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/search/AssertingScorable.java) | org.gnit.lucenekmp.tests.search.AssertingScorable | Depth 7 | [x] | 6 | 5 | 87% |
| [org.apache.lucene.tests.search.CheckHits](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/search/CheckHits.java) | org.gnit.lucenekmp.tests.search.CheckHits | Depth 2 | [x] | 2 | 2 | 9% |
| [org.apache.lucene.tests.search.DummyTotalHitCountCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/search/DummyTotalHitCountCollector.java) | org.gnit.lucenekmp.tests.search.DummyTotalHitCountCollector | Depth 2 | [] | 4 | 0 | 0% |
| [org.apache.lucene.tests.search.FixedBitSetCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/search/FixedBitSetCollector.java) | org.gnit.lucenekmp.tests.search.FixedBitSetCollector | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.search.MatchesTestBase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/search/MatchesTestBase.java) | org.gnit.lucenekmp.tests.search.MatchesTestBase | Depth 1 | [] | 147 | 0 | 0% |
| [org.apache.lucene.tests.search.QueryUtils](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/search/QueryUtils.java) | org.gnit.lucenekmp.tests.search.QueryUtils | Depth 2 | [x] | 5 | 4 | 0% |
| [org.apache.lucene.tests.search.RandomApproximationQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/search/RandomApproximationQuery.java) | org.gnit.lucenekmp.tests.search.RandomApproximationQuery | Depth 2 | [x] | 7 | 1 | 0% |
| [org.apache.lucene.tests.search.similarities.AssertingSimilarity](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/search/similarities/AssertingSimilarity.java) | org.gnit.lucenekmp.tests.search.similarities.AssertingSimilarity | Depth 3 | [x] | 2 | 2 | 62% |
| [org.apache.lucene.tests.store.BaseDirectoryTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/store/BaseDirectoryTestCase.java) | org.gnit.lucenekmp.tests.store.BaseDirectoryTestCase | Depth 1 | [x] | 204 | 85 | 76% |
| [org.apache.lucene.tests.store.BaseDirectoryWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/store/BaseDirectoryWrapper.java) | org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper | Depth 1 | [x] | 16 | 14 | 93% |
| [org.apache.lucene.tests.store.MockDirectoryWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/store/MockDirectoryWrapper.java) | org.gnit.lucenekmp.tests.store.MockDirectoryWrapper | Depth 2 | [x] | 0 | 15 | 0% |
| [org.apache.lucene.tests.store.MockIndexInputWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/store/MockIndexInputWrapper.java) | org.gnit.lucenekmp.tests.store.MockIndexInputWrapper | Depth 2 | [] | 30 | 0 | 0% |
| [org.apache.lucene.tests.store.MockIndexOutputWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/store/MockIndexOutputWrapper.java) | org.gnit.lucenekmp.tests.store.MockIndexOutputWrapper | Depth 2 | [] | 24 | 0 | 0% |
| [org.apache.lucene.tests.store.RawDirectoryWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/store/RawDirectoryWrapper.java) | org.gnit.lucenekmp.tests.store.RawDirectoryWrapper | Depth 2 | [x] | 16 | 14 | 93% |
| [org.apache.lucene.tests.store.SlowClosingMockIndexInputWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/store/SlowClosingMockIndexInputWrapper.java) | org.gnit.lucenekmp.tests.store.SlowClosingMockIndexInputWrapper | Depth 2 | [] | 30 | 0 | 0% |
| [org.apache.lucene.tests.store.SlowOpeningMockIndexInputWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/store/SlowOpeningMockIndexInputWrapper.java) | org.gnit.lucenekmp.tests.store.SlowOpeningMockIndexInputWrapper | Depth 2 | [] | 30 | 0 | 0% |
| [org.apache.lucene.tests.util.AbstractBeforeAfterRule](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/AbstractBeforeAfterRule.java) | org.gnit.lucenekmp.tests.util.AbstractBeforeAfterRule | Depth 3 | [x] | 1 | 2 | 0% |
| [org.apache.lucene.tests.util.BaseBitSetTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/BaseBitSetTestCase.java) | org.gnit.lucenekmp.tests.util.BaseBitSetTestCase | Depth 2 | [x] | 15 | 13 | 96% |
| [org.apache.lucene.tests.util.FailureMarker](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/FailureMarker.java) | org.gnit.lucenekmp.tests.util.FailureMarker | Depth 2 | [] | 3 | 0 | 0% |
| [org.apache.lucene.tests.util.LineFileDocs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/LineFileDocs.java) | org.gnit.lucenekmp.tests.util.LineFileDocs | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.LuceneJUnit3MethodProvider](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/LuceneJUnit3MethodProvider.java) | org.gnit.lucenekmp.tests.util.LuceneJUnit3MethodProvider | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.LuceneTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/LuceneTestCase.java) | org.gnit.lucenekmp.tests.util.LuceneTestCase | Depth 2 | [x] | 2 | 1 | 0% |
| [org.apache.lucene.tests.util.NullInfoStream](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/NullInfoStream.java) | org.gnit.lucenekmp.tests.util.NullInfoStream | Depth 2 | [x] | 3 | 3 | 83% |
| [org.apache.lucene.tests.util.QuickPatchThreadsFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/QuickPatchThreadsFilter.java) | org.gnit.lucenekmp.tests.util.QuickPatchThreadsFilter | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.RamUsageTester](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/RamUsageTester.java) | org.gnit.lucenekmp.tests.util.RamUsageTester | Depth 2 | [x] | 1 | 1 | 0% |
| [org.apache.lucene.tests.util.RunListenerPrintReproduceInfo](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/RunListenerPrintReproduceInfo.java) | org.gnit.lucenekmp.tests.util.RunListenerPrintReproduceInfo | Depth 2 | [] | 9 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleAssertionsRequired](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleAssertionsRequired.java) | org.gnit.lucenekmp.tests.util.TestRuleAssertionsRequired | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleDelegate](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleDelegate.java) | org.gnit.lucenekmp.tests.util.TestRuleDelegate | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleIgnoreAfterMaxFailures](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleIgnoreAfterMaxFailures.java) | org.gnit.lucenekmp.tests.util.TestRuleIgnoreAfterMaxFailures | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleIgnoreTestSuites](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleIgnoreTestSuites.java) | org.gnit.lucenekmp.tests.util.TestRuleIgnoreTestSuites | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleLimitSysouts](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleLimitSysouts.java) | org.gnit.lucenekmp.tests.util.TestRuleLimitSysouts | Depth 3 | [] | 6 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleMarkFailure](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleMarkFailure.java) | org.gnit.lucenekmp.tests.util.TestRuleMarkFailure | Depth 2 | [x] | 1 | 3 | 0% |
| [org.apache.lucene.tests.util.TestRuleRestoreSystemProperties](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleRestoreSystemProperties.java) | org.gnit.lucenekmp.tests.util.TestRuleRestoreSystemProperties | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleSetupAndRestoreClassEnv](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleSetupAndRestoreClassEnv.java) | org.gnit.lucenekmp.tests.util.TestRuleSetupAndRestoreClassEnv | Depth 2 | [x] | 3 | 4 | 0% |
| [org.apache.lucene.tests.util.TestRuleSetupAndRestoreInstanceEnv](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleSetupAndRestoreInstanceEnv.java) | org.gnit.lucenekmp.tests.util.TestRuleSetupAndRestoreInstanceEnv | Depth 2 | [] | 3 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleSetupTeardownChained](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleSetupTeardownChained.java) | org.gnit.lucenekmp.tests.util.TestRuleSetupTeardownChained | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleStoreClassName](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleStoreClassName.java) | org.gnit.lucenekmp.tests.util.TestRuleStoreClassName | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleTemporaryFilesCleanup](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleTemporaryFilesCleanup.java) | org.gnit.lucenekmp.tests.util.TestRuleTemporaryFilesCleanup | Depth 1 | [] | 8 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleThreadAndTestName](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleThreadAndTestName.java) | org.gnit.lucenekmp.tests.util.TestRuleThreadAndTestName | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestUtil.java) | org.gnit.lucenekmp.tests.util.TestUtil | Depth 2 | [x] | 18 | 3 | 0% |
| [org.apache.lucene.tests.util.ThrottledIndexOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/ThrottledIndexOutput.java) | org.gnit.lucenekmp.tests.util.ThrottledIndexOutput | Depth 2 | [] | 25 | 0 | 0% |
| [org.apache.lucene.tests.util.VerifyTestClassNamingConvention](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/VerifyTestClassNamingConvention.java) | org.gnit.lucenekmp.tests.util.VerifyTestClassNamingConvention | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.util.automaton.AutomatonTestUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/automaton/AutomatonTestUtil.java) | org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil | Depth 3 | [x] | 1 | 0 | 0% |
| [org.apache.lucene.util.Accountable](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/Accountable.java) | org.gnit.lucenekmp.util.Accountable | Depth 2 | [x] | 1 | 1 | 0% |
| [org.apache.lucene.util.Accountables](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/Accountables.java) | org.gnit.lucenekmp.util.Accountables | Depth 4 | [x] | 1 | 1 | 90% |
| [org.apache.lucene.util.ArrayInPlaceMergeSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/ArrayInPlaceMergeSorter.java) | org.gnit.lucenekmp.util.ArrayInPlaceMergeSorter | Depth 1 | [x] | 22 | 20 | 99% |
| [org.apache.lucene.util.ArrayIntroSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/ArrayIntroSorter.java) | org.gnit.lucenekmp.util.ArrayIntroSorter | Depth 1 | [x] | 23 | 21 | 99% |
| [org.apache.lucene.util.ArrayTimSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/ArrayTimSorter.java) | org.gnit.lucenekmp.util.ArrayTimSorter | Depth 1 | [x] | 42 | 39 | 87% |
| [org.apache.lucene.util.ArrayUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/ArrayUtil.java) | org.gnit.lucenekmp.util.ArrayUtil | Depth 2 | [x] | 11 | 11 | 90% |
| [org.apache.lucene.util.Attribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/Attribute.java) | org.gnit.lucenekmp.util.Attribute | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.util.AttributeFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/AttributeFactory.java) | org.gnit.lucenekmp.util.AttributeFactory | Depth 2 | [x] | 4 | 2 | 85% |
| [org.apache.lucene.util.AttributeImpl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/AttributeImpl.java) | org.gnit.lucenekmp.util.AttributeImpl | Depth 1 | [x] | 5 | 7 | 72% |
| [org.apache.lucene.util.AttributeSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/AttributeSource.java) | org.gnit.lucenekmp.util.AttributeSource | Depth 3 | [x] | 2 | 1 | 0% |
| [org.apache.lucene.util.BaseSortTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/BaseSortTestCase.java) | org.gnit.lucenekmp.util.BaseSortTestCase | Depth 2 | [x] | 2 | 2 | 82% |
| [org.apache.lucene.util.BitSetIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BitSetIterator.java) | org.gnit.lucenekmp.util.BitSetIterator | Depth 1 | [x] | 10 | 3 | 66% |
| [org.apache.lucene.util.BitUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BitUtil.java) | org.gnit.lucenekmp.util.BitUtil | Depth 1 | [x] | 10 | 1 | 0% |
| [org.apache.lucene.util.Bits](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/Bits.java) | org.gnit.lucenekmp.util.Bits | Depth 2 | [x] | 2 | 1 | 61% |
| [org.apache.lucene.util.ByteBlockPool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/ByteBlockPool.java) | org.gnit.lucenekmp.util.ByteBlockPool | Depth 1 | [x] | 12 | 1 | 0% |
| [org.apache.lucene.util.BytesRef](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BytesRef.java) | org.gnit.lucenekmp.util.BytesRef | Depth 1 | [x] | 4 | 2 | 38% |
| [org.apache.lucene.util.BytesRefArray](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BytesRefArray.java) | org.gnit.lucenekmp.util.BytesRefArray | Depth 3 | [x] | 26 | 22 | 85% |
| [org.apache.lucene.util.BytesRefBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BytesRefBuilder.java) | org.gnit.lucenekmp.util.BytesRefBuilder | Depth 1 | [x] | 16 | 17 | 94% |
| [org.apache.lucene.util.BytesRefComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BytesRefComparator.java) | org.gnit.lucenekmp.util.BytesRefComparator | Depth 3 | [x] | 3 | 3 | 80% |
| [org.apache.lucene.util.BytesRefHash](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BytesRefHash.java) | org.gnit.lucenekmp.util.BytesRefHash | Depth 4 | [x] | 1 | 34 | 0% |
| [org.apache.lucene.util.BytesRefIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/BytesRefIterator.java) | org.gnit.lucenekmp.util.BytesRefIterator | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.util.CharsRef](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/CharsRef.java) | org.gnit.lucenekmp.util.CharsRef | Depth 3 | [x] | 1 | 2 | 0% |
| [org.apache.lucene.util.CloseableThreadLocal](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/CloseableThreadLocal.java) | org.gnit.lucenekmp.util.CloseableThreadLocal | Depth 1 | [x] | 5 | 1 | 0% |
| [org.apache.lucene.util.CollectionUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/CollectionUtil.java) | org.gnit.lucenekmp.util.CollectionUtil | Depth 3 | [x] | 23 | 39 | 41% |
| [org.apache.lucene.util.CommandLineUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/CommandLineUtil.java) | org.gnit.lucenekmp.util.CommandLineUtil | Depth 2 | [x] | 7 | 7 | 94% |
| [org.apache.lucene.util.Constants](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/Constants.java) | org.gnit.lucenekmp.util.Constants | Depth 1 | [x] | 6 | 8 | 34% |
| [org.apache.lucene.util.DocIdSetBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/DocIdSetBuilder.java) | org.gnit.lucenekmp.util.DocIdSetBuilder | Depth 2 | [x] | 0 | 3 | 0% |
| [org.apache.lucene.util.FileDeleter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/FileDeleter.java) | org.gnit.lucenekmp.util.FileDeleter | Depth 4 | [x] | 2 | 2 | 40% |
| [org.apache.lucene.util.FixedBits](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/FixedBits.java) | org.gnit.lucenekmp.util.FixedBits | Depth 2 | [x] | 2 | 2 | 76% |
| [org.apache.lucene.util.FrequencyTrackingRingBuffer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/FrequencyTrackingRingBuffer.java) | org.gnit.lucenekmp.util.FrequencyTrackingRingBuffer | Depth 4 | [x] | 7 | 1 | 83% |
| [org.apache.lucene.util.GroupVIntUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/GroupVIntUtil.java) | org.gnit.lucenekmp.util.GroupVIntUtil | Depth 2 | [x] | 11 | 1 | 0% |
| [org.apache.lucene.util.HotspotVMOptions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/HotspotVMOptions.java) | org.gnit.lucenekmp.util.HotspotVMOptions | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.util.IOBooleanSupplier](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/IOBooleanSupplier.java) | org.gnit.lucenekmp.util.IOBooleanSupplier | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.util.IOSupplier](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/IOSupplier.java) | org.gnit.lucenekmp.util.IOSupplier | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.util.IOUtils](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/IOUtils.java) | org.gnit.lucenekmp.util.IOUtils | Depth 2 | [x] | 4 | 25 | 0% |
| [org.apache.lucene.util.InPlaceMergeSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/InPlaceMergeSorter.java) | org.gnit.lucenekmp.util.InPlaceMergeSorter | Depth 2 | [x] | 22 | 20 | 99% |
| [org.apache.lucene.util.InfoStream](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/InfoStream.java) | org.gnit.lucenekmp.util.InfoStream | Depth 2 | [x] | 3 | 3 | 83% |
| [org.apache.lucene.util.IntArrayDocIdSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/IntArrayDocIdSet.java) | org.gnit.lucenekmp.util.IntArrayDocIdSet | Depth 1 | [x] | 2 | 5 | 0% |
| [org.apache.lucene.util.IntBlockPool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/IntBlockPool.java) | org.gnit.lucenekmp.util.IntBlockPool | Depth 4 | [x] | 1 | 1 | 72% |
| [org.apache.lucene.util.IntsRef](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/IntsRef.java) | org.gnit.lucenekmp.util.IntsRef | Depth 1 | [x] | 3 | 1 | 62% |
| [org.apache.lucene.util.LSBRadixSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/LSBRadixSorter.java) | org.gnit.lucenekmp.util.LSBRadixSorter | Depth 1 | [x] | 6 | 5 | 92% |
| [org.apache.lucene.util.LongBitSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/LongBitSet.java) | org.gnit.lucenekmp.util.LongBitSet | Depth 1 | [x] | 21 | 2 | 76% |
| [org.apache.lucene.util.LongValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/LongValues.java) | org.gnit.lucenekmp.util.LongValues | Depth 4 | [x] | 1 | 1 | 71% |
| [org.apache.lucene.util.LongsRef](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/LongsRef.java) | org.gnit.lucenekmp.util.LongsRef | Depth 1 | [x] | 3 | 1 | 62% |
| [org.apache.lucene.util.MSBRadixSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/MSBRadixSorter.java) | org.gnit.lucenekmp.util.MSBRadixSorter | Depth 6 | [x] | 23 | 21 | 99% |
| [org.apache.lucene.util.MathUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/MathUtil.java) | org.gnit.lucenekmp.util.MathUtil | Depth 1 | [x] | 8 | 9 | 88% |
| [org.apache.lucene.util.MergedIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/MergedIterator.java) | org.gnit.lucenekmp.util.MergedIterator | Depth 5 | [x] | 0 | 15 | 0% |
| [org.apache.lucene.util.NamedSPILoader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/NamedSPILoader.java) | org.gnit.lucenekmp.util.NamedSPILoader | Depth 2 | [x] | 5 | 0 | 0% |
| [org.apache.lucene.util.NamedThreadFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/NamedThreadFactory.java) | org.gnit.lucenekmp.util.NamedThreadFactory | Depth 1 | [x] | 2 | 3 | 0% |
| [org.apache.lucene.util.NotDocIdSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/NotDocIdSet.java) | org.gnit.lucenekmp.util.NotDocIdSet | Depth 4 | [x] | 7 | 5 | 70% |
| [org.apache.lucene.util.NumericUtils](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/NumericUtils.java) | org.gnit.lucenekmp.util.NumericUtils | Depth 1 | [x] | 14 | 16 | 87% |
| [org.apache.lucene.util.PrintStreamInfoStream](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/PrintStreamInfoStream.java) | org.gnit.lucenekmp.util.PrintStreamInfoStream | Depth 2 | [x] | 3 | 0 | 0% |
| [org.apache.lucene.util.PriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/PriorityQueue.java) | org.gnit.lucenekmp.util.PriorityQueue | Depth 3 | [x] | 1 | 2 | 42% |
| [org.apache.lucene.util.RamUsageEstimator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/RamUsageEstimator.java) | org.gnit.lucenekmp.util.RamUsageEstimator | Depth 3 | [x] | 6 | 5 | 56% |
| [org.apache.lucene.util.RecyclingByteBlockAllocator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/RecyclingByteBlockAllocator.java) | org.gnit.lucenekmp.util.RecyclingByteBlockAllocator | Depth 1 | [] | 5 | 0 | 0% |
| [org.apache.lucene.util.RefCount](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/RefCount.java) | org.gnit.lucenekmp.util.RefCount | Depth 3 | [x] | 3 | 3 | 84% |
| [org.apache.lucene.util.RoaringDocIdSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/RoaringDocIdSet.java) | org.gnit.lucenekmp.util.RoaringDocIdSet | Depth 3 | [x] | 8 | 6 | 62% |
| [org.apache.lucene.util.SameThreadExecutorService](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/SameThreadExecutorService.java) | org.gnit.lucenekmp.util.SameThreadExecutorService | Depth 2 | [x] | 5 | 15 | 23% |
| [org.apache.lucene.util.SetOnce](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/SetOnce.java) | org.gnit.lucenekmp.util.SetOnce | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.util.SparseFixedBitSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/SparseFixedBitSet.java) | org.gnit.lucenekmp.util.SparseFixedBitSet | Depth 1 | [x] | 31 | 3 | 83% |
| [org.apache.lucene.util.StableMSBRadixSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/StableMSBRadixSorter.java) | org.gnit.lucenekmp.util.StableMSBRadixSorter | Depth 7 | [x] | 26 | 24 | 99% |
| [org.apache.lucene.util.StableStringSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/StableStringSorter.java) | org.gnit.lucenekmp.util.StableStringSorter | Depth 5 | [x] | 37 | 35 | 95% |
| [org.apache.lucene.util.StringHelper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/StringHelper.java) | org.gnit.lucenekmp.util.StringHelper | Depth 1 | [x] | 14 | 14 | 92% |
| [org.apache.lucene.util.StringSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/StringSorter.java) | org.gnit.lucenekmp.util.StringSorter | Depth 4 | [x] | 23 | 21 | 99% |
| [org.apache.lucene.util.SuppressForbidden](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/SuppressForbidden.java) | org.gnit.lucenekmp.util.SuppressForbidden | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.util.TestCloseableThreadLocal](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/TestCloseableThreadLocal.java) | org.gnit.lucenekmp.util.TestCloseableThreadLocal | Depth 1 | [] | 5 | 0 | 0% |
| [org.apache.lucene.util.TestFixedBitSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/TestFixedBitSet.java) | org.gnit.lucenekmp.util.TestFixedBitSet | Depth 1 | [x] | 2 | 1 | 80% |
| [org.apache.lucene.util.TestVectorUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/TestVectorUtil.java) | org.gnit.lucenekmp.util.TestVectorUtil | Depth 1 | [x] | 184 | 1 | 0% |
| [org.apache.lucene.util.ThreadInterruptedException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/ThreadInterruptedException.java) | org.gnit.lucenekmp.util.ThreadInterruptedException | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.util.UnicodeUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/UnicodeUtil.java) | org.gnit.lucenekmp.util.UnicodeUtil | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.util.VectorUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/VectorUtil.java) | org.gnit.lucenekmp.util.VectorUtil | Depth 1 | [x] | 20 | 20 | 97% |
| [org.apache.lucene.util.Version](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/Version.java) | org.gnit.lucenekmp.util.Version | Depth 1 | [x] | 5 | 3 | 41% |
| [org.apache.lucene.util.VirtualMethod](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/VirtualMethod.java) | org.gnit.lucenekmp.util.VirtualMethod | Depth 4 | [x] | 1 | 1 | 0% |
| [org.apache.lucene.util.automaton.Automaton](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/Automaton.java) | org.gnit.lucenekmp.util.automaton.Automaton | Depth 2 | [x] | 7 | 21 | 0% |
| [org.apache.lucene.util.automaton.ByteRunnable](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/ByteRunnable.java) | org.gnit.lucenekmp.util.automaton.ByteRunnable | Depth 2 | [x] | 3 | 1 | 0% |
| [org.apache.lucene.util.automaton.CharacterRunAutomaton](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/CharacterRunAutomaton.java) | org.gnit.lucenekmp.util.automaton.CharacterRunAutomaton | Depth 1 | [x] | 6 | 6 | 66% |
| [org.apache.lucene.util.automaton.FrozenIntSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/FrozenIntSet.java) | org.gnit.lucenekmp.util.automaton.FrozenIntSet | Depth 1 | [x] | 1 | 1 | 60% |
| [org.apache.lucene.util.automaton.NFARunAutomaton](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/NFARunAutomaton.java) | org.gnit.lucenekmp.util.automaton.NFARunAutomaton | Depth 3 | [x] | 6 | 6 | 65% |
| [org.apache.lucene.util.automaton.Operations](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/Operations.java) | org.gnit.lucenekmp.util.automaton.Operations | Depth 2 | [x] | 5 | 1 | 29% |
| [org.apache.lucene.util.automaton.RegExp](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/RegExp.java) | org.gnit.lucenekmp.util.automaton.RegExp | Depth 3 | [x] | 1 | 1 | 0% |
| [org.apache.lucene.util.automaton.StatePair](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/StatePair.java) | org.gnit.lucenekmp.util.automaton.StatePair | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.util.automaton.StringsToAutomaton](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/StringsToAutomaton.java) | org.gnit.lucenekmp.util.automaton.StringsToAutomaton | Depth 3 | [x] | 7 | 2 | 45% |
| [org.apache.lucene.util.automaton.TooComplexToDeterminizeException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/TooComplexToDeterminizeException.java) | org.gnit.lucenekmp.util.automaton.TooComplexToDeterminizeException | Depth 2 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.util.automaton.Transition](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/Transition.java) | org.gnit.lucenekmp.util.automaton.Transition | Depth 2 | [x] | 1 | 0 | 0% |
| [org.apache.lucene.util.automaton.UTF32ToUTF8](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/UTF32ToUTF8.java) | org.gnit.lucenekmp.util.automaton.UTF32ToUTF8 | Depth 3 | [x] | 0 | 4 | 0% |
| [org.apache.lucene.util.bkd.BKDRadixSelector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/bkd/BKDRadixSelector.java) | org.gnit.lucenekmp.util.bkd.BKDRadixSelector | Depth 5 | [x] | 15 | 21 | 4% |
| [org.apache.lucene.util.bkd.BKDReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/bkd/BKDReader.java) | org.gnit.lucenekmp.util.bkd.BKDReader | Depth 6 | [x] | 7 | 5 | 80% |
| [org.apache.lucene.util.bkd.BKDUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/bkd/BKDUtil.java) | org.gnit.lucenekmp.util.bkd.BKDUtil | Depth 5 | [x] | 7 | 1 | 0% |
| [org.apache.lucene.util.bkd.BKDWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/bkd/BKDWriter.java) | org.gnit.lucenekmp.util.bkd.BKDWriter | Depth 5 | [x] | 7 | 3 | 65% |
| [org.apache.lucene.util.bkd.DocIdsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/bkd/DocIdsWriter.java) | org.gnit.lucenekmp.util.bkd.DocIdsWriter | Depth 4 | [x] | 17 | 8 | 83% |
| [org.apache.lucene.util.bkd.MutablePointTreeReaderUtils](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/bkd/MutablePointTreeReaderUtils.java) | org.gnit.lucenekmp.util.bkd.MutablePointTreeReaderUtils | Depth 6 | [x] | 37 | 21 | 83% |
| [org.apache.lucene.util.bkd.OfflinePointReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/bkd/OfflinePointReader.java) | org.gnit.lucenekmp.util.bkd.OfflinePointReader | Depth 6 | [x] | 3 | 3 | 66% |
| [org.apache.lucene.util.bkd.OfflinePointWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/bkd/OfflinePointWriter.java) | org.gnit.lucenekmp.util.bkd.OfflinePointWriter | Depth 5 | [x] | 6 | 6 | 67% |
| [org.apache.lucene.util.compress.LZ4](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/compress/LZ4.java) | org.gnit.lucenekmp.util.compress.LZ4 | Depth 5 | [x] | 6 | 2 | 0% |
| [org.apache.lucene.util.fst.BytesRefFSTEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/BytesRefFSTEnum.java) | org.gnit.lucenekmp.util.fst.BytesRefFSTEnum | Depth 5 | [x] | 27 | 0 | 0% |
| [org.apache.lucene.util.fst.FST](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/FST.java) | org.gnit.lucenekmp.util.fst.FST | Depth 2 | [x] | 10 | 1 | 0% |
| [org.apache.lucene.util.fst.FSTCompiler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/FSTCompiler.java) | org.gnit.lucenekmp.util.fst.FSTCompiler | Depth 3 | [x] | 0 | 6 | 0% |
| [org.apache.lucene.util.fst.FSTEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/FSTEnum.java) | org.gnit.lucenekmp.util.fst.FSTEnum | Depth 6 | [x] | 23 | 23 | 78% |
| [org.apache.lucene.util.fst.FSTReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/FSTReader.java) | org.gnit.lucenekmp.util.fst.FSTReader | Depth 2 | [x] | 2 | 1 | 0% |
| [org.apache.lucene.util.fst.FSTSuffixNodeCache](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/FSTSuffixNodeCache.java) | org.gnit.lucenekmp.util.fst.FSTSuffixNodeCache | Depth 4 | [x] | 9 | 9 | 68% |
| [org.apache.lucene.util.fst.Util](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/Util.java) | org.gnit.lucenekmp.util.fst.Util | Depth 3 | [x] | 1 | 0 | 0% |
| [org.apache.lucene.util.hnsw.AbstractHnswGraphSearcher](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/AbstractHnswGraphSearcher.java) | org.gnit.lucenekmp.util.hnsw.AbstractHnswGraphSearcher | Depth 5 | [x] | 3 | 0 | 0% |
| [org.apache.lucene.util.hnsw.BlockingFloatHeap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/BlockingFloatHeap.java) | org.gnit.lucenekmp.util.hnsw.BlockingFloatHeap | Depth 1 | [x] | 6 | 1 | 0% |
| [org.apache.lucene.util.hnsw.ConcurrentHnswMerger](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/ConcurrentHnswMerger.java) | org.gnit.lucenekmp.util.hnsw.ConcurrentHnswMerger | Depth 4 | [x] | 5 | 4 | 41% |
| [org.apache.lucene.util.hnsw.HnswConcurrentMergeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/HnswConcurrentMergeBuilder.java) | org.gnit.lucenekmp.util.hnsw.HnswConcurrentMergeBuilder | Depth 6 | [x] | 19 | 3 | 0% |
| [org.apache.lucene.util.hnsw.HnswGraph](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/HnswGraph.java) | org.gnit.lucenekmp.util.hnsw.HnswGraph | Depth 3 | [x] | 8 | 1 | 0% |
| [org.apache.lucene.util.hnsw.HnswGraphSearcher](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/HnswGraphSearcher.java) | org.gnit.lucenekmp.util.hnsw.HnswGraphSearcher | Depth 5 | [x] | 10 | 7 | 66% |
| [org.apache.lucene.util.hnsw.HnswLock](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/HnswLock.java) | org.gnit.lucenekmp.util.hnsw.HnswLock | Depth 4 | [x] | 3 | 1 | 83% |
| [org.apache.lucene.util.hnsw.HnswUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/HnswUtil.java) | org.gnit.lucenekmp.util.hnsw.HnswUtil | Depth 4 | [x] | 7 | 0 | 0% |
| [org.apache.lucene.util.hnsw.IncrementalHnswGraphMerger](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/IncrementalHnswGraphMerger.java) | org.gnit.lucenekmp.util.hnsw.IncrementalHnswGraphMerger | Depth 5 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.util.hnsw.NeighborQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/NeighborQueue.java) | org.gnit.lucenekmp.util.hnsw.NeighborQueue | Depth 3 | [x] | 2 | 2 | 82% |
| [org.apache.lucene.util.hnsw.OnHeapHnswGraph](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/OnHeapHnswGraph.java) | org.gnit.lucenekmp.util.hnsw.OnHeapHnswGraph | Depth 5 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.util.hnsw.OrdinalTranslatedKnnCollector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/OrdinalTranslatedKnnCollector.java) | org.gnit.lucenekmp.util.hnsw.OrdinalTranslatedKnnCollector | Depth 4 | [x] | 7 | 7 | 93% |
| [org.apache.lucene.util.hnsw.RandomVectorScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/RandomVectorScorer.java) | org.gnit.lucenekmp.util.hnsw.RandomVectorScorer | Depth 2 | [x] | 4 | 2 | 0% |
| [org.apache.lucene.util.hnsw.RandomVectorScorerSupplier](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/RandomVectorScorerSupplier.java) | org.gnit.lucenekmp.util.hnsw.RandomVectorScorerSupplier | Depth 1 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.util.hnsw.UpdateableRandomVectorScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/UpdateableRandomVectorScorer.java) | org.gnit.lucenekmp.util.hnsw.UpdateableRandomVectorScorer | Depth 2 | [x] | 4 | 2 | 0% |
| [org.apache.lucene.util.packed.BlockPackedWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BlockPackedWriter.java) | org.gnit.lucenekmp.util.packed.BlockPackedWriter | Depth 5 | [x] | 5 | 7 | 28% |
| [org.apache.lucene.util.packed.BulkOperation](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperation.java) | org.gnit.lucenekmp.util.packed.BulkOperation | Depth 3 | [x] | 0 | 0 | 0% |
| [org.apache.lucene.util.packed.BulkOperationPacked](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked1](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked1.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked1 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked10](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked10.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked10 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked11](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked11.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked11 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked12](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked12.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked12 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked13](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked13.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked13 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked14](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked14.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked14 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked15](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked15.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked15 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked16](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked16.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked16 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked17](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked17.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked17 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked18](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked18.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked18 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked19](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked19.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked19 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked2](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked2.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked2 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked20](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked20.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked20 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked21](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked21.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked21 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked22](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked22.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked22 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked23](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked23.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked23 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked24](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked24.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked24 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked3](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked3.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked3 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked4](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked4.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked4 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked5](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked5.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked5 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked6](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked6.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked6 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked7](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked7.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked7 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked8](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked8.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked8 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPacked9](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPacked9.java) | org.gnit.lucenekmp.util.packed.BulkOperationPacked9 | Depth 3 | [x] | 15 | 14 | 98% |
| [org.apache.lucene.util.packed.BulkOperationPackedSingleBlock](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/BulkOperationPackedSingleBlock.java) | org.gnit.lucenekmp.util.packed.BulkOperationPackedSingleBlock | Depth 3 | [x] | 20 | 1 | 83% |
| [org.apache.lucene.util.packed.DirectMonotonicReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/DirectMonotonicReader.java) | org.gnit.lucenekmp.util.packed.DirectMonotonicReader | Depth 3 | [x] | 6 | 0 | 0% |
| [org.apache.lucene.util.packed.DirectReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/DirectReader.java) | org.gnit.lucenekmp.util.packed.DirectReader | Depth 5 | [x] | 2 | 2 | 83% |
| [org.apache.lucene.util.packed.DirectWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/DirectWriter.java) | org.gnit.lucenekmp.util.packed.DirectWriter | Depth 4 | [x] | 8 | 5 | 86% |
| [org.apache.lucene.util.packed.GrowableWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/GrowableWriter.java) | org.gnit.lucenekmp.util.packed.GrowableWriter | Depth 4 | [x] | 10 | 1 | 83% |
| [org.apache.lucene.util.packed.MonotonicBlockPackedReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/MonotonicBlockPackedReader.java) | org.gnit.lucenekmp.util.packed.MonotonicBlockPackedReader | Depth 6 | [x] | 1 | 2 | 0% |
| [org.apache.lucene.util.packed.Packed64](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/Packed64.java) | org.gnit.lucenekmp.util.packed.Packed64 | Depth 2 | [x] | 8 | 1 | 83% |
| [org.apache.lucene.util.packed.Packed64SingleBlock](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/Packed64SingleBlock.java) | org.gnit.lucenekmp.util.packed.Packed64SingleBlock | Depth 3 | [x] | 10 | 7 | 82% |
| [org.apache.lucene.util.packed.PackedInts](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/PackedInts.java) | org.gnit.lucenekmp.util.packed.PackedInts | Depth 2 | [x] | 8 | 3 | 0% |
| [org.apache.lucene.util.packed.PackedLongValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/PackedLongValues.java) | org.gnit.lucenekmp.util.packed.PackedLongValues | Depth 3 | [x] | 10 | 2 | 0% |
| [org.apache.lucene.util.packed.PackedReaderIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/PackedReaderIterator.java) | org.gnit.lucenekmp.util.packed.PackedReaderIterator | Depth 2 | [x] | 1 | 1 | 36% |
| [org.apache.lucene.util.packed.PackedWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/PackedWriter.java) | org.gnit.lucenekmp.util.packed.PackedWriter | Depth 2 | [x] | 4 | 4 | 40% |
| [org.apache.lucene.util.packed.PagedGrowableWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/PagedGrowableWriter.java) | org.gnit.lucenekmp.util.packed.PagedGrowableWriter | Depth 3 | [x] | 13 | 13 | 79% |
| [org.apache.lucene.util.packed.PagedMutable](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/PagedMutable.java) | org.gnit.lucenekmp.util.packed.PagedMutable | Depth 2 | [x] | 13 | 13 | 79% |
| [org.apache.lucene.util.quantization.QuantizedVectorsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/quantization/QuantizedVectorsReader.java) | org.gnit.lucenekmp.util.quantization.QuantizedVectorsReader | Depth 4 | [x] | 3 | 1 | 0% |
| [org.apache.lucene.util.quantization.ScalarQuantizedVectorSimilarity](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/quantization/ScalarQuantizedVectorSimilarity.java) | org.gnit.lucenekmp.util.quantization.ScalarQuantizedVectorSimilarity | Depth 7 | [x] | 0 | 1 | 0% |
| [org.apache.lucene.util.quantization.ScalarQuantizer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/quantization/ScalarQuantizer.java) | org.gnit.lucenekmp.util.quantization.ScalarQuantizer | Depth 5 | [x] | 11 | 1 | 0% |


TODO_TEST written to: /home/joel/code/bbl-lucene/lucene-kmp/TODO_TEST.md

## Summary

### Lucene Classes (Semantic Analysis)
- Total Priority-1 Classes: 668
- Ported Classes: 666
- Class Porting Progress: 99%
- **Semantic Completion Progress: 55%**
- Total Core Methods Needed: 4987
- Core Methods Implemented: 2769

### Unit Test Classes (Semantic Analysis)
- Total Unit Test Classes: 1078
- Ported Unit Test Classes: 928
- Unit Test Porting Progress: 86%
- **Unit Test Semantic Completion: 40%**
- Total Test Core Methods Needed: 9333
- Test Core Methods Implemented: 3785
