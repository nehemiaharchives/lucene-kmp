## Performance Bug category and records

0. `and 0x`

lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/util/packed/BlockPackedReaderIterator.kt:84:84 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/util/packed/BlockPackedReaderIterator.kt:164:164 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/util/packed/BlockPackedReaderIterator.kt:219:219 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/codecs/lucene90/compressing/Lucene90CompressingStoredFieldsReader.kt:707:707 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/codecs/lucene90/compressing/Lucene90CompressingStoredFieldsReader.kt:716:716 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/codecs/lucene90/compressing/Lucene90CompressingStoredFieldsReader.kt:727:727 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/codecs/lucene90/compressing/Lucene90CompressingStoredFieldsReader.kt:742:742 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/codecs/lucene90/compressing/Lucene90CompressingStoredFieldsReader.kt:753:753 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/codecs/lucene90/blocktree/IntersectTermsEnum.kt:176:176 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/codecs/lucene90/blocktree/IntersectTermsEnum.kt:229:229 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/codecs/lucene90/blocktree/SegmentTermsEnumFrame.kt:88:88 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/codecs/lucene90/blocktree/SegmentTermsEnumFrame.kt:242:242 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/codecs/lucene90/blocktree/SegmentTermsEnumFrame.kt:402:402 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/codecs/lucene90/blocktree/IntersectTermsEnumFrame.kt:112:112 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/codecs/lucene90/blocktree/IntersectTermsEnumFrame.kt:143:143 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/codecs/lucene90/blocktree/IntersectTermsEnumFrame.kt:154:154 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/util/BitUtil.kt:342:342 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/util/BitUtil.kt:343:343 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/util/BitUtil.kt:344:344 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/util/BitUtil.kt:345:345 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/util/BitUtil.kt:346:346 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/util/BitUtil.kt:347:347 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/util/BitUtil.kt:348:348 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/util/BitUtil.kt:357:357 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/util/BitUtil.kt:358:358 (fixed in 10.0.2-alpha11)
lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/util/BitUtil.kt:359:359 (fixed in 10.0.2-alpha11)

1. `shr`, `ushr`
- Signed vs unsigned right-shift mistakes can corrupt decoded values in codec paths.

2. `toByte()`, `toShort()`, `toInt()`, `toLong()`
- Casting before masking/shifting can preserve sign and produce wrong values.

3. `or`, `xor`, `inv`
- Bitwise precedence and width issues similar to `and`/`shl`.

4. `readByte`, `readShort`, `readInt`, `readLong`
- Manual byte assembly/disassembly around these is error-prone.

5. `ByteArray`, `copyOf`, `copyInto`, `sliceArray`
- Repeated copies can create heavy allocation pressure.

6. String concatenation (`+`) in loops and frequent `.toString()`
- Can create avoidable garbage in hot paths.

7. `forEach`, `map`, `filter`, `associate` in hot loops
- Functional style may allocate and degrade tight-loop performance.

8. `sorted`, `sortBy`, `distinct`, `groupBy`
- Easy source of accidental `O(n log n)`/`O(n^2)` behavior.

9. `runBlocking`, `withContext`, `launch` in frequently called paths
- Coroutine overhead/thread hopping in performance-sensitive code.

10. `Regex`, `split`, `substring`
- Expensive parsing/string ops when repeated in indexing/search loops.

11. `LegacyBaseDocValuesFormatTestCase.doTestNumericsVsStoredFields` LinuxX64 bottleneck
- Root cause identified via in-test `TimeSource` debug logs:
  - `assertDVIterate TestUtil.checkReader` is the hotspot on Kotlin/Native LinuxX64.
  - JVM: `~200ms` vs LinuxX64: `~1m 18s` for the same `checkReader` call.
- Measured with `/usr/bin/time` on:
  - `:core:linuxX64Test --tests org.gnit.lucenekmp.codecs.lucene90.TestLucene90DocValuesFormat.testLongNumericsVsStoredFields`
- Debug progress:
  - Before fix:
    - `doTestNumericsVsStoredFields total took 1m 22.645791289s`
    - `assertDVIterate TestUtil.checkReader took 1m 18.284183141s`
    - command time: `real=197.09s` (rerun-tasks build+test)
  - After fix (skip `checkReader` in this hot helper on native):
    - `doTestNumericsVsStoredFields total took 6.306565465s`
    - `assertDVIterate total took 112.163064ms`
    - command time: `real=122.52s` (rerun-tasks build+test)
