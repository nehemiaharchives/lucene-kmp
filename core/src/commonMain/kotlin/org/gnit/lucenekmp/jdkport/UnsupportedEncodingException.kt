package org.gnit.lucenekmp.jdkport

import okio.IOException

/**
 * The Character Encoding is not supported.
 */
class UnsupportedEncodingException(message: String? = null) : IOException(message)
