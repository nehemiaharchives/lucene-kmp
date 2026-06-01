package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.analysis.ar.ArabicNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.ar.ArabicStemFilterFactory
import org.gnit.lucenekmp.analysis.bn.BengaliNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.bn.BengaliStemFilterFactory
import org.gnit.lucenekmp.analysis.br.BrazilianStemFilterFactory
import org.gnit.lucenekmp.analysis.cjk.CJKBigramFilterFactory
import org.gnit.lucenekmp.analysis.cjk.CJKWidthCharFilterFactory
import org.gnit.lucenekmp.analysis.cjk.CJKWidthFilterFactory
import org.gnit.lucenekmp.analysis.ckb.SoraniNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.ckb.SoraniStemFilterFactory
import org.gnit.lucenekmp.analysis.charfilter.HTMLStripCharFilterFactory
import org.gnit.lucenekmp.analysis.charfilter.MappingCharFilterFactory
import org.gnit.lucenekmp.analysis.commongrams.CommonGramsFilterFactory
import org.gnit.lucenekmp.analysis.commongrams.CommonGramsQueryFilterFactory
import org.gnit.lucenekmp.analysis.compound.DictionaryCompoundWordTokenFilterFactory
import org.gnit.lucenekmp.analysis.compound.HyphenationCompoundWordTokenFilterFactory
import org.gnit.lucenekmp.analysis.core.DecimalDigitFilterFactory
import org.gnit.lucenekmp.analysis.core.LowerCaseFilterFactory
import org.gnit.lucenekmp.analysis.cz.CzechStemFilterFactory
import org.gnit.lucenekmp.analysis.en.EnglishMinimalStemFilterFactory
import org.gnit.lucenekmp.analysis.en.EnglishPossessiveFilterFactory
import org.gnit.lucenekmp.analysis.en.KStemFilterFactory
import org.gnit.lucenekmp.analysis.en.PorterStemFilterFactory
import org.gnit.lucenekmp.analysis.de.GermanLightStemFilterFactory
import org.gnit.lucenekmp.analysis.de.GermanMinimalStemFilterFactory
import org.gnit.lucenekmp.analysis.de.GermanNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.de.GermanStemFilterFactory
import org.gnit.lucenekmp.analysis.es.SpanishLightStemFilterFactory
import org.gnit.lucenekmp.analysis.es.SpanishPluralStemFilterFactory
import org.gnit.lucenekmp.analysis.el.GreekLowerCaseFilterFactory
import org.gnit.lucenekmp.analysis.el.GreekStemFilterFactory
import org.gnit.lucenekmp.analysis.email.UAX29URLEmailTokenizerFactory
import org.gnit.lucenekmp.analysis.fa.PersianCharFilterFactory
import org.gnit.lucenekmp.analysis.fa.PersianNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.fa.PersianStemFilterFactory
import org.gnit.lucenekmp.analysis.hi.HindiNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.hi.HindiStemFilterFactory
import org.gnit.lucenekmp.analysis.hu.HungarianLightStemFilterFactory
import org.gnit.lucenekmp.analysis.`in`.IndicNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.id.IndonesianStemFilterFactory
import org.gnit.lucenekmp.analysis.it.ItalianLightStemFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.CapitalizationFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.ConcatenateGraphFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.CodepointCountFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.DateRecognizerFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.DropIfFlaggedFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.ASCIIFoldingFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.FingerprintFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.FixBrokenOffsetsFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.HyphenatedWordsFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.KeepWordFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.KeywordMarkerFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.KeywordRepeatFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.LengthFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.LimitTokenCountFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.LimitTokenOffsetFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.LimitTokenPositionFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.ProtectedTermFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.RemoveDuplicatesTokenFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.ScandinavianFoldingFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.ScandinavianNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.StemmerOverrideFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.TrimFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.TruncateTokenFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.TypeAsSynonymFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.WordDelimiterFilterFactory
import org.gnit.lucenekmp.analysis.ngram.EdgeNGramFilterFactory
import org.gnit.lucenekmp.analysis.ngram.EdgeNGramTokenizerFactory
import org.gnit.lucenekmp.analysis.ngram.NGramFilterFactory
import org.gnit.lucenekmp.analysis.ngram.NGramTokenizerFactory
import org.gnit.lucenekmp.analysis.payloads.DelimitedPayloadTokenFilterFactory
import org.gnit.lucenekmp.analysis.payloads.NumericPayloadTokenFilterFactory
import org.gnit.lucenekmp.analysis.payloads.TokenOffsetPayloadTokenFilterFactory
import org.gnit.lucenekmp.analysis.payloads.TypeAsPayloadTokenFilterFactory
import org.gnit.lucenekmp.analysis.pattern.PatternTokenizerFactory
import org.gnit.lucenekmp.analysis.no.NorwegianLightStemFilterFactory
import org.gnit.lucenekmp.analysis.no.NorwegianMinimalStemFilterFactory
import org.gnit.lucenekmp.analysis.no.NorwegianNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.pt.PortugueseLightStemFilterFactory
import org.gnit.lucenekmp.analysis.pt.PortugueseMinimalStemFilterFactory
import org.gnit.lucenekmp.analysis.pt.PortugueseStemFilterFactory
import org.gnit.lucenekmp.analysis.ro.RomanianNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.reverse.ReverseStringFilterFactory
import org.gnit.lucenekmp.analysis.ru.RussianLightStemFilterFactory
import org.gnit.lucenekmp.analysis.shingle.FixedShingleFilterFactory
import org.gnit.lucenekmp.analysis.shingle.ShingleFilterFactory
import org.gnit.lucenekmp.analysis.sr.SerbianNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.sv.SwedishLightStemFilterFactory
import org.gnit.lucenekmp.analysis.sv.SwedishMinimalStemFilterFactory
import org.gnit.lucenekmp.analysis.te.TeluguNormalizationFilterFactory
import org.gnit.lucenekmp.analysis.te.TeluguStemFilterFactory
import org.gnit.lucenekmp.analysis.th.ThaiTokenizerFactory
import org.gnit.lucenekmp.analysis.synonym.SynonymFilterFactory
import org.gnit.lucenekmp.analysis.synonym.SynonymGraphFilterFactory
import org.gnit.lucenekmp.analysis.util.ElisionFilterFactory

