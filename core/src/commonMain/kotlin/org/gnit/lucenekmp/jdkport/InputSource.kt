package org.gnit.lucenekmp.jdkport

import okio.IOException

/**
 * A single input source for an XML entity.
 *
 *
 * This class allows a SAX application to encapsulate information
 * about an input source in a single object, which may include
 * a public identifier, a system identifier, a byte stream (possibly
 * with a specified encoding), and/or a character stream.
 *
 *
 * There are two places that the application can deliver an
 * input source to the parser: as the argument to the Parser.parse
 * method, or as the return value of the EntityResolver.resolveEntity
 * method.
 *
 *
 * The SAX parser will use the InputSource object to determine how
 * to read XML input.  If there is a character stream available, the
 * parser will read that stream directly, disregarding any text
 * encoding declaration found in that stream.
 * If there is no character stream, but there is
 * a byte stream, the parser will use that byte stream, using the
 * encoding specified in the InputSource or else (if no encoding is
 * specified) autodetecting the character encoding using an algorithm
 * such as the one in the XML specification.  If neither a character
 * stream nor a
 * byte stream is available, the parser will attempt to open a URI
 * connection to the resource identified by the system
 * identifier.
 *
 *
 * An InputSource object belongs to the application: the SAX parser
 * shall never modify it in any way (it may modify a copy if
 * necessary).  However, standard processing of both byte and
 * character streams is to close them on as part of end-of-parse cleanup,
 * so applications should not attempt to re-use such streams after they
 * have been handed to a parser.
 *
 * @since 1.4, SAX 1.0
 * @author David Megginson
 * @see org.xml.sax.XMLReader.parse
 * @see org.xml.sax.EntityResolver.resolveEntity
 *
 * @see InputStream
 *
 * @see Reader
 */
@Ported(from = "org.xml.sax.InputSource")
open class InputSource {
    /**
     * Zero-argument default constructor.
     *
     * @see .setPublicId
     *
     * @see .setSystemId
     *
     * @see .setByteStream
     *
     * @see .setCharacterStream
     *
     * @see .setEncoding
     */
    constructor()


    /**
     * Create a new input source with a system identifier.
     *
     *
     * Applications may use setPublicId to include a
     * public identifier as well, or setEncoding to specify
     * the character encoding, if known.
     *
     *
     * If the system identifier is a URL, it must be fully
     * resolved (it may not be a relative URL).
     *
     * @param systemId The system identifier (URI).
     * @see .setPublicId
     *
     * @see .setSystemId
     *
     * @see .setByteStream
     *
     * @see .setEncoding
     *
     * @see .setCharacterStream
     */
    constructor(systemId: String?) {
        this.systemId = systemId
    }


    /**
     * Create a new input source with a byte stream.
     *
     *
     * Application writers should use setSystemId() to provide a base
     * for resolving relative URIs, may use setPublicId to include a
     * public identifier, and may use setEncoding to specify the object's
     * character encoding.
     *
     * @param byteStream The raw byte stream containing the document.
     * @see .setPublicId
     *
     * @see .setSystemId
     *
     * @see .setEncoding
     *
     * @see .setByteStream
     *
     * @see .setCharacterStream
     */
    constructor(byteStream: InputStream?) {
        this.byteStream = byteStream
    }


    /**
     * Create a new input source with a character stream.
     *
     *
     * Application writers should use setSystemId() to provide a base
     * for resolving relative URIs, and may use setPublicId to include a
     * public identifier.
     *
     *
     * The character stream shall not include a byte order mark.
     *
     * @param characterStream the character stream
     * @see .setPublicId
     *
     * @see .setSystemId
     *
     * @see .setByteStream
     *
     * @see .setCharacterStream
     */
    constructor(characterStream: Reader?) {
        setCharacterStream(characterStream)
    }


    /**
     * Set the byte stream for this input source.
     *
     *
     * The SAX parser will ignore this if there is also a character
     * stream specified, but it will use a byte stream in preference
     * to opening a URI connection itself.
     *
     *
     * If the application knows the character encoding of the
     * byte stream, it should set it with the setEncoding method.
     *
     * @param byteStream A byte stream containing an XML document or
     * other entity.
     * @see .setEncoding
     *
     * @see .getByteStream
     *
     * @see .getEncoding
     *
     * @see InputStream
     */
    /*private fun setByteStream(byteStream: InputStream?) {
        this.byteStream = byteStream
    }*/


    /**
     * Get the byte stream for this input source.
     *
     *
     * The getEncoding method will return the character
     * encoding for this byte stream, or null if unknown.
     *
     * @return The byte stream, or null if none was supplied.
     * @see .getEncoding
     *
     * @see .setByteStream
     */
    /*fun getByteStream(): InputStream? {
        return byteStream
    }*/


