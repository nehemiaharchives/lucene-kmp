package org.gnit.lucenekmp.util.compress

import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals

class TestLowercaseAsciiCompression : LuceneTestCase() {

    private fun doTestCompress(bytes: ByteArray): Boolean {
        return doTestCompress(bytes, bytes.size)
    }

    private fun doTestCompress(bytes: ByteArray, len: Int): Boolean {
        val compressed = ByteBuffersDataOutput()
        val tmp = ByteArray(len + random().nextInt(10))
        Random.nextBytes(tmp)
        return if (LowercaseAsciiCompression.compress(bytes, len, tmp, compressed)) {
            assertTrue(compressed.size() < len)
            val restored = ByteArray(len + random().nextInt(10))
            LowercaseAsciiCompression.decompress(compressed.toDataInput(), restored, len)
            assertContentEquals(ArrayUtil.copyOfSubArray(bytes, 0, len), ArrayUtil.copyOfSubArray(restored, 0, len))
            true
        } else {
            false
        }
    }

    @Test
    fun testSimple() {
        assertFalse(doTestCompress("".encodeToByteArray())) // too short
        assertFalse(doTestCompress("ab1".encodeToByteArray())) // too short
        assertFalse(doTestCompress("ab1cdef".encodeToByteArray())) // too short
        assertTrue(doTestCompress("ab1cdefg".encodeToByteArray()))
        assertFalse(doTestCompress("ab1cdEfg".encodeToByteArray())) // too many exceptions
        assertTrue(doTestCompress("ab1cdefg".encodeToByteArray()))
        // 1 exception, but enough chars to be worth encoding an exception
        assertTrue(doTestCompress("ab1.dEfg427hiogchio:'nwm un!94twxz".encodeToByteArray()))
    }

    // LUCENE-10551
    @Test
    fun testNotReallySimple() {
        doTestCompress(
            "cion1cion_desarrollociones_oraclecionesnaturacionesnatura2tedppsa-integrationdemotiontion cloud gen2tion instance - dev1tion instance - testtion-devbtion-instancetion-prdtion-promerication-qation064533tion535217tion697401tion761348tion892818tion_matrationcauto_simmonsintgic_testtioncloudprodictioncloudservicetiongateway10tioninstance-jtsundatamartprd??o".encodeToByteArray()
        )
    }

    // LUCENE-10551
    @Test
    fun testNotReallySimple2() {
        doTestCompress(
            "analytics-platform-test/koala/cluster-tool:1.0-20220310151438.492,mesh_istio_examples-bookinfo-details-v1:1.16.2mesh_istio_examples-bookinfo-reviews-v3:1.16.2oce-clamav:1.0.219oce-tesseract:1.0.7oce-traefik:2.5.1oci-opensearch:1.2.4.8.103oda-digital-assistant-control-plane-train-pool-workflow-v6:22.02.14oke-coresvcs-k8s-dns-dnsmasq-nanny-amd64@sha256:41aa9160ceeaf712369ddb660d02e5ec06d1679965e6930351967c8cf5ed62d4oke-coresvcs-k8s-dns-kube-dns-amd64@sha256:2cf34b04106974952996c6ef1313f165ce65b4ad68a3051f51b1b8f91ba5f838oke-coresvcs-k8s-dns-sidecar-amd64@sha256:8a82c7288725cb4de9c7cd8d5a78279208e379f35751539b406077f9a3163dcdoke-coresvcs-node-problem-detector@sha256:9d54df11804a862c54276648702a45a6a0027a9d930a86becd69c34cc84bf510oke-coresvcs-oke-fluentd-lumberjack@sha256:5f3f10b187eb804ce4e84bc3672de1cf318c0f793f00dac01cd7da8beea8f269oke-etcd-operator@sha256:4353a2e5ef02bb0f6b046a8d6219b1af359a2c1141c358ff110e395f29d0bfc8oke-oke-hyperkube-amd64@sha256:3c734f46099400507f938090eb9a874338fa25cde425ac9409df4c885759752foke-public-busybox@sha256:4cee1979ba0bf7db9fc5d28fb7b798ca69ae95a47c5fecf46327720df4ff352doke-public-coredns@sha256:86f8cfc74497f04e181ab2e1d26d2fd8bd46c4b33ce24b55620efcdfcb214670oke-public-coredns@sha256:8cd974302f1f6108f6f31312f8181ae723b514e2022089cdcc3db10666c49228oke-public-etcd@sha256:b751e459bc2a8f079f6730dd8462671b253c7c8b0d0eb47c67888d5091c6bb77oke-public-etcd@sha256:d6a76200a6e9103681bc2cf7fefbcada0dd9372d52cf8964178d846b89959d14oke-public-etcd@sha256:fa056479342b45479ac74c58176ddad43687d5fc295375d705808f9dfb48439aoke-public-kube-proxy@sha256:93b2da69d03413671606e22294c59a69fe404088a5f6e74d6394a8641fdb899boke-public-tiller@sha256:c2eb6e580123622e1bc0ff3becae3a3a71ac36c98a2786d780590197839175e5osms/opcbuild-osms-agent-proxy-java:0.4.0-129rosms/opcbuild-osms-agent-proxy-nginx:0.4.0-129rosms/opcbuild-osms-ingestion-cert:0.4.0-129rscs-lcm/drift-detector:227scs-lcm/salt-state-sync:242streaming-alpine:30.10.183streaming-kafka:30.10.183vision-service-document-classification:1.1.55vision-service-image-classification:1.4.52".encodeToByteArray()
        )
    }

