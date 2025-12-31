# Kuromoji Porting TODO

## Goal
Port Kuromoji production code first (no tests), then port and run tests.

## Current status note (important)
- Kuromoji tests live under `analysis/kuromoji/src/commonTest`.
- `compileKotlinJvm` and `compileTestKotlinLinuxX64` are green.
- `:analysis:kuromoji:jvmTest` currently discovers **zero tests** (needs test wiring). Until fixed, test status should be treated as "ported but not yet runnable".

---

## A) Production code (NO TESTS FIRST)

### A.1 Core tokenizer / viterbi / token types
- PORTED: Token
- PORTED: JapaneseTokenizer
- PORTED: ViterbiNBest (common wrapper + Kuromoji-specific subclass: `KurosakiViterbiNBest` / `KuromojiViterbiNBest`)

### A.2 Dictionaries / data generation
- PORTED: DictionaryBuilder / DictionaryConstants / CharacterDefinition
- PORTED: ConnectionCosts (+ builder)
- PORTED: TokenInfoDictionary (+ writer/builder/FST/morph data)
- PORTED: UnknownDictionary (+ writer/builder/morph data)
- PORTED: UserDictionary (+ morph data)
- PORTED: Gradle dictionary generation (`generateJapaneseDictionaryData`) wired in `build.gradle.kts`

### A.3 Filters (production)
- PORTED: JapaneseBaseFormFilter
- PORTED: JapaneseHiraganaUppercaseFilter
- PORTED: JapaneseIterationMarkCharFilter
- PORTED: JapaneseKatakanaStemFilter
- PORTED: JapaneseKatakanaUppercaseFilter
- PORTED: JapaneseNumberFilter
- PORTED: JapanesePartOfSpeechStopFilter
- PORTED: JapaneseReadingFormFilter
- PORTED: JapaneseCompletionFilter

### A.4 Analyzers (production)
- PORTED: JapaneseAnalyzer
- PORTED: JapaneseCompletionAnalyzer

### A.5 Factories (production)
- PORTED: JapaneseTokenizerFactory
- PORTED: JapaneseBaseFormFilterFactory
- PORTED: JapaneseHiraganaUppercaseFilterFactory
- PORTED: JapaneseIterationMarkCharFilterFactory
- PORTED: JapaneseKatakanaStemFilterFactory
- PORTED: JapaneseKatakanaUppercaseFilterFactory
- PORTED: JapaneseNumberFilterFactory
- PORTED: JapanesePartOfSpeechStopFilterFactory
- PORTED: JapaneseReadingFormFilterFactory
- PORTED: JapaneseCompletionFilterFactory

### A.6 Resources
- PORTED: `stopwords.txt` (inline String)
- PORTED: `stoptags.txt` (inline String)
- PORTED: `completion/romaji_map.txt` (inline String)
- PORTED: dict binary resources via Gradle generation inputs (copied to `lucene-kmp/gradle/kuromoji/...`)

---

## B) Unit tests (ONLY AFTER A is finished)

### B.1 analysis.ja.dict
- PORTED: TestExternalDictionary (compiles, need fix)
- PORTED: TestTokenInfoDictionary (compiles, need fix)
- PORTED: TestToStringUtil (compiles, test fails)
- PORTED: TestUnknownDictionary (test PASSES done)
- PORTED: TestUserDictionary (compiles, not tested yet)

### B.2 analysis.ja
- PORTED: TestExtendedMode (compiles, test fails)
- PORTED: TestJapaneseBaseFormFilterFactory (compiles, test fails)
- PORTED: StringMockResourceLoader (compiles, need fix)
- PORTED: TestJapaneseCompletionFilterFactory (compiles, test fails)
- PORTED: CJKWidthFilter (need to port test if exist)
- PORTED: CJKWidthFilterFactory (need to port test if exist)
- PORTED: TestJapaneseHiraganaUppercaseFilter (compiles, test fails)
- PORTED: TestJapaneseKatakanaStemFilterFactory (compiles, test fails)
- PORTED: TestJapaneseKatakanaUppercaseFilter (compiles, test fails)
- PORTED: TestJapanesePartOfSpeechStopFilterFactory (compiles, test fails)
- PORTED: TestJapaneseTokenizerFactory (compiles, test fails)
- PORTED: TestFactories (test PASSES, but need to implement something)
- PORTED: TestJapaneseBaseFormFilter (compiles, test fails)
- PORTED: TestJapaneseCompletionFilter (compiles, test fails)
- PORTED: TestJapaneseIterationMarkCharFilterFactory (compiles, test fails)
- TO_BE_PORTED: TestJapaneseKatakanaStemFilter
- TO_BE_PORTED: TestJapaneseNumberFilterFactory
- TO_BE_PORTED: TestJapaneseReadingFormFilterFactory
- PORTED: TestJapaneseTokenizer (compiles, need fix)
- PORTED: LineNumberReader (need to port jdk unit test)
- TO_BE_PORTED: TestJapaneseAnalyzer
- TO_BE_PORTED: TestJapaneseCompletionAnalyzer
- TO_BE_PORTED: TestJapaneseHiraganaUppercaseFilterFactory
- TO_BE_PORTED: TestJapaneseIterationMarkCharFilter
- TO_BE_PORTED: TestJapaneseKatakanaUppercaseFilterFactory
- TO_BE_PORTED: TestJapaneseNumberFilter
- TO_BE_PORTED: TestJapaneseReadingFormFilter
- TO_BE_PORTED: TestSearchMode

### B.3 Integration.completion
- TO_BE_PORTED: TestKatakanaRomanizer
