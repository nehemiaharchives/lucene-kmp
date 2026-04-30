# Release Notes 10.2.0-alpha09

## Changes since 10.2.0-alpha08

- Added `BibleNepaliAnalyzer`.
- Refactored `suffixLeadLabel` calculation for clarity and consistency.
- Fixed initialization of `completeReaderSet` in `ParallelLeafReader`.
- `FilterIndexInput` and `FilterIndexInputAccess` now compile.
- `FilterBinaryDocValues`, `FilterSortedDocValues`, `FilterSortedNumericDocValues`, and `FilterSortedSetDocValues` now compile.
- `SortedSetSortField` now compiles.
- `blockterms`-related classes now compile, including readers/writers and terms index variants:
  `BlockTermsReader`, `BlockTermsWriter`, `TermsIndexReaderBase`, `TermsIndexWriterBase`,
  `FixedGapTermsIndexReader/Writer`, and `VariableGapTermsIndexReader/Writer`.
- Updated progress tracking docs (`PROGRESS.md`, `PROGRESS2.md`).
