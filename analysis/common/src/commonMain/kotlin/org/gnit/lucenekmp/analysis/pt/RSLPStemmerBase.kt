package org.gnit.lucenekmp.analysis.pt

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.util.StemmerUtil.endsWith
import org.gnit.lucenekmp.jdkport.BufferedReader
import org.gnit.lucenekmp.jdkport.InputStreamReader
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.util.ClasspathResourceLoader

/**
 * Base class for stemmers that use a set of RSLP-like stemming steps.
 */
abstract class RSLPStemmerBase {
    /** A basic rule, with no exceptions. */
    internal open class Rule(suffix: String, val min: Int, replacement: String) {
        private val suffixChars: CharArray = suffix.toCharArray()
        val suffixLength: Int = suffixChars.size
        private val replacementChars: CharArray = replacement.toCharArray()

        open fun matches(s: CharArray, len: Int): Boolean {
            return len - suffixChars.size >= min && endsWith(s, len, suffixChars)
        }

        open fun replace(s: CharArray, len: Int): Int {
            if (replacementChars.isNotEmpty()) {
                System.arraycopy(replacementChars, 0, s, len - suffixChars.size, replacementChars.size)
            }
            return len - suffixChars.size + replacementChars.size
        }
    }

    /** A rule with a set of whole-word exceptions. */
    internal class RuleWithSetExceptions(
        suffix: String,
        min: Int,
        replacement: String,
        exceptions: Array<String>
    ) : Rule(suffix, min, replacement) {
        private val exceptionsSet: CharArraySet

        init {
            for (exception in exceptions) {
                if (!exception.endsWith(suffix)) {
                    throw RuntimeException("useless exception '$exception' does not end with '$suffix'")
                }
            }
            val list = exceptions.map { it as Any }.toMutableList()
            exceptionsSet = CharArraySet(list, false)
        }

        override fun matches(s: CharArray, len: Int): Boolean {
            return super.matches(s, len) && !exceptionsSet.contains(s, 0, len)
        }
    }

    /** A rule with a set of exceptional suffixes. */
    internal class RuleWithSuffixExceptions(
        suffix: String,
        min: Int,
        replacement: String,
        exceptions: Array<String>
    ) : Rule(suffix, min, replacement) {
        private val exceptionsSuffixes: Array<CharArray>

        init {
            for (exception in exceptions) {
                if (!exception.endsWith(suffix)) {
                    throw RuntimeException("warning: useless exception '$exception' does not end with '$suffix'")
                }
            }
            exceptionsSuffixes = Array(exceptions.size) { index -> exceptions[index].toCharArray() }
        }

        override fun matches(s: CharArray, len: Int): Boolean {
            if (!super.matches(s, len)) return false
            for (exception in exceptionsSuffixes) {
                if (endsWith(s, len, exception)) return false
            }
            return true
        }
    }

    /** A step containing a list of rules. */
    internal class Step(
        val name: String,
        private val rules: Array<Rule>,
        min: Int,
        suffixes: Array<String>?
    ) {
        private val minWordSize: Int
        private val conditionalSuffixes: Array<CharArray>?

        init {
            var computedMin = min
            if (computedMin == 0) {
                computedMin = Int.MAX_VALUE
                for (rule in rules) {
                    computedMin = minOf(computedMin, rule.min + rule.suffixLength)
                }
            }
            minWordSize = computedMin

            conditionalSuffixes = if (suffixes.isNullOrEmpty()) {
                null
            } else {
                Array(suffixes.size) { index -> suffixes[index].toCharArray() }
            }
        }

        fun apply(s: CharArray, len: Int): Int {
            if (len < minWordSize) return len

            if (conditionalSuffixes != null) {
                var found = false
                for (suffix in conditionalSuffixes) {
                    if (endsWith(s, len, suffix)) {
                        found = true
                        break
                    }
                }
                if (!found) return len
            }

            for (rule in rules) {
                if (rule.matches(s, len)) return rule.replace(s, len)
            }

            return len
        }
    }

    internal fun parse(resource: String): Map<String, Step> {
        return parseInternal(resource)
    }

