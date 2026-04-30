#!/usr/bin/env -S kotlin -J--enable-native-access=ALL-UNNAMED -J--sun-misc-unsafe-memory-access=allow

@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:5.0.3")
@file:DependsOn("com.github.ajalt.mordant:mordant:3.0.2")
@file:DependsOn("io.github.classgraph:classgraph:4.8.180")

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import io.github.classgraph.MethodInfo
import io.github.classgraph.ScanResult
import java.io.File
import kotlin.text.replace

val javaLuceneCommitId = "ec75fcad5a4208c7b9e35e870229d9b703cda8f3"

// priority‑1 API lists
private val coreWrite = setOf(
    "org.apache.lucene.index.IndexWriter",
    "org.apache.lucene.index.IndexWriterConfig",
    "org.apache.lucene.store.FSDirectory",
    "org.apache.lucene.analysis.Analyzer",
    "org.apache.lucene.document.Document",
    "org.apache.lucene.document.Field",
    "org.apache.lucene.document.IntPoint",
    "org.apache.lucene.document.StoredField",
    "org.apache.lucene.document.TextField"
)

private val coreSearch = setOf(
    "org.apache.lucene.index.DirectoryReader",
    "org.apache.lucene.index.StandardDirectoryReader",
    "org.apache.lucene.queryparser.classic.QueryParser",
    "org.apache.lucene.search.IndexSearcher",
    "org.apache.lucene.store.FSLockFactory",
    "org.apache.lucene.store.NIOFSDirectory",
    "org.apache.lucene.document.IntPoint",
    "org.apache.lucene.search.Query",
    "org.apache.lucene.search.BooleanQuery",
    "org.apache.lucene.search.BooleanClause",
    "org.apache.lucene.search.Sort",
    "org.apache.lucene.search.SortField"
)

private val pri1Names get() = coreWrite + coreSearch

val modulesToPort = listOf(
    "core",
    "test-framework",
    "queryparser",
    "queries",
    "analysis",
    "codecs",
)

val srcDirMap = mapOf(
    "main" to "java",
    "test" to "test",
)

val externalLibraryJavaKmpRefs = mapOf(
    "java.io.Closeable" to setOf("okio.Closeable"),
    "java.io.EOFException" to setOf("okio.EOFException"),
    "java.io.FileNotFoundException" to setOf("okio.FileNotFoundException"),
    "java.io.IOException" to setOf("okio.IOException"),
    "java.io.InputStream" to setOf("okio.Source", "okio.BufferedSource", "okio.GzipSource"),
    "java.io.OutputStream" to setOf("okio.Sink", "okio.BufferedSink"),
    "java.lang.ArrayIndexOutOfBoundsException" to setOf("okio.ArrayIndexOutOfBoundsException"),
    "java.lang.Class" to setOf("kotlin.reflect.KClass"),
    "java.lang.Runnable" to setOf("kotlinx.coroutines.Runnable"),
    "java.math.BigDecimal" to setOf("com.ionspin.kotlin.bignum.decimal.BigDecimal"),
    "java.math.BigInteger" to setOf("com.ionspin.kotlin.bignum.integer.BigInteger"),
    "java.nio.channels.FileChannel" to setOf("okio.FileHandle"),
    "java.nio.channels.SeekableByteChannel" to setOf("okio.FileHandle"),
    "java.nio.file.FileSystem" to setOf("okio.FileSystem"),
    "java.nio.file.Path" to setOf("okio.Path"),
    "java.nio.file.attribute.BasicFileAttributes" to setOf("okio.FileMetadata"),
    "java.time.Instant" to setOf("kotlinx.datetime.Instant"),
    "java.time.LocalDate" to setOf("kotlinx.datetime.LocalDate"),
    "java.time.LocalDateTime" to setOf("kotlinx.datetime.LocalDateTime"),
    "java.time.LocalTime" to setOf("kotlinx.datetime.LocalTime"),
    "java.time.ZoneId" to setOf("kotlinx.datetime.TimeZone"),
    "java.util.Random" to setOf("kotlin.random.Random"),
    "java.util.SplittableRandom" to setOf("kotlin.random.Random"),
    "java.util.TimeZone" to setOf("kotlinx.datetime.TimeZone"),
    "java.util.concurrent.CancellationException" to setOf("kotlinx.coroutines.CancellationException"),
    "java.util.concurrent.CompletableFuture" to setOf("kotlinx.coroutines.Deferred", "kotlinx.coroutines.CompletableDeferred"),
    "java.util.concurrent.Executor" to setOf("kotlinx.coroutines.CoroutineScope"),
    "java.util.concurrent.Future" to setOf("kotlinx.coroutines.Deferred", "kotlinx.coroutines.Job"),
    "java.util.concurrent.RunnableFuture" to setOf("kotlinx.coroutines.Job"),
    "java.util.concurrent.TimeoutException" to setOf("kotlinx.coroutines.TimeoutCancellationException"),
    "java.util.function.BiConsumer" to setOf("kotlin.jvm.functions.Function2"),
    "java.util.function.BiFunction" to setOf("kotlin.jvm.functions.Function2"),
    "java.util.function.BiPredicate" to setOf("kotlin.jvm.functions.Function2"),
    "java.util.function.BooleanSupplier" to setOf("kotlin.jvm.functions.Function0"),
    "java.util.function.Consumer" to setOf("kotlin.jvm.functions.Function1"),
    "java.util.function.DoublePredicate" to setOf("kotlin.jvm.functions.Function1"),
    "java.util.function.DoubleToLongFunction" to setOf("kotlin.jvm.functions.Function1"),
    "java.util.function.Function" to setOf("kotlin.jvm.functions.Function1"),
    "java.util.function.IntConsumer" to setOf("kotlin.jvm.functions.Function1"),
    "java.util.function.IntFunction" to setOf("kotlin.jvm.functions.Function1"),
    "java.util.function.IntPredicate" to setOf("kotlin.jvm.functions.Function1"),
    "java.util.function.IntSupplier" to setOf("kotlin.jvm.functions.Function0"),
    "java.util.function.IntToLongFunction" to setOf("kotlin.jvm.functions.Function1"),
    "java.util.function.LongPredicate" to setOf("kotlin.jvm.functions.Function1"),
    "java.util.function.LongSupplier" to setOf("kotlin.jvm.functions.Function0"),
    "java.util.function.LongToDoubleFunction" to setOf("kotlin.jvm.functions.Function1"),
    "java.util.function.Predicate" to setOf("kotlin.jvm.functions.Function1"),
    "java.util.function.Supplier" to setOf("kotlin.jvm.functions.Function0"),
    "java.util.function.ToDoubleFunction" to setOf("kotlin.jvm.functions.Function1"),
    "java.util.function.ToIntFunction" to setOf("kotlin.jvm.functions.Function1"),
    "java.util.function.ToLongFunction" to setOf("kotlin.jvm.functions.Function1"),
    "java.util.function.UnaryOperator" to setOf("kotlin.jvm.functions.Function1"),
    // TODO add more pairs of Java classes replaced by external KMP libraries.
)