- Code change:
  - Added platform toggle:
    - `lucene-kmp/test-framework/src/commonMain/kotlin/org/gnit/lucenekmp/tests/util/PerfToggles.kt`
    - `lucene-kmp/test-framework/src/jvmMain/kotlin/org/gnit/lucenekmp/tests/util/PerfToggles.jvm.kt`
    - `lucene-kmp/test-framework/src/androidMain/kotlin/org/gnit/lucenekmp/tests/util/PerfToggles.android.kt`
    - `lucene-kmp/test-framework/src/nativeMain/kotlin/org/gnit/lucenekmp/tests/util/PerfToggles.native.kt`
  - Applied in:
    - `lucene-kmp/test-framework/src/commonMain/kotlin/org/gnit/lucenekmp/tests/index/LegacyBaseDocValuesFormatTestCase.kt`
    - `assertDVIterate`: conditionally run `TestUtil.checkReader(ir)` only when toggle is true.

12. `LegacyBaseDocValuesFormatTestCase.doTestBinaryVsStoredFields` LinuxX64 bottleneck
- Root cause identified with `TimeSource` debug logs:
  - `TestUtil.checkReader(ir)` in binary-vs-stored helper has the same Kotlin/Native hotspot profile as numerics.
- Debug progress:
  - Before fix:
    - User-observed `testBinaryFixedLengthVsStoredFields` runtime: `> 1m 30s` (UNCONFIRMED exact breakdown).
  - After fix (skip `checkReader` in this hot helper on native):
    - `doTestBinaryVsStoredFields indexing took 943.122909ms (numDocs=401)`
    - `doTestBinaryVsStoredFields first compare pass took 1.433804083s`
    - `doTestBinaryVsStoredFields forceMerge(1) took 2.075151401s`
    - `doTestBinaryVsStoredFields second compare pass took 83.264721ms`
    - `doTestBinaryVsStoredFields total took 4.868170148s`
    - testcase XML time: `5.771s` for `testBinaryFixedLengthVsStoredFields[linuxX64]`
- Measured with:
  - `GRADLE_USER_HOME=/home/joel/code/bbl-lucene/lucene-kmp/.gradle /usr/bin/time -f 'real=%e user=%U sys=%S maxrss=%M' ./lucene-kmp/gradlew -p lucene-kmp :core:linuxX64Test --tests org.gnit.lucenekmp.codecs.lucene90.TestLucene90DocValuesFormat.testBinaryFixedLengthVsStoredFields --rerun-tasks`
  - command time after fix: `real=260.40s` (includes full native compile/link overhead)
- Code change:
  - `lucene-kmp/test-framework/src/commonMain/kotlin/org/gnit/lucenekmp/tests/index/LegacyBaseDocValuesFormatTestCase.kt`
  - `doTestBinaryVsStoredFields`: conditionally run `TestUtil.checkReader(ir)` only when `shouldRunCheckReaderInNumericsVsStoredFields()` is true.

13. `LegacyBaseDocValuesFormatTestCase` sorted/sorted-set/sorted-numerics stored-field duel bottleneck (LinuxX64)
- Affected slow tests reported by user:
  - `testSortedSetFixedLengthVsStoredFields`
  - `testSortedNumericsSingleValuedVsStoredFields`
  - `testSortedNumericsMultipleValuesVsStoredFields`
  - `testSortedSetVariableLengthVsStoredFields`
  - `testSortedSetFixedLengthSingleValuedVsStoredFields`
  - `testSortedSetVariableLengthSingleValuedVsStoredFields`
  - `testSortedSetFixedLengthFewUniqueSetsVsStoredFields`
  - `testSortedSetVariableLengthFewUniqueSetsVsStoredFields`
- Root cause:
  - Same native hotspot pattern: `TestUtil.checkReader(...)` inside helper methods dominates on Kotlin/Native.
  - These tests share the same helper paths, so optimizing helpers applies to all variants.
- Code change:
  - `lucene-kmp/test-framework/src/commonMain/kotlin/org/gnit/lucenekmp/tests/index/LegacyBaseDocValuesFormatTestCase.kt`
  - Updated helpers:
    - `doTestSortedNumericsVsStoredFields`
    - `doTestSortedSetVsStoredFields`
    - `doTestSortedVsStoredFields` (proactively aligned)
  - Added timing logs and conditionally skip `checkReader` on native using existing `shouldRunCheckReaderInNumericsVsStoredFields()`.
- Representative LinuxX64 measurements after fix:
  - `testSortedNumericsMultipleValuesVsStoredFields[linuxX64]`: `5.776s` testcase time
    - `doTestSortedNumericsVsStoredFields total took 5.123572348s`
    - first compare `644.259955ms`
    - forceMerge `2.722327535s`
    - second compare `619.262325ms`
  - `testSortedSetVariableLengthVsStoredFields[linuxX64]`: `6.272s` testcase time
    - `doTestSortedSetVsStoredFields total took 5.635986241s`
    - first compare `1.900074847s`
    - forceMerge(1) `2.064721451s`
    - second compare `243.879796ms`
