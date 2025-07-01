#!/usr/bin/env -S kotlin -J--enable-native-access=ALL-UNNAMED -J--sun-misc-unsafe-memory-access=allow

/**
 * Enhanced Lucene KMP Port Progress Tracker v2
 * 
 * This script provides intelligent Java-to-Kotlin method comparison and progress tracking
 * for the Lucene KMP port project. Instead of simple method counting, it performs:
 * 
 * - Semantic method matching between Java and Kotlin classes
 * - Property-aware comparison (Java getters/setters ↔ Kotlin properties)  
 * - Method categorization (Core/Property/Generated/Utility)
 * - Weighted completion percentages based on method importance
 * - Detailed missing method analysis and reporting
 * 
 * The enhanced comparison provides more realistic and actionable progress metrics.
 */

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

// Method analysis data classes for smart Java/Kotlin comparison
data class MethodSignature(
    val name: String,
    val parameterTypes: List<String>,
    val returnType: String,
    val isStatic: Boolean = false,
    val isAbstract: Boolean = false,
    val isSynthetic: Boolean = false
) {
    /** Normalized signature for comparison across languages */
    val normalizedSignature: String 
        get() = "${name}(${parameterTypes.joinToString(",")}):${returnType}"
}

enum class MethodCategory {
    CORE,       // Core business logic methods
    PROPERTY,   // Property accessors (getters/setters)
    GENERATED,  // Auto-generated methods (synthetic, compiler-generated)
    UTILITY     // Helper/utility methods
}

data class MethodAnalysis(
    val signature: MethodSignature,
    val category: MethodCategory,
    val isImplemented: Boolean = false,
    val notes: String = ""
)

data class MethodComparisonResult(
    val javaMethod: MethodAnalysis?,
    val kotlinMethod: MethodAnalysis?,
    val isMatched: Boolean,
    val matchType: MatchType,
    val semanticEquivalence: Double // 0.0 to 1.0
)

enum class MatchType {
    EXACT_MATCH,        // Identical signatures
    SEMANTIC_MATCH,     // Different signatures but same functionality
    PROPERTY_MATCH,     // Java getter/setter <-> Kotlin property
    MISSING_IN_KOTLIN,  // Method exists in Java but not in Kotlin
    EXTRA_IN_KOTLIN,    // Method exists in Kotlin but not in Java
    NO_MATCH           // No equivalent found
}

data class ClassComparison(
    val javaClass: ClassInfo,
    val kotlinClass: ClassInfo?,
    val javaMethods: List<MethodAnalysis>,
    val kotlinMethods: List<MethodAnalysis>,
    val methodComparisons: List<MethodComparisonResult>,
    val semanticCompletionPercent: Double,
    val coreMethodsCompletionPercent: Double,
    val propertyMethodsCompletionPercent: Double,
    val missingCoreMethods: List<MethodAnalysis>,
    val missingPropertyMethods: List<MethodAnalysis>
)

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
    // "analysis",
    // "codecs",
)

val srcDirMap = mapOf(
    "main" to "java",
    "test" to "test",
)

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

// Method normalization and analysis functions

/**
 * Converts a MethodInfo from ClassGraph into a normalized MethodSignature
 * that can be compared across Java and Kotlin implementations.
 */
fun normalizeMethodSignature(methodInfo: MethodInfo): MethodSignature {
    val paramTypes = methodInfo.parameterInfo.map { param ->
        normalizeTypeName(param.typeDescriptor.toString())
    }
    val returnType = normalizeTypeName(methodInfo.typeDescriptor.resultType.toString())
    
    return MethodSignature(
        name = methodInfo.name,
        parameterTypes = paramTypes,
        returnType = returnType,
        isStatic = methodInfo.isStatic,
        isAbstract = methodInfo.isAbstract,
        isSynthetic = methodInfo.isSynthetic
    )
}

/**
 * Normalizes type names to handle common differences between Java and Kotlin,
 * such as java.lang.String -> String, org.apache.lucene -> org.gnit.lucenekmp, etc.
 */
fun normalizeTypeName(typeName: String): String {
    // Normalize common type differences between Java and Kotlin
    return typeName
        .replace("java.lang.String", "String")
        .replace("java.lang.Object", "Any")
        .replace("java.lang.Integer", "Int")
        .replace("java.lang.Long", "Long")
        .replace("java.lang.Boolean", "Boolean")
        .replace("java.lang.Double", "Double")
        .replace("java.lang.Float", "Float")
        .replace("java.util.List", "List")
        .replace("java.util.Set", "Set")
        .replace("java.util.Map", "Map")
        .replace("org.apache.lucene.", "org.gnit.lucenekmp.")
}

