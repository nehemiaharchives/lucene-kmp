# Release Notes 10.2.0-alpha08

## Changes since 10.2.0-alpha07

- Added `TestSort` and its supporting classes (doc values fields, sort/values sources, vector similarity values sources, and numeric comparators).
- Fixed multiple `TestSort` cases (float, float ghost, string reverse/ghost, equals) and related index/search behavior.
- Stabilized merge scheduling and merge thread tracking in `ConcurrentMergeScheduler`.
- Reworked `ReentrantLock` semantics (thread ownership, reentrancy, and monitor errors) and expanded `ReentrantLockTest`.
- Implemented/updated KNN vectors format lookup and PerField KNN vectors handling; added Lucene99 vector reader/format support and extra logging for vector scoring.
- Fixed Android device tests crashing due to logging dependencies by:
  - Switching Android to `kotlin-logging-android` and adding `slf4j-api`.
  - Keeping `logback` on JVM only.
  - Enabling Android native logging via `configureTestLogging()`.
- Fixed Android packaging conflicts by excluding `META-INF/INDEX.LIST`.
- Renamed JDK port test methods to avoid DEX invalid simple names (spaces in method names) in `CharacterDataLatin1Test`.
- Centralized `configureTestLogging()` expect/actuals across platforms to avoid missing/duplicate actuals.
- Updated progress tracking docs (`PROGRESS.md`, `PROGRESS2.md`).