private val portedAnnotationFqn = "org.gnit.lucenekmp.jdkport.Ported"
private val semanticTypeAliases = mutableMapOf<String, String>()
private val semanticTypeEquivalentKeys = mutableMapOf<String, MutableSet<String>>()
private val companionClassInfoByOwnerName = mutableMapOf<String, ClassInfo>()

// Helper functions to create markdown links for Java FQNs
fun markdownLinkForJavaFQN(classInfo: ClassInfo): String {
    // for exammple: https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/Analyzer.java

    val classPath = classInfo.classpathElementURL.toString()
    val luceneModule = modulesToPort.first { classPath.contains("/$it/build/classes/java/") }

    //println("classPath: $classPath luceneModule: $luceneModule")

    val srcDir = srcDirMap.entries.first { classPath.contains("/$luceneModule/build/classes/java/${it.key}/") }.value

    val fqn = classInfo.name!!.substringBefore("$")
    val fqnExt = fqn.replace('.', '/') + ".java"
    val url = "https://github.com/apache/lucene/blob/$javaLuceneCommitId/lucene/$luceneModule/src/$srcDir/$fqnExt"

    return "[$fqn]($url)"
}

class ProgressPrintStream(val term: Terminal, val markDown: StringBuilder) {
    fun println(text: String) {
        term.println(text)
        markDown.appendLine(text)
    }

    fun printTable(headers: List<String>, rows: List<List<Any>>) {
        val table = table {
            header { row(*headers.toTypedArray()) }
            body {
                rows.forEach { rowData ->

                    val printableRowData = rowData.map { item ->
                        when (item) {
                            is ClassInfo -> item.name!!.substringBefore("$")
                            else -> item.toString()
                        }
                    }

                    row(*printableRowData.toTypedArray())
                }
            }
        }
        term.println(table)

        val mdTable = buildString {
            append("| ").append(headers.joinToString(" | ")).append(" |\n")
            append("| ").append(headers.joinToString(" | ") { "---" }).append(" |\n")
            rows.forEach { row ->
                append("| ").append(row.joinToString(" | ") {
                    if (it is ClassInfo && it.name.startsWith("org.apache.lucene.")) {
                        markdownLinkForJavaFQN(it)
                    } else {
                        it.toString()
                    }
                }).append(" |\n")
            }
        }
        markDown.appendLine(mdTable)
    }
}

class ClassInfoWithDepth(val classInfo: ClassInfo, val depth: Int)

fun topLevelClassName(className: String): String = className.substringBefore("$")

fun topLevelClassInfo(classInfo: ClassInfo, allClassesByName: Map<String, ClassInfo>): ClassInfo =
    allClassesByName[topLevelClassName(classInfo.name)] ?: classInfo

fun topLevelClassMap(classes: Iterable<ClassInfo>, excludedPrefix: String? = null): Map<String, ClassInfo> =
    classes
        .filterNot { classInfo -> excludedPrefix != null && classInfo.name.startsWith(excludedPrefix) }
        .filterNot { classInfo -> classInfo.name.contains("$") }
        .associateBy { classInfo -> classInfo.name }

fun resetSemanticTypeAliases() {
    semanticTypeAliases.clear()
    semanticTypeEquivalentKeys.clear()
    externalLibraryJavaKmpRefs.forEach { (javaFqn, kmpFqns) ->
        kmpFqns.forEach { kmpFqn -> registerSemanticTypeAlias(javaFqn, kmpFqn) }
    }
}

fun registerSemanticTypeAlias(javaFqn: String, kmpFqn: String) {
    val javaKey = normalizeTypeKey(javaFqn)
    val kmpKey = normalizeTypeKey(kmpFqn)
    semanticTypeAliases[javaKey] = javaKey
    semanticTypeAliases[kmpKey] = javaKey
    semanticTypeEquivalentKeys.getOrPut(javaKey) { mutableSetOf(javaKey) }.add(kmpKey)
    semanticTypeEquivalentKeys.getOrPut(kmpKey) { mutableSetOf(kmpKey) }.add(javaKey)
}

fun registerJdkPortedTypeAliases(classes: Iterable<ClassInfo>) {
    classes
        .filter { classInfo -> classInfo.name.startsWith("org.gnit.lucenekmp.jdkport.") }
        .forEach { classInfo ->
            val portedAnnotation = classInfo.annotationInfo.firstOrNull { annotationInfo ->
                annotationInfo.name == portedAnnotationFqn
            }
            val javaFqn = portedAnnotation?.parameterValues?.getValue("from")?.toString()
            if (!javaFqn.isNullOrBlank()) {
                registerSemanticTypeAlias(javaFqn, classInfo.name)
            }
        }
}

fun registerCompanionClasses(classes: Iterable<ClassInfo>) {
    classes
        .filter { classInfo -> classInfo.name.endsWith("\$Companion") }
        .forEach { classInfo ->
            companionClassInfoByOwnerName[classInfo.name.removeSuffix("\$Companion")] = classInfo
        }
}

// Method categorization and analysis utilities
enum class MethodCategory(val priority: Int, val description: String) {
    CORE_BUSINESS_LOGIC(1, "Core business logic methods"),
    PROPERTY_ACCESSOR(2, "Property getter/setter methods"),
    AUTO_GENERATED(3, "Auto-generated methods (equals, hashCode, toString, etc.)"),
    SYNTHETIC(4, "Synthetic/compiler-generated methods")
}

data class MethodSignature(
    val name: String,
    val returnType: String,
    val parameters: List<String>,
    val isStatic: Boolean,
    val isPublic: Boolean,
    val isProtected: Boolean,
    val isPrivate: Boolean,
    val category: MethodCategory
) {
    fun toNormalizedString(): String {
        val visibility = when {
            isPublic -> "public"
            isProtected -> "protected"
            isPrivate -> "private"
            else -> "package-private"
        }
        val staticModifier = if (isStatic) "static" else ""
        val paramStr = parameters.joinToString(", ")
        return "$visibility $staticModifier $returnType $name($paramStr)".replace(Regex("\\s+"), " ").trim()
    }
}