/**
 * Categorizes methods into Core, Property, Generated, or Utility based on 
 * naming patterns and characteristics. This helps prioritize which methods
 * are most important for the port completion.
 */
fun categorizeMethod(methodInfo: MethodInfo): MethodCategory {
    val methodName = methodInfo.name
    val paramCount = methodInfo.parameterInfo.size
    
    return when {
        // Synthetic or compiler-generated methods
        methodInfo.isSynthetic -> MethodCategory.GENERATED
        methodName.startsWith("access$") -> MethodCategory.GENERATED
        methodName.contains("$") -> MethodCategory.GENERATED
        
        // Property accessors (getters/setters)
        (methodName.startsWith("get") && paramCount == 0 && methodName.length > 3) -> MethodCategory.PROPERTY
        (methodName.startsWith("set") && paramCount == 1 && methodName.length > 3) -> MethodCategory.PROPERTY
        (methodName.startsWith("is") && paramCount == 0 && methodName.length > 2) -> MethodCategory.PROPERTY
        
        // Utility methods (common patterns)
        methodName in setOf("toString", "hashCode", "equals", "clone") -> MethodCategory.UTILITY
        methodName.startsWith("checkConsistency") -> MethodCategory.UTILITY
        methodName.endsWith("Impl") -> MethodCategory.UTILITY
        
        // Everything else is considered core business logic
        else -> MethodCategory.CORE
    }
}

fun analyzeMethodsInClass(classInfo: ClassInfo): List<MethodAnalysis> {
    return classInfo.methodInfo
        .filter { !it.isConstructor } // Exclude constructors from comparison
        .map { methodInfo ->
            MethodAnalysis(
                signature = normalizeMethodSignature(methodInfo),
                category = categorizeMethod(methodInfo),
                isImplemented = true
            )
        }
}

/**
 * Performs intelligent matching between Java and Kotlin methods, accounting for:
 * - Exact signature matches
 * - Semantic equivalence (similar names, same purpose)
 * - Property-style matches (Java getters/setters <-> Kotlin properties)
 * - Missing methods in either direction
 */
fun findSemanticMatches(javaMethods: List<MethodAnalysis>, kotlinMethods: List<MethodAnalysis>): List<MethodComparisonResult> {
    val results = mutableListOf<MethodComparisonResult>()
    val matchedKotlinMethods = mutableSetOf<MethodAnalysis>()
    
    // First pass: Find exact matches
    for (javaMethod in javaMethods) {
        val exactMatch = kotlinMethods.find { kotlinMethod ->
            kotlinMethod !in matchedKotlinMethods && 
            javaMethod.signature.normalizedSignature == kotlinMethod.signature.normalizedSignature
        }
        
        if (exactMatch != null) {
            matchedKotlinMethods.add(exactMatch)
            results.add(MethodComparisonResult(
                javaMethod = javaMethod,
                kotlinMethod = exactMatch,
                isMatched = true,
                matchType = MatchType.EXACT_MATCH,
                semanticEquivalence = 1.0
            ))
        }
    }
    
    // Second pass: Find semantic matches (similar names, same category)
    for (javaMethod in javaMethods) {
        if (results.any { it.javaMethod == javaMethod }) continue // Already matched
        
        val semanticMatch = kotlinMethods.find { kotlinMethod ->
            kotlinMethod !in matchedKotlinMethods && 
            javaMethod.category == kotlinMethod.category &&
            calculateNameSimilarity(javaMethod.signature.name, kotlinMethod.signature.name) > 0.7
        }
        
        if (semanticMatch != null) {
            matchedKotlinMethods.add(semanticMatch)
            val similarity = calculateNameSimilarity(javaMethod.signature.name, semanticMatch.signature.name)
            results.add(MethodComparisonResult(
                javaMethod = javaMethod,
                kotlinMethod = semanticMatch,
                isMatched = true,
                matchType = MatchType.SEMANTIC_MATCH,
                semanticEquivalence = similarity
            ))
        }
    }
    
    // Third pass: Handle property-style matches
    for (javaMethod in javaMethods) {
        if (results.any { it.javaMethod == javaMethod }) continue // Already matched
        if (javaMethod.category != MethodCategory.PROPERTY) continue
        
        val propertyMatch = findPropertyMatch(javaMethod, kotlinMethods.filter { it !in matchedKotlinMethods })
        if (propertyMatch != null) {
            matchedKotlinMethods.add(propertyMatch)
            results.add(MethodComparisonResult(
                javaMethod = javaMethod,
                kotlinMethod = propertyMatch,
                isMatched = true,
                matchType = MatchType.PROPERTY_MATCH,
                semanticEquivalence = 0.8 // Properties are semantically equivalent but syntactically different
            ))
        }
    }
    
    // Fourth pass: Mark unmatched Java methods as missing
    for (javaMethod in javaMethods) {
        if (!results.any { it.javaMethod == javaMethod }) {
            results.add(MethodComparisonResult(
                javaMethod = javaMethod,
                kotlinMethod = null,
                isMatched = false,
                matchType = MatchType.MISSING_IN_KOTLIN,
                semanticEquivalence = 0.0
            ))
        }
    }
    
    // Fifth pass: Mark unmatched Kotlin methods as extra
    for (kotlinMethod in kotlinMethods) {
        if (kotlinMethod !in matchedKotlinMethods) {
            results.add(MethodComparisonResult(
                javaMethod = null,
                kotlinMethod = kotlinMethod,
                isMatched = false,
                matchType = MatchType.EXTRA_IN_KOTLIN,
                semanticEquivalence = 0.0
            ))
        }
    }
    
    return results
}

