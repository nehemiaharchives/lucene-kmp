# lucene-kmp 10.2.0-alpha05 Release Notes

**Version:** `10.2.0-alpha05`

**Baseline:** tag `10.2.0-alpha04` (commit `beba6eca`)

**Release tag:** `10.2.0-alpha05` (commit `TBD`)

---

## Highlights

- **Publish/CI reliability**: fixed missing task dependencies for sources JAR generation across modules.
- **Dictionary generation performance**: speedups for SmartCN and Morfologik generated sources.
- **Artifact naming**: corrected analysis module artifact IDs to include the `analysis-` prefix.
- **Vector scoring stability**: Lucene99 scalar quantized vector scorer tests now pass.

---

## Build / CI / Publishing

- Added explicit task dependencies so `sourcesJar` waits for generated Kotlin sources.
  - Modules: `core`, `analysis:kuromoji`, `analysis:morfologik`, `analysis:nori`, `analysis:smartcn`.
- Improved reliability of publish pipeline when generated sources are involved.

---

## Analysis modules

- **SmartCN**
  - Faster dictionary generation.
- **Morfologik**
  - Faster dictionary generation.
- **Analysis artifact IDs**
  - Fixed naming to align with expected `lucene-kmp-analysis-*` pattern.

---

## Core / Vectors

- Stabilized Lucene99 scalar quantized vector scoring; unit tests passing for scalar quantized scoring.

---

## Tooling / Docs

- `PUBLISH.md` updated with additional guidance for CI/publish troubleshooting.
- Progress script path defaults adjusted.

