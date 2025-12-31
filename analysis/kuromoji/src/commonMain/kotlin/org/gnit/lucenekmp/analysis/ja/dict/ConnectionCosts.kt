package org.gnit.lucenekmp.analysis.ja.dict

import okio.Path
import org.gnit.lucenekmp.jdkport.ByteArrayInputStream
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.util.IOSupplier

/** n-gram connection cost data */
class ConnectionCosts private constructor(connectionCostResource: IOSupplier<InputStream>) :
    org.gnit.lucenekmp.analysis.morph.ConnectionCosts(
        connectionCostResource,
        DictionaryConstants.CONN_COSTS_HEADER,
        DictionaryConstants.VERSION
    ) {

    companion object {
        const val FILENAME_SUFFIX: String = ".dat"

        fun getInstance(): ConnectionCosts = SingletonHolder.INSTANCE

        private object SingletonHolder {
            val INSTANCE: ConnectionCosts = ConnectionCosts { getClassResource() }
        }

        private fun getClassResource(): InputStream {
            return ByteArrayInputStream(JapaneseDictionaryData.connectionCosts)
        }
    }

    /**
     * Create a [ConnectionCosts] from an external resource path.
     */
    constructor(connectionCostFile: Path) : this({ org.gnit.lucenekmp.jdkport.Files.newInputStream(connectionCostFile) })
}