fun calculateNameSimilarity(name1: String, name2: String): Double {
    // Simple Levenshtein distance-based similarity
    val maxLen = maxOf(name1.length, name2.length)
    if (maxLen == 0) return 1.0
    
    val distance = levenshteinDistance(name1.lowercase(), name2.lowercase())
    return 1.0 - (distance.toDouble() / maxLen)
}

fun levenshteinDistance(s1: String, s2: String): Int {
    val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
    
    for (i in 0..s1.length) dp[i][0] = i
    for (j in 0..s2.length) dp[0][j] = j
    
    for (i in 1..s1.length) {
        for (j in 1..s2.length) {
            dp[i][j] = if (s1[i-1] == s2[j-1]) {
                dp[i-1][j-1]
            } else {
                1 + minOf(dp[i-1][j], dp[i][j-1], dp[i-1][j-1])
            }
        }
    }
    
    return dp[s1.length][s2.length]
}

fun findPropertyMatch(javaMethod: MethodAnalysis, availableKotlinMethods: List<MethodAnalysis>): MethodAnalysis? {
    val javaName = javaMethod.signature.name
    
    // Handle getter patterns: getXxx() -> xxx (property) or xxx() (method)
    if (javaName.startsWith("get") && javaName.length > 3) {
        val propertyName = javaName.substring(3).lowercase()
        return availableKotlinMethods.find { kotlinMethod ->
            kotlinMethod.signature.name.lowercase() == propertyName ||
            kotlinMethod.signature.name.lowercase() == javaName.lowercase()
        }
    }
    
    // Handle setter patterns: setXxx(value) -> xxx = value (property assignment)
    if (javaName.startsWith("set") && javaName.length > 3) {
        val propertyName = javaName.substring(3).lowercase()
        return availableKotlinMethods.find { kotlinMethod ->
            kotlinMethod.signature.name.lowercase() == propertyName
        }
    }
    
    // Handle boolean getter patterns: isXxx() -> xxx (property) or isXxx() (method)
    if (javaName.startsWith("is") && javaName.length > 2) {
        val propertyName = javaName.substring(2).lowercase()
        return availableKotlinMethods.find { kotlinMethod ->
            kotlinMethod.signature.name.lowercase() == propertyName ||
            kotlinMethod.signature.name.lowercase() == javaName.lowercase()
        }
    }
    
    return null
}

/**
 * Performs a complete class comparison using the smart method analysis,
 * returning detailed statistics about completion percentages, missing methods,
 * and semantic equivalence scores.
 */
