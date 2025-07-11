package org.gnit.lucenekmp.progress

import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import io.github.classgraph.MethodInfo
import io.github.classgraph.ScanResult

// Method status enumeration for tracking porting progress

enum class MethodStatus(val printedName: String) {
    PORTED("[x] Ported"),
    MISSING_IN_KMP("[ ] Missing"),
    EXTRA_IN_KMP("Extra in KMP"),
    IGNORED("Ignored"),
    UNKNOWN("Unknown")
}

const val javaBasePackage = "org.apache.lucene"
const val kmpBasePackage = "org.gnit.lucenekmp"

enum class SourceType(val lowerCaseName: String) {
    MAIN("main"),
    TEST("test")
}

class Progress() {
    val homeDir: String = System.getenv("HOME") ?: System.getProperty("user.home")
    val lucenePortProjectRoot = "$homeDir/code/lp"
    val javaDir = "$lucenePortProjectRoot/lucene"
    val kmpDir = "$lucenePortProjectRoot/lucene-kmp"

    fun javaClassPath(moduleName: String, sourceType: SourceType): String {
        val mainOrTest = sourceType.lowerCaseName
        return "$javaDir/lucene/$moduleName/build/classes/java/$mainOrTest"
    }

    fun kmpClassPath(moduleName: String, sourceType: SourceType): String {
        val mainOrTest = sourceType.lowerCaseName
        return "$kmpDir/$moduleName/build/classes/kotlin/jvm/$mainOrTest"
    }

    val javaScanResultCache: MutableMap<String, ScanResult> = mutableMapOf()

    fun javaScanResult(javaClassPathList: List<String>): ScanResult {
        val javaClassPathKey = javaClassPathList.joinToString(separator = ":")
        var scanResult = javaScanResultCache[javaClassPathKey]

        if (scanResult == null) {
            scanResult = ClassGraph().enableAllInfo().overrideClasspath(javaClassPathList).scan()
            javaScanResultCache[javaClassPathKey] = scanResult
        }

        return scanResult
    }

    val kmpScanResultCache: MutableMap<String, ScanResult> = mutableMapOf()

    fun kmpScanResult(kmpClassPathList: List<String>): ScanResult {
        val kmpClassPathKey = kmpClassPathList.joinToString(separator = ":")
        var scanResult = kmpScanResultCache[kmpClassPathKey]

        if (scanResult == null) {
            scanResult = ClassGraph().enableAllInfo().overrideClasspath(kmpClassPathList).scan()
            kmpScanResultCache[kmpClassPathKey] = scanResult
        }

        return scanResult
    }

    val boxingMap = mutableMapOf(
        "java.lang.Integer" to "int",
        "java.lang.Long" to "long",
        "java.lang.Float" to "float",
        "java.lang.Double" to "double",
    )

    fun String.normalizeBoxedTypeName(): String {
        var toBeReplaced = this

        boxingMap.forEach { (boxedType, primitiveType) ->
            toBeReplaced = toBeReplaced.replace(boxedType, primitiveType)
        }

        return toBeReplaced
    }

    val portedFromMap = mutableMapOf(
        "java.util.IdentityHashMap" to "java.util.Map",
        "java.util.stream.LongStream" to "kotlin.sequences.Sequence",

        "java.io.Closeable" to "java.lang.AutoCloseable",

        "java.util.function.Supplier" to "kotlin.jvm.functions.Function0",
        "java.util.function.BooleanSupplier" to "kotlin.jvm.functions.Function0",

        "java.util.function.Function" to "kotlin.jvm.functions.Function1",
        "java.util.function.IntFunction" to "kotlin.jvm.functions.Function1",
        "java.util.function.Predicate" to "kotlin.jvm.functions.Function1",
        "java.util.function.LongPredicate" to "kotlin.jvm.functions.Function1",

        "java.util.function.BiPredicate" to "kotlin.jvm.functions.Function2",
        "java.util.function.BiFunction" to "kotlin.jvm.functions.Function2",

        "java.lang.Class" to "kotlin.reflect.KClass",
        "java.lang.Thread" to "kotlinx.coroutines.Job",

        "java.util.Random" to "kotlin.random.Random",
        "java.util.SplittableRandom" to "kotlin.random.Random",

        "java.nio.file.Path" to "okio.Path",

        "java.math.BigInteger" to "com.ionspin.kotlin.bignum.integer.BigInteger",

        "org.apache.lucene.util.automaton.RegExp\$MakeRegexGroup" to "kotlin.jvm.functions.Function3",
    )

