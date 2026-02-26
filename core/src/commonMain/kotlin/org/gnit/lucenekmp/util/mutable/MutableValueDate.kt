package org.gnit.lucenekmp.util.mutable

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * [MutableValue] implementation of type [LocalDateTime].
 *
 * @see MutableValueLong
 */
class MutableValueDate : MutableValueLong() {
    override fun toObject(): Any? {
        return if (exists) {
            Instant.fromEpochMilliseconds(value).toLocalDateTime(TimeZone.UTC)
        } else {
            null
        }
    }

    override fun duplicate(): MutableValue {
        val v = MutableValueDate()
        v.value = this.value
        v.exists = this.exists
        return v
    }
}