fun performClassComparison(javaClass: ClassInfo, kotlinClass: ClassInfo?): ClassComparison {
    val javaMethods = analyzeMethodsInClass(javaClass)
    val kotlinMethods = if (kotlinClass != null) analyzeMethodsInClass(kotlinClass) else emptyList()
    
    val methodComparisons = findSemanticMatches(javaMethods, kotlinMethods)
    
    // Calculate completion percentages by category
    val coreJavaMethods = javaMethods.filter { it.category == MethodCategory.CORE }
    val propertyJavaMethods = javaMethods.filter { it.category == MethodCategory.PROPERTY }
    
    val matchedCoreMethods = methodComparisons.filter { 
        it.javaMethod?.category == MethodCategory.CORE && it.isMatched 
    }
    val matchedPropertyMethods = methodComparisons.filter { 
        it.javaMethod?.category == MethodCategory.PROPERTY && it.isMatched 
    }
    
    val coreCompletionPercent = if (coreJavaMethods.isNotEmpty()) {
        (matchedCoreMethods.size * 100.0) / coreJavaMethods.size
    } else 100.0
    
    val propertyCompletionPercent = if (propertyJavaMethods.isNotEmpty()) {
        (matchedPropertyMethods.size * 100.0) / propertyJavaMethods.size
    } else 100.0
    
    // Semantic completion considers weighted importance of different method categories
    val coreWeight = 0.7
    val propertyWeight = 0.2
    val utilityWeight = 0.1
    
    val utilityJavaMethods = javaMethods.filter { it.category == MethodCategory.UTILITY }
    val matchedUtilityMethods = methodComparisons.filter { 
        it.javaMethod?.category == MethodCategory.UTILITY && it.isMatched 
    }
    val utilityCompletionPercent = if (utilityJavaMethods.isNotEmpty()) {
        (matchedUtilityMethods.size * 100.0) / utilityJavaMethods.size
    } else 100.0
    
    val semanticCompletionPercent = (
        coreCompletionPercent * coreWeight +
        propertyCompletionPercent * propertyWeight +
        utilityCompletionPercent * utilityWeight
    )
    
    val missingCoreMethods = methodComparisons
        .filter { it.matchType == MatchType.MISSING_IN_KOTLIN && it.javaMethod?.category == MethodCategory.CORE }
        .mapNotNull { it.javaMethod }
    
    val missingPropertyMethods = methodComparisons
        .filter { it.matchType == MatchType.MISSING_IN_KOTLIN && it.javaMethod?.category == MethodCategory.PROPERTY }
        .mapNotNull { it.javaMethod }
    
    return ClassComparison(
        javaClass = javaClass,
        kotlinClass = kotlinClass,
        javaMethods = javaMethods,
        kotlinMethods = kotlinMethods,
        methodComparisons = methodComparisons,
        semanticCompletionPercent = semanticCompletionPercent,
        coreMethodsCompletionPercent = coreCompletionPercent,
        propertyMethodsCompletionPercent = propertyCompletionPercent,
        missingCoreMethods = missingCoreMethods,
        missingPropertyMethods = missingPropertyMethods
    )
}

class Progress : CliktCommand() {

    private val homeDir = System.getenv("HOME") ?: System.getProperty("user.home")
    private val javaDir by option("--java", "-j").default("$homeDir/code/lp/lucene")
    private val kmpDir by option("--kmp", "-k").default("$homeDir/code/lp/lucene-kmp")

    val ps = ProgressPrintStream(Terminal(), StringBuilder())