data class MethodAnalysis(
    val coreBusinessLogic: List<MethodSignature>,
    val propertyAccessors: List<MethodSignature>,
    val autoGenerated: List<MethodSignature>,
    val synthetic: List<MethodSignature>,
    val totalMethods: Int
) {
    fun getSemanticCompletionPercent(actual: MethodAnalysis): Int {
        // Weight different categories differently
        val coreWeight = 5
        val propertyWeight = 3
        val autoGenWeight = 1
        // Synthetic methods are compiler artifacts, not source-level porting requirements.
        val syntheticWeight = 0

        val totalExpectedScore = (coreBusinessLogic.size * coreWeight) +
                (propertyAccessors.size * propertyWeight) +
                (autoGenerated.size * autoGenWeight) +
                (synthetic.size * syntheticWeight)

        if (totalExpectedScore == 0) return 100

        val actualScore = (matchingMethods(coreBusinessLogic, actual.coreBusinessLogic).size * coreWeight) +
                (matchingMethods(propertyAccessors, actual.propertyAccessors).size * propertyWeight) +
                (matchingMethods(autoGenerated, actual.autoGenerated).size * autoGenWeight) +
                (matchingMethods(synthetic, actual.synthetic).size * syntheticWeight)

        return (actualScore * 100) / totalExpectedScore
    }

    private fun matchingMethods(expected: List<MethodSignature>, actual: List<MethodSignature>): List<MethodSignature> {
        return expected.filter { expectedMethod ->
            actual.any { actualMethod ->
                methodsMatch(expectedMethod, actualMethod)
            }
        }
    }

    private fun methodsMatch(expected: MethodSignature, actual: MethodSignature): Boolean {
        // Normalize method names for comparison (handle Kotlin property conventions)
        val expectedNormalized = normalizeMethodName(expected.name, expected.returnType != "void")
        val actualNormalized = normalizeMethodName(actual.name, actual.returnType != "void")

        return expectedNormalized == actualNormalized &&
                semanticTypesMatch(expected.returnType, actual.returnType) &&
                expected.parameters.size == actual.parameters.size &&
                expected.parameters.zip(actual.parameters).all { (exp, act) ->
                    semanticTypesMatch(exp, act)
                }
    }

    private fun normalizeMethodName(name: String, isGetter: Boolean): String {
        // Convert Kotlin property names to Java getter/setter style
        return when {
            name.startsWith("get") && name.length > 3 -> name
            name.startsWith("set") && name.length > 3 -> name
            name.startsWith("is") && name.length > 2 -> name
            isGetter -> "get" + name.replaceFirstChar { it.uppercase() }
            else -> name
        }
    }

}

fun normalizeSemanticType(type: String): String {
    val normalized = normalizeTypeKey(type)

    val aliased = semanticTypeAliases[normalized] ?: normalized

    return when (aliased) {
        "kotlin.String", "java.lang.String", "java.lang.CharSequence" -> "String"
        "kotlin.Int", "java.lang.Integer", "int" -> "Int"
        "kotlin.Long", "java.lang.Long", "long" -> "Long"
        "kotlin.Float", "java.lang.Float", "float" -> "Float"
        "kotlin.Double", "java.lang.Double", "double" -> "Double"
        "kotlin.Short", "java.lang.Short", "short" -> "Short"
        "kotlin.Byte", "java.lang.Byte", "byte" -> "Byte"
        "kotlin.Char", "java.lang.Character", "char" -> "Char"
        "kotlin.Boolean", "java.lang.Boolean", "boolean" -> "Boolean"
        "kotlin.Unit", "java.lang.Void", "void" -> "void"
        else -> aliased
            .replace("org.apache.lucene.", "")
            .replace("org.gnit.lucenekmp.", "")
            .replace('$', '.')
            .substringAfterLast('.')
    }
}

fun semanticTypesMatch(expected: String, actual: String): Boolean {
    val expectedKey = normalizeTypeKey(expected)
    val actualKey = normalizeTypeKey(actual)
    if (expectedKey == actualKey) return true
    if (luceneComparableTypeKey(expectedKey) == luceneComparableTypeKey(actualKey)) return true
    if (semanticTypeEquivalentKeys[expectedKey]?.contains(actualKey) == true) return true
    if (semanticTypeEquivalentKeys[actualKey]?.contains(expectedKey) == true) return true
    return false
}

fun normalizeTypeKey(type: String): String {
    val normalized = eraseTypeArguments(type)
        .trim()
        .removeSuffix("?")
        .removePrefix("? extends ")
        .removePrefix("? super ")
        .replace('$', '.')
        .replace("kotlin.collections.", "")

    return when (normalized) {
        "kotlin.String", "java.lang.String" -> "String"
        "kotlin.Int", "java.lang.Integer", "int" -> "Int"
        "kotlin.Long", "java.lang.Long", "long" -> "Long"
        "kotlin.Float", "java.lang.Float", "float" -> "Float"
        "kotlin.Double", "java.lang.Double", "double" -> "Double"
        "kotlin.Short", "java.lang.Short", "short" -> "Short"
        "kotlin.Byte", "java.lang.Byte", "byte" -> "Byte"
        "kotlin.Char", "java.lang.Character", "char" -> "Char"
        "kotlin.Boolean", "java.lang.Boolean", "boolean" -> "Boolean"
        "kotlin.Unit", "java.lang.Void", "void" -> "void"
        else -> normalized
    }
}

fun eraseTypeArguments(type: String): String {
    val sb = StringBuilder()
    var depth = 0
    type.forEach { ch ->
        when (ch) {
            '<' -> depth++
            '>' -> if (depth > 0) depth--
            else -> if (depth == 0) sb.append(ch)
        }
    }
    return sb.toString()
}

fun luceneComparableTypeKey(type: String): String =
    when {
        type.startsWith("org.apache.lucene.") -> "lucene." + type.removePrefix("org.apache.lucene.")
        type.startsWith("org.gnit.lucenekmp.") -> "lucene." + type.removePrefix("org.gnit.lucenekmp.")
        else -> type
    }

fun kotlinSuspendReturnType(continuationType: String): String? {
    val normalized = continuationType
        .trim()
        .removeSuffix("?")
        .replace('$', '.')

    if (!eraseTypeArguments(normalized).trim().removeSuffix("?").endsWith("kotlin.coroutines.Continuation")) {
        return null
    }

    val genericStart = normalized.indexOf('<')
    val genericEnd = normalized.lastIndexOf('>')
    if (genericStart == -1 || genericEnd <= genericStart) return null

    return normalized.substring(genericStart + 1, genericEnd)
        .trim()
        .removePrefix("? super ")
        .removePrefix("? extends ")
        .trim()
        .takeIf { it.isNotEmpty() }
}