    val kmpClassPathList = listOf(
        kmpClassPath("core", SourceType.MAIN),
        kmpClassPath("core", SourceType.TEST),
    )

    val javaClassPathList = listOf(
        javaClassPath("core", SourceType.MAIN),
        javaClassPath("core", SourceType.TEST),
    )

    init {
        val allKmpClasses = kmpScanResult(kmpClassPathList).allClasses
        val allJdkPortClasses =
            allKmpClasses.filter { it.name.startsWith("$kmpBasePackage.jdkport") && !it.name.endsWith("Kt") }
        allJdkPortClasses.forEach { jdkPortClass ->
            val portedAnnotation =
                jdkPortClass.annotationInfo.firstOrNull { it.name == "org.gnit.lucenekmp.jdkport.Ported" }
            if (portedAnnotation != null) {
                val fromValue = portedAnnotation.parameterValues.getValue("from").toString()
                portedFromMap[fromValue] = jdkPortClass.name
            }
        }
    }

    fun String.normalizeJdkPortedTypeName(): String {

        var toBeReplaced = this

        portedFromMap.forEach { (javaFqn, kmpFqn) ->
            toBeReplaced = toBeReplaced.replace(javaFqn, kmpFqn)
        }

        return toBeReplaced
    }

    fun getJavaClass(javaFqn: String): ClassInfo {
        val allJavaClasses = javaScanResult(javaClassPathList).allClasses
        return allJavaClasses.first { it.name == javaFqn }
    }

    fun getKmpClass(kmpFqn: String): ClassInfo {
        val allKmpClasses = kmpScanResult(kmpClassPathList).allClasses
        return allKmpClasses.first { it.name == kmpFqn }
    }

