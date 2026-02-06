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