fun normalizeKotlinSuspendSignature(returnType: String, parameters: List<String>): Pair<String, List<String>> {
    val suspendReturnType = parameters.lastOrNull()?.let(::kotlinSuspendReturnType)
    val hasSuspendBytecodeShape = suspendReturnType != null && normalizeTypeKey(returnType) in setOf("java.lang.Object", "kotlin.Any")

    return if (hasSuspendBytecodeShape) {
        suspendReturnType!! to parameters.dropLast(1)
    } else {
        returnType to parameters
    }
}

// Method analysis utilities
fun analyzeClassMethods(classInfo: ClassInfo): MethodAnalysis {
    val methods = classInfo.declaredMethodInfo.toList() +
            companionClassInfoByOwnerName[classInfo.name]?.declaredMethodInfo.orEmpty()

    val coreBusinessLogic = mutableListOf<MethodSignature>()
    val propertyAccessors = mutableListOf<MethodSignature>()
    val autoGenerated = mutableListOf<MethodSignature>()
    val synthetic = mutableListOf<MethodSignature>()

    methods.forEach { methodInfo ->
        val signature = createMethodSignature(methodInfo)

        when (signature.category) {
            MethodCategory.CORE_BUSINESS_LOGIC -> coreBusinessLogic.add(signature)
            MethodCategory.PROPERTY_ACCESSOR -> propertyAccessors.add(signature)
            MethodCategory.AUTO_GENERATED -> autoGenerated.add(signature)
            MethodCategory.SYNTHETIC -> synthetic.add(signature)
        }
    }

    return MethodAnalysis(
        coreBusinessLogic = coreBusinessLogic,
        propertyAccessors = propertyAccessors,
        autoGenerated = autoGenerated,
        synthetic = synthetic,
        totalMethods = methods.size
    )
}

fun createMethodSignature(methodInfo: MethodInfo): MethodSignature {
    val name = methodInfo.name
    val rawReturnType = methodInfo.typeSignatureOrTypeDescriptor?.resultType?.toString() ?: "void"
    val rawParameters = methodInfo.parameterInfo?.map {
        it.typeSignatureOrTypeDescriptor?.toString() ?: "Object"
    } ?: emptyList()
    val (returnType, parameters) = normalizeKotlinSuspendSignature(rawReturnType, rawParameters)

    val category = categorizeMethod(methodInfo)

    return MethodSignature(
        name = name,
        returnType = returnType,
        parameters = parameters,
        isStatic = methodInfo.isStatic,
        isPublic = methodInfo.isPublic,
        isProtected = methodInfo.isProtected,
        isPrivate = methodInfo.isPrivate,
        category = category
    )
}

fun categorizeMethod(methodInfo: MethodInfo): MethodCategory {
    val name = methodInfo.name

    // Check if synthetic or compiler-generated
    if (methodInfo.isSynthetic || methodInfo.isBridge) {
        return MethodCategory.SYNTHETIC
    }

    // Check for auto-generated methods (data class methods, etc.)
    if (isAutoGeneratedMethod(name)) {
        return MethodCategory.AUTO_GENERATED
    }

    // Check for property accessors
    if (isPropertyAccessor(name, methodInfo)) {
        return MethodCategory.PROPERTY_ACCESSOR
    }

    // Everything else is considered core business logic
    return MethodCategory.CORE_BUSINESS_LOGIC
}

fun isAutoGeneratedMethod(methodName: String): Boolean {
    val autoGenMethodNames = setOf(
        "equals", "hashCode", "toString", "copy", "clone",
        "finalize", "wait", "notify", "notifyAll", "getClass"
    )

    // Check for exact matches
    if (methodName in autoGenMethodNames) return true

    // Check for component methods (data class destructuring)
    if (methodName.matches(Regex("component\\d+"))) return true

    // Check for copy methods with parameters (data class copy)
    if (methodName.startsWith("copy\$") || methodName == "copy") return true

    return false
}

fun isPropertyAccessor(methodName: String, methodInfo: MethodInfo): Boolean {
    val paramCount = methodInfo.parameterInfo?.size ?: 0

    // Getter patterns
    if ((methodName.startsWith("get") && methodName.length > 3) ||
        (methodName.startsWith("is") && methodName.length > 2)) {
        return paramCount == 0 && methodInfo.typeSignatureOrTypeDescriptor?.resultType?.toString() != "void"
    }

    // Setter patterns
    if (methodName.startsWith("set") && methodName.length > 3) {
        return paramCount == 1 && methodInfo.typeSignatureOrTypeDescriptor?.resultType?.toString() == "void"
    }

    // Kotlin property patterns (for properties that don't follow get/set naming)
    // This is a heuristic - single parameter setters or no-parameter getters
    if (paramCount == 0 && methodInfo.typeSignatureOrTypeDescriptor?.resultType?.toString() != "void" &&
        !methodInfo.name.contains("$") && methodInfo.name.all { it.isLowerCase() || it.isDigit() }) {
        return true
    }

    return false
}

// Helper functions for detailed reporting
fun methodsSemanticMatch(javaMethod: MethodSignature, kmpMethod: MethodSignature): Boolean {
    // Normalize method names for comparison
    val javaMethodName = normalizeMethodNameForComparison(javaMethod.name, javaMethod.returnType != "void")
    val kmpMethodName = normalizeMethodNameForComparison(kmpMethod.name, kmpMethod.returnType != "void")

    return javaMethodName == kmpMethodName &&
            semanticTypesMatch(javaMethod.returnType, kmpMethod.returnType) &&
            javaMethod.parameters.size == kmpMethod.parameters.size &&
            javaMethod.parameters.zip(kmpMethod.parameters).all { (javaParam, kmpParam) ->
                semanticTypesMatch(javaParam, kmpParam)
            }
}

fun normalizeMethodNameForComparison(name: String, isGetter: Boolean): String {
    return when {
        name.startsWith("get") && name.length > 3 -> name
        name.startsWith("set") && name.length > 3 -> name
        name.startsWith("is") && name.length > 2 -> name
        isGetter -> "get" + name.replaceFirstChar { it.uppercase() }
        else -> name
    }
}

fun normalizeTypeForComparison(type: String): String {
    return normalizeSemanticType(type)
}