    fun analyzeClass(javaFqn: String) {
        val javaClass = getJavaClass(javaFqn)
        val kmpClass = getKmpClass(javaFqn.replace(javaBasePackage, kmpBasePackage))

        val javaMethodInfos = javaClass.methodInfo
        val javaInnerClasses = javaClass.innerClasses
        val javaFields = javaClass.fieldInfo

        val kmpMethodInfos = kmpClass.methodInfo.toMutableList()

        // Recursively collect companion object methods from inheritance hierarchy
        fun collectCompanionMethods(classInfo: ClassInfo) {
            // Check for companion object in current class/interface
            val companionObject = classInfo.getCompanionObject()
            if (companionObject != null) {
                kmpMethodInfos += companionObject.methodInfo
            }

            // Recursively check superclass
            classInfo.superclass?.let { collectCompanionMethods(it) }

            // Recursively check interfaces
            classInfo.interfaces.forEach { collectCompanionMethods(it) }
        }

        collectCompanionMethods(kmpClass)

        val kmpInnerClasses = kmpClass.innerClasses
        val kmpFields = kmpClass.fieldInfo

        val term = Terminal(width = 600)

        // Compare method lists by making table, method details needed to be compared
        val methodHeaders = listOf("Method Name", "Java Signature", "KMP Signature", "Status")
        val methodRows = mutableListOf<List<String>>()

        // Create maps for easier lookup
        val javaMethodMap =
            javaMethodInfos.associateBy { methodInfo ->

                val methodName =
                    if (javaClass.isRecord && (methodInfo.name in javaFields.map { it.name }) && !methodInfo.isSynthetic) {
                        "${methodInfo.name}()".normalizeRecordMethodName() // prepend with "get" and capitalize first letter
                    } else {
                        methodInfo.name
                    }

                "${methodName}(${methodInfo.parameterInfo.joinToString(",") { param -> param.typeDescriptor.toString() }})"
            }

        val kmpMethodMap =
            kmpMethodInfos.associateBy { methodInfo ->
                val suspendSignature = reconstructSuspendSignature(methodInfo)
                val signature = if (suspendSignature != null) {
                    val name = suspendSignature.substringBefore("(")
                    val params = suspendSignature.substringAfter("(").substringBefore(")")
                    "$name($params)"
                } else {
                    "${methodInfo.name}(${methodInfo.parameterInfo.joinToString(",") { param -> param.typeDescriptor.toString() }})"
                }

                signature.normalizeBoxedTypeName()
            }

        // Get all unique method signatures
        val allMethodSignatures =
            (javaMethodMap.keys + kmpMethodMap.keys).toSet().sorted()
                .filterNot { it.startsWith("access$") }
                .filterNot { it.startsWith("lambda$") }

        allMethodSignatures.forEach { javaSignature ->
            val javaMethod = javaMethodMap[javaSignature]

            val kmpSignature = javaSignature
                .normalizeBoxedTypeName()
                .normalizeJdkPortedTypeName()
                .replace(javaBasePackage, kmpBasePackage)

            val kmpMethod = kmpMethodMap[kmpSignature]

            val methodName = javaSignature.substringBefore("(")
            val javaSignatureToPrint = javaMethod?.toString().cleanMethodSignature() ?: "Missing"
            val kmpSignatureToPrint = kmpMethod?.toString()?.cleanMethodSignature() ?: "Missing"

            val ignoredMethodNames = listOf(
                "keySet", "put", "size", "equals", "hashCode", "toString",
            )

            val status = when {
                javaMethod != null && kmpMethod != null -> MethodStatus.PORTED

                // Handle Kotlin default parameters case
                javaMethod != null && kmpMethod == null && hasKotlinDefaultParameterVariant(kmpMethodMap, javaSignature) -> MethodStatus.PORTED

                // special cases core/main
                javaClass.name == "org.apache.lucene.store.RandomAccessInput" && javaMethod?.name == "isLoaded" -> MethodStatus.IGNORED
                javaClass.name == "org.apache.lucene.search.SloppyPhraseMatcher" && javaMethod?.name == "ppTermsBitSets" -> MethodStatus.PORTED // HashMap / LinkedHashMap difference remains
                javaClass.name == "org.apache.lucene.search.SloppyPhraseMatcher" && javaMethod?.name == "repeatingPPs" -> MethodStatus.PORTED // HashMap / LinkedHashMap difference remains
                javaClass.name == "org.apache.lucene.search.Multiset" && javaMethod?.name == "remove" -> MethodStatus.PORTED // as removeKt()
                javaClass.name == "org.apache.lucene.internal.vectorization.DefaultVectorUtilSupport" && javaMethod?.name == "fma" -> MethodStatus.IGNORED
                javaClass.name == "org.apache.lucene.internal.vectorization.DefaultVectorUtilSupport" && javaMethod?.name == "int4BitDotProductImpl" -> MethodStatus.IGNORED
                javaClass.name == "org.apache.lucene.internal.hppc.LongArrayList" && javaMethod?.name == "stream" -> MethodStatus.PORTED // as asSequence()
                javaClass.name == "org.apache.lucene.index.FieldInfo" && javaMethod?.name == "getFieldNumber" -> MethodStatus.IGNORED // as field val number
                javaClass.name == "org.apache.lucene.geo.Circle2D\$HaversinDistance" && javaMethod?.name == "geX" -> MethodStatus.PORTED // as field val x, typo of getX, sent PR https://github.com/apache/lucene/pull/14898
                javaClass.name == "org.apache.lucene.geo.Circle2D\$DistanceCalculator" && javaMethod?.name == "geX" -> MethodStatus.PORTED // as field val x, typo of getX, sent PR https://github.com/apache/lucene/pull/14898
                javaClass.name == "org.apache.lucene.geo.Circle2D\$CartesianDistance" && javaMethod?.name == "geX" -> MethodStatus.PORTED // as field val x, typo of getX, sent PR https://github.com/apache/lucene/pull/14898
                javaClass.name == "org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsReader\$FieldEntry" && javaMethod?.name == "M" -> MethodStatus.PORTED // as field val M
                javaClass.name == "org.apache.lucene.analysis.standard.StandardAnalyzer" && javaMethod?.name == "getStopwordSet" -> MethodStatus.PORTED // as field val stopwords which turns into getStopwords()
                javaClass.name == "org.apache.lucene.analysis.StopwordAnalyzerBase" && javaMethod?.name == "getStopwordSet" -> MethodStatus.PORTED // as field val stopwords which turns into getStopwords()

                // special cases core/test

                // general cases
                (javaMethod != null && javaMethod.name in ignoredMethodNames) && kmpMethod == null -> MethodStatus.IGNORED
                (javaMethod != null && javaMethod.name !in ignoredMethodNames) && kmpMethod == null -> MethodStatus.MISSING_IN_KMP
                javaMethod == null && kmpMethod != null -> MethodStatus.EXTRA_IN_KMP
                else -> MethodStatus.UNKNOWN
            }

            methodRows.add(listOf(methodName, javaSignatureToPrint, kmpSignatureToPrint, status.printedName))
        }

        val methodTable = table {
            header { row(*methodHeaders.toTypedArray()) }
            body {
                methodRows.forEach { rowData ->
                    row(*rowData.toTypedArray())
                }
            }
        }

        // Summary statistics
        val totalJavaMethods = javaMethodInfos.size
        val totalKmpMethods = kmpMethodInfos.size
        val portedMethods = methodRows.count { it[3] == MethodStatus.PORTED.printedName }
        val missingMethods = methodRows.count { it[3] == MethodStatus.MISSING_IN_KMP.printedName }
        val extraMethods = methodRows.count { it[3] == MethodStatus.EXTRA_IN_KMP.printedName }
        val ignoredMethods = methodRows.count { it[3] == MethodStatus.IGNORED.printedName }
        val relevantJavaMethods = totalJavaMethods - ignoredMethods
        val portingProgress = if (relevantJavaMethods > 0) {
            if (missingMethods == 0) 100 else (portedMethods * 100) / relevantJavaMethods
        } else {
            100
        }

        if (portingProgress != 100) {
            term.println("Method Comparison for:")
            term.println(javaClass.name)
            term.println(kmpClass.name)
            term.println(methodTable)
            term.println("\nSummary:")
            term.println("- Total Java Methods: $totalJavaMethods")
            term.println("- Total KMP Methods: $totalKmpMethods")
            term.println("- Ported Methods: $portedMethods")
            term.println("- Missing in KMP: $missingMethods")
            term.println("- Extra in KMP: $extraMethods")
            term.println("- Ignored Methods: $ignoredMethods")
            term.println("- Porting Progress: $portingProgress%")
            term.println()
        } else {
            // term.println("${javaClass.name} methods ported to ${kmpClass.name} 100%")
        }
    }

