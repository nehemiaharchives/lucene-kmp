package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.InetAddressPoint
import org.gnit.lucenekmp.document.InetAddressRange
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.InetAddress
import org.gnit.lucenekmp.tests.search.BaseRangeFieldQueryTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.SuppressCodecs
import kotlin.test.Test

/** Random testing for [InetAddressRange] */
@SuppressCodecs("Direct")
class TestInetAddressRangeQueries : BaseRangeFieldQueryTestCase() {
    companion object {
        private const val FIELD_NAME = "ipRangeField"
    }

    override fun nextRange(dimensions: Int): Range {
        val min = nextInetaddress()
        val bMin = InetAddressPoint.encode(min)
        val max = nextInetaddress()
        val bMax = InetAddressPoint.encode(max)
        return if (Arrays.compareUnsigned(bMin, 0, bMin.size, bMax, 0, bMin.size) > 0) {
            IpRange(max, min)
        } else {
            IpRange(min, max)
        }
    }

    /** return random IPv4 or IPv6 address */
    private fun nextInetaddress(): InetAddress {
        val b = if (random().nextBoolean()) ByteArray(4) else ByteArray(16)
        return when (random().nextInt(5)) {
            0 -> InetAddress.getByAddress(b)
            1 -> {
                Arrays.fill(b, 0xFF.toByte())
                InetAddress.getByAddress(b)
            }
            2 -> {
                Arrays.fill(b, 42.toByte())
                InetAddress.getByAddress(b)
            }
            else -> {
                random().nextBytes(b)
                InetAddress.getByAddress(b)
            }
        }
    }

    override fun newRangeField(box: Range): InetAddressRange {
        box as IpRange
        return InetAddressRange(FIELD_NAME, box.minAddress, box.maxAddress)
    }

    override fun newIntersectsQuery(box: Range): Query {
        box as IpRange
        return InetAddressRange.newIntersectsQuery(FIELD_NAME, box.minAddress, box.maxAddress)
    }

    override fun newContainsQuery(box: Range): Query {
        box as IpRange
        return InetAddressRange.newContainsQuery(FIELD_NAME, box.minAddress, box.maxAddress)
    }

    override fun newWithinQuery(box: Range): Query {
        box as IpRange
        return InetAddressRange.newWithinQuery(FIELD_NAME, box.minAddress, box.maxAddress)
    }

    override fun newCrossesQuery(box: Range): Query {
        box as IpRange
        return InetAddressRange.newCrossesQuery(FIELD_NAME, box.minAddress, box.maxAddress)
    }

    /** encapsulated IpRange for test validation */
    private class IpRange(min: InetAddress, max: InetAddress) : Range() {
        var minAddress: InetAddress = min
        var maxAddress: InetAddress = max
        var min: ByteArray = InetAddressPoint.encode(min)
        var max: ByteArray = InetAddressPoint.encode(max)

        override fun numDimensions(): Int {
            return 1
        }

        override fun getMin(dim: Int): InetAddress {
            return minAddress
        }

        override fun setMin(dim: Int, value: Any) {
            val v = value as InetAddress
            val e = InetAddressPoint.encode(v)

            if (Arrays.compareUnsigned(min, 0, e.size, e, 0, e.size) < 0) {
                max = e
                maxAddress = v
            } else {
                min = e
                minAddress = v
            }
        }

        override fun getMax(dim: Int): InetAddress {
            return maxAddress
        }

        override fun setMax(dim: Int, value: Any) {
            val v = value as InetAddress
            val e = InetAddressPoint.encode(v)

            if (Arrays.compareUnsigned(max, 0, e.size, e, 0, e.size) > 0) {
                min = e
                minAddress = v
            } else {
                max = e
                maxAddress = v
            }
        }

        override fun isEqual(other: Range): Boolean {
            other as IpRange
            return min.contentEquals(other.min) && max.contentEquals(other.max)
        }

        override fun isDisjoint(other: Range): Boolean {
            other as IpRange
            return Arrays.compareUnsigned(min, 0, min.size, other.max, 0, min.size) > 0 ||
                Arrays.compareUnsigned(max, 0, max.size, other.min, 0, max.size) < 0
        }

        override fun isWithin(other: Range): Boolean {
            other as IpRange
            return Arrays.compareUnsigned(min, 0, min.size, other.min, 0, min.size) >= 0 &&
                Arrays.compareUnsigned(max, 0, max.size, other.max, 0, max.size) <= 0
        }

        override fun contains(other: Range): Boolean {
            other as IpRange
            return Arrays.compareUnsigned(min, 0, min.size, other.min, 0, min.size) <= 0 &&
                Arrays.compareUnsigned(max, 0, max.size, other.max, 0, max.size) >= 0
        }

        override fun toString(): String {
            return "Box(${minAddress.getHostAddress()} TO ${maxAddress.getHostAddress()})"
        }
    }

    // Tests inherited from BaseRangeFieldQueryTestCase

    @Test
    override fun testRandomTiny() = super.testRandomTiny()

    @Test
    override fun testMultiValued() = super.testRandomMedium()

    @Test
    override fun testRandomMedium() = super.testMultiValued()

    @Test
    override fun testRandomBig() = super.testRandomBig()
}
