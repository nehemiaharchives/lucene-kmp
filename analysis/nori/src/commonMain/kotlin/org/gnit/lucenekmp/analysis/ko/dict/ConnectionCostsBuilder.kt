package org.gnit.lucenekmp.analysis.ko.dict

import org.gnit.lucenekmp.analysis.morph.ConnectionCostsWriter
import org.gnit.lucenekmp.jdkport.BufferedReader
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.StandardCharsets
import okio.IOException
import okio.Path

internal object ConnectionCostsBuilder {
    @Throws(IOException::class)
    fun build(path: Path): ConnectionCostsWriter<ConnectionCosts> {
        Files.newBufferedReader(path, StandardCharsets.US_ASCII).use { br ->
            val line = br.readLine() ?: throw IllegalArgumentException("Empty matrix file")
            val dimensions = line.split(Regex("\\s+"))
            val forwardSize = dimensions[0].toInt()
            val backwardSize = dimensions[1].toInt()
            val costs = ConnectionCostsWriter(ConnectionCosts::class, forwardSize, backwardSize)
            var entry: String?
            while (true) {
                entry = br.readLine() ?: break
                val fields = entry.split(Regex("\\s+"))
                val forwardId = fields[0].toInt()
                val backwardId = fields[1].toInt()
                val cost = fields[2].toInt()
                costs.add(forwardId, backwardId, cost)
            }
            return costs
        }
    }
}