    fun analyzeAllClasses() {
        val allJavaClasses = javaScanResult(javaClassPathList).allClasses
        val allKmpClasses = kmpScanResult(kmpClassPathList).allClasses

        /**
         * we need to port these but we first port their dependencies, so we skip them for now
         */
        val skipClasses = listOf(
            "org.apache.lucene.search.IndexSearcher",
            "org.apache.lucene.internal.vectorization.VectorizationProvider",
            "org.apache.lucene.internal.vectorization.DefaultVectorizationProvider",
            "org.apache.lucene.internal.tests.TestSecrets",
            "org.apache.lucene.index.LiveIndexWriterConfig",
            "org.apache.lucene.index.IndexWriterConfig",
            "org.apache.lucene.index.IndexWriter",
        )

        allJavaClasses
            .filterNot { it.name in skipClasses }
            .filterNot { it.name.endsWithDollarSignAndDigit() }
            .forEach { javaClass ->
            val kmpFqn = javaClass.name.replace(javaBasePackage, kmpBasePackage)
            if (allKmpClasses.any { it.name == kmpFqn }) {
                analyzeClass(javaClass.name)
            } else {
                // println("KMP class not found for Java class: ${javaClass.name}")
            }
        }
    }
}

// Add this helper function to detect Kotlin default parameter methods
fun hasKotlinDefaultParameterVariant(kmpMethodMap: Map<String, MethodInfo>, baseSignature: String): Boolean {
    // Extract method name and parameters from signature like "methodName(params)"
    val methodName = baseSignature.substringBefore("(")
    val baseParams = baseSignature.substringAfter("(").substringBefore(")")
    val baseParamList = if (baseParams.isEmpty()) emptyList() else baseParams.split(",")

    // Look for Kotlin methods with the same name that have more parameters
    return kmpMethodMap.keys.any { kmpSignature ->
        val kmpMethodName = kmpSignature.substringBefore("(")
        val kmpParams = kmpSignature.substringAfter("(").substringBefore(")")
        val kmpParamList = if (kmpParams.isEmpty()) emptyList() else kmpParams.split(",")

        // Check if this is the same method name with more parameters than the base
        if (kmpMethodName == methodName && kmpParamList.size > baseParamList.size) {
            // Check if the first N parameters match (where N is the number of base parameters)
            val baseParamCount = baseParamList.size
            val kmpFirstNParams = kmpParamList.take(baseParamCount)

            // Parameters should match (allowing for type normalization)
            kmpFirstNParams.zip(baseParamList).all { (kmpParam, baseParam) ->
                kmpParam.trim() == baseParam.trim()
            }
        } else {
            false
        }
    }
}

