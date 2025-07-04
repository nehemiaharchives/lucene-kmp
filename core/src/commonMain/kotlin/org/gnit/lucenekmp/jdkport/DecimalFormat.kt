package org.gnit.lucenekmp.jdkport

/**
 * placeholder for java.text.DecimalFormat which is used in org.apache.lucene.util.RamUsageEstimator
 * org.gnit.lucenekmp.util.RamUsageEstimator does not use this but it is kept here for porting progress script to mark the class ported
 */
@Ported(from = "java.text.DecimalFormat")
object DecimalFormat