    /**
     * Set the character stream for this input source.
     *
     *
     * If there is a character stream specified, the SAX parser
     * will ignore any byte stream and will not attempt to open
     * a URI connection to the system identifier.
     *
     * @param characterStream The character stream containing the
     * XML document or other entity.
     * @see .getCharacterStream
     *
     * @see Reader
     */
    open fun setCharacterStream(characterStream: Reader?) {
        this.characterStream = characterStream
    }


    /**
     * Get the character stream for this input source.
     *
     * @return The character stream, or null if none was supplied.
     * @see .setCharacterStream
     */
    open fun getCharacterStream(): Reader? {
        return characterStream
    }

    val isEmpty: Boolean
        /**
         * Indicates whether the `InputSource` object is empty. Empty is
         * defined as follows:
         *
         *  * All of the input sources, including the public identifier, system
         * identifier, byte stream, and character stream, are `null`.
         *
         *  * The public identifier and system identifier are  `null`, and
         * byte and character stream are either  `null` or contain no byte
         * or character.
         *
         *
         * Note that this method will reset the byte stream if it is provided, or
         * the character stream if the byte stream is not provided.
         *
         *
         *
         *
         * In case of error while checking the byte or character stream, the method
         * will return false to allow the XML processor to handle the error.
         *
         * @return true if the `InputSource` object is empty, false otherwise
         */
        get() = (publicId == null && systemId == null && this.isStreamEmpty)

    private val isStreamEmpty: Boolean
        get() {
            val empty = true
            try {
                if (byteStream != null) {
                    byteStream!!.reset()
                    val bytesRead: Int = byteStream!!.available()
                    if (bytesRead > 0) {
                        return false
                    }
                }

                if (characterStream != null) {
                    characterStream!!.reset()
                    val c: Int = characterStream!!.read()
                    characterStream!!.reset()
                    if (c != -1) {
                        return false
                    }
                }
            } catch (ex: IOException) {
                //in case of error, return false
                return false
            }

            return empty
        }
    /**
     * Get the public identifier for this input source.
     *
     * @return The public identifier, or null if none was supplied.
     * @see .setPublicId
     */
    /**
     * Set the public identifier for this input source.
     *
     *
     * The public identifier is always optional: if the application
     * writer includes one, it will be provided as part of the
     * location information.
     *
     * @param publicId The public identifier as a string.
     * @see .getPublicId
     *
     * @see org.xml.sax.Locator.getPublicId
     *
     * @see org.xml.sax.SAXParseException.getPublicId
     */
    /**///////////////////////////////////////////////////////////////// */ // Internal state.
    /**///////////////////////////////////////////////////////////////// */
    var publicId: String? = null
    /**
     * Get the system identifier for this input source.
     *
     *
     * The getEncoding method will return the character encoding
     * of the object pointed to, or null if unknown.
     *
     *
     * If the system ID is a URL, it will be fully resolved.
     *
     * @return The system identifier, or null if none was supplied.
     * @see .setSystemId
     *
     * @see .getEncoding
     */
    /**
     * Set the system identifier for this input source.
     *
     *
     * The system identifier is optional if there is a byte stream
     * or a character stream, but it is still useful to provide one,
     * since the application can use it to resolve relative URIs
     * and can include it in error messages and warnings (the parser
     * will attempt to open a connection to the URI only if
     * there is no byte stream or character stream specified).
     *
     *
     * If the application knows the character encoding of the
     * object pointed to by the system identifier, it can register
     * the encoding using the setEncoding method.
     *
     *
     * If the system identifier is a URL, it must be fully
     * resolved (it may not be a relative URL).
     *
     * @param systemId The system identifier as a string.
     * @see .setEncoding
     *
     * @see .getSystemId
     *
     * @see org.xml.sax.Locator.getSystemId
     *
     * @see org.xml.sax.SAXParseException.getSystemId
     */
    var systemId: String? = null
    var byteStream: InputStream? = null
    /**
     * Get the character encoding for a byte stream or URI.
     * This value will be ignored when the application provides a
     * character stream.
     *
     * @return The encoding, or null if none was supplied.
     * @see .setByteStream
     *
     * @see .getSystemId
     *
     * @see .getByteStream
     */
    /**
     * Set the character encoding, if known.
     *
     *
     * The encoding must be a string acceptable for an
     * XML encoding declaration (see section 4.3.3 of the XML 1.0
     * recommendation).
     *
     *
     * This method has no effect when the application provides a
     * character stream.
     *
     * @param encoding A string describing the character encoding.
     * @see .setSystemId
     *
     * @see .setByteStream
     *
     * @see .getEncoding
     */
    var encoding: String? = null
    private var characterStream: Reader? = null
} // end of InputSource.java