    companion object {
        private val HEADER_PATTERN =
            Regex("^\\{\\s*\"([^\"]*)\",\\s*([0-9]+),\\s*(0|1),\\s*\\{(.*)\\},\\s*$")
        private val STRIP_PATTERN =
            Regex("^\\{\\s*\"([^\"]*)\",\\s*([0-9]+)\\s*\\}\\s*(,|(\\}\\s*;))$")
        private val REP_PATTERN =
            Regex("^\\{\\s*\"([^\"]*)\",\\s*([0-9]+),\\s*\"([^\"]*)\"\\s*\\}\\s*(,|(\\}\\s*;))$")
        private val EXC_PATTERN =
            Regex("^\\{\\s*\"([^\"]*)\",\\s*([0-9]+),\\s*\"([^\"]*)\",\\s*\\{(.*)\\}\\s*\\}\\s*(,|(\\}\\s*;))$")

        private const val PORTUGUESE_RSLP_DATA: String = """
#  Steps file for the RSLP stemmer.

# Step 1: Plural Reduction
{  "Plural", 3, 1, {"s"},
  # bons -> bom 
  {"ns",1,"m"},
  # balões -> balão
  {"ões",3,"ão"},
  # capitães -> capitão
  {"ães",1,"ão",{"mães"}},
  # normais -> normal
  {"ais",1,"al",{"cais","mais"}},
  # papéis -> papel
  {"éis",2,"el"},
  # amáveis -> amável
  {"eis",2,"el"},
  # lençóis -> lençol
  {"óis",2,"ol"},
  # barris -> barril
  {"is",2,"il",{"lápis","cais","mais","crúcis","biquínis","pois","depois","dois","leis"}},
  # males -> mal
  {"les",3,"l"},
  # mares -> mar
  {"res",3,"r", {"árvores"}},
  # casas -> casa
  {"s",2,"",{"aliás","pires","lápis","cais","mais","mas","menos","férias","fezes","pêsames","crúcis","gás","atrás","moisés","através","convés","ês","país","após","ambas","ambos","messias", "depois"}}};

# Step 2: Adverb Reduction
{  "Adverb", 0, 0, {},
  # felizmente -> feliz
  {"mente",4,"",{"experimente"}}};
  
# Step 3: Feminine Reduction
{  "Feminine", 3, 1, {"a","ã"},
  # chefona -> chefão
  {"ona",3,"ão",{"abandona","lona","iona","cortisona","monótona","maratona","acetona","detona","carona"}},
  # vilã -> vilão
  {"ã",2,"ão",{"amanhã","arapuã","fã","divã"}},
  # professora -> professor
  {"ora",3,"or"},
  # americana -> americano
  {"na",4,"no",{"carona","abandona","lona","iona","cortisona","monótona","maratona","acetona","detona","guiana","campana","grana","caravana","banana","paisana"}},
  # sozinha -> sozinho
  {"inha",3,"inho",{"rainha","linha","minha"}},
  # inglesa -> inglês
  {"esa",3,"ês",{"mesa","obesa","princesa","turquesa","ilesa","pesa","presa"}},
  # famosa -> famoso
  {"osa",3,"oso",{"mucosa","prosa"}},
  # maníaca -> maníaco
  {"íaca",3,"íaco"},
  # prática -> prático
  {"ica",3,"ico",{"dica"}},
  # cansada -> cansado
  {"ada",2,"ado",{"pitada"}},
  # mantida -> mantido
  {"ida",3,"ido",{"vida","dúvida"}},
  {"ída",3,"ido",{"recaída","saída"}},
  # prima -> primo
  {"ima",3,"imo",{"vítima"}},
  # passiva -> passivo
  {"iva",3,"ivo",{"saliva","oliva"}},
  # primeira -> primeiro
  {"eira",3,"eiro",{"beira","cadeira","frigideira","bandeira","feira","capoeira","barreira","fronteira","besteira","poeira"}}};

# Step 4: Augmentative/Diminutive Reduction
{  "Augmentative", 0, 1, {},
  # cansadíssimo -> cansad
  {"díssimo",5},
  # amabilíssimo -> ama
  {"abilíssimo",5},
  # fortíssimo -> fort
  {"íssimo",3},
  {"ésimo",3},
  # chiquérrimo -> chiqu
  {"érrimo",4},
  # pezinho -> pe
  {"zinho",2},
  # maluquinho -> maluc
  {"quinho",4,"c"},
  # amiguinho -> amig
  {"uinho",4},
  # cansadinho -> cansad
  {"adinho",3},
  # carrinho -> carr
  {"inho",3,"",{"caminho","cominho"}},
  # grandalhão -> grand
  {"alhão",4},
  # dentuça -> dent
  {"uça",4},
  # ricaço -> ric
  {"aço",4,"",{"antebraço"}},
  {"aça",4},
  # casadão -> cans
  {"adão",4}, 
  {"idão",4},
  # corpázio -> corp
  {"ázio",3,"",{"topázio"}},
  # pratarraz -> prat
  {"arraz",4},
  {"zarrão",3},
  {"arrão",4},
  # bocarra -> boc
  {"arra",3},
  # calorzão -> calor
  {"zão",2,"",{"coalizão"}},
  # meninão -> menin
  {"ão",3,"",{"camarão","chimarrão","canção","coração","embrião","grotão","glutão","ficção","fogão","feição","furacão","gamão","lampião","leão","macacão","nação","órfão","orgão","patrão","portão","quinhão","rincão","tração","falcão","espião","mamão","folião","cordão","aptidão","campeão","colchão","limão","leilão","melão","barão","milhão","bilhão","fusão","cristão","ilusão","capitão","estação","senão"}}};

# Step 5: Noun Suffix Reduction
{  "Noun", 0, 0, {},
  # existencialista -> exist
  {"encialista",4},
  # minimalista -> minim
  {"alista",5},
  # contagem -> cont
  {"agem",3,"",{"coragem","chantagem","vantagem","carruagem"}},
  # gerenciamento -> gerenc
  {"iamento",4},
  # monitoramento -> monitor
  {"amento",3,"",{"firmamento","fundamento","departamento"}},
  # nascimento -> nasc
  {"imento",3},
  {"mento",6,"",{"firmamento","elemento","complemento","instrumento","departamento"}},
  # comercializado -> comerci
  {"alizado",4},
  # traumatizado -> traum
  {"atizado",4},
  {"tizado",4,"",{"alfabetizado"}},
  # alfabetizado -> alfabet
  {"izado",5,"",{"organizado","pulverizado"}},
  # associativo -> associ
  {"ativo",4,"",{"pejorativo","relativo"}},
  # contraceptivo -> contracep
  {"tivo",4,"",{"relativo"}},
  # esportivo -> esport
  {"ivo",4,"",{"passivo","possessivo","pejorativo","positivo"}},
  # abalado -> abal
  {"ado",2,"",{"grado"}},
  # impedido -> imped
  {"ido",3,"",{"cândido","consolido","rápido","decido","tímido","duvido","marido"}},
  # ralador -> ral
  {"ador",3},
  # entendedor -> entend
  {"edor",3},
  # cumpridor -> cumpr
  {"idor",4,"",{"ouvidor"}},
  {"dor",4,"",{"ouvidor"}},
  {"sor",4,"",{"assessor"}},
  {"atoria",5},
  {"tor",3,"",{"benfeitor","leitor","editor","pastor","produtor","promotor","consultor"}},
  {"or",2,"",{"motor","melhor","redor","rigor","sensor","tambor","tumor","assessor","benfeitor","pastor","terior","favor","autor"}},
  # comparabilidade -> compar
  {"abilidade",5},
  # abolicionista -> abol
  {"icionista",4},
  # intervencionista -> interven
  {"cionista",5},
  {"ionista",5},
  {"ionar",5},
  # profissional -> profiss
  {"ional",4},
  # referência -> refer
  {"ência",3},
  # repugnância -> repugn
  {"ância",4,"",{"ambulância"}},
  # abatedouro -> abat
  {"edouro",3},
  # fofoqueiro -> fofoc
  {"queiro",3,"c"},
  {"adeiro",4,"",{"desfiladeiro"}},
  # brasileiro -> brasil
  {"eiro",3,"",{"desfiladeiro","pioneiro","mosteiro"}},
  {"uoso",3},
  # gostoso -> gost
  {"oso",3,"",{"precioso"}},
  # comercializaç -> comerci
  {"alizaç",5},
  {"atizaç",5},
  {"tizaç",5},
  {"izaç",5,"",{"organizaç"}},
  # alegaç -> aleg
  {"aç",3,"",{"equaç","relaç"}},
  # aboliç -> abol
  {"iç",3,"",{"eleiç"}},
  # anedotário -> anedot
  {"ário",3,"",{"voluntário","salário","aniversário","diário","lionário","armário"}},
  {"atório",3},
  {"rio",5,"",{"voluntário","salário","aniversário","diário","compulsório","lionário","próprio","stério","armário"}},
  # ministério -> minist
  {"ério",6},
  # chinês -> chin
  {"ês",4},
  # beleza -> bel
  {"eza",3},
  # rigidez -> rigid
  {"ez",4},
  # parentesco -> parent
  {"esco",4},
  # ocupante -> ocup
  {"ante",2,"",{"gigante","elefante","adiante","possante","instante","restaurante"}},
  # bombástico -> bomb
  {"ástico",4,"",{"eclesiástico"}},
  {"alístico",3},
  {"áutico",4},
  {"êutico",4},
  {"tico",3,"",{"político","eclesiástico","diagnostico","prático","doméstico","diagnóstico","idêntico","alopático","artístico","autêntico","eclético","crítico","critico"}},
  # polêmico -> polêm
  {"ico",4,"",{"tico","público","explico"}},
  # produtividade -> produt
  {"ividade",5},
  # profundidade -> profund
  {"idade",4,"",{"autoridade","comunidade"}},
  # aposentadoria -> aposentad
  {"oria",4,"",{"categoria"}},
  # existencial -> exist
  {"encial",5},
  # artista -> art
  {"ista",4},
  {"auta",5},
  # maluquice -> maluc
  {"quice",4,"c"},
  # chatice -> chat
  {"ice",4,"",{"cúmplice"}},
  # demoníaco -> demon
  {"íaco",3},
  # decorrente -> decorr
  {"ente",4,"",{"freqüente","alimente","acrescente","permanente","oriente","aparente"}},
  {"ense",5},
  # criminal -> crim
  {"inal",3},
  # americano -> americ
  {"ano",4},
  # amável -> am
  {"ável",2,"",{"afável","razoável","potável","vulnerável"}},
  # combustível -> combust
  {"ível",3,"",{"possível"}},
  {"vel",5,"",{"possível","vulnerável","solúvel"}},
  {"bil",3,"vel"},
  # cobertura -> cobert
  {"ura",4,"",{"imatura","acupuntura","costura"}},
  {"ural",4},
  # consensual -> consens
  {"ual",3,"",{"bissexual","virtual","visual","pontual"}},
  # mundial -> mund
  {"ial",3},
  # experimental -> experiment
  {"al",4,"",{"afinal","animal","estatal","bissexual","desleal","fiscal","formal","pessoal","liberal","postal","virtual","visual","pontual","sideral","sucursal"}},
  {"alismo",4},
  {"ivismo",4},
  {"ismo",3,"",{"cinismo"}}};

# Step 6: Verb Suffix Reduction
{  "Verb", 0, 0, {}, 
  # cantaríamo -> cant
  {"aríamo",2},
  # cantássemo -> cant
  {"ássemo",2},
  # beberíamo -> beb
  {"eríamo",2},
  # bebêssemo -> beb
  {"êssemo",2},
  # partiríamo -> part
  {"iríamo",3},
  # partíssemo -> part
  {"íssemo",3},
  # cantáramo -> cant
  {"áramo",2},
  # cantárei -> cant
  {"árei",2},
  # cantaremo -> cant
  {"aremo",2},
  # cantariam -> cant
  {"ariam",2},
  # cantaríei -> cant
  {"aríei",2},
  # cantássei -> cant
  {"ássei",2},
  # cantassem -> cant
  {"assem",2},
  # cantávamo -> cant
  {"ávamo",2},
  # bebêramo -> beb
  {"êramo",3},
  # beberemo -> beb
  {"eremo",3},
  # beberiam -> beb
  {"eriam",3},
  # beberíei -> beb
  {"eríei",3},
  # bebêssei -> beb
  {"êssei",3},
  # bebessem -> beb
  {"essem",3},
  # partiríamo -> part
  {"íramo",3},
  # partiremo -> part
  {"iremo",3},
  # partiriam -> part
  {"iriam",3},
  # partiríei -> part
  {"iríei",3},
  # partíssei -> part
  {"íssei",3},
  # partissem -> part
  {"issem",3},
  # cantando -> cant
  {"ando",2},
  # bebendo -> beb
  {"endo",3},
  # partindo -> part
  {"indo",3},
  # propondo -> prop
  {"ondo",3},
  # cantaram -> cant
  {"aram",2},
  {"arão",2},
  # cantarde -> cant
  {"arde",2},
  # cantarei -> cant
  {"arei",2},
  # cantarem -> cant
  {"arem",2},
  # cantaria -> cant
  {"aria",2},
  # cantarmo -> cant
  {"armo",2},
  # cantasse -> cant
  {"asse",2},
  # cantaste -> cant
  {"aste",2},
  # cantavam -> cant
  {"avam",2,"",{"agravam"}},
  # cantávei -> cant
  {"ávei",2},
  # beberam -> beb
  {"eram",3},
  {"erão",3},
  # beberde -> beb
  {"erde",3},
  # beberei -> beb
  {"erei",3},
  # bebêrei -> beb
  {"êrei",3},
  # beberem -> beb
  {"erem",3},
  # beberia -> beb
  {"eria",3},
  # bebermo -> beb
  {"ermo",3},
  # bebesse -> beb
  {"esse",3},
  # bebeste -> beb
  {"este",3,"",{"faroeste","agreste"}},
  # bebíamo -> beb
  {"íamo",3},
  # partiram -> part
  {"iram",3},
  # concluíram -> conclu
  {"íram",3},
  {"irão",2},
  # partirde -> part
  {"irde",2},
  # partírei -> part
  {"irei",3,"",{"admirei"}},
  # partirem -> part
  {"irem",3,"",{"adquirem"}},
  # partiria -> part
  {"iria",3},
  # partirmo -> part
  {"irmo",3},
  # partisse -> part
  {"isse",3},
  # partiste -> part
  {"iste",4},
  {"iava",4,"",{"ampliava"}},
  # cantamo -> cant
  {"amo",2},
  {"iona",3},
  # cantara -> cant
  {"ara",2,"",{"arara","prepara"}},
  # cantará -> cant
  {"ará",2,"",{"alvará"}},
  # cantare -> cant
  {"are",2,"",{"prepare"}},
  # cantava -> cant
  {"ava",2,"",{"agrava"}},
  # cantemo -> cant
  {"emo",2},
  # bebera -> beb
  {"era",3,"",{"acelera","espera"}},
  # beberá -> beb
  {"erá",3},
  # bebere -> beb
  {"ere",3,"",{"espere"}},
  # bebiam -> beb
  {"iam",3,"",{"enfiam","ampliam","elogiam","ensaiam"}},
  # bebíei -> beb
  {"íei",3},
  # partimo -> part
  {"imo",3,"",{"reprimo","intimo","íntimo","nimo","queimo","ximo"}},
  # partira -> part
  {"ira",3,"",{"fronteira","sátira"}},
  {"ído",3},
  # partirá -> part
  {"irá",3},
  {"tizar",4,"",{"alfabetizar"}},
  {"izar",5,"",{"organizar"}},
  {"itar",5,"",{"acreditar","explicitar","estreitar"}},
  # partire -> part
  {"ire",3,"",{"adquire"}},
  # compomo -> comp
  {"omo",3},
  # cantai -> cant
  {"ai",2},
  # cantam -> cant
  {"am",2},
  # barbear -> barb
  {"ear",4,"",{"alardear","nuclear"}},
  # cantar -> cant
  {"ar",2,"",{"azar","bazaar","patamar"}},
  # cheguei -> cheg
  {"uei",3},
  {"uía",5,"u"},
  # cantei -> cant
  {"ei",3},
  {"guem",3,"g"},
  # cantem -> cant
  {"em",2,"",{"alem","virgem"}},
  # beber -> beb
  {"er",2,"",{"éter","pier"}},
  # bebeu -> beb
  {"eu",3,"",{"chapeu"}},
  # bebia -> beb
  {"ia",3,"",{"estória","fatia","acia","praia","elogia","mania","lábia","aprecia","polícia","arredia","cheia","ásia"}},
  # partir -> part
  {"ir",3,"",{"freir"}},
  # partiu -> part
  {"iu",3},
  {"eou",5},
  # chegou -> cheg
  {"ou",3},
  # bebi -> beb
  {"i",3}};

# Step 7: Vowel Removal 
{  "Vowel", 0, 0, {}, 
  {"bil",2,"vel"},
  {"gue",2,"g",{"gangue","jegue"}},
  {"á",3}, 
  {"ê",3,"",{"bebê"}},
  # menina -> menin
  {"a",3,"",{"ásia"}},
  # grande -> grand
  {"e",3},
  # menino -> menin
  {"o",3,"",{"ão"}}};
"""

        private const val GALICIAN_RSLP_DATA: String = """
#	Steps file for the RSLP stemmer.

# Step 1: Plural Reduction
{  "Plural", 3, 1, {"s"}, 
  # bons -> bon
  {"ns",1,"n",{"luns","furatapóns","furatapons"}},
  # xamós -> xamón
  {"ós",3,"ón"},
  # balões -> balón
  {"ões",3,"ón"},
  # capitães -> capitão
  {"ães",1,"ão",{"mães","magalhães"}},
  # normais -> normal
  {"ais",2,"al",{"cais","tais","mais","pais","ademais"}},
  {"áis",2,"al",{"cáis","táis", "máis", "páis", "ademáis"}},
  # papéis -> papel
  {"éis",2,"el"},
  # posíbeis -> posíbel
  {"eis",2,"el"},
  # espanhóis -> espanhol
  {"óis",2,"ol",{"escornabóis"}},
  # caracois -> caracol
  {"ois",2,"ol",{"escornabois"}},
  # cadrís -> cadril
  {"ís",2,"il",{"país"}},
  # cadris -> cadril
  {"is",2,"il",{"menfis","pais","kinguis"}},
  # males -> mal
  {"les",2,"l",{"ingles","marselles","montreales","senegales","manizales","móstoles","nápoles"}},
  # mares -> mar
  {"res",3,"r",{"petres","henares","cáceres","baleares","linares","londres","mieres","miraflores","mércores","venres", "pires"}},
  # luces -> luz
  {"ces",2,"z"},
  # luzes -> luz
  {"zes",2,"z"},
  # leises -> lei
  {"ises",3,"z"},
  # animás -> animal
  {"ás",1,"al",{"más"}},
  # gases -> gas
  {"ses",2,"s"},
  # casas -> casa
  {"s",2,"",{"barbadés","barcelonés","cantonés","gabonés","llanés","medinés","escocés","escocês","francês","barcelonês","cantonês","macramés","reves","barcelones","cantones","gabones","llanes","magallanes","medines","escoces","frances","xoves","martes","aliás","pires","lápis","cais","mais","mas","menos","férias","pêsames","crúcis","país","cangas","atenas","asturias","canarias","filipinas","honduras","molucas","caldas","mascareñas","micenas","covarrubias","psoas","óculos","nupcias","xoves","martes","llanes"}}};

{  "Unification", 0, 0, {},
  # cansadísimo -> cansadísimo
  {"íssimo",5,"ísimo"},
  # cansadísima -> cansadísima
  {"íssima",5,"ísima"},
  # homaço -> homazo
  {"aço",4,"azo"},
  # mulheraça -> mulheraza
  {"aça",4,"aza"},
  # xentuça -> xentuza
  {"uça",4,"uza"},
  # manilhar -> manillar
  {"lhar",2,"llar"},
  # colher -> coller
  {"lher",2,"ller"},
  # melhor -> mellor
  {"lhor",2,"llor"},
  # alho -> allo
  {"lho",1,"llo"},
  # linhar -> liñar
  {"nhar",2,"ñar"},
  # penhor -> peñor
  {"nhor",2,"ñor"},
  # anho -> año
  {"nho",1,"ño"},
  # cunha -> cuña
  {"nha",1,"ña"},
  # hospitalário -> hospitalario
  {"ário",3,"ario"},
  # bibliotecária -> bibliotecaria
  {"ária",3,"aria"},
  # agradable -> agradábel
  {"able",2,"ábel"},
  # agradávele -> agradábel
  {"ável",2,"ábel"},
  # imposible -> imposíbel
  {"ible",2,"íbel"},
  # imposível -> imposíbel
  {"ível",2,"íbel"},
  # imposiçom -> imposición
  {"çom",2,"ción"},
  # garagem -> garaxe
  {"agem",2,"axe"},
  # garage -> garaxe
  {"age",2,"axe"},
  # impressão -> impressón
  {"ão",3,"ón"},
  # irmao -> irmán
  {"ao",1,"án"},
  # irmau -> irmán
  {"au",1,"án"},
  # garrafom -> garrafón
  {"om",3,"ón"},
  # cantem -> canten
  {"m",2,"n"}};

{  "Adverb", 0, 0, {},
  # felizmente -> feliz
  {"mente",4,"",{"experimente","vehemente","sedimente"}}};

{  "Augmentative", 0, 1, {},
  # cansadísimo -> cansad
  {"dísimo",5},
  # cansadísima -> cansad
  {"dísima",5},
  # amabilísimo -> ama
  {"bilísimo",3},
  # amabilísima -> ama
  {"bilísima",3},
  # fortísimo -> fort
  {"ísimo",3},
  # fortísima -> fort
  {"ísima",3},
  # centésimo -> cent
  {"ésimo",3},
  # centésima -> cent
  {"ésima",3},
  # paupérrimo -> paup
  {"érrimo",4},
  # paupérrima -> paup
  {"érrima",4},
  # charlatana -> charlat
  {"ana",2,"",{"argana","banana","choupana","espadana","faciana","iguana","lantana","macana","membrana","mesana","nirvana","obsidiana","palangana","pavana","persiana","pestana","porcelana","pseudomembrana","roldana","sábana","salangana","saragana","ventana"}},
  # charlatán -> charlat
  {"án",3,"",{"ademán","bardán","barregán","corricán","curricán","faisán","furacán","fustán","gabán","gabián","galán","gañán","lavacán","mazán","mourán","rabadán","serán","serrán","tabán","titán","tobogán","verán","volcán","volován"}},
  # homazo -> hom
  {"azo",4,"",{"abrazo","espazo","andazo","bagazo","balazo","bandazo","cachazo","carazo","denazo","engazo","famazo","lampreazo","pantocazo","pedazo","preñazo","regazo","ribazo","sobrazo","terrazo","trompazo"}},
  # mulleraza -> muller
  {"aza",3,"",{"alcarraza","ameaza","baraza","broucaza","burgaza","cabaza","cachaza","calaza","carpaza","carraza","coiraza","colmaza","fogaza","famaza","labaza","liñaza","melaza","mordaza","paraza","pinaza","rabaza","rapaza","trancaza"}},
  # cascallo -> casc
  {"allo",4,"",{"traballo"}},
  # xentalla -> xent
  {"alla",4},
  # bocarra -> boc
  {"arra",3,"",{"cigarra","cinzarra"}},
  # medicastro -> medic
  {"astro",3,"",{"balastro","bimbastro","canastro","retropilastro"}},
  # poetastra -> poet
  {"astra",3,"",{"banastra","canastra","contrapilastra","piastra","pilastra"}},
  # corpázio -> corp
  {"ázio",3,"",{"topázio"}},
  # soutelo -> sout
  {"elo",4,"",{"bacelo","barrelo","bicarelo","biquelo","boquelo","botelo","bouquelo","cacarelo","cachelo","cadrelo","campelo","candelo","cantelo","carabelo","carambelo","caramelo","cercelo","cerebelo","chocarelo","coitelo","conchelo","corbelo","cotobelo","couselo","destelo","desvelo","esfácelo","fandelo","fardelo","farelo","farnelo","flabelo","ganchelo","garfelo","involucelo","mantelo","montelo","outerelo","padicelo","pesadelo","pinguelo","piquelo","rampelo","rastrelo","restelo","tornecelo","trabelo","restrelo","portelo","ourelo","zarapelo"}},
  # avioneta -> avion
  {"eta",3,"",{"arqueta","atleta","avoceta","baioneta","baldeta","banqueta","barraganeta","barreta","borleta","buceta","caceta","calceta","caldeta","cambeta","canaleta","caneta","carreta","cerceta","chaparreta","chapeta","chareta","chincheta","colcheta","cometa","corbeta","corveta","cuneta","desteta","espeta","espoleta","estafeta","esteta","faceta","falanxeta","frasqueta","gaceta","gabeta","galleta","garabeta","gaveta","glorieta","lagareta","lambeta","lanceta","libreta","maceta","macheta","maleta","malleta","mareta","marreta","meseta","mofeta","muleta","peseta","planeta","raqueta","regreta","saqueta","veleta","vendeta","viñeta"}},
  # guapete -> guap
  {"ete",3,"",{"alfinete","ariete","bacinete","banquete","barallete","barrete","billete","binguelete","birrete","bonete","bosquete","bufete","burlete","cabalete","cacahuete","cavinete","capacete","carrete","casarete","casete","chupete","clarinete","colchete","colete","capete","curupete","disquete","estilete","falsete","ferrete","filete","gallardete","gobelete","inglete","machete","miquelete","molete","mosquete","piquete","ribete","rodete","rolete","roquete","sorvete","vedete","vendete"}},
  # práctica -> práct
  {"ica",3,"",{"andarica","botánica","botica","dialéctica","dinámica","física","formica","gráfica","marica","túnica"}},
  # práctico -> práct
  {"ico",3,"",{"conico","acetifico","acidifico"}},
  # trapexo -> trap
  {"exo",3,"",{"arpexo","arquexo","asexo","axexo","azulexo","badexo","bafexo","bocexo","bosquexo","boubexo","cacarexo","carrexo","cascarexo","castrexo","convexo","cotexo","desexo","despexo","forcexo","gabexo","gargarexo","gorgolexo","inconexo","manexo","merexo","narnexo","padexo","patexo","sopexo","varexo"}},
  {"exa",3,"",{"airexa","bandexa","carrexa","envexa","igrexa","larexa","patexa","presexa","sobexa"}},
  # multidão -> mult
  {"idão",3},
  # pequeniño -> pequeno
  {"iño",3,"o",{"camiño","cariño","comiño","golfiño","padriño","sobriño","viciño","veciño"}},
  # pequeniña -> pequena
  {"iña",3,"a",{"camariña","campiña","entreliña","espiña","fariña","moriña","valiña"}},
  # grandito -> grand
  {"ito",3,""},
  # grandita -> grand
  {"ita",3,""},
  # anomaloide -> animal
  {"oide",3,"",{"anaroide","aneroide","asteroide","axoide","cardioide","celuloide","coronoide","discoide","espermatozoide","espiroide","esquizoide","esteroide","glenoide","linfoide","hemorroide","melaloide","sacaroide","tetraploide","varioloide"}},
  # cazola -> caz
  {"ola",3,"",{"aixola","ampola","argola","arola","arteríola","bandola","bítola","bractéola","cachola","carambola","carapola","carola","carrandiola","catrapola","cebola","centola","champola","chatola","cirola","cítola","consola","corola","empola","escarola","esmola","estola","fitola","florícola","garañola","gárgola","garxola","glicocola","góndola","mariola","marola","michola","pirola","rebola","rupícola","saxícola","sémola","tachola","tómbola"}},
  # pedrolo -> pedr
  {"olo",3,"",{"arrolo","babiolo","cacharolo","caixarolo","carolo","carramolo","cascarolo","cirolo","codrolo","correolo","cotrolo","desconsolo","rebolo","repolo","subsolo","tixolo","tómbolo","torolo","trémolo","vacúolo","xermolo","zócolo"}},
  # vellote -> vell
  {"ote",3,"",{"aigote","alcaiote","barbarote","balote","billote","cachote","camarote","capote","cebote","chichote","citote","cocorote","escote","gañote","garrote","gavote","lamote","lapote","larapote","lingote","lítote","magote","marrote","matalote","pandote","paparote","rebote","tagarote","zarrote"}},
  # mozota -> moz
  {"ota",3,"",{"asíntota","caiota","cambota","chacota","compota","creosota","curota","derrota","díspota","gamota","maniota","pelota","picota","pillota","pixota","queirota","remota"}},
  # gordocho -> gord
  {"cho",3,"",{"abrocho","arrocho","carocho","falucho","bombacho","borracho","mostacho"}},
  # gordecha -> gord
  {"cha",3,"",{"borracha","carracha","estacha","garnacha","limacha","remolacha","abrocha"}},
  # baratuco -> barat
  {"uco",4,"",{"caduco","estuco","fachuco","malluco","saluco","trabuco"}},
  # borrachuzo -> borrach
  {"uzo",3,"",{"carriñouzo","fachuzo","mañuzo","mestruzo","tapuzo"}},
  # xentuza -> xent
  {"uza",3,"",{"barruza","chamuza","chapuza","charamuza","conduza","deduza","desluza","entreluza","induza","reluza","seduza","traduza","trasluza"}},
  # babuxa -> bab
  {"uxa",3,"",{"caramuxa","carrabouxa","cartuxa","coruxa","curuxa","gaturuxa","maruxa","meruxa","miruxa","moruxa","muruxa","papuxa","rabuxa","trouxa"}},
  {"uxo",3,"",{"caramuxo","carouxo","carrabouxo","curuxo","debuxo","ganduxo","influxo","negouxo","pertuxo","refluxo"}},
  # grupello -> grup
  {"ello",3,"",{"alborello","artello","botello","cachafello","calello","casarello","cazabello","cercello","cocerello","concello","consello","desparello","escaravello","espello","fedello","fervello","gagafello","gorrobello","nortello","pendello","troupello","trebello"}},
  # pontella -> pont
  {"ella",3,"",{"alborella","bertorella","bocatella","botella","calella","cercella","gadella","grosella","lentella","movella","nocella","noitevella","parella","pelella","percebella","segorella","sabella"}}};

{  "Noun", 0, 0, {},
  # lealdade -> leal 
  {"dade",3,"",{"acridade","calidade"}},
  # clarificar -> clar
  {"ificar",2},
  # brasileiro->brasil
  {"eiro",3,"",{"agoireiro","bardalleiro","braseiro","barreiro","canteiro","capoeiro","carneiro","carteiro","cinceiro","faroleiro","mareiro","preguiceiro","quinteiro","raposeiro","retranqueiro","regueiro","sineiro","troleiro","ventureiro"}},
  # marisqueira -> marisqu
  {"eira",3,"",{"cabeleira","canteira","cocheira","folleira","milleira"}},
  # hospitalario -> hospital
  {"ario",3,"",{"armario","calcario","lionario","salario"}},
  # bibliotecaria -> bibliotec
  {"aria",3,"",{"cetaria","coronaria","fumaria","linaria","lunaria","parietaria","saponaria","serpentaria"}},
  # humorístico -> humor
  {"ístico",3,"",{"balístico", "ensaístico"}},
  # castrista -> castr
  {"ista",3,"",{"batista","ciclista","fadista","operista","tenista","verista"}},
  # lavado -> lav
  {"ado",2,"",{"grado","agrado"}},
  # decanato -> decan
  {"ato",2,"",{"agnato"}},
  # xemido -> xem
  {"ido",3,"",{"cándido","cândido","consolido","decidido","duvido","marido","rápido"}},
  # mantida -> mant
  {"ida",3,"",{"bastida","dúbida","dubida","duvida","ermida","éxida","guarida","lapicida","medida","morida"}},
  {"ída",3},
  # mantído -> mant
  {"ido",3},
  # orelludo -> orell
  {"udo",3,"",{"estudo","escudo"}},
  # orelluda -> orell
  {"uda",3},
  {"ada",3,"",{"abada","alhada","allada","pitada"}},
  # comedela -> come
  {"dela",3,"",{"cambadela","cavadela","forcadela","erisipidela","mortadela","espadela","fondedela","picadela","arandela","candela","cordela","escudela","pardela"}},
  # fontela -> font
  {"ela",3,"",{"canela","capela","cotela","cubela","curupela","escarapela","esparrela","estela","fardela","flanela","fornela","franela","gabela","gamela","gavela","glumela","granicela","lamela","lapela","malvela","manela","manganela","mexarela","micela","mistela","novela","ourela","panela","parcela","pasarela","patamela","patela","paxarela","pipela","pitela","postela","pubela","restela","sabela","salmonela","secuela","sentinela","soldanela","subela","temoncela","tesela","tixela","tramela","trapela","varela","vitela","xanela","xestela"}},
  # agradábel -> agrad
  {"ábel",2,"",{"afábel","fiábel"}},
  # combustíbel -> combust
  {"íbel",2,"",{"críbel","imposíbel","posíbel","fisíbel","falíbel"}},
  # fabricante -> frabrica
  {"nte",3,"",{"alimente","adiante","acrescente","elefante","frequente","freqüente","gigante","instante","oriente","permanente","posante","possante","restaurante"}},
  # ignorancia -> ignora
  {"ncia",3},
  # temperanza -> tempera
  {"nza",3},
  {"acia",3,"",{"acracia","audacia","falacia","farmacia"}},
  # inmundicia -> inmund
  {"icia",3,"",{"caricia","delicia","ledicia","malicia","milicia","noticia","pericia","presbicia","primicia","regalicia","sevicia","tiricia"}},
  # xustiza -> xust
  {"iza",3,"",{"alvariza","baliza","cachiza","caniza","cañiza","carbaliza","carriza","chamariza","chapiza","fraguiza","latiza","longaniza","mañiza","nabiza","peliza","preguiza","rabiza"}},
  # clarexar -> clar
  {"exar",3,"",{"palmexar"}},
  # administración -> administr
  {"ación",2,"",{"aeración"}},
  # expedición -> exped
  {"ición",3,"",{"condición","gornición","monición","nutrición","petición","posición","sedición","volición"}},
  # excepción -> except
  {"ción",3,"t"},
  # comprensión -> comprens
  {"sión",3,"s",{"abrasión", "alusión"}},
  # doazón -> do
  {"azón",2,"",{"armazón"}},
  # garrafón -> garraf
  {"ón",3,"",{"abalón","acordeón","alción","aldrabón","alerón","aliñón","ambón","bombón","calzón","campón","canalón","cantón","capitón","cañón","centón","ciclón","collón","colofón","copón","cotón","cupón","petón","tirón","tourón","turón","unción","versión","zubón","zurrón"}},
  # lambona -> lamb
  {"ona",3,"",{"abandona","acetona","aleurona","amazona","anémona","bombona","cambona","carona","chacona","charamona","cincona","condona","cortisona","cretona","cretona","detona","estona","fitohormona","fregona","gerona","hidroquinona","hormona","lesiona","madona","maratona","matrona","metadona","monótona","neurona","pamplona","peptona","poltrona","proxesterona","quinona","quinona","silicona","sulfona"}},
  # bretoa -> bretón
  {"oa",3,"",{"abandoa","madroa","barbacoa","estoa","airoa","eiroa","amalloa","ámboa","améndoa","anchoa","antinéboa","avéntoa","avoa","bágoa","balboa","bisavoa","boroa","canoa","caroa","comadroa","coroa","éngoa","espácoa","filloa","fírgoa","grañoa","lagoa","lanzoa","magoa","mámoa","morzoa","noiteboa","noraboa","parañoa","persoa","queiroa","rañoa","táboa","tataravoa","teiroa"}},
  # demoníaco -> demoní
  {"aco",3},
  # demoníaca -> demoní
  {"aca",3,"",{"alpaca","barraca","bullaca","buraca","carraca","casaca","cavaca","cloaca","entresaca","ervellaca","espinaca","estaca","farraca","millaca","pastinaca","pataca","resaca","urraca","purraca"}},
  # carballal -> carball
  {"al",4,"",{"afinal","animal","estatal","bisexual","bissexual","desleal","fiscal","formal","pessoal","persoal","liberal","postal","virtual","visual","pontual","puntual","homosexual","heterosexual"}},
  # nadador -> nada
  {"dor",2,"",{"abaixador"}},
  # benfeitor -> benfei
  {"tor",3,"",{"autor","motor","pastor","pintor"}},
  # produtor -> produt
  {"or",2,"",{"asesor","assessor","favor","mellor","melhor","redor","rigor","sensor","tambor","tumor"}},
  # profesora -> profes
  {"ora",3,"",{"albacora","anáfora","áncora","apisoadora","ardora","ascospora","aurora","avéspora","bitácora","canéfora","cantimplora","catáfora","cepilladora","demora","descalcificadora","diáspora","empacadora","epífora","ecavadora","escora","eslora","espora","fotocompoñedora","fotocopiadora","grampadora","isícora","lavadora","lixadora","macrospora","madrépora","madrágora","masora","mellora","metáfora","microspora","milépora","milpéndora","nécora","oospora","padeadora","pasiflora","pécora","píldora","pólvora","ratinadora","rémora","retroescavadora","sófora","torradora","trémbora","uredospora","víbora","víncora","zoospora"}},
  # zapataría -> zapat
  {"aría",3,"",{"libraría"}},
  # etiquetaxe -> etiquet
  {"axe",3,"",{"aluaxe","amaraxe","amperaxe","bagaxe","balaxe","barcaxe","borraxe","bescaxe","cabotaxe","carraxe","cartilaxe","chantaxe","colaxe","coraxe","carruaxe","dragaxe","embalaxe","ensilaxe","epistaxe","fagundaxe","fichaxe","fogaxe","forraxe","fretaxe","friaxe","garaxe","homenaxe","leitaxe","liñaxe","listaxe","maraxe","marcaxe","maridaxe","masaxe","miraxe","montaxe","pasaxe","peaxe","portaxe","ramaxe","rebelaxe","rodaxe","romaxe","sintaxe","sondaxe","tiraxe","vantaxe","vendaxe","viraxe"}},
  # movedizo -> move
  {"dizo",3},
  # limpeza -> limp
  {"eza",3,"",{"alteza","beleza","fereza","fineza","vasteza","vileza"}},
  # rixidez -> rixid
  {"ez",3,"",{"acidez","adultez","adustez","avidez","candidez","mudez","nenez","nudez","pomez"}},
  # mullerengo -> muller
  {"engo",3},
  # chairego -> chair
  {"ego",3,"",{"corego","derrego","entrego","lamego","sarego","sartego"}},
  # cariñoso -> cariñ
  {"oso",3,"",{"afanoso","algoso","caldoso","caloso","cocoso","ditoso","favoso","fogoso","lamoso","mecoso","mocoso","precioso","rixoso","venoso","viroso","xesoso"}},
  # cariñosa -> cariñ
  {"osa",3,"",{"mucosa","glicosa","baldosa","celulosa","isoglosa","nitrocelulosa","levulosa","ortosa","pectosa","preciosa","sacarosa","serosa","ventosa"}},
  # negrume -> negr
  {"ume",3,"",{"agrume","albume","alcume","batume","cacume","cerrume","chorume","churume","costume","curtume","estrume","gafume","legume","perfume","queixume","zarrume"}},
  # altura -> alt
  {"ura",3,"",{"albura","armadura","imatura","costura"}},
  # cuspiñar -> cusp
  {"iñar",3},
  # febril -> febr
  {"il",3,"",{"abril","alfil","anil","atril","badil","baril","barril","brasil","cadril","candil","cantil","carril","chamil","chancil","civil","cubil","dátil","difícil","dócil","edil","estéril","fácil","fráxil","funil","fusil","grácil","gradil","hábil","hostil","marfil"}},
  # principesco -> princip
  {"esco",4},
  # mourisco -> mour
  {"isco",4},
  # esportivo -> esport
  {"ivo",3,"",{"pasivo","positivo","passivo","possessivo","posesivo","pexotarivo","relativo"}}};

{  "Verb", 0, 0, {},
  # amaba -> am
  {"aba",2},
  # andabade -> and
  {"abade",2},
  # andábade -> and
  {"ábade",2},
  # chorabamo -> chor
  {"abamo",2},
  # chorábamo -> chor
  {"ábamo",2}, 
  # moraban -> morab
  {"aban",2},
  # andache -> and
  {"ache",2},
  # andade -> and
  {"ade",2},
  {"an",2}, 
  # cantando -> cant
  {"ando",2},
  # cantar -> cant
  {"ar",2,"",{"azar","bazar","patamar"}}, 
  # lembrarade -> lembra
  {"arade",2},
  {"aramo",2}, 
  {"arán",2},
  # cantaran -> cant
  {"aran",2},
  # convidárade -> convid
  {"árade",2},
  # convidaría -> convid
  {"aría",2},
  # cantariade -> cant
  {"ariade",2},
  # cantaríade -> cant
  {"aríade",2},
  # cantarian -> cant 
  {"arian",2},
  # cantariamo -> cant
  {"ariamo",2},
  # pescaron -> pesc
  {"aron",2},
  # cantase -> cant
  {"ase",2},
  # cantasede -> cant
  {"asede",2},
  # cantásede -> cant
  {"ásede",2},
  # cantasemo -> cant
  {"asemo",2},
  # cantásemo -> cant
  {"ásemo",2},
  # cantasen -> cant
  {"asen",2},
  # loitavan -> loitav
  {"avan",2},
  # cantaríamo -> cant
  {"aríamo",2},
  # cantassen -> cant
  {"assen",2},
  # cantássemo -> cant
  {"ássemo",2},
  # beberíamo -> beb
  {"eríamo",2},
  # bebêssemo -> beb
  {"êssemo",2},
  # partiríamo -> part
  {"iríamo",3},
  # partíssemo -> part
  {"íssemo",3},
  # cantáramo -> cant
  {"áramo",2},
  # cantárei -> cant
  {"árei",2},
  # cantaren -> cant
  {"aren",2},
  # cantaremo -> cant
  {"aremo",2},
  # cantaríei -> cant
  {"aríei",2},
  {"ássei",2},
  # cantávamo-> cant
  {"ávamo",2},
  # bebêramo -> beb
  {"êramo",1},
  # beberemo -> beb
  {"eremo",1},
  # beberíei -> beb
  {"eríei",1},
  # bebêssei -> beb
  {"êssei",1},
  # partiríamo -> part
  {"íramo",3},
  # partiremo -> part
  {"iremo",3},
  # partiríei -> part
  {"iríei",3},
  # partíssei -> part
  {"íssei",3},
  # partissen -> part
  {"issen",3},
  # bebendo -> beb
  {"endo",1},
  # partindo -> part
  {"indo",3},
  # propondo -> prop
  {"ondo",3},
  # cantarde -> cant
  {"arde",2},
  # cantarei -> cant
  {"arei",2},
  # cantaria -> cant
  {"aria",2},
  # cantarmo -> cant
  {"armo",2},
  # cantasse -> cant
  {"asse",2},
  {"aste",2},
  # cantávei -> cant
  {"ávei",2},
  # perderão -> perd
  {"erão",1},
  # beberde -> beb
  {"erde",1},
  # beberei -> beb
  {"erei",1},
  # bebêrei -> beb
  {"êrei",1},
  # beberen -> beb
  {"eren",2},
  # beberia -> beb
  {"eria",1},
  # bebermo -> beb
  {"ermo",1},
  # bebeste -> beb
  {"este",1,"",{"faroeste","agreste"}},
  # bebíamo -> beb
  {"íamo",1},
  # fuxian -> fux
  {"ian",2,"",{"enfian","eloxian","ensaian"}},
  # partirde -> part
  {"irde",2},
  # partírei -> part
  {"irei",3,"",{"admirei"}},
  # partiren -> part
  {"iren",3},
  # partiria -> part
  {"iria",3},
  # partirmo -> part
  {"irmo",3},
  # partisse -> part
  {"isse",3},
  # partiste -> part
  {"iste",4},
  {"iava",1,"",{"ampliava"}},
  # cantamo -> cant
  {"amo",2},
  # funciona -> func
  {"iona",3},
  # cantara -> cant
  {"ara",2,"",{"arara","prepara"}},
  # enviará -> envi
  {"ará",2,"",{"alvará","bacará"}},
  # cantare -> cant
  {"are",2,"",{"prepare"}},
  # cantava -> cant
  {"ava",2,"",{"agrava"}},
  # cantemo -> cant
  {"emo",2},
  # bebera -> beb
  {"era",1,"",{"acelera","espera"}},
  # beberá -> beb
  {"erá",1},
  # bebere -> beb
  {"ere",1,"",{"espere"}},
  # bebíei -> beb
  {"íei",1},
  # metin -> met
  {"in",3},
  # partimo -> part
  {"imo",3,"",{"reprimo","intimo","íntimo","nimo","queimo","ximo"}},
  # partira -> part
  {"ira",3,"",{"fronteira","sátira"}},
  {"ído",3},
  # partirá -> part
  {"irá",3},
  # concretizar -> concret
  {"tizar",4,"",{"alfabetizar"}},
  {"izar",3,"",{"organizar"}},
  # saltitar -> salt
  {"itar",5,"",{"acreditar","explicitar","estreitar"}},
  # partire -> part
  {"ire",3,"",{"adquire"}},
  # compomo -> comp
  {"omo",3},
  {"ai",2},
  # barbear -> barb
  {"ear",4,"",{"alardear","nuclear"}},
  # cheguei -> cheg
  {"uei",3},
  {"uía",5,"u"},
  # cantei -> cant
  {"ei",3},
  # beber -> beb
  {"er",1,"",{"éter","pier"}},
  # bebeu -> beb
  {"eu",1,"",{"chapeu"}},
  # bebia -> beb
  {"ia",1,"",{"estória","fatia","acia","praia","elogia","mania","lábia","aprecia","polícia","arredia","cheia","ásia"}},
  # partir -> part
  {"ir",3},
  # partiu -> part
  {"iu",3},
  # fraqueou -> fraqu
  {"eou",5},
  # chegou -> cheg
  {"ou",3},
  # bebi -> beb
  {"i",1},
  # varrede -> varr
  {"ede",1,"",{"rede","bípede","céspede","parede","palmípede","vostede","hóspede","adrede"}},
  # cantei -> cant
  {"ei",3},
  # anden -> and
  {"en",2},
  # descerade -> desc
  {"erade",1},
  # vivérade -> viv
  {"érade",1},
  # beberan -> beb
  {"eran",2},
  # colleramo -> coller
  {"eramo",1},
  # bebéramo -> beb
  {"éramo",1},
  # perderán -> perd
  {"erán",1},
  # varrería -> varr
  {"ería",1},
  # beberiade -> beb
  {"eriade",1},
  # beberíade -> beb
  {"eríade",1},
  # beberiamo -> beb
  {"eriamo",1},
  # beberian -> beb
  {"erian",1},
  # beberían -> beb
  {"erían",1},
  # perderon -> perd
  {"eron",1},
  # bebese -> beb
  {"ese",1},
  # bebesedes -> beb
  {"esedes",1},
  # bebésedes -> beb
  {"ésedes",1}, 
  # bebesemo -> beb
  {"esemo",1},
  # bebésemo -> beb
  {"ésemo",1},
  # bebesen -> beb
  {"esen",1},
  # bebêssede -> beb 
  {"êssede",1},
  # chovía -> chov
  {"ía",1},
  # faciade -> fac
  {"iade",1},
  # facíade -> fac
  {"íade",1},
  # perdiamo -> perd
  {"iamo",1},
  # fuxían -> fux 
  {"ían",1},
  # corriche -> corr
  {"iche",1},
  # partide -> part
  {"ide",1},
  # escribirade -> escrib
  {"irade",3},
  # parírade -> par
  {"írade",3},
  # partiramo -> part
  {"iramo",3}, 
  # fugirán -> fug
  {"irán",3},
  # viviría -> viv
  {"iría",3},
  # partiriade -> part
  {"iriade",3},
  # partiríade -> part
  {"iríade",3},
  # partiriamo -> part
  {"iriamo",3}, 
  # partirian -> part
  {"irian",3},
  # partirían -> part
  {"irían",3},
  # reflectiron -> reflect
  {"iron",3},
  # partise -> part
  {"ise",3},
  # partisede -> part
  {"isede",3},
  # partísede -> part
  {"ísede",3},
  # partisemo -> part
  {"isemo",3},
  # partísemo -> part
  {"ísemo",3},
  # partisen -> part
  {"isen",3},
  # partíssede -> part
  {"íssede",3}, 
  {"tizar",3,"",{"alfabetizar"}},
  {"ondo",3}};

{  "Vowel", 0, 0, {},
  # segue -> seg
  {"gue",2,"g",{"azougue","dengue","merengue","nurague","merengue","rengue"}},
  {"que",2,"c",{"alambique","albaricoque","abaroque","alcrique","almadraque","almanaque","arenque","arinque","baduloque","ballestrinque","betoque","bivaque","bloque","bodaque","bosque","breque","buque","cacique","cheque","claque","contradique","coque","croque","dique","duque","enroque","espeque","estoque","estoraque","estraloque","estrinque","milicroque","monicreque","orinque","arinque","palenque","parque","penique","picabeque","pique","psique","raque","remolque","xeque","repenique","roque","sotobosque","tabique","tanque","toque","traque","truque","vivaque","xaque"}},
  {"a",3,"",{"amasadela","cerva"}},
  {"e",3,"",{"marte"}},
  {"o",3,"",{"barro","fado","cabo","libro","cervo"}},
  {"â",3},
  {"ã",3,"",{"amanhã","arapuã","fã","divã","manhã"}},
  {"ê",3},
  {"ô",3},
  {"á",3},
  {"é",3},
  {"ó",3},
  # munxi -> munx
  {"i",3}};
"""


        private fun parseInternal(resource: String): Map<String, Step> {
            val data = when (resource) {
                "portuguese.rslp" -> PORTUGUESE_RSLP_DATA
                "galician.rslp" -> GALICIAN_RSLP_DATA
                else -> readResource(resource)
            }

            val reader = LineReader(BufferedReader(StringReader(data)))
            val steps = mutableMapOf<String, Step>()
            var header = reader.readLine()
            while (header != null) {
                val step = parseStep(reader, header)
                steps[step.name] = step
                header = reader.readLine()
            }
            return steps
        }

        private fun readResource(resource: String): String {
            try {
                ClasspathResourceLoader(RSLPStemmerBase::class).openResource(resource).use { input ->
                    InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                        val builder = StringBuilder()
                        val buffer = CharArray(1024)
                        while (true) {
                            val count = reader.read(buffer, 0, buffer.size)
                            if (count == -1) break
                            builder.append(buffer.concatToString(0, count))
                        }
                        return builder.toString()
                    }
                }
            } catch (e: Exception) {
                throw RuntimeException("Unable to load resource: $resource", e)
            }
        }

