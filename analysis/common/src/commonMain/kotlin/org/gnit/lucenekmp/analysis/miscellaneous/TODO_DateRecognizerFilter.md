# TODO: Port `DateRecognizerFilter` to lucene-kmp commonMain

## Source Java class

Java Lucene class:

```java
package org.apache.lucene.analysis.miscellaneous;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Locale;
import org.apache.lucene.analysis.FilteringTokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/** Filters all tokens that cannot be parsed to a date, using the provided {@link DateFormat}. */
public class DateRecognizerFilter extends FilteringTokenFilter {

  public static final String DATE_TYPE = "date";

  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
  private final DateFormat dateFormat;

  /**
   * Uses {@link DateFormat#DEFAULT} and {@link Locale#ENGLISH} to create a {@link DateFormat}
   * instance.
   */
  public DateRecognizerFilter(TokenStream input) {
    this(input, null);
  }

  public DateRecognizerFilter(TokenStream input, DateFormat dateFormat) {
    super(input);
    this.dateFormat =
        dateFormat != null
            ? dateFormat
            : DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.ENGLISH);
  }

  @Override
  public boolean accept() {
    try {
      // We don't care about the date, just that the term can be parsed to one.
      dateFormat.parse(termAtt.toString());
      return true;
    } catch (
        @SuppressWarnings("unused")
        ParseException e) {
      // This term is not a date.
    }

    return false;
  }
}
```

## Key porting decision

Do **not** try to port `java.text.DateFormat` directly into Kotlin common code.

This class does not need full date formatting behavior. It only needs to answer one question:

```text
Can this token be parsed or recognized as a date?
```

So the KMP/commonMain port should replace `DateFormat` with a small date-recognition abstraction.

## Recommended KMP design

Create a small functional interface:

```kotlin
fun interface DateRecognizer {
    fun isDate(text: String): Boolean
}
```

Then use this in `DateRecognizerFilter` instead of `DateFormat`.

## Target Kotlin class shape

Package should follow lucene-kmp conventions. If this project uses `org.gnit.lucenekmp`, use:

```kotlin
package org.gnit.lucenekmp.analysis.miscellaneous
```

Suggested implementation:

```kotlin
package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.FilteringTokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/**
 * Filters all tokens that cannot be recognized as a date.
 */
class DateRecognizerFilter(
    input: TokenStream,
    dateRecognizer: DateRecognizer? = null,
) : FilteringTokenFilter(input) {

    companion object {
        const val DATE_TYPE: String = "date"
    }

    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val dateRecognizer: DateRecognizer =
        dateRecognizer ?: EnglishDefaultDateRecognizer

    override fun accept(): Boolean {
        return dateRecognizer.isDate(termAtt.toString())
    }
}
```

This mirrors the Java constructor behavior:

```java
this(input, null)
dateFormat != null ? dateFormat : defaultDateFormat
```

but avoids pretending that `java.text.DateFormat` exists in commonMain.

## Default recognizer strategy

Java uses:

```java
DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.ENGLISH)
```

That is a locale-sensitive English date parser. Kotlin commonMain does not have a full equivalent.

Use `kotlinx-datetime` formats instead. The goal is to support reasonable English default date forms, not every JDK/provider-specific parsing quirk.

Suggested default recognizer:

```kotlin
package org.gnit.lucenekmp.analysis.miscellaneous

import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.format.chars

fun interface DateRecognizer {
    fun isDate(text: String): Boolean
}

object EnglishDefaultDateRecognizer : DateRecognizer {

    private val formats: List<DateTimeFormat<LocalDate>> = listOf(
        LocalDate.Formats.ISO,

        LocalDate.Format {
            monthName(MonthNames.ENGLISH_ABBREVIATED)
            char(' ')
            day()
            chars(", ")
            year()
        },

        LocalDate.Format {
            monthName(MonthNames.ENGLISH_FULL)
            char(' ')
            day()
            chars(", ")
            year()
        }
    )

    override fun isDate(text: String): Boolean {
        val s = text.trim()

        for (format in formats) {
            try {
                LocalDate.parse(s, format)
                return true
            } catch (_: IllegalArgumentException) {
                // Try next format.
            }
        }

        return false
    }
}
```

