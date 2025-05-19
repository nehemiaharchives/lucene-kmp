package org.gnit.lucenekmp.jdkport

class UTF_16BE: Unicode("UTF-16BE", StandardCharsets.aliases_UTF_16BE()) {

    override fun newDecoder(): CharsetDecoder {
        return Decoder(this)
    }

    override fun newEncoder(): CharsetEncoder {
        return Encoder(this)
    }

    private class Decoder(cs: Charset) : UnicodeDecoder(cs, BIG)

    class Encoder(cs: Charset) : UnicodeEncoder(cs, UnicodeEncoder.BIG, false)
}
