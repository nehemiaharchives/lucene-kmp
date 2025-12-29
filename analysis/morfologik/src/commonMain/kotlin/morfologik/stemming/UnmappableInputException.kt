package morfologik.stemming

import org.gnit.lucenekmp.jdkport.CharacterCodingException

/**
 * Thrown when some input cannot be mapped using the declared charset (bytes
 * to characters or the other way around).
 */
class UnmappableInputException(message: String, cause: CharacterCodingException) : Exception(message, cause)
