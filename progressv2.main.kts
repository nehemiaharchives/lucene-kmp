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
            "Method Progress"
        )

        // Collect class and method data
        val luceneTableRows = mutableListOf<List<Any>>()

        // Create progress table using javaPr1Classes and kmpClasses
        javaPr1Classes.entries.sortedBy { it.key }.forEach { (javaFqn, javaClassWithDepth) ->
            val javaClassInfo = javaClassWithDepth.classInfo
            val depthToPrint = "Depth ${javaClassWithDepth.depth}"
            val kmpFqn = mapToKmp(javaFqn)
            val kmpClassInfo = kmpClasses[kmpFqn]

            val classPorted = if (kmpClassInfo != null) "[x]" else "[]"

            val javaMethods = javaClassInfo.methodInfo.size
            val kmpMethods = kmpClassInfo?.methodInfo?.size ?: 0

            val methodProgressPercent = if (kmpClassInfo != null && javaMethods > 0) {
                (kmpMethods * 100) / javaMethods
            } else {
                0
            }

            val methodProgress = "${methodProgressPercent}%"

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
                        methodProgress
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
            "Method Progress"
        )

        // Collect unit test class and method data
        val unitTestTableRows = mutableListOf<List<Any>>()

        // Create progress table using javaUnitTestClasses and kmpUnitTestClasses
        javaUnitTestClasses.entries.sortedBy { it.key }.forEach { (javaFqn, javaClassWithDepth) ->
            val javaClassInfo = javaClassWithDepth.classInfo
            val depthToPrint = "Depth ${javaClassWithDepth.depth}"
            val kmpFqn = mapToKmp(javaFqn)
            val kmpClassInfo = kmpUnitTestClasses[kmpFqn]

            val classPorted = if (kmpClassInfo != null) "[x]" else "[]"

            val javaMethods = javaClassInfo.methodInfo.size
            val kmpMethods = kmpClassInfo?.methodInfo?.size ?: 0

            val methodProgressPercent = if (kmpClassInfo != null && javaMethods > 0) {
                (kmpMethods * 100) / javaMethods
            } else {
                0
            }

            val methodProgress = "${methodProgressPercent}%"

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
                        methodProgress
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
