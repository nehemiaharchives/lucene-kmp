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
        val fqn = "${javaBasePackage}.codecs.lucene90.blocktree.IntersectTermsEnumFrame"
        progress.analyzeClass(fqn)
    }

    /*@Test
    fun testAnalyzeSingleModule() {
        progress.analyzeAllClasses()
    }*/

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