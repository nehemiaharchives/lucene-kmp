# lucene-kmp 10.2.0-alpha04 Release Notes

**Version:** `10.2.0-alpha04`

**Baseline:** commit `02035125` ("10.2.0-alpha03")

**Release tag:** `10.2.0-alpha04` (commit `c51c2c7c`)

> Note: The `lucene-kmp` repo does not currently have a tag `10.2.0-alpha03`; the closest reliable baseline is the commit named `10.2.0-alpha03` (`02035125`), which updates `gradle.properties`.

---

## Highlights

This alpha is a large milestone focused on:

- **Analysis module breadth** (many analyzers, filters, factories, and tests)
- **Kotlin/Native compatibility** (avoid unsupported patterns; stabilize tests)
- **CI reliability and performance** (timeouts, coverage splits, OOM mitigations)

---

## Added / Ported analyzers & analysis components

Major expansion of analyzers and supporting filters/tokenizers/factories, including:

- **Japanese (Kuromoji)** analyzer + dictionary tooling and extensive tests
  - `analysis/kuromoji`, `analysis/ja/*`
- **Korean (Nori)** analyzer + dictionary tooling and tests
  - `analysis/nori`, `analysis/ko/*`
- **Smart Chinese (smartcn)** analyzer
  - dictionary/map data is now self-contained in this repo
  - `analysis/smartcn/*`
- Additional analyzers and related components with tests:
  - English, German, Spanish, Portuguese, Russian, Dutch, Italian, Swedish
  - Indonesian, Thai, Hindi (+ IndicNormalizer), Bengali, Telugu, Tamil, Nepali
  - Morfologik analyzer + Ukrainian morfologik support

---

## Kotlin/Native improvements (compat + stability)

- Refactoring driven by Kotlin/Native constraints (notably around **resource handling**).
- Multiple test fixes and implementations to get previously failing areas passing on Native targets.
- Increased iOS simulator timeouts to reduce CI flakiness.

---

## JDK porting (jdkport) and text boundary work

Significant work in `org.gnit.lucenekmp.jdkport` to support analysis and ICU-like behavior in common code, including:

- BreakIterator ecosystem (providers, implementations, dictionary-based break iterator, rule-based break iterator)
- Additional JDK-like utilities/classes to unblock ports (e.g., latch behavior, iterators, readers/writers, charset-related pieces, etc.)
- Added/updated many corresponding unit tests

---

## Test framework and utilities

- Improved test infrastructure, including coroutine-based thread control in analysis tests.
- Many individual test ports/fixes (tokenizers, filters, IO helpers) to keep parity and achieve green runs across environments.

---

## CI / build / publish improvements

Focused updates to make CI more reliable and manageable:

- CI matrix readability improvements
- More granular module test/coverage execution
- Kover coverage adjustments (including `:analysis:smartcn`)
- Attempts to mitigate CI OOMs
  - reducing Android-related work in coverage jobs
  - experimenting with toggles (e.g., disabling K2 in CI while investigating)
- `publish.yml` updates (including Android SDK availability where needed)
- Fix for CI failure related to malformed gradle task args (`Task ' -x' not found`)

---

## Notable changes in repo contents

- Large amount of analyzer dictionary and mapping data is now **self-contained in the repo**, enabling repeatable builds without external resource fetching.
  - Chinese dictionaries/maps
  - Kuromoji/Nori dictionaries
  - Morfologik dictionaries
  - generated break iterator data

---

## What changed (index from commit subjects)

A compressed view of what landed after alpha03 baseline (`02035125`) up to alpha04 (`beba6eca`):

- Analyzer ports reaching “passes tests” status:
  - English → German → Spanish → Portuguese → Russian → Dutch → Italian → Swedish
  - Indonesian → Thai → Hindi/Indic → Bengali → Telugu → Tamil → Nepali
  - Morfologik/Ukrainian → SmartCN → Korean → Japanese
- Heavy CI investment:
  - timeouts
  - matrix readability
  - per-module testing/coverage
  - OOM mitigation
- Large text boundary work:
  - BreakIterator / RuleBasedBreakIterator / DictionaryBasedBreakIterator
  - generated data plumbing
- Stabilization fixes:
  - StandardTokenizerImpl loop bug
  - CharTermAttributeImpl buffer copy bug
  - assorted test regressions fixed