    @Test
    fun testFarAwayExceptions() {
        val s = "01W" + IntRange(0, 299).map { "a" }.joinToString("") + "W."
        assertTrue(doTestCompress(s.encodeToByteArray()))
    }

    @Test
    fun testRandomAscii() {
        for (iter in 0 until 1000) {
            val len = random().nextInt(1000)
            val bytes = ByteArray(len + random().nextInt(10))
            for (i in bytes.indices) {
                bytes[i] = TestUtil.nextInt(random(), ' '.code, '~'.code).toByte()
            }
            doTestCompress(bytes, len)
        }
    }

    @Test
    fun testRandomCompressibleAscii() {
        for (iter in 0 until 1000) {
            val len = TestUtil.nextInt(random(), 8, 1000)
            val bytes = ByteArray(len + random().nextInt(10))
            for (i in bytes.indices) {
                var b = random().nextInt(32)
                b = b or 0x20 or ((b and 0x20) shl 1)
                b -= 1
                bytes[i] = b.toByte()
            }
            assertTrue(doTestCompress(bytes, len))
        }
    }

    @Test
    fun testRandomCompressibleAsciiWithExceptions() {
        for (iter in 0 until 1000) {
            val len = TestUtil.nextInt(random(), 8, 1000)
            var exceptions = 0
            val maxExceptions = len ushr 5
            val bytes = ByteArray(len + random().nextInt(10))
            for (i in bytes.indices) {
                if (exceptions == maxExceptions || random().nextInt(100) != 0) {
                    var b = random().nextInt(32)
                    b = b or 0x20 or ((b and 0x20) shl 1)
                    b -= 1
                    bytes[i] = b.toByte()
                } else {
                    exceptions++
                    bytes[i] = random().nextInt(256).toByte()
                }
            }
            assertTrue(doTestCompress(bytes, len))
        }
    }

    @Test
    fun testRandom() {
        for (iter in 0 until 1000) {
            val len = random().nextInt(1000)
            val bytes = ByteArray(len + random().nextInt(10))
            Random.nextBytes(bytes)
            doTestCompress(bytes, len)
        }
    }

    @Test
    fun testAsciiCompressionRandom2() {
        val iters = atLeast(1000)
        for (iter in 0 until iters) {
            doTestCompress(
                TestUtil.randomUnicodeString(random(), atLeast(400)).encodeToByteArray()
            )
        }
    }
}