    override fun run() {
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

        // Collect depth-1 dependencies for priority-1 classes
        val pr1DependenciesDepth1 = mutableSetOf<ClassInfo>()

        javaLucene.classDependencyMap.entries
            .filter { (classInfo, dependencies) ->
                classInfo.name in pri1Names
            }
            .forEach { (classInfo, dependencies) ->
                if (dependencies.isNotEmpty()) {
                    // Add the class itself and all its dependencies to the set
                    pr1DependenciesDepth1.addAll(dependencies)
                }
            }

        val pr1DepthToDependencyClassMap = mutableMapOf<Int/* depth */, Set<ClassInfo>>()

        var depth = 1
        pr1DepthToDependencyClassMap[depth] = pr1DependenciesDepth1.toSet()
        depth++

        val tempSet = pr1DependenciesDepth1.toMutableSet()
        val allSeenDependencies = mutableSetOf<ClassInfo>()
        val pri1Classes = javaLucene.allClasses.filter { it.name in pri1Names }

        // Add initial depth-1 dependencies
        allSeenDependencies.addAll(pr1DependenciesDepth1)
        allSeenDependencies.addAll(pri1Classes)

        while (tempSet.isNotEmpty()) {

            val dependencies = mutableSetOf<ClassInfo>()

            // Collect dependencies for current depth classes
            tempSet.forEach { classInfo ->
                val classDependencies = javaLucene.classDependencyMap[classInfo]
                if (classDependencies != null && classDependencies.isNotEmpty()) {
                    dependencies.addAll(classDependencies)
                }
            }

            // Remove already seen dependencies to avoid duplicates
            dependencies.removeAll(allSeenDependencies)

            if (dependencies.isNotEmpty()) {
                pr1DepthToDependencyClassMap[depth] = dependencies.toSet()
            }

            // Update tracking sets for next iteration
            allSeenDependencies.addAll(dependencies)
            tempSet.clear()
            tempSet.addAll(dependencies)

            depth++
        }

        pr1DepthToDependencyClassMap.entries.forEach { (depth, classes) ->
            ps.println("* Dependencies: ${classes.size} at Depth $depth")
        }

        // Create javaPr1Classes: Map<String/*fqn*/, ClassInfoWithDepth> - deduplicated and top-level only
        val javaPr1Classes: Map<String/*fqn*/, ClassInfoWithDepth> =
            pr1DepthToDependencyClassMap.flatMap { (depth, classes) ->
                classes.map { classInfo ->
                    val className = classInfo.name!!
                    val topLevelClassName = if (className.contains("$")) {
                        className.substringBefore("$")
                    } else {
                        className
                    }
                    topLevelClassName to ClassInfoWithDepth(classInfo, depth)
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

        ps.println("### total unit test classes: ${javaLuceneUnitTest.allClasses.size}")

        // Collect all unit test dependencies with depth tracking
        val pr1UnitTestDepthToDependencyClassMap = mutableMapOf<Int/* depth */, Set<ClassInfo>>()

        val classNeedsUnitTest = pri1Classes.toMutableSet().plus(javaPr1Classes.values.map { it.classInfo })

        val pr1UnitTestClasses = javaLuceneUnitTest.allClasses.filter { unitTestClassInfo ->
            classNeedsUnitTest.any { pri1ClassInfo ->
                "Test${pri1ClassInfo.simpleName}" == unitTestClassInfo.simpleName
            }
        }

        val pr1UnitTestDependenciesDepth1 = mutableSetOf<ClassInfo>()

        javaLuceneUnitTest.classDependencyMap.entries
            .filter { (classInfo, dependencies) ->
                pr1UnitTestClasses.any { it.name == classInfo.name }
            }
            .forEach { (classInfo, dependencies) ->
                if (dependencies.isNotEmpty()) {
                    // Add the class itself and all its dependencies to the set
                    pr1UnitTestDependenciesDepth1.addAll(dependencies)
                }
            }

        val allSeenUnitTestDependencies = mutableSetOf<ClassInfo>()

        // Add initial depth-1 dependencies
        allSeenUnitTestDependencies.addAll(pr1UnitTestDependenciesDepth1)
        allSeenUnitTestDependencies.addAll(pr1UnitTestClasses)

        val unitTestTempSet = pr1UnitTestDependenciesDepth1.toMutableSet()

        var unitTestDepth = 1
        pr1UnitTestDepthToDependencyClassMap[unitTestDepth] = pr1UnitTestDependenciesDepth1.toSet()
        unitTestDepth++

        while (unitTestTempSet.isNotEmpty()) {
            val unitTestDependencies = mutableSetOf<ClassInfo>()

            // Collect dependencies for current depth classes
            unitTestTempSet.forEach { classInfo ->
                val classDependencies = javaLuceneUnitTest.classDependencyMap[classInfo]
                if (classDependencies != null && classDependencies.isNotEmpty()) {
                    unitTestDependencies.addAll(classDependencies)
                }
            }

            // Remove already seen dependencies to avoid duplicates
            unitTestDependencies.removeAll(allSeenUnitTestDependencies)

            if (unitTestDependencies.isNotEmpty()) {
                pr1UnitTestDepthToDependencyClassMap[unitTestDepth] = unitTestDependencies.toSet()
            }

            // Update tracking sets for next iteration
            allSeenUnitTestDependencies.addAll(unitTestDependencies)
            unitTestTempSet.clear()
            unitTestTempSet.addAll(unitTestDependencies)

            unitTestDepth++
        }

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
                val className = classInfo.name
                val topLevelClassName = if (className.contains("$")) {
                    className.substringBefore("$")
                } else {
                    className
                }
                topLevelClassName to ClassInfoWithDepth(classInfo, depth)
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
        val kmpSR: ScanResult = ClassGraph().enableAllInfo()
            //.acceptPackages()
            .overrideClasspath(
                "$kmpDir/core/build/classes/kotlin/jvm/main",
                //"$kmpDir/core/build/classes/kotlin/jvm/test",
            ).scan()

        // Build indexes for faster lookup - filter out inner classes and get unique top-level classes
        val kmpClasses: Map<String/*fqn*/, ClassInfo> = kmpSR.allClasses
            .filterNot { it.name.startsWith("org.gnit.lucenekmp.jdkport.") }.associate { classInfo ->
                val className = classInfo.name
                if (className.contains("$")) {
                    className.substringBefore("$") to classInfo
                } else {
                    className to classInfo
                }
            }

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

        val kmpUnitTestClasses: Map<String/*fqn*/, ClassInfo> = kmpUnitTestSR.allClasses
            .filterNot { it.name.startsWith("org.gnit.lucenekmp.jdkport.") }.associate { classInfo ->
                val className = classInfo.name
                if (className.contains("$")) {
                    className.substringBefore("$") to classInfo
                } else {
                    className to classInfo
                }
            }

        ps.println("### Total KMP Unit Test classes: ${kmpUnitTestClasses.size}")

        // #
        // #  PROGRESS TABLE for Lucene Classes
        // #
        ps.println("")
        ps.println("## Progress Table for Lucene Classes")

        // Create and display the table
        val luceneHeaders = listOf(
            "Java Class",
            "KMP Class",
            "Depth",
            "Class Ported",
            "Java Methods",
            "KMP Methods",
            "Semantic Progress",
            "Core Methods %",
            "Property Methods %"
        )

        // Collect class and method data
        val luceneTableRows = mutableListOf<List<Any>>()

        // Create progress table using javaPr1Classes and kmpClasses with smart comparison
        javaPr1Classes.entries.sortedBy { it.key }.forEach { (javaFqn, javaClassWithDepth) ->
            val javaClassInfo = javaClassWithDepth.classInfo
            val depthToPrint = "Depth ${javaClassWithDepth.depth}"
            val kmpFqn = mapToKmp(javaFqn)
            val kmpClassInfo = kmpClasses[kmpFqn]

            val classPorted = if (kmpClassInfo != null) "[x]" else "[]"

            // Perform smart method comparison
            val classComparison = performClassComparison(javaClassInfo, kmpClassInfo)
            
            val javaMethods = classComparison.javaMethods.size
            val kmpMethods = classComparison.kotlinMethods.size
            
            // Use semantic completion percentage instead of simple ratio
            val methodProgressPercent = classComparison.semanticCompletionPercent.toInt()
            val methodProgress = "${methodProgressPercent}%"
            val coreProgress = "${classComparison.coreMethodsCompletionPercent.toInt()}%"
            val propertyProgress = "${classComparison.propertyMethodsCompletionPercent.toInt()}%"

            // Only add rows where method progress is less than 100%
            if (methodProgressPercent < 100) {
                luceneTableRows.add(
                    listOf(
                        javaClassInfo,
                        kmpFqn,
                        depthToPrint,
                        classPorted,
                        javaMethods,
                        kmpMethods,
                        methodProgress,
                        coreProgress,
                        propertyProgress
                    )
                )
            }
        }

        ps.printTable(luceneHeaders, luceneTableRows)

        // #
        // #  PROGRESS TABLE for Unit Test Classes
        // #
        ps.println("")
        ps.println("## Progress Table for Unit Test Classes")

        // Create and display the unit test table
        val unitTestHeaders = listOf(
            "Java Unit Test Class",
            "KMP Unit Test Class",
            "Depth",
            "Class Ported",
            "Java Methods",
            "KMP Methods",
            "Semantic Progress",
            "Core Methods %",
            "Property Methods %"
        )

        // Collect unit test class and method data
        val unitTestTableRows = mutableListOf<List<Any>>()

        // Create progress table using javaUnitTestClasses and kmpUnitTestClasses with smart comparison
        javaUnitTestClasses.entries.sortedBy { it.key }.forEach { (javaFqn, javaClassWithDepth) ->
            val javaClassInfo = javaClassWithDepth.classInfo
            val depthToPrint = "Depth ${javaClassWithDepth.depth}"
            val kmpFqn = mapToKmp(javaFqn)
            val kmpClassInfo = kmpUnitTestClasses[kmpFqn]

            val classPorted = if (kmpClassInfo != null) "[x]" else "[]"

            // Perform smart method comparison for unit test classes
            val classComparison = performClassComparison(javaClassInfo, kmpClassInfo)
            
            val javaMethods = classComparison.javaMethods.size
            val kmpMethods = classComparison.kotlinMethods.size
            
            // Use semantic completion percentage instead of simple ratio
            val methodProgressPercent = classComparison.semanticCompletionPercent.toInt()
            val methodProgress = "${methodProgressPercent}%"
            val coreProgress = "${classComparison.coreMethodsCompletionPercent.toInt()}%"
            val propertyProgress = "${classComparison.propertyMethodsCompletionPercent.toInt()}%"

            // Only add rows where method progress is less than 100%
            if (methodProgressPercent < 100) {
                unitTestTableRows.add(
                    listOf(
                        javaClassInfo,
                        kmpFqn,
                        depthToPrint,
                        classPorted,
                        javaMethods,
                        kmpMethods,
                        methodProgress,
                        coreProgress,
                        propertyProgress
                    )
                )
            }
        }

        ps.printTable(unitTestHeaders, unitTestTableRows)

        // #
        // #  SUMMARY
        // #
        ps.println("")
        ps.println("## Summary")

        ps.println("")
        ps.println("### Lucene Classes")
        val totalClasses = javaPr1Classes.size
        val portedClasses = totalClasses - luceneTableRows.count { it[3] == "[]" }
        val classPortingProgress = if (totalClasses > 0) (portedClasses * 100) / totalClasses else 0

        ps.println("- Total Priority-1 Classes: $totalClasses")
        ps.println("- Ported Classes: $portedClasses")
        ps.println("- Class Porting Progress: $classPortingProgress%")

        ps.println("")
        ps.println("### Unit Test Classes")
        val totalUnitTestClasses = javaUnitTestClasses.size
        val portedUnitTestClasses = totalUnitTestClasses - unitTestTableRows.count { it[3] == "[]" }
        val unitTestPortingProgress = if (totalUnitTestClasses > 0) (portedUnitTestClasses * 100) / totalUnitTestClasses else 0

        ps.println("- Total Unit Test Classes: $totalUnitTestClasses")
        ps.println("- Ported Unit Test Classes: $portedUnitTestClasses")
        ps.println("- Unit Test Porting Progress: $unitTestPortingProgress%")

        // Enhanced detailed analysis
        ps.println("")
        ps.println("### Detailed Method Analysis")
        
        // Collect all class comparisons for summary analysis
        val allLuceneComparisons = mutableListOf<ClassComparison>()
        val allUnitTestComparisons = mutableListOf<ClassComparison>()
        
        javaPr1Classes.entries.forEach { (javaFqn, javaClassWithDepth) ->
            val kmpFqn = mapToKmp(javaFqn)
            val kmpClassInfo = kmpClasses[kmpFqn]
            val comparison = performClassComparison(javaClassWithDepth.classInfo, kmpClassInfo)
            allLuceneComparisons.add(comparison)
        }
        
        javaUnitTestClasses.entries.forEach { (javaFqn, javaClassWithDepth) ->
            val kmpFqn = mapToKmp(javaFqn)
            val kmpClassInfo = kmpUnitTestClasses[kmpFqn]
            val comparison = performClassComparison(javaClassWithDepth.classInfo, kmpClassInfo)
            allUnitTestComparisons.add(comparison)
        }
        
        // Lucene classes analysis
        val luceneCoreMethods = allLuceneComparisons.flatMap { it.javaMethods.filter { method -> method.category == MethodCategory.CORE } }
        val lucenePropertyMethods = allLuceneComparisons.flatMap { it.javaMethods.filter { method -> method.category == MethodCategory.PROPERTY } }
        val luceneUtilityMethods = allLuceneComparisons.flatMap { it.javaMethods.filter { method -> method.category == MethodCategory.UTILITY } }
        
        val luceneCoreImplemented = allLuceneComparisons.flatMap { it.methodComparisons.filter { comp -> 
            comp.javaMethod?.category == MethodCategory.CORE && comp.isMatched 
        } }.size
        val lucenePropertyImplemented = allLuceneComparisons.flatMap { it.methodComparisons.filter { comp -> 
            comp.javaMethod?.category == MethodCategory.PROPERTY && comp.isMatched 
        } }.size
        val luceneUtilityImplemented = allLuceneComparisons.flatMap { it.methodComparisons.filter { comp -> 
            comp.javaMethod?.category == MethodCategory.UTILITY && comp.isMatched 
        } }.size
        
        ps.println("#### Lucene Classes Method Breakdown:")
        ps.println("- Core Methods: ${luceneCoreImplemented}/${luceneCoreMethods.size} (${if (luceneCoreMethods.isNotEmpty()) (luceneCoreImplemented * 100) / luceneCoreMethods.size else 100}%)")
        ps.println("- Property Methods: ${lucenePropertyImplemented}/${lucenePropertyMethods.size} (${if (lucenePropertyMethods.isNotEmpty()) (lucenePropertyImplemented * 100) / lucenePropertyMethods.size else 100}%)")
        ps.println("- Utility Methods: ${luceneUtilityImplemented}/${luceneUtilityMethods.size} (${if (luceneUtilityMethods.isNotEmpty()) (luceneUtilityImplemented * 100) / luceneUtilityMethods.size else 100}%)")
        
        // Unit test classes analysis
        val unitTestCoreMethods = allUnitTestComparisons.flatMap { it.javaMethods.filter { method -> method.category == MethodCategory.CORE } }
        val unitTestPropertyMethods = allUnitTestComparisons.flatMap { it.javaMethods.filter { method -> method.category == MethodCategory.PROPERTY } }
        val unitTestUtilityMethods = allUnitTestComparisons.flatMap { it.javaMethods.filter { method -> method.category == MethodCategory.UTILITY } }
        
        val unitTestCoreImplemented = allUnitTestComparisons.flatMap { it.methodComparisons.filter { comp -> 
            comp.javaMethod?.category == MethodCategory.CORE && comp.isMatched 
        } }.size
        val unitTestPropertyImplemented = allUnitTestComparisons.flatMap { it.methodComparisons.filter { comp -> 
            comp.javaMethod?.category == MethodCategory.PROPERTY && comp.isMatched 
        } }.size
        val unitTestUtilityImplemented = allUnitTestComparisons.flatMap { it.methodComparisons.filter { comp -> 
            comp.javaMethod?.category == MethodCategory.UTILITY && comp.isMatched 
        } }.size
        
        ps.println("")
        ps.println("#### Unit Test Classes Method Breakdown:")
        ps.println("- Core Methods: ${unitTestCoreImplemented}/${unitTestCoreMethods.size} (${if (unitTestCoreMethods.isNotEmpty()) (unitTestCoreImplemented * 100) / unitTestCoreMethods.size else 100}%)")
        ps.println("- Property Methods: ${unitTestPropertyImplemented}/${unitTestPropertyMethods.size} (${if (unitTestPropertyMethods.isNotEmpty()) (unitTestPropertyImplemented * 100) / unitTestPropertyMethods.size else 100}%)")
        ps.println("- Utility Methods: ${unitTestUtilityImplemented}/${unitTestUtilityMethods.size} (${if (unitTestUtilityMethods.isNotEmpty()) (unitTestUtilityImplemented * 100) / unitTestUtilityMethods.size else 100}%)")
        
        // Show top missing core methods across all classes
        val topMissingCoreMethods = allLuceneComparisons
            .flatMap { it.missingCoreMethods }
            .groupBy { "${it.signature.name}(${it.signature.parameterTypes.joinToString(",")})" }
            .entries
            .sortedByDescending { it.value.size }
            .take(10)
        
        if (topMissingCoreMethods.isNotEmpty()) {
            ps.println("")
            ps.println("#### Top Missing Core Methods (Most Common):")
            topMissingCoreMethods.forEach { (methodSig, methods) ->
                ps.println("- $methodSig (missing in ${methods.size} classes)")
            }
        }
        
        // Overall semantic completion
        val overallSemanticCompletion = if (allLuceneComparisons.isNotEmpty()) {
            allLuceneComparisons.map { it.semanticCompletionPercent }.average()
        } else 0.0
        
        ps.println("")
        ps.println("#### Overall Analysis:")
        ps.println("- **Overall Semantic Completion**: ${overallSemanticCompletion.toInt()}%")
        ps.println("- **Prioritize**: Core methods implementation for maximum impact")
        ps.println("- **Properties**: Many Java getters/setters may already be covered by Kotlin properties")
        ps.println("- **Focus Areas**: Classes with low core method completion percentages")

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
