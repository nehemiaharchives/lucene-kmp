---
name: speedup_native
description: Diagnose and fix Kotlin/Native performance bottlenecks with reproducible tests, lowest-level profiling, and minimal expect/actual optimization patches.
---

## Purpose
Use this skill when a test or code path is significantly slower on Kotlin/Native than JVM and you need a reliable, repeatable way to find and fix the lowest-level bottleneck.

## Scope
- Kotlin Multiplatform projects with JVM + Native targets.
- Works best when one target is clearly slower (for example, linuxX64Test vs jvmTest).
- Focuses on minimal-risk optimizations that preserve behavior.

## Core Principles
1. Reproduce first, optimize second.
2. Measure with monotonic clocks, not assumptions.
3. Always isolate the lowest-level slow step before patching.
4. Keep patches minimal and reversible.
5. Prefer compile/test truth over IDE false negatives for expect/actual wiring.

## End-to-End Workflow

### 1. Reproduce with smallest failing/slow test
- Run exactly one test method that exhibits slowness.
- Keep both target runs comparable (same method, same data).

### 2. Add phase-level timing in test (coarse)
- Use `TimeSource.Monotonic.markNow()`.
- Add logs for major phases only first.
- Include total elapsed and per-phase elapsed.

### 3. Compare JVM vs Native and pick worst phase
- Identify the phase with the largest native/JVM ratio.
- Ignore small differences; chase the dominant hotspot.

### 4. Add substep timing inside worst phase (fine)
- Split compound calls into substeps (`a().b()` -> time `a` and `b` separately).
- Iterate until one substep clearly dominates native time.

### 5. Move instrumentation into production hot function (lowest level)
- Add temporary counters around internal branches/subcalls in the suspected function.
- Confirm the exact low-level operation causing native overhead.

### 6. Add dedicated reproducible performance test
- Create a small deterministic perf test in relevant test class.
- Keep correctness assertions plus one timing output line.
- Ensure it exercises the exact hot path repeatedly.

### 7. Implement minimal expect/actual optimization
- Keep common API identical.
- Introduce `expect` in common only where needed.
- Add minimal `actual` implementations:
  - JVM path: preserve existing behavior (baseline path).
  - Native path: optimized implementation for confirmed bottleneck.
- Prefer smallest patch that changes only the hot operation.

### 8. Validate wiring and platform compilation
- Open changed files in IDE and inspect file problems.
- If IDE expect/actual diagnostics conflict, run Gradle compile for target source sets.
- Treat compile/test success as source of truth.

### 9. Measure before/after on both targets
- Run reproducible perf test on JVM + Native.
- Re-run original slow test on JVM + Native.
- Report:
  - total elapsed
  - hotspot substep time
  - improvement/regression ratio

### 10. Keep or revert based on measurements
- If native improves without correctness regressions, keep patch.
- If native regresses, revert immediately.
- Do not keep speculative optimizations.

## Timeout and Run Strategy
- For strict timeout requests, compile first, then run timed tests.
- Why: avoid wasting timeout budget on compilation/linking.
- Use separate commands:
  1. compile tasks (no timeout)
  2. test tasks (with timeout)

## Instrumentation Guidelines
- Use stable log keys to simplify diffing:
  - `phase=...`, `substep=...`, `elapsedMs=...`, `Ns=...`
- Keep instrumentation local and temporary.
- Remove debug/perf logs after conclusion unless user asks to keep them.

## expect/actual Best Practices
- Add `expect` only for proven hotspot APIs.
- Keep signatures simple and explicit.
- Avoid creating many platform files unless necessary.
- If project uses shared intermediate source sets (for example `jvmAndroidMain`, `nativeMain`), place `actual` there when supported by build graph.
- If IDE reports false negatives, verify with Gradle compile/test.

## Safety Checklist Before Finalizing
- [ ] Single-test reproducibility confirmed on JVM + Native.
- [ ] Lowest bottleneck isolated with measured evidence.
- [ ] Minimal patch only changes bottleneck path.
- [ ] Compile passes for impacted targets.
- [ ] Functional tests pass.
- [ ] Before/after metrics captured.
- [ ] Temporary profiling logs removed (unless requested).

## Output Template for Agents
When done, report in this order:
1. Bottleneck location (function + substep).
2. Evidence (JVM vs Native numbers).
3. Patch summary (expect/actual + call-site changes).
4. Validation results (compile/tests).
5. Net performance change and recommendation (keep/revert).
