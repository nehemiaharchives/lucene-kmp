package org.gnit.lucenekmp.codecs

import org.gnit.lucenekmp.codecs.blocktreeords.BlockTreeOrdsPostingsFormat
import org.gnit.lucenekmp.codecs.simpletext.SimpleTextPostingsFormat

/**
 * Registers postings formats provided by the codecs module.
 */
fun registerCodecsPostingsFormats() {
    PostingsFormat.registerPostingsFormat("BlockTreeOrds") { BlockTreeOrdsPostingsFormat() }
    PostingsFormat.registerPostingsFormat("SimpleText") { SimpleTextPostingsFormat() }
}
