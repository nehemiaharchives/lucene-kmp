# 10.2.0-alpha06

Date: 2026-01-04

## Highlights
- Added new language support in `analysis:extra`: Tagalog, Marathi, Gujarati, Urdu, and Vietnamese analyzers, normalization, and stemming.
- Improved Vietnamese analysis pipeline with diacritic folding and tokenizer simplification.
- Integrated `analysis:extra` into CI and publishing workflows.

## Added
- Tagalog analyzer stack with normalization and light stemming.
- Marathi analyzer stack (based on Hindi pipeline) with normalization and stemming.
- Gujarati analyzer stack with Gujarati-specific normalization and light stemming.
- Urdu analyzer stack with Urdu-specific normalization and light stemming.
- Vietnamese normalization and stem filters, plus filter factories.
- New tests for Tagalog, Marathi, Gujarati, Urdu, and Vietnamese analysis components.

## Changed
- Vietnamese tokenizer implementation simplified by merging the CocCoc-based segmentation logic into `VietnameseTokenizer`.
- Gujarati analyzer/stemmer behavior now normalizes plural forms like `ગુજરાતીઓ` to `ગુજરાતી`.
- Vietnamese analyzer output now folds diacritics (e.g., "Việt Nam" -> "viet nam").
- CI now runs JVM/Android/iOS/Linux tests and coverage for `analysis:extra`.
- Publish workflow now includes `analysis:extra` artifacts.

## Fixed
- Alignment of analyzer/filter test expectations with new normalization and stemming behavior for Gujarati and Vietnamese.

## Notes
- macOS native test runs for Vietnamese previously timed out in MCP; rerun if needed in release validation.