fun createDetailedClassReport(
    javaFqn: String,
    kmpFqn: String,
    javaAnalysis: MethodAnalysis,
    kmpAnalysis: MethodAnalysis?,
    missingMethods: List<MethodSignature>
): String {
    val sb = StringBuilder()
    sb.appendLine("## Detailed Analysis: $javaFqn -> $kmpFqn")
    sb.appendLine()

    sb.appendLine("### Method Categories:")
    sb.appendLine("- **Java Core Business Logic**: ${javaAnalysis.coreBusinessLogic.size}")
    sb.appendLine("- **Java Property Accessors**: ${javaAnalysis.propertyAccessors.size}")
    sb.appendLine("- **Java Auto-Generated**: ${javaAnalysis.autoGenerated.size}")
    sb.appendLine("- **Java Synthetic**: ${javaAnalysis.synthetic.size}")

    if (kmpAnalysis != null) {
        sb.appendLine("- **KMP Core Business Logic**: ${kmpAnalysis.coreBusinessLogic.size}")
        sb.appendLine("- **KMP Property Accessors**: ${kmpAnalysis.propertyAccessors.size}")
        sb.appendLine("- **KMP Auto-Generated**: ${kmpAnalysis.autoGenerated.size}")
        sb.appendLine("- **KMP Synthetic**: ${kmpAnalysis.synthetic.size}")

        val semanticCompletion = javaAnalysis.getSemanticCompletionPercent(kmpAnalysis)
        sb.appendLine("- **Semantic Completion**: ${semanticCompletion}%")
    } else {
        sb.appendLine("- **KMP Class**: Not yet ported")
    }

    if (missingMethods.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("### Missing Core Methods:")
        missingMethods.forEach { method ->
            sb.appendLine("- `${method.toNormalizedString()}`")
        }
    }

    sb.appendLine()
    return sb.toString()
}

class Progress : CliktCommand() {

    private val homeDir = System.getenv("HOME") ?: System.getProperty("user.home")
    private val cwd = File(".").canonicalFile
    private val inferredJavaDir = run {
        when {
            cwd.name == "lucene-kmp" && File(cwd.parentFile, "lucene").isDirectory ->
                File(cwd.parentFile, "lucene").canonicalPath
            File(cwd, "lucene").isDirectory -> File(cwd, "lucene").canonicalPath
            else -> "$homeDir/code/bbl-lucene/lucene"
        }
    }
    private val inferredKmpDir = run {
        when {
            cwd.name == "lucene-kmp" -> cwd.canonicalPath
            File(cwd, "lucene-kmp").isDirectory -> File(cwd, "lucene-kmp").canonicalPath
            else -> "$homeDir/code/bbl-lucene/lucene-kmp"
        }
    }
    private val javaDir by option("--java", "-j").default(inferredJavaDir)
    private val kmpDir by option("--kmp", "-k").default(inferredKmpDir)

    val ps = ProgressPrintStream(Terminal(), StringBuilder())

    data class TodoTestEntry(val depth: Int, val javaFqn: String, val kmpFqn: String)

    private fun buildTodoTestMarkdown(entries: List<TodoTestEntry>): String {
        val grouped = entries.groupBy { it.depth }
        return buildString {
            appendLine("# TODO: Unported Unit Test Classes")
            appendLine()
            appendLine("From PROGRESS2.md → Progress Table for Unit Test Classes, ordered by dependency depth (higher first).")
            appendLine()

            grouped.keys.sortedDescending().forEach { depth ->
                val depthEntries = grouped[depth].orEmpty().sortedBy { it.javaFqn }
                if (depthEntries.isEmpty()) return@forEach
                appendLine("## Depth $depth")
                depthEntries.forEach { entry ->
                    appendLine("- ${entry.javaFqn} → ${entry.kmpFqn}")
                }
                appendLine()
            }
        }
    }