        private fun parseStep(reader: LineReader, header: String): Step {
            val matcher = HEADER_PATTERN.matchEntire(header)
                ?: throw RuntimeException("Illegal Step header specified at line ${reader.lineNumber}")
            val name = matcher.groupValues[1]
            val min = matcher.groupValues[2].toInt()
            val type = matcher.groupValues[3].toInt()
            val suffixes = parseList(matcher.groupValues[4])
            val rules = parseRules(reader, type)
            return Step(name, rules, min, suffixes)
        }

        private fun parseRules(reader: LineReader, type: Int): Array<Rule> {
            val rules = mutableListOf<Rule>()
            var line = reader.readLine()
            while (line != null) {
                val stripMatch = STRIP_PATTERN.matchEntire(line)
                if (stripMatch != null) {
                    rules.add(Rule(stripMatch.groupValues[1], stripMatch.groupValues[2].toInt(), ""))
                } else {
                    val repMatch = REP_PATTERN.matchEntire(line)
                    if (repMatch != null) {
                        rules.add(Rule(repMatch.groupValues[1], repMatch.groupValues[2].toInt(), repMatch.groupValues[3]))
                    } else {
                        val excMatch = EXC_PATTERN.matchEntire(line)
                        if (excMatch != null) {
                            val suffix = excMatch.groupValues[1]
                            val min = excMatch.groupValues[2].toInt()
                            val replacement = excMatch.groupValues[3]
                            val exceptions = parseList(excMatch.groupValues[4]) ?: emptyArray()
                            if (type == 0) {
                                rules.add(RuleWithSuffixExceptions(suffix, min, replacement, exceptions))
                            } else {
                                rules.add(RuleWithSetExceptions(suffix, min, replacement, exceptions))
                            }
                        } else {
                            throw RuntimeException("Illegal Step rule specified at line ${reader.lineNumber}")
                        }
                    }
                }
                if (line.endsWith(";")) return rules.toTypedArray()
                line = reader.readLine()
            }
            return emptyArray()
        }

        private fun parseList(s: String): Array<String>? {
            if (s.isEmpty()) return null
            val list = s.split(",")
            val parsed = Array(list.size) { index -> parseString(list[index].trim()) }
            return parsed
        }

        private fun parseString(s: String): String {
            return s.substring(1, s.length - 1)
        }

        private class LineReader(private val reader: BufferedReader) {
            var lineNumber: Int = 0
                private set

            fun readLine(): String? {
                while (true) {
                    val raw = reader.readLine() ?: return null
                    lineNumber++
                    val line = raw.trim()
                    if (line.isNotEmpty() && line[0] != '#') {
                        return line
                    }
                }
            }
        }
    }
}
