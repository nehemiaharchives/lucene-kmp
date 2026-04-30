package org.gnit.lucenekmp.jdkport

import okio.IOException

/**
 * The Character Encoding is not supported.
 */
@Ported(from = "java.io.UnsupportedEncodingException")
class UnsupportedEncodingException(message: String? = null) : IOException(message)
