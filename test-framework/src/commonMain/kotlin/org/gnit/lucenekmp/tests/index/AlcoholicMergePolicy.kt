package org.gnit.lucenekmp.tests.index

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.IOException
import org.gnit.lucenekmp.index.LogMergePolicy
import org.gnit.lucenekmp.index.SegmentCommitInfo
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.random.Random
import kotlin.time.Instant

/**
 * Merge policy for testing, it is like an alcoholic. It drinks (merges) at night, and randomly
 * decides what to drink. During the daytime it sleeps.
 *
 *
 * if tests pass with this, then they are likely to pass with any bizarro merge policy users
 * might write.
 *
 *
 * It is a fine bottle of champagne (Ordered by Martijn).
 */
class AlcoholicMergePolicy(private val tz: TimeZone, private val random: Random) : LogMergePolicy() {

    /*private val calendar: java.util.Calendar
    this.calendar = java.util.GregorianCalendar(tz, java.util.Locale.ROOT)
    calendar.setTimeInMillis(TestUtil.nextLong(random,0,Long.MAX_VALUE))*/
    private val nowMillis: Long = TestUtil.nextLong(random, 0, Long.MAX_VALUE)

    init {
        maxMergeSize = TestUtil.nextInt(
            random,
            1024 * 1024,
            Int.MAX_VALUE
        ).toLong()
    }

    @Throws(IOException::class)  // @BlackMagic(level=Voodoo);
    override fun size(
        info: SegmentCommitInfo,
        mergeContext: MergeContext
    ): Long {
        //val hourOfDay: Int = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val hourOfDay: Int = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(tz).hour

        if (hourOfDay !in 6..20 ||  // it's 5 o'clock somewhere
            random.nextInt(23) == 5
        ) {
            val values: Array<Drink> = Drink.entries.toTypedArray()
            // pick a random drink during the day
            return values[random.nextInt(values.size)].drunkFactor * info.sizeInBytes()
        }

        return info.sizeInBytes()
    }

    private enum class Drink(var drunkFactor: Long) {
        Beer(15),
        Wine(17),
        Champagne(21),
        WhiteRussian(22),
        SingleMalt(30)
    }
}