Expected examples accepted by the default recognizer:

```text
2026-05-31
May 31, 2026
May 1, 2026
Jan 12, 2026
January 12, 2026
```

## Optional additional recognizers

If Lucene tests require slash-style US dates, add a separate recognizer instead of bloating the default one.

Example:

```kotlin
object UsSlashDateRecognizer : DateRecognizer {

    private val formats: List<DateTimeFormat<LocalDate>> = listOf(
        LocalDate.Format {
            monthNumber()
            char('/')
            day()
            char('/')
            year()
        }
    )

    override fun isDate(text: String): Boolean {
        val s = text.trim()

        for (format in formats) {
            try {
                LocalDate.parse(s, format)
                return true
            } catch (_: IllegalArgumentException) {
                // Try next format.
            }
        }

        return false
    }
}
```

Then compose recognizers:

```kotlin
class CompositeDateRecognizer(
    private vararg val recognizers: DateRecognizer,
) : DateRecognizer {
    override fun isDate(text: String): Boolean {
        return recognizers.any { it.isDate(text) }
    }
}
```

Possible default if tests require US slash dates:

```kotlin
val DefaultDateRecognizer: DateRecognizer = CompositeDateRecognizer(
    EnglishDefaultDateRecognizer,
    UsSlashDateRecognizer,
)
```

But start simple. Add extra formats only when tests prove they are required.

## Important semantic note

The original Lucene Java class uses `DateFormat.parse(...)` only as a filter predicate.

It discards the parsed date:

```java
dateFormat.parse(termAtt.toString());
return true;
```

Therefore the KMP port should not expose or store parsed `LocalDate` unless another class needs it.

The correct commonMain abstraction is date recognition, not date formatting.

## Avoid this design

Avoid creating a fake KMP `DateFormat` class just for this filter.

Bad direction:

```kotlin
abstract class DateFormat {
    abstract fun parse(text: String): Date
    abstract fun format(date: Date): String
}
```

Reasons:

1. `DateRecognizerFilter` does not need formatting.
2. Java `DateFormat` is locale-heavy and mutable.
3. Java `DateFormat` behavior can vary by JDK/provider.
4. A fake `DateFormat` would become maintenance debt.
5. KMP commonMain has no real `Locale.ENGLISH DateFormat` equivalent.

## Testing strategy

Create tests that verify the filter accepts date-like tokens and rejects non-date tokens.

Suggested accepted tokens:

```text
2026-05-31
May 31, 2026
Jan 12, 2026
January 12, 2026
```

Suggested rejected tokens:

```text
Jesus
church
2026-99-99
May 99, 2026
not-a-date
```

If the existing Java Lucene tests expect tokens like `5/31/2026`, then either:

1. add `UsSlashDateRecognizer`, or
2. compose a broader default recognizer.

Do not add two-digit-year support unless a test specifically requires it. Two-digit-year parsing introduces ambiguity and can easily diverge from Java `DateFormat` behavior.

## Final implementation goal

The KMP class should express the real dependency:

```text
DateRecognizerFilter needs date recognition, not DateFormat.
```

Final expected files may be:

```text
core/src/commonMain/kotlin/org/gnit/lucenekmp/analysis/miscellaneous/DateRecognizerFilter.kt
core/src/commonMain/kotlin/org/gnit/lucenekmp/analysis/miscellaneous/DateRecognizer.kt
core/src/commonTest/kotlin/org/gnit/lucenekmp/analysis/miscellaneous/DateRecognizerFilterTest.kt
```

Adjust paths/packages to match the current lucene-kmp module layout.