    override fun run() {
        val javaRoot = File(javaDir).canonicalFile
        val kmpRoot = File(kmpDir).canonicalFile
        require(javaRoot.isDirectory && kmpRoot.isDirectory) {
            "Invalid roots. javaRoot=$javaRoot (exists=${javaRoot.exists()}), kmpRoot=$kmpRoot (exists=${kmpRoot.exists()})"
        }
        resetSemanticTypeAliases()
        companionClassInfoByOwnerName.clear()

        ps.println("# Lucene KMP Port Progress")

        // #
        // #  PRIORITY 1 DEPENDENCIES JAVA
        // #
        ps.println("")
        ps.println("## Priority 1 Dependencies (Java)")

        val javaClassPaths = modulesToPort.map {
            "$javaDir/lucene/$it/build/classes/java/main"
        }.toTypedArray()

        val javaLucene: ScanResult = ClassGraph().enableAllInfo()
            //.acceptPackages()
            .overrideClasspath(
                *javaClassPaths
            )
            .enableInterClassDependencies()
            .scan()
        val javaLuceneClassesByName = javaLucene.allClasses.associateBy { classInfo -> classInfo.name }

        // Collect depth-1 dependencies for priority-1 classes
        val pr1DependenciesDepth1 = javaLucene.classDependencyMap.entries
            .filter { (classInfo, _) -> classInfo.name in pri1Names }
            .flatMap { (_, dependencies) -> dependencies }
            .toSet()

        val pri1Classes = javaLucene.allClasses.filter { it.name in pri1Names }
        val pr1DepthToDependencyClassMap = buildDependencyDepthMap(
            initial = pr1DependenciesDepth1,
            seedSeen = pr1DependenciesDepth1 + pri1Classes,
            dependencyProvider = { classInfo -> javaLucene.classDependencyMap[classInfo].orEmpty().toSet() }
        )

        pr1DepthToDependencyClassMap.entries.forEach { (depth, classes) ->
            ps.println("* Dependencies: ${classes.size} at Depth $depth")
        }

        // Create javaPr1Classes: Map<String/*fqn*/, ClassInfoWithDepth> - deduplicated and top-level only
        val javaPr1Classes: Map<String/*fqn*/, ClassInfoWithDepth> =
            pr1DepthToDependencyClassMap.flatMap { (depth, classes) ->
                classes.map { classInfo ->
                    val topLevelInfo = topLevelClassInfo(classInfo, javaLuceneClassesByName)
                    topLevelInfo.name to ClassInfoWithDepth(topLevelInfo, depth)
                }
            }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, classInfoWithDepths) ->
                    // For each top-level class, keep the one with maximum depth
                    classInfoWithDepths.maxByOrNull { it.depth } ?: classInfoWithDepths.first()
                }
                .toSortedMap() // Sort by FQN

        ps.println("### Total priority-1 classes and their dependencies: ${javaPr1Classes.size}")

        // #
        // #  UNIT TEST DEPENDENCIES JAVA
        // #
        ps.println("")
        ps.println("## Unit Test Dependencies (Java)")

        // Collect all Unit Test classes for priority-1 classes and their dependencie
        //             "$javaDir/lucene/$it/build/classes/java/main"
        val javaUnitTestMainPaths: List<String> = modulesToPort.map { luceneModule ->
            "$javaDir/lucene/$luceneModule/build/classes/java/main"
        }

        val javaUnitTestTestPaths: List<String> = modulesToPort.map { luceneModule ->
            "$javaDir/lucene/$luceneModule/build/classes/java/test"
        }

        val javaUnitTestPaths = (javaUnitTestMainPaths + javaUnitTestTestPaths).asIterable()

        val javaLuceneUnitTest: ScanResult = ClassGraph().enableAllInfo()
            //.acceptPackages()
            .overrideClasspath(
                javaUnitTestPaths
            )
            .enableInterClassDependencies()
            .scan()
        val javaLuceneUnitTestClassesByName = javaLuceneUnitTest.allClasses.associateBy { classInfo -> classInfo.name }

        ps.println("### total unit test classes: ${javaLuceneUnitTest.allClasses.size}")

        val classNeedsUnitTestTopNames = (pri1Classes.map { it.name } + javaPr1Classes.keys).toSet()
        val expectedTestNames = classNeedsUnitTestTopNames
            .map { fqn -> "Test${fqn.substringAfterLast('.')}" }
            .toSet()

        val testClasses = javaLuceneUnitTest.allClasses.filter { classInfo ->
            classInfo.classpathElementURL.toString().contains("/build/classes/java/test/")
        }

        val pr1UnitTestClasses = testClasses.filter { unitTestClassInfo ->
            val matchesByName = unitTestClassInfo.simpleName in expectedTestNames
            val matchesByDependency = javaLuceneUnitTest.classDependencyMap[unitTestClassInfo]
                ?.any { dependency -> dependency.name?.substringBefore("$") in classNeedsUnitTestTopNames }
                ?: false
            matchesByName || matchesByDependency
        }

        val pr1UnitTestDepthToDependencyClassMap = buildDependencyDepthMap(
            initial = pr1UnitTestClasses.toSet(),
            seedSeen = pr1UnitTestClasses.toSet(),
            dependencyProvider = { classInfo -> javaLuceneUnitTest.classDependencyMap[classInfo].orEmpty().toSet() }
        )

        pr1UnitTestDepthToDependencyClassMap.entries.forEach { (depth, classes) ->
            ps.println("* Unit Test Dependencies: ${classes.size} at Depth $depth")
        }

        val pr1UnitTestDependencyToDepthMap = pr1UnitTestDepthToDependencyClassMap
            .flatMap { (depth, classes) -> classes.map { it to depth } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.maxOf { depth -> depth } }

        // Create javaUnitTestClasses: Map<String/*fqn*/, ClassInfoWithDepth>
        val javaUnitTestClasses: Map<String, ClassInfoWithDepth> = pr1UnitTestDependencyToDepthMap
            .map { (classInfo, depth) ->
                val topLevelInfo = topLevelClassInfo(classInfo, javaLuceneUnitTestClassesByName)
                topLevelInfo.name to ClassInfoWithDepth(topLevelInfo, depth)
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, classInfoWithDepths) ->
                // For each top-level class, keep the one with maximum depth
                classInfoWithDepths.maxByOrNull { it.depth } ?: classInfoWithDepths.first()
            }

        ps.println("### Total Unit Test and their Dependencies: ${javaUnitTestClasses.size}")

        // #
        // #  PRIORITY 1 DEPENDENCIES KMP
        // #
        ps.println("")
        ps.println("## Priority 1 Dependencies (KMP)")

        // operation on KMP classes
        val kmpMainPaths: List<String> = modulesToPort.map { luceneModule ->
            "$kmpDir/$luceneModule/build/classes/kotlin/jvm/main"
        }

        val kmpSR: ScanResult = ClassGraph().enableAllInfo()
            //.acceptPackages()
            .overrideClasspath(
                kmpMainPaths
            ).scan()
        registerJdkPortedTypeAliases(kmpSR.allClasses)
        registerCompanionClasses(kmpSR.allClasses)

        // Build indexes for faster lookup. Keep the real top-level class; nested/anonymous Kotlin
        // bytecode classes must not overwrite their enclosing class in semantic method analysis.
        val kmpClasses: Map<String/*fqn*/, ClassInfo> =
            topLevelClassMap(kmpSR.allClasses, excludedPrefix = "org.gnit.lucenekmp.jdkport.")

        ps.println("### Total KMP classes: ${kmpClasses.size}")

        // #
        // #  UNIT TEST DEPENDENCIES KMP
        // #
        ps.println("")
        ps.println("## Unit Test Dependencies (KMP)")

        val kmpUnitTestMainPaths: List<String> = modulesToPort.map { luceneModule ->
            "$kmpDir/$luceneModule/build/classes/kotlin/jvm/main"
        }

        val kmpUnitTestTestPaths: List<String> = modulesToPort.map { luceneModule ->
            "$kmpDir/$luceneModule/build/classes/kotlin/jvm/test"
        }

        val kmpUnitTestPaths = (kmpUnitTestMainPaths + kmpUnitTestTestPaths).asIterable()

        val kmpUnitTestSR: ScanResult = ClassGraph().enableAllInfo()
            //.acceptPackages()
            .overrideClasspath(
                kmpUnitTestPaths
            ).scan()
        registerJdkPortedTypeAliases(kmpUnitTestSR.allClasses)
        registerCompanionClasses(kmpUnitTestSR.allClasses)

        val kmpUnitTestClasses: Map<String/*fqn*/, ClassInfo> =
            topLevelClassMap(kmpUnitTestSR.allClasses, excludedPrefix = "org.gnit.lucenekmp.jdkport.")

        ps.println("### Total KMP Unit Test classes: ${kmpUnitTestClasses.size}")

        // #
        // #  PROGRESS TABLE for Lucene Classes
        // #
        ps.println("")
        ps.println("## Progress Table for Lucene Classes")

        // Create and display the table with enhanced method analysis
        val luceneHeaders = listOf(
            "Java Class",
            "KMP Class",
            "Depth",
            "Class Ported",
            "Java Core Methods",
            "KMP Core Methods",
            "Semantic Progress",
            "Missing Core Methods"
        )

        // Collect class and method data with detailed analysis
        val luceneTableRows = mutableListOf<List<Any>>()
        val detailedReports = mutableListOf<String>()

        // Create progress table using javaPr1Classes and kmpClasses
        javaPr1Classes.entries.sortedBy { it.key }.forEach { (javaFqn, javaClassWithDepth) ->
            val javaClassInfo = javaClassWithDepth.classInfo
            val depthToPrint = "Depth ${javaClassWithDepth.depth}"
            val kmpFqn = mapToKmp(javaFqn)
            val kmpClassInfo = kmpClasses[kmpFqn]

            val classPorted = if (kmpClassInfo != null) "[x]" else "[]"

            // Perform detailed method analysis
            val javaAnalysis = analyzeClassMethods(javaClassInfo)
            val kmpAnalysis = if (kmpClassInfo != null) analyzeClassMethods(kmpClassInfo) else null

            val javaCoreMethodCount = javaAnalysis.coreBusinessLogic.size
            val kmpCoreMethodCount = kmpAnalysis?.coreBusinessLogic?.size ?: 0

            val semanticProgressPercent = if (kmpAnalysis != null) {
                javaAnalysis.getSemanticCompletionPercent(kmpAnalysis)
            } else {
                0
            }

            val semanticProgress = "${semanticProgressPercent}%"

            // Find missing core methods
            val missingCoreMethods = if (kmpAnalysis != null) {
                javaAnalysis.coreBusinessLogic.filter { javaMethod ->
                    !kmpAnalysis.coreBusinessLogic.any { kmpMethod ->
                        methodsSemanticMatch(javaMethod, kmpMethod)
                    }
                }
            } else {
                javaAnalysis.coreBusinessLogic
            }

            val missingCoreMethodsCount = missingCoreMethods.size

            // Create detailed report for this class
            if (kmpClassInfo != null && (semanticProgressPercent < 100 || missingCoreMethodsCount > 0)) {
                val report = createDetailedClassReport(javaFqn, kmpFqn, javaAnalysis, kmpAnalysis, missingCoreMethods)
                detailedReports.add(report)
            }

            // Only add rows where semantic progress is less than 100% or missing core methods
            if (semanticProgressPercent < 100 || missingCoreMethodsCount > 0) {
                luceneTableRows.add(
                    listOf(
                        javaClassInfo,
                        kmpFqn,
                        depthToPrint,
                        classPorted,
                        javaCoreMethodCount,
                        kmpCoreMethodCount,
                        semanticProgress,
                        missingCoreMethodsCount
                    )
                )
            }
        }

        ps.printTable(luceneHeaders, luceneTableRows)

        // Print detailed analysis reports
        if (detailedReports.isNotEmpty()) {
            ps.println("")
            ps.println("## Detailed Method Analysis Reports")
            ps.println("")
            detailedReports.forEach { report ->
                ps.println(report)
            }
        }

        // #
        // #  PROGRESS TABLE for Unit Test Classes
        // #
        ps.println("")
        ps.println("## Progress Table for Unit Test Classes")

        // Create and display the unit test table with enhanced analysis
        val unitTestHeaders = listOf(
            "Java Unit Test Class",
            "KMP Unit Test Class",
            "Depth",
            "Class Ported",
            "Java Core Methods",
            "KMP Core Methods",
            "Semantic Progress"
        )

        // Collect unit test class and method data with detailed analysis
        val unitTestTableRows = mutableListOf<List<Any>>()

        // Create progress table using javaUnitTestClasses and kmpUnitTestClasses
        javaUnitTestClasses.entries.sortedBy { it.key }.forEach { (javaFqn, javaClassWithDepth) ->
            val javaClassInfo = javaClassWithDepth.classInfo
            val depthToPrint = "Depth ${javaClassWithDepth.depth}"
            val kmpFqn = mapToKmp(javaFqn)
            val kmpClassInfo = kmpUnitTestClasses[kmpFqn]

            val classPorted = if (kmpClassInfo != null) "[x]" else "[]"

            // Perform detailed method analysis for unit tests
            val javaTestAnalysis = analyzeClassMethods(javaClassInfo)
            val kmpTestAnalysis = if (kmpClassInfo != null) analyzeClassMethods(kmpClassInfo) else null

            val javaTestCoreMethodCount = javaTestAnalysis.coreBusinessLogic.size
            val kmpTestCoreMethodCount = kmpTestAnalysis?.coreBusinessLogic?.size ?: 0

            val testSemanticProgressPercent = if (kmpTestAnalysis != null) {
                javaTestAnalysis.getSemanticCompletionPercent(kmpTestAnalysis)
            } else {
                0
            }

            val testSemanticProgress = "${testSemanticProgressPercent}%"

            // Only add rows where semantic progress is less than 100%
            if (testSemanticProgressPercent < 100) {
                unitTestTableRows.add(
                    listOf(
                        javaClassInfo,
                        kmpFqn,
                        depthToPrint,
                        classPorted,
                        javaTestCoreMethodCount,
                        kmpTestCoreMethodCount,
                        testSemanticProgress
                    )
                )
            }
        }

        ps.printTable(unitTestHeaders, unitTestTableRows)

        // #
        // #  TODO.md OUTPUT
        // #
        val unportedUnitTestEntries = javaUnitTestClasses.entries.mapNotNull { (javaFqn, javaClassWithDepth) ->
            val kmpFqn = mapToKmp(javaFqn)
            if (kmpUnitTestClasses.containsKey(kmpFqn)) {
                null
            } else {
                TodoTestEntry(javaClassWithDepth.depth, javaFqn, kmpFqn)
            }
        }
        val todoTestFile = File("$kmpDir/TODO.md")
        todoTestFile.writeText(buildTodoTestMarkdown(unportedUnitTestEntries))
        ps.println("\nTODO.md written to: ${todoTestFile.absolutePath}")

        // #
        // #  SUMMARY
        // #
        ps.println("")
        ps.println("## Summary")

        ps.println("")
        ps.println("### Lucene Priority-1 Classes (Semantic Analysis)")
        val totalPri1Classes = javaPr1Classes.size
        val portedPri1Classes = totalPri1Classes - luceneTableRows.count { it[3] == "[]" }
        val pri1ClassPortingProgress = if (totalPri1Classes > 0) (portedPri1Classes * 100) / totalPri1Classes else 0

        // Calculate semantic completion metrics
        var totalCoreMethodsNeeded = 0
        var totalCoreMethodsImplemented = 0
        javaPr1Classes.entries.forEach { (javaFqn, javaClassWithDepth) ->
            val javaAnalysis = analyzeClassMethods(javaClassWithDepth.classInfo)
            val kmpFqn = mapToKmp(javaFqn)
            val kmpClassInfo = kmpClasses[kmpFqn]
            val kmpAnalysis = if (kmpClassInfo != null) analyzeClassMethods(kmpClassInfo) else null

            totalCoreMethodsNeeded += javaAnalysis.coreBusinessLogic.size
            if (kmpAnalysis != null) {
                totalCoreMethodsImplemented += javaAnalysis.coreBusinessLogic.count { javaMethod ->
                    kmpAnalysis.coreBusinessLogic.any { kmpMethod ->
                        methodsSemanticMatch(javaMethod, kmpMethod)
                    }
                }
            }
        }

        val semanticCompletionPercent = if (totalCoreMethodsNeeded > 0) {
            (totalCoreMethodsImplemented * 100) / totalCoreMethodsNeeded
        } else {
            100
        }

        ps.println("- Total Priority-1 Classes: $totalPri1Classes")
        ps.println("- Ported Priority-1 Classes: $portedPri1Classes")
        ps.println("- Priority-1 Class Porting Progress: $pri1ClassPortingProgress%")
        ps.println("- **Semantic Completion Progress: $semanticCompletionPercent%**")
        ps.println("- Total Core Methods Needed: $totalCoreMethodsNeeded")
        ps.println("- Core Methods Implemented: $totalCoreMethodsImplemented")

        ps.println("")
        ps.println("### Lucene Classes (Semantic Analysis)")
        val javaLuceneTopLevelClasses = topLevelClassMap(javaLucene.allClasses).values
        val totalAllClasses = javaLuceneTopLevelClasses.size
        val portedAllClasses = javaLuceneTopLevelClasses.count { classInfo ->
            val kmpFqn = mapToKmp(classInfo.name)
            kmpClasses.containsKey(kmpFqn)
        }
        val allClassPortingProgress = if (totalAllClasses > 0) (portedAllClasses * 100) / totalAllClasses else 0

        var totalAllCoreMethodsNeeded = 0
        var totalAllCoreMethodsImplemented = 0
        javaLuceneTopLevelClasses.forEach { classInfo ->
            val javaAnalysis = analyzeClassMethods(classInfo)
            val kmpFqn = mapToKmp(classInfo.name)
            val kmpClassInfo = kmpClasses[kmpFqn]
            val kmpAnalysis = if (kmpClassInfo != null) analyzeClassMethods(kmpClassInfo) else null

            totalAllCoreMethodsNeeded += javaAnalysis.coreBusinessLogic.size
            if (kmpAnalysis != null) {
                totalAllCoreMethodsImplemented += javaAnalysis.coreBusinessLogic.count { javaMethod ->
                    kmpAnalysis.coreBusinessLogic.any { kmpMethod ->
                        methodsSemanticMatch(javaMethod, kmpMethod)
                    }
                }
            }
        }

        val allSemanticCompletionPercent = if (totalAllCoreMethodsNeeded > 0) {
            (totalAllCoreMethodsImplemented * 100) / totalAllCoreMethodsNeeded
        } else {
            100
        }

        ps.println("- Total Classes: $totalAllClasses")
        ps.println("- Ported Classes: $portedAllClasses")
        ps.println("- Class Porting Progress: $allClassPortingProgress%")
        ps.println("- **Semantic Completion Progress: $allSemanticCompletionPercent%**")
        ps.println("- Total Core Methods Needed: $totalAllCoreMethodsNeeded")
        ps.println("- Core Methods Implemented: $totalAllCoreMethodsImplemented")

        ps.println("")
        ps.println("### Unit Test Classes (Semantic Analysis)")
        val totalUnitTestClasses = javaUnitTestClasses.size
        val portedUnitTestClasses = totalUnitTestClasses - unitTestTableRows.count { it[3] == "[]" }
        val unitTestPortingProgress = if (totalUnitTestClasses > 0) (portedUnitTestClasses * 100) / totalUnitTestClasses else 0

        // Calculate unit test semantic completion metrics
        var totalTestCoreMethodsNeeded = 0
        var totalTestCoreMethodsImplemented = 0
        javaUnitTestClasses.entries.forEach { (javaFqn, javaClassWithDepth) ->
            val javaTestAnalysis = analyzeClassMethods(javaClassWithDepth.classInfo)
            val kmpFqn = mapToKmp(javaFqn)
            val kmpClassInfo = kmpUnitTestClasses[kmpFqn]
            val kmpTestAnalysis = if (kmpClassInfo != null) analyzeClassMethods(kmpClassInfo) else null

            totalTestCoreMethodsNeeded += javaTestAnalysis.coreBusinessLogic.size
            if (kmpTestAnalysis != null) {
                totalTestCoreMethodsImplemented += javaTestAnalysis.coreBusinessLogic.count { javaMethod ->
                    kmpTestAnalysis.coreBusinessLogic.any { kmpMethod ->
                        methodsSemanticMatch(javaMethod, kmpMethod)
                    }
                }
            }
        }

        val testSemanticCompletionPercent = if (totalTestCoreMethodsNeeded > 0) {
            (totalTestCoreMethodsImplemented * 100) / totalTestCoreMethodsNeeded
        } else {
            100
        }

        ps.println("- Total Unit Test Classes: $totalUnitTestClasses")
        ps.println("- Ported Unit Test Classes: $portedUnitTestClasses")
        ps.println("- Unit Test Porting Progress: $unitTestPortingProgress%")
        ps.println("- **Unit Test Semantic Completion: $testSemanticCompletionPercent%**")
        ps.println("- Total Test Core Methods Needed: $totalTestCoreMethodsNeeded")
        ps.println("- Test Core Methods Implemented: $totalTestCoreMethodsImplemented")

        // #
        // #  MARKDOWN FILE OUTPUT
        // #
        val progress2File = File("$kmpDir/PROGRESS2.md")
        progress2File.writeText(ps.markDown.toString())
        ps.println("\nProgress report written to: ${progress2File.absolutePath}")
    }


    private fun mapToKmp(javaFqn: String): String = when {
        javaFqn.startsWith("org.apache.lucene.") -> "org.gnit.lucenekmp." + javaFqn.removePrefix("org.apache.lucene.")
        javaFqn.startsWith("java.") -> "org.gnit.lucenekmp.jdkport." + javaFqn.substringAfterLast('.')
        else -> javaFqn
    }
}

Progress().main(args)

fun buildDependencyDepthMap(
    initial: Set<ClassInfo>,
    seedSeen: Set<ClassInfo> = emptySet(),
    dependencyProvider: (ClassInfo) -> Set<ClassInfo>
): Map<Int, Set<ClassInfo>> {
    if (initial.isEmpty()) return emptyMap()
    val seen = seedSeen.toMutableSet()
    seen.addAll(initial)

    return generateSequence(initial) { current ->
        val next = current
            .asSequence()
            .flatMap { dependencyProvider(it).asSequence() }
            .toSet()
            .minus(seen)
        if (next.isEmpty()) null else {
            seen.addAll(next)
            next
        }
    }
        .mapIndexed { index, classes -> (index + 1) to classes }
        .toMap()
}
