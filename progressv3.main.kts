#!/usr/bin/env -S kotlin -J--enable-native-access=ALL-UNNAMED -J--sun-misc-unsafe-memory-access=allow

@file:DependsOn("io.github.classgraph:classgraph:4.8.180")
@file:DependsOn("com.github.ajalt.mordant:mordant-jvm:3.0.2")

import io.github.classgraph.ClassGraph
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.table.table
import java.io.File

fun main(){

    val homeDir = System.getenv("HOME") ?: System.getProperty("user.home")
    val cwd = File(".").canonicalFile
    val javaDir = when {
        cwd.name == "lucene-kmp" && File(cwd.parentFile, "lucene").isDirectory ->
            File(cwd.parentFile, "lucene").canonicalPath
        File(cwd, "lucene").isDirectory -> File(cwd, "lucene").canonicalPath
        else -> "$homeDir/code/lp/lucene"
    }
    val kmpDir = when {
        cwd.name == "lucene-kmp" -> cwd.canonicalPath
        File(cwd, "lucene-kmp").isDirectory -> File(cwd, "lucene-kmp").canonicalPath
        else -> "$homeDir/code/lp/lucene-kmp"
    }

    val javaRoot = File(javaDir).canonicalFile
    val kmpRoot = File(kmpDir).canonicalFile
    require(javaRoot.isDirectory && kmpRoot.isDirectory) {
        "Invalid roots. javaRoot=$javaRoot (exists=${javaRoot.exists()}), kmpRoot=$kmpRoot (exists=${kmpRoot.exists()})"
    }

    val javaClassPath = "$javaDir/lucene/core/build/classes/java/main"
    val kmpClassPath = "$kmpDir/core/build/classes/kotlin/jvm/main"

    if (!File(javaClassPath).isDirectory || !File(kmpClassPath).isDirectory) {
        System.err.println(
            "Build outputs not found.\n" +
                "javaClassPath=$javaClassPath (exists=${File(javaClassPath).exists()})\n" +
                "kmpClassPath=$kmpClassPath (exists=${File(kmpClassPath).exists()})\n" +
                "Run:\n" +
                "  (cd $javaDir/lucene && ./gradlew :lucene:core:compileJava)\n" +
                "  (cd $kmpDir && ./gradlew :core:compileKotlinJvm)\n"
        )
        return
    }

    val javaSearchResult = ClassGraph().enableAllInfo().overrideClasspath(javaClassPath).scan()
    val kmpSearchResult = ClassGraph().enableAllInfo().overrideClasspath(kmpClassPath).scan()

    val javaBasePackage = "org.apache.lucene"
    val kmpBasePackage = "org.gnit.lucenekmp"

    val javaClass = javaSearchResult.allClasses.firstOrNull { it.name == "$javaBasePackage.analysis.Analyzer"}
    val kmpClass = kmpSearchResult.allClasses.firstOrNull { it.name == "$kmpBasePackage.analysis.Analyzer" }
    if (javaClass == null || kmpClass == null) {
        System.err.println(
            "Analyzer class not found.\n" +
                "javaClassPath=$javaClassPath\n" +
                "kmpClassPath=$kmpClassPath\n" +
                "java Analyzer present? ${javaClass != null}\n" +
                "kmp Analyzer present? ${kmpClass != null}\n" +
                "Ensure compiled classes exist for both projects."
        )
        return
    }

    val javaMethodInfos = javaClass.methodInfo
    val javaInnerClasses = javaClass.innerClasses
    val javaFields = javaClass.fieldInfo

    val kmpMethodInfos = kmpClass.methodInfo
    val kmpInnerClasses = kmpClass.innerClasses
    val kmpFields = kmpClass.fieldInfo

    val term = Terminal()

    // Compare method lists by making table, method details needed to be compared
    val methodHeaders = listOf("Method Name", "Java Signature", "KMP Signature", "Status")
    val methodRows = mutableListOf<List<String>>()

    // Create maps for easier lookup
    val javaMethodMap = javaMethodInfos.associateBy { "${it.name}(${it.parameterInfo.joinToString(",") { param -> param.typeDescriptor.toString() }})" }
    val kmpMethodMap = kmpMethodInfos.associateBy { "${it.name}(${it.parameterInfo.joinToString(",") { param -> param.typeDescriptor.toString() }})" }

    // Get all unique method signatures
    val allMethodSignatures = (javaMethodMap.keys + kmpMethodMap.keys).toSet().sorted()

    allMethodSignatures.forEach { signature ->
        val javaMethod = javaMethodMap[signature]
        val kmpMethod = kmpMethodMap[signature]

        val methodName = signature.substringBefore("(")
        val javaSignature = javaMethod?.toString() ?: "Missing"
        val kmpSignature = kmpMethod?.toString() ?: "Missing"

        val status = when {
            javaMethod != null && kmpMethod != null -> "✓ Ported"
            javaMethod != null && kmpMethod == null -> "❌ Missing in KMP"
            javaMethod == null && kmpMethod != null -> "⚠️ Extra in KMP"
            else -> "❓ Unknown"
        }

        methodRows.add(listOf(methodName, javaSignature, kmpSignature, status))
    }

    val methodTable = table {
        header { row(*methodHeaders.toTypedArray()) }
        body {
            methodRows.forEach { rowData ->
                row(*rowData.toTypedArray())
            }
        }
    }

    term.println("Method Comparison for ${javaClass.simpleName}:")
    term.println(methodTable)

    // Summary statistics
    val totalJavaMethods = javaMethodInfos.size
    val totalKmpMethods = kmpMethodInfos.size
    val portedMethods = methodRows.count { it[3] == "✓ Ported" }
    val missingMethods = methodRows.count { it[3] == "❌ Missing in KMP" }
    val extraMethods = methodRows.count { it[3] == "⚠️ Extra in KMP" }

    term.println("\nSummary:")
    term.println("- Total Java Methods: $totalJavaMethods")
    term.println("- Total KMP Methods: $totalKmpMethods")
    term.println("- Ported Methods: $portedMethods")
    term.println("- Missing in KMP: $missingMethods")
    term.println("- Extra in KMP: $extraMethods")
    term.println("- Porting Progress: ${if (totalJavaMethods > 0) (portedMethods * 100) / totalJavaMethods else 0}%")
}

main()
