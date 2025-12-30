# Nori (Korean) port TODO

## Dependency graph (high level)
- analysis.morph (common): TokenType/Token/Dictionary/MorphData -> BinaryDictionary/TokenInfoFST/CharacterDefinition/ConnectionCosts -> *Writers* (BinaryDictionaryWriter/DictionaryEntryWriter/CharacterDefinitionWriter/ConnectionCostsWriter)
- ko.dict: DictionaryConstants -> KoMorphData -> TokenInfoMorphData/UnknownMorphData/UserMorphData -> TokenInfoDictionary/UnknownDictionary/UserDictionary (+ TokenInfoFST, CharacterDefinition, ConnectionCosts) -> *Builders/Writers* (TokenInfoDictionaryEntryWriter/TokenInfoDictionaryWriter/TokenInfoDictionaryBuilder/UnknownDictionaryWriter/UnknownDictionaryBuilder/ConnectionCostsBuilder/DictionaryBuilder)
- ko core: POS -> Token -> DictionaryToken/DecompoundToken
- ko tokenattributes: PartOfSpeechAttribute(+Impl)/ReadingAttribute(+Impl) -> filters (KoreanReadingFormFilter/KoreanPartOfSpeechStopFilter/KoreanNumberFilter)
- ko tokenizer: Viterbi -> KoreanTokenizer -> factories -> KoreanAnalyzer

## Ordered port list (leaf -> root)

### Production
1) analysis.morph.TokenType -> TO_BE_PORTED
2) analysis.morph.Dictionary -> TO_BE_PORTED
3) analysis.morph.MorphData -> TO_BE_PORTED
4) analysis.morph.Token -> TO_BE_PORTED
5) analysis.morph.TokenInfoFST -> TO_BE_PORTED
6) analysis.morph.CharacterDefinition -> TO_BE_PORTED
7) analysis.morph.ConnectionCosts -> TO_BE_PORTED
8) analysis.morph.BinaryDictionary -> TO_BE_PORTED
9) analysis.morph.DictionaryEntryWriter -> TO_BE_PORTED
10) analysis.morph.BinaryDictionaryWriter -> TO_BE_PORTED
11) analysis.morph.CharacterDefinitionWriter -> TO_BE_PORTED
12) analysis.morph.ConnectionCostsWriter -> TO_BE_PORTED
13) org.gnit.lucenekmp.analysis.ko.dict.DictionaryConstants -> TO_BE_PORTED
14) org.gnit.lucenekmp.analysis.ko.POS -> TO_BE_PORTED
15) org.gnit.lucenekmp.analysis.ko.dict.KoMorphData -> TO_BE_PORTED
16) org.gnit.lucenekmp.analysis.ko.dict.TokenInfoMorphData -> TO_BE_PORTED
17) org.gnit.lucenekmp.analysis.ko.dict.UnknownMorphData -> TO_BE_PORTED
18) org.gnit.lucenekmp.analysis.ko.dict.UserMorphData -> TO_BE_PORTED
19) org.gnit.lucenekmp.analysis.ko.dict.CharacterDefinition -> TO_BE_PORTED
20) org.gnit.lucenekmp.analysis.ko.dict.ConnectionCosts -> TO_BE_PORTED
21) org.gnit.lucenekmp.analysis.ko.dict.TokenInfoFST -> TO_BE_PORTED
22) org.gnit.lucenekmp.analysis.ko.dict.TokenInfoDictionary -> TO_BE_PORTED
23) org.gnit.lucenekmp.analysis.ko.dict.UnknownDictionary -> TO_BE_PORTED
24) org.gnit.lucenekmp.analysis.ko.dict.UserDictionary -> TO_BE_PORTED
25) org.gnit.lucenekmp.analysis.ko.dict.TokenInfoDictionaryEntryWriter -> TO_BE_PORTED
26) org.gnit.lucenekmp.analysis.ko.dict.TokenInfoDictionaryWriter -> TO_BE_PORTED
27) org.gnit.lucenekmp.analysis.ko.dict.TokenInfoDictionaryBuilder -> TO_BE_PORTED
28) org.gnit.lucenekmp.analysis.ko.dict.UnknownDictionaryWriter -> TO_BE_PORTED
29) org.gnit.lucenekmp.analysis.ko.dict.UnknownDictionaryBuilder -> TO_BE_PORTED
30) org.gnit.lucenekmp.analysis.ko.dict.ConnectionCostsBuilder -> TO_BE_PORTED
31) org.gnit.lucenekmp.analysis.ko.dict.DictionaryBuilder -> TO_BE_PORTED
32) org.gnit.lucenekmp.analysis.ko.Token -> TO_BE_PORTED
33) org.gnit.lucenekmp.analysis.ko.DictionaryToken -> TO_BE_PORTED
34) org.gnit.lucenekmp.analysis.ko.DecompoundToken -> TO_BE_PORTED
35) org.gnit.lucenekmp.analysis.ko.tokenattributes.PartOfSpeechAttribute -> TO_BE_PORTED
36) org.gnit.lucenekmp.analysis.ko.tokenattributes.PartOfSpeechAttributeImpl -> TO_BE_PORTED
37) org.gnit.lucenekmp.analysis.ko.tokenattributes.ReadingAttribute -> TO_BE_PORTED
38) org.gnit.lucenekmp.analysis.ko.tokenattributes.ReadingAttributeImpl -> TO_BE_PORTED
39) org.gnit.lucenekmp.analysis.ko.KoreanReadingFormFilter -> TO_BE_PORTED
40) org.gnit.lucenekmp.analysis.ko.KoreanPartOfSpeechStopFilter -> TO_BE_PORTED
41) org.gnit.lucenekmp.analysis.ko.KoreanNumberFilter -> TO_BE_PORTED
42) org.gnit.lucenekmp.analysis.ko.Viterbi -> TO_BE_PORTED
43) org.gnit.lucenekmp.analysis.ko.KoreanTokenizer -> TO_BE_PORTED
44) org.gnit.lucenekmp.analysis.ko.KoreanTokenizerFactory -> TO_BE_PORTED
45) org.gnit.lucenekmp.analysis.ko.KoreanReadingFormFilterFactory -> TO_BE_PORTED
46) org.gnit.lucenekmp.analysis.ko.KoreanPartOfSpeechStopFilterFactory -> TO_BE_PORTED
47) org.gnit.lucenekmp.analysis.ko.KoreanNumberFilterFactory -> TO_BE_PORTED
48) org.gnit.lucenekmp.analysis.ko.KoreanAnalyzer -> TO_BE_PORTED

