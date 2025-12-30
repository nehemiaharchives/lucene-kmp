package org.gnit.lucenekmp.analysis.ko.dict

import org.gnit.lucenekmp.analysis.morph.BinaryDictionaryWriter
import org.gnit.lucenekmp.analysis.morph.CharacterDefinitionWriter
import org.gnit.lucenekmp.jdkport.Ported
import okio.Path

@Ported(from = "org.apache.lucene.analysis.ko.dict.UnknownDictionaryWriter")
internal class UnknownDictionaryWriter(size: Int) :
    BinaryDictionaryWriter<UnknownDictionary>(UnknownDictionary::class, TokenInfoDictionaryEntryWriter(size)) {

    private val characterDefinition = CharacterDefinitionWriter(
        CharacterDefinition::class,
        CharacterDefinition.DEFAULT,
        CharacterDefinition.CLASS_COUNT,
        CharacterDefinition::lookupCharacterClass
    )

    override fun put(entry: Array<String>): Int {
        val wordId = entryWriter.currentPosition()
        val result = super.put(entry)
        val characterId = CharacterDefinition.lookupCharacterClass(entry[0])
        addMapping(characterId.toInt(), wordId)
        return result
    }

    /**
     * Put mapping from unicode code point to character class.
     */
    fun putCharacterCategory(codePoint: Int, characterClassName: String) {
        characterDefinition.putCharacterCategory(codePoint, characterClassName)
    }

    fun putInvokeDefinition(characterClassName: String, invoke: Int, group: Int, length: Int) {
        characterDefinition.putInvokeDefinition(characterClassName, invoke, group, length)
    }

    override fun write(baseDir: Path) {
        super.write(
            baseDir,
            DictionaryConstants.TARGETMAP_HEADER,
            DictionaryConstants.POSDICT_HEADER,
            DictionaryConstants.DICT_HEADER,
            DictionaryConstants.VERSION
        )
        characterDefinition.write(baseDir, DictionaryConstants.CHARDEF_HEADER, DictionaryConstants.VERSION)
    }
}
