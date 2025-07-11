package org.gnit.lucenekmp.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestProgress {

    val progress = Progress()

    @Test
    fun testNormalizeMethodName(){
        // Test Java record accessor methods to Kotlin getter style
        assertEquals("getName", "name()".normalizeRecordMethodName())
        assertEquals("getValue", "value()".normalizeRecordMethodName())
        assertEquals("getAge", "age()".normalizeRecordMethodName())

        assertEquals("isExternal", "isExternal()".normalizeRecordMethodName())

    }

    @Test
    fun testAnalyzeSingleClass() {
        val fqn = "${javaBasePackage}.geo.TestTessellator"
        progress.analyzeClass(fqn)
    }

    /*@Test
    fun testAnalyzeSingleModule() {
        progress.analyzeAllClasses()
    }*/

    @Test
    fun testHasKotlinDefaultParameterVariantWithTestTessellator(){
        // Get actual classes for TestTessellator
        val javaFqn = "${javaBasePackage}.geo.TestTessellator"
        val kmpFqn = "${kmpBasePackage}.geo.TestTessellator"

        val javaClass = progress.getJavaClass(javaFqn)
        val kmpClass = progress.getKmpClass(kmpFqn)

        // Get method info
        val javaMethodInfos = javaClass.methodInfo
        val kmpMethodInfos = kmpClass.methodInfo

        // Create KMP method map similar to analyzeClass
        val kmpMethodMap = kmpMethodInfos.associateBy { methodInfo ->
            "${methodInfo.name}(${methodInfo.parameterInfo.joinToString(",") { param -> param.typeDescriptor.toString() }})"
                .let { signature ->
                    with(progress) {
                        signature.normalizeBoxedTypeName()
                    }
                }
        }

        // Test the checkMultiPolygon case - Java has overloaded method, Kotlin has default parameter

        // Java checkMultiPolygon() with String parameter - should find Kotlin equivalent with default
        val javaCheckMultiPolygonString = javaMethodInfos.find {
            it.name == "checkMultiPolygon" && it.parameterInfo.size == 1 &&
            it.parameterInfo[0].typeDescriptor.toString().contains("String")
        }
        assertNotNull(javaCheckMultiPolygonString, "Java checkMultiPolygon(String) method should exist")

        val javaSignatureString = "checkMultiPolygon(java.lang.String)"

        // This should detect Kotlin default parameter variant
        assertTrue(
            hasKotlinDefaultParameterVariant(kmpMethodMap, javaSignatureString),
            "Should detect Kotlin default parameter variant for checkMultiPolygon(String)"
        )

        // Java checkMultiPolygon() with Polygon array and delta parameter - should also find Kotlin equivalent
        val javaCheckMultiPolygonWithParams = javaMethodInfos.find {
            it.name == "checkMultiPolygon" && it.parameterInfo.size == 2 &&
            it.parameterInfo[0].typeDescriptor.toString().contains("Polygon") &&
            it.parameterInfo[1].typeDescriptor.toString() == "double"
        }
        assertNotNull(javaCheckMultiPolygonWithParams, "Java checkMultiPolygon(Polygon[], double) method should exist")

        val javaSignatureWithParams = "checkMultiPolygon([Lorg.apache.lucene.geo.Polygon;,double)"
        // This should NOT be detected as needing default parameter variant since it matches directly
        assertFalse(
            hasKotlinDefaultParameterVariant(kmpMethodMap, javaSignatureWithParams),
            "Should not detect default parameter variant for checkMultiPolygon(Polygon[], double) as it has direct match"
        )

        // Print debug info to verify our understanding
        println("Java checkMultiPolygon methods:")
        javaMethodInfos.filter { it.name == "checkMultiPolygon" }.forEach { method ->
            println("  ${method.name}(${method.parameterInfo.joinToString(",") { it.typeDescriptor.toString() }})")
        }

        println("Kotlin checkMultiPolygon methods:")
        kmpMethodInfos.filter { it.name == "checkMultiPolygon" }.forEach { method ->
            println("  ${method.name}(${method.parameterInfo.joinToString(",") { it.typeDescriptor.toString() }})")
        }

        println("KMP method map keys containing 'checkMultiPolygon':")
        kmpMethodMap.keys.filter { it.contains("checkMultiPolygon") }.forEach { key ->
            println("  $key")
        }
    }

    @Test
    fun testHasKotlinDefaultParameterVariantWithTestVectorUtil(){
        // Get actual classes for TestVectorUtil
        val javaFqn = "${javaBasePackage}.util.TestVectorUtil"
        val kmpFqn = "${kmpBasePackage}.util.TestVectorUtil"

        val javaClass = progress.getJavaClass(javaFqn)
        val kmpClass = progress.getKmpClass(kmpFqn)

        // Get method info
        val javaMethodInfos = javaClass.methodInfo
        val kmpMethodInfos = kmpClass.methodInfo

        // Create KMP method map similar to analyzeClass
        val kmpMethodMap = kmpMethodInfos.associateBy { methodInfo ->
            "${methodInfo.name}(${methodInfo.parameterInfo.joinToString(",") { param -> param.typeDescriptor.toString() }})"
                .let { signature ->
                    with(progress) {
                        signature.normalizeBoxedTypeName()
                    }
                }
        }

        // Test the randomVector case - Java has overloaded method, Kotlin has default parameter

        // Java randomVector() with no parameters - should find Kotlin equivalent with default
        val javaRandomVectorNoParams = javaMethodInfos.find {
            it.name == "randomVector" && it.parameterInfo.isEmpty()
        }
        assertNotNull(javaRandomVectorNoParams, "Java randomVector() method should exist")

        val javaSignatureNoParams = "randomVector()"
        assertTrue(
            hasKotlinDefaultParameterVariant(kmpMethodMap, javaSignatureNoParams),
            "Should detect Kotlin default parameter variant for randomVector()"
        )

        // Java randomVector(int) with parameter - should also find Kotlin equivalent
        val javaRandomVectorWithParam = javaMethodInfos.find {
            it.name == "randomVector" && it.parameterInfo.size == 1
        }
        assertNotNull(javaRandomVectorWithParam, "Java randomVector(int) method should exist")

        val javaSignatureWithParam = "randomVector(int)"
        // This should NOT be detected as needing default parameter variant since it matches directly
        assertFalse(
            hasKotlinDefaultParameterVariant(kmpMethodMap, javaSignatureWithParam),
            "Should not detect default parameter variant for randomVector(int) as it has direct match"
        )

        // Test with randomVectorBytes case (the actual Kotlin implementation)
        val javaRandomVectorBytesNoParams = javaMethodInfos.find {
            it.name == "randomVectorBytes" && it.parameterInfo.isEmpty()
        }

        if (javaRandomVectorBytesNoParams != null) {
            val javaBytesSignatureNoParams = "randomVectorBytes()"
            assertTrue(
                hasKotlinDefaultParameterVariant(kmpMethodMap, javaBytesSignatureNoParams),
                "Should detect Kotlin default parameter variant for randomVectorBytes()"
            )
        }

        // Test a method that should NOT have default parameter variant
        val javaBasicDotProduct = javaMethodInfos.find {
            it.name == "testBasicDotProduct" && it.parameterInfo.isEmpty()
        }

        if (javaBasicDotProduct != null) {
            val basicDotProductSignature = "testBasicDotProduct()"
            assertFalse(
                hasKotlinDefaultParameterVariant(kmpMethodMap, basicDotProductSignature),
                "Should not detect default parameter variant for testBasicDotProduct()"
            )
        }

        // Print debug info to verify our understanding
        println("Java randomVector methods:")
        javaMethodInfos.filter { it.name == "randomVector" }.forEach { method ->
            println("  ${method.name}(${method.parameterInfo.joinToString(",") { it.typeDescriptor.toString() }})")
        }

        println("Kotlin randomVector methods:")
        kmpMethodInfos.filter { it.name == "randomVector" }.forEach { method ->
            println("  ${method.name}(${method.parameterInfo.joinToString(",") { it.typeDescriptor.toString() }})")
        }

        println("Kotlin randomVectorBytes methods:")
        kmpMethodInfos.filter { it.name == "randomVectorBytes" }.forEach { method ->
            println("  ${method.name}(${method.parameterInfo.joinToString(",") { it.typeDescriptor.toString() }})")
        }
    }

    @Test
    fun testEndsWithDollarSignAndDigit(){
        assertTrue("org.apache.lucene.search.TermQuery\$TermWeight\$1".endsWithDollarSignAndDigit())
        assertTrue("org.apache.lucene.search.TermQuery\$TermWeight\$2".endsWithDollarSignAndDigit())
        assertFalse("org.apache.lucene.search.TermQuery".endsWithDollarSignAndDigit())
    }

    // suspend function tests
    @Test
    fun testExtractReturnTypeFromContinuation() {
        // Test extracting Float from Continuation<? super java.lang.Float>
        val continuationFloat = "kotlin.coroutines.Continuation<? super java.lang.Float>"
        assertEquals("java.lang.Float", extractReturnTypeFromContinuation(continuationFloat))

        // Test with other types
        val continuationString = "kotlin.coroutines.Continuation<? super java.lang.String>"
        assertEquals("java.lang.String", extractReturnTypeFromContinuation(continuationString))
    }

    @Test
    fun testIsSuspendFunction() {
        val fqn = "${kmpBasePackage}.util.hnsw.BlockingFloatHeap"
        val javaClass = progress.getKmpClass(fqn)
        val methods = javaClass.methodInfo

        println("Methods in $fqn:")

        // Find the offer methods - should have both regular and suspend versions
        val offerMethods = methods.filter { it.name == "offer" }

        // Test that methods with Continuation parameter are identified as suspend
        val suspendOfferMethods = offerMethods.filter { method ->
            method.parameterInfo.any { param ->
                param.typeDescriptor.toString().contains("kotlin.coroutines.Continuation")
            }
        }
        suspendOfferMethods.forEach { method ->
            assertTrue(isSuspendFunction(method), "Method should be identified as suspend: $method")
        }

        // Test that methods without Continuation parameter are not identified as suspend
        val regularOfferMethods = offerMethods.filter { method ->
            !method.parameterInfo.any { param ->
                param.typeDescriptor.toString().contains("kotlin.coroutines.Continuation")
            }
        }
        regularOfferMethods.forEach { method ->
            assertFalse(isSuspendFunction(method), "Method should not be identified as suspend: $method")
        }

        // Find peek methods
        val peekMethods = methods.filter { it.name == "peek" }

        val suspendPeekMethods = peekMethods.filter { method ->
            method.parameterInfo.any { param ->
                param.typeDescriptor.toString().contains("kotlin.coroutines.Continuation")
            }
        }
        suspendPeekMethods.forEach { method ->
            assertTrue(isSuspendFunction(method), "Peek method should be identified as suspend: $method")
        }

        val regularPeekMethods = peekMethods.filter { method ->
            !method.parameterInfo.any { param ->
                param.typeDescriptor.toString().contains("kotlin.coroutines.Continuation")
            }
        }
        regularPeekMethods.forEach { method ->
            assertFalse(isSuspendFunction(method), "Peek method should not be identified as suspend: $method")
        }
    }

    @Test
    fun testReconstructSuspendSignature() {
        val fqn = "${kmpBasePackage}.util.hnsw.BlockingFloatHeap"
        val javaClass = progress.getKmpClass(fqn)
        val methods = javaClass.methodInfo

        // Test suspend offer methods
        val suspendOfferMethods = methods.filter { method ->
            method.name == "offer" &&
            method.parameterInfo.any { param ->
                param.typeDescriptor.toString().contains("kotlin.coroutines.Continuation")
            }
        }

        suspendOfferMethods.forEach { method ->
            val reconstructed = reconstructSuspendSignature(method)
            assertNotNull(reconstructed, "Should reconstruct signature for suspend method")
            assertTrue(reconstructed.startsWith("offer("), "Should start with method name")
            assertTrue(reconstructed.contains(":"), "Should contain return type")
        }

        // Test suspend peek methods
        val suspendPeekMethods = methods.filter { method ->
            method.name == "peek" &&
            method.parameterInfo.any { param ->
                param.typeDescriptor.toString().contains("kotlin.coroutines.Continuation")
            }
        }

        suspendPeekMethods.forEach { method ->
            val reconstructed = reconstructSuspendSignature(method)
            assertNotNull(reconstructed, "Should reconstruct signature for suspend peek method")
            assertTrue(reconstructed.startsWith("peek("), "Should start with peek")
            assertTrue(reconstructed.contains(":"), "Should contain return type")
        }

        // Test that non-suspend methods return null
        val regularMethods = methods.filter { method ->
            !method.parameterInfo.any { param ->
                param.typeDescriptor.toString().contains("kotlin.coroutines.Continuation")
            }
        }

        regularMethods.forEach { method ->
            val reconstructed = reconstructSuspendSignature(method)
            assertNull(reconstructed, "Should return null for non-suspend method: $method")
        }
    }
}