### Tests (in port order)
1) org.gnit.lucenekmp.analysis.ko.dict.TestTokenInfoDictionary -> TO_BE_PORTED
2) org.gnit.lucenekmp.analysis.ko.dict.TestUnknownDictionary -> TO_BE_PORTED
3) org.gnit.lucenekmp.analysis.ko.dict.TestExternalDictionary -> TO_BE_PORTED
4) org.gnit.lucenekmp.analysis.ko.dict.TestUserDictionary -> TO_BE_PORTED
5) org.gnit.lucenekmp.analysis.ko.TestKoreanTokenizer -> TO_BE_PORTED
6) org.gnit.lucenekmp.analysis.ko.TestKoreanTokenizerFactory -> TO_BE_PORTED
7) org.gnit.lucenekmp.analysis.ko.TestKoreanReadingFormFilter -> TO_BE_PORTED
8) org.gnit.lucenekmp.analysis.ko.TestKoreanReadingFormFilterFactory -> TO_BE_PORTED
9) org.gnit.lucenekmp.analysis.ko.TestKoreanPartOfSpeechStopFilterFactory -> TO_BE_PORTED
10) org.gnit.lucenekmp.analysis.ko.TestKoreanNumberFilter -> TO_BE_PORTED
11) org.gnit.lucenekmp.analysis.ko.TestKoreanNumberFilterFactory -> TO_BE_PORTED
12) org.gnit.lucenekmp.analysis.ko.TestKoreanAnalyzer -> TO_BE_PORTED

## Resources
- lucene/analysis/nori/src/resources/org/apache/lucene/analysis/ko/dict/*.dat -> TO_BE_PORTED
- gradle/generateKoreanDicData.gradle.kts (new) -> TO_BE_PORTED
- lucene-kmp/analysis/nori/build.gradle.kts wiring -> TO_BE_PORTED