fun ClassInfo.getCompanionObject(): ClassInfo? {
    return this.innerClasses.firstOrNull {
        it.name == "${this.name}\$Companion"
    }
}

fun String.endsWithDollarSignAndDigit(): Boolean {
    // Check if the string ends with a dollar sign followed by a digit
    return this.matches(Regex(".*\\$\\d$"))
}

fun String?.cleanMethodSignature(): String? {
    return this?.replace("@org.jetbrains.annotations.NotNull ", "")
        ?.replace("@org.jetbrains.annotations.Nullable ", "")
        //?.replace(javaBasePackage, "o.a.l")
        //?.replace(kmpBasePackage, "o.g.l")
        ?.substringBeforeLast(" throws ")
}

// record related utilities

fun String.normalizeRecordMethodName(): String {
    // Handle Java record accessor methods vs Kotlin getter methods
    return when {
        // Convert Java record accessor methods to Kotlin getter style
        this == "toString()" -> "toString"
        this == "hashCode()" -> "hashCode"
        this == "equals()" -> "equals"
        // Handle boolean getters that start with "is"
        this.matches(Regex("^is[A-Z][a-zA-Z0-9]*\\(\\)$")) -> this.substringBefore('(')
        this.matches(Regex("^[a-z][a-zA-Z0-9]*\\(\\)$")) -> "get${this.first().uppercase()}${
            this.substring(
                1,
                this.indexOf('(')
            )
        }"

        else -> this.substringBefore('(')
    }
}

// suspend function related utilities

fun extractReturnTypeFromContinuation(continuationTypeStr: String): String {
    // Extract the actual type from Continuation<? super ReturnType>
    val regex = Regex("Continuation<\\? super ([^>]+)>")
    val match = regex.find(continuationTypeStr)
    return match?.groupValues?.get(1) ?: "void"
}

fun isSuspendFunction(methodInfo: io.github.classgraph.MethodInfo): Boolean {
    // Check if the method has Continuation as the last parameter
    val params = methodInfo.parameterInfo
    return params.isNotEmpty() &&
            params.last().typeDescriptor.toString().contains("kotlin.coroutines.Continuation")
}

fun reconstructSuspendSignature(methodInfo: io.github.classgraph.MethodInfo): String? {
    if (!isSuspendFunction(methodInfo)) return null

    // Remove the Continuation parameter
    val paramsWithoutContinuation = methodInfo.parameterInfo.dropLast(1)

    // Extract the actual return type from Continuation generic
    val continuationParam = methodInfo.parameterInfo.last()
    val continuationTypeStr = continuationParam.typeDescriptor.toString()

    val returnType = extractReturnTypeFromContinuation(continuationTypeStr)

    // Use parameter types directly from ClassGraph
    val params = paramsWithoutContinuation.joinToString(",") { param ->
        param.typeDescriptor.toString()
    }

    return "${methodInfo.name}($params):$returnType"
}

fun main() {
    val progress = Progress()
    progress.analyzeAllClasses()
}
