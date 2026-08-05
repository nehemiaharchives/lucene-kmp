# androidx.test references these compile-time-only annotations. They are not needed at runtime.
-dontwarn com.google.errorprone.annotations.**

# The instrumentation APK runs in its own process and therefore needs its Kotlin runtime.
# This does not affect optimization of the release APK under test.
-keep class kotlin.** { *; }