/** Registers analysis/common factories for SPI lookups. */
object AnalysisCommonFactories {
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        AnalysisSPIRegistry.register(
            CharFilterFactory::class,
            CJKWidthCharFilterFactory.NAME,
            CJKWidthCharFilterFactory::class
        ) { args -> CJKWidthCharFilterFactory(args) }
        AnalysisSPIRegistry.register(
            CharFilterFactory::class,
            HTMLStripCharFilterFactory.NAME,
            HTMLStripCharFilterFactory::class
        ) { args -> HTMLStripCharFilterFactory(args) }
        AnalysisSPIRegistry.register(
            CharFilterFactory::class,
            MappingCharFilterFactory.NAME,
            MappingCharFilterFactory::class
        ) { args -> MappingCharFilterFactory(args) }
        AnalysisSPIRegistry.register(
            CharFilterFactory::class,
            PersianCharFilterFactory.NAME,
            PersianCharFilterFactory::class
        ) { args -> PersianCharFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            ASCIIFoldingFilterFactory.NAME,
            ASCIIFoldingFilterFactory::class
        ) { args -> ASCIIFoldingFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            ArabicNormalizationFilterFactory.NAME,
            ArabicNormalizationFilterFactory::class
        ) { args -> ArabicNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            ArabicStemFilterFactory.NAME,
            ArabicStemFilterFactory::class
        ) { args -> ArabicStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            PersianNormalizationFilterFactory.NAME,
            PersianNormalizationFilterFactory::class
        ) { args -> PersianNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            PersianStemFilterFactory.NAME,
            PersianStemFilterFactory::class
        ) { args -> PersianStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            BengaliNormalizationFilterFactory.NAME,
            BengaliNormalizationFilterFactory::class
        ) { args -> BengaliNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            BengaliStemFilterFactory.NAME,
            BengaliStemFilterFactory::class
        ) { args -> BengaliStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            BrazilianStemFilterFactory.NAME,
            BrazilianStemFilterFactory::class
        ) { args -> BrazilianStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            CJKBigramFilterFactory.NAME,
            CJKBigramFilterFactory::class
        ) { args -> CJKBigramFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            CJKWidthFilterFactory.NAME,
            CJKWidthFilterFactory::class
        ) { args -> CJKWidthFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            CapitalizationFilterFactory.NAME,
            CapitalizationFilterFactory::class
        ) { args -> CapitalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            ConcatenateGraphFilterFactory.NAME,
            ConcatenateGraphFilterFactory::class
        ) { args -> ConcatenateGraphFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            WordDelimiterFilterFactory.NAME,
            WordDelimiterFilterFactory::class
        ) { args -> WordDelimiterFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            CodepointCountFilterFactory.NAME,
            CodepointCountFilterFactory::class
        ) { args -> CodepointCountFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            DateRecognizerFilterFactory.NAME,
            DateRecognizerFilterFactory::class
        ) { args -> DateRecognizerFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            CommonGramsFilterFactory.NAME,
            CommonGramsFilterFactory::class
        ) { args -> CommonGramsFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            CommonGramsQueryFilterFactory.NAME,
            CommonGramsQueryFilterFactory::class
        ) { args -> CommonGramsQueryFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            DictionaryCompoundWordTokenFilterFactory.NAME,
            DictionaryCompoundWordTokenFilterFactory::class
        ) { args -> DictionaryCompoundWordTokenFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            HyphenationCompoundWordTokenFilterFactory.NAME,
            HyphenationCompoundWordTokenFilterFactory::class
        ) { args -> HyphenationCompoundWordTokenFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SoraniNormalizationFilterFactory.NAME,
            SoraniNormalizationFilterFactory::class
        ) { args -> SoraniNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SoraniStemFilterFactory.NAME,
            SoraniStemFilterFactory::class
        ) { args -> SoraniStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            CzechStemFilterFactory.NAME,
            CzechStemFilterFactory::class
        ) { args -> CzechStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            DecimalDigitFilterFactory.NAME,
            DecimalDigitFilterFactory::class
        ) { args -> DecimalDigitFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            DropIfFlaggedFilterFactory.NAME,
            DropIfFlaggedFilterFactory::class
        ) { args -> DropIfFlaggedFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            DelimitedPayloadTokenFilterFactory.NAME,
            DelimitedPayloadTokenFilterFactory::class
        ) { args -> DelimitedPayloadTokenFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            NumericPayloadTokenFilterFactory.NAME,
            NumericPayloadTokenFilterFactory::class
        ) { args -> NumericPayloadTokenFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            TokenOffsetPayloadTokenFilterFactory.NAME,
            TokenOffsetPayloadTokenFilterFactory::class
        ) { args -> TokenOffsetPayloadTokenFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            TypeAsPayloadTokenFilterFactory.NAME,
            TypeAsPayloadTokenFilterFactory::class
        ) { args -> TypeAsPayloadTokenFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SynonymFilterFactory.NAME,
            SynonymFilterFactory::class
        ) { args -> SynonymFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SynonymGraphFilterFactory.NAME,
            SynonymGraphFilterFactory::class
        ) { args -> SynonymGraphFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            EdgeNGramFilterFactory.NAME,
            EdgeNGramFilterFactory::class
        ) { args -> EdgeNGramFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            ElisionFilterFactory.NAME,
            ElisionFilterFactory::class
        ) { args -> ElisionFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            FixBrokenOffsetsFilterFactory.NAME,
            FixBrokenOffsetsFilterFactory::class
        ) { args -> FixBrokenOffsetsFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            KeywordMarkerFilterFactory.NAME,
            KeywordMarkerFilterFactory::class
        ) { args -> KeywordMarkerFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            EnglishMinimalStemFilterFactory.NAME,
            EnglishMinimalStemFilterFactory::class
        ) { args -> EnglishMinimalStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            FingerprintFilterFactory.NAME,
            FingerprintFilterFactory::class
        ) { args -> FingerprintFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            EnglishPossessiveFilterFactory.NAME,
            EnglishPossessiveFilterFactory::class
        ) { args -> EnglishPossessiveFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            HyphenatedWordsFilterFactory.NAME,
            HyphenatedWordsFilterFactory::class
        ) { args -> HyphenatedWordsFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            KeepWordFilterFactory.NAME,
            KeepWordFilterFactory::class
        ) { args -> KeepWordFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            KeywordRepeatFilterFactory.NAME,
            KeywordRepeatFilterFactory::class
        ) { args -> KeywordRepeatFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            LowerCaseFilterFactory.NAME,
            LowerCaseFilterFactory::class
        ) { args -> LowerCaseFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            KStemFilterFactory.NAME,
            KStemFilterFactory::class
        ) { args -> KStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            ProtectedTermFilterFactory.NAME,
            ProtectedTermFilterFactory::class
        ) { args -> ProtectedTermFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            LengthFilterFactory.NAME,
            LengthFilterFactory::class
        ) { args -> LengthFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            LimitTokenCountFilterFactory.NAME,
            LimitTokenCountFilterFactory::class
        ) { args -> LimitTokenCountFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            LimitTokenOffsetFilterFactory.NAME,
            LimitTokenOffsetFilterFactory::class
        ) { args -> LimitTokenOffsetFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            LimitTokenPositionFilterFactory.NAME,
            LimitTokenPositionFilterFactory::class
        ) { args -> LimitTokenPositionFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            PorterStemFilterFactory.NAME,
            PorterStemFilterFactory::class
        ) { args -> PorterStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            RemoveDuplicatesTokenFilterFactory.NAME,
            RemoveDuplicatesTokenFilterFactory::class
        ) { args -> RemoveDuplicatesTokenFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            ReverseStringFilterFactory.NAME,
            ReverseStringFilterFactory::class
        ) { args -> ReverseStringFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            FixedShingleFilterFactory.NAME,
            FixedShingleFilterFactory::class
        ) { args -> FixedShingleFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            ShingleFilterFactory.NAME,
            ShingleFilterFactory::class
        ) { args -> ShingleFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            GermanLightStemFilterFactory.NAME,
            GermanLightStemFilterFactory::class
        ) { args -> GermanLightStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            GermanMinimalStemFilterFactory.NAME,
            GermanMinimalStemFilterFactory::class
        ) { args -> GermanMinimalStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            GermanStemFilterFactory.NAME,
            GermanStemFilterFactory::class
        ) { args -> GermanStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            GermanNormalizationFilterFactory.NAME,
            GermanNormalizationFilterFactory::class
        ) { args -> GermanNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            GreekLowerCaseFilterFactory.NAME,
            GreekLowerCaseFilterFactory::class
        ) { args -> GreekLowerCaseFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            GreekStemFilterFactory.NAME,
            GreekStemFilterFactory::class
        ) { args -> GreekStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SpanishLightStemFilterFactory.NAME,
            SpanishLightStemFilterFactory::class
        ) { args -> SpanishLightStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SpanishPluralStemFilterFactory.NAME,
            SpanishPluralStemFilterFactory::class
        ) { args -> SpanishPluralStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            IndicNormalizationFilterFactory.NAME,
            IndicNormalizationFilterFactory::class
        ) { args -> IndicNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            HindiNormalizationFilterFactory.NAME,
            HindiNormalizationFilterFactory::class
        ) { args -> HindiNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            HindiStemFilterFactory.NAME,
            HindiStemFilterFactory::class
        ) { args -> HindiStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            HungarianLightStemFilterFactory.NAME,
            HungarianLightStemFilterFactory::class
        ) { args -> HungarianLightStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            IndonesianStemFilterFactory.NAME,
            IndonesianStemFilterFactory::class
        ) { args -> IndonesianStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            ItalianLightStemFilterFactory.NAME,
            ItalianLightStemFilterFactory::class
        ) { args -> ItalianLightStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            NorwegianLightStemFilterFactory.NAME,
            NorwegianLightStemFilterFactory::class
        ) { args -> NorwegianLightStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            NorwegianMinimalStemFilterFactory.NAME,
            NorwegianMinimalStemFilterFactory::class
        ) { args -> NorwegianMinimalStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            NorwegianNormalizationFilterFactory.NAME,
            NorwegianNormalizationFilterFactory::class
        ) { args -> NorwegianNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            NGramFilterFactory.NAME,
            NGramFilterFactory::class
        ) { args -> NGramFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            PortugueseLightStemFilterFactory.NAME,
            PortugueseLightStemFilterFactory::class
        ) { args -> PortugueseLightStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            PortugueseMinimalStemFilterFactory.NAME,
            PortugueseMinimalStemFilterFactory::class
        ) { args -> PortugueseMinimalStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            PortugueseStemFilterFactory.NAME,
            PortugueseStemFilterFactory::class
        ) { args -> PortugueseStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            RomanianNormalizationFilterFactory.NAME,
            RomanianNormalizationFilterFactory::class
        ) { args -> RomanianNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            RussianLightStemFilterFactory.NAME,
            RussianLightStemFilterFactory::class
        ) { args -> RussianLightStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            ScandinavianFoldingFilterFactory.NAME,
            ScandinavianFoldingFilterFactory::class
        ) { args -> ScandinavianFoldingFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            ScandinavianNormalizationFilterFactory.NAME,
            ScandinavianNormalizationFilterFactory::class
        ) { args -> ScandinavianNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SerbianNormalizationFilterFactory.NAME,
            SerbianNormalizationFilterFactory::class
        ) { args -> SerbianNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            StemmerOverrideFilterFactory.NAME,
            StemmerOverrideFilterFactory::class
        ) { args -> StemmerOverrideFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SwedishLightStemFilterFactory.NAME,
            SwedishLightStemFilterFactory::class
        ) { args -> SwedishLightStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            SwedishMinimalStemFilterFactory.NAME,
            SwedishMinimalStemFilterFactory::class
        ) { args -> SwedishMinimalStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            TeluguNormalizationFilterFactory.NAME,
            TeluguNormalizationFilterFactory::class
        ) { args -> TeluguNormalizationFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            TeluguStemFilterFactory.NAME,
            TeluguStemFilterFactory::class
        ) { args -> TeluguStemFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            TrimFilterFactory.NAME,
            TrimFilterFactory::class
        ) { args -> TrimFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            TruncateTokenFilterFactory.NAME,
            TruncateTokenFilterFactory::class
        ) { args -> TruncateTokenFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            TypeAsSynonymFilterFactory.NAME,
            TypeAsSynonymFilterFactory::class
        ) { args -> TypeAsSynonymFilterFactory(args) }
        AnalysisSPIRegistry.register(
            TokenizerFactory::class,
            EdgeNGramTokenizerFactory.NAME,
            EdgeNGramTokenizerFactory::class
        ) { args -> EdgeNGramTokenizerFactory(args) }
        AnalysisSPIRegistry.register(
            TokenizerFactory::class,
            NGramTokenizerFactory.NAME,
            NGramTokenizerFactory::class
        ) { args -> NGramTokenizerFactory(args) }
        AnalysisSPIRegistry.register(
            TokenizerFactory::class,
            PatternTokenizerFactory.NAME,
            PatternTokenizerFactory::class
        ) { args -> PatternTokenizerFactory(args) }
        AnalysisSPIRegistry.register(
            TokenizerFactory::class,
            ThaiTokenizerFactory.NAME,
            ThaiTokenizerFactory::class
        ) { args -> ThaiTokenizerFactory(args) }
        AnalysisSPIRegistry.register(
            TokenizerFactory::class,
            UAX29URLEmailTokenizerFactory.NAME,
            UAX29URLEmailTokenizerFactory::class
        ) { args -> UAX29URLEmailTokenizerFactory(args) }
        initialized = true
    }
}
