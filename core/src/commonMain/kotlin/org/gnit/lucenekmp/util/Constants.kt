package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.store.ReadAdvice
import dev.scottpierce.envvar.EnvVar
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gnit.lucenekmp.jdkport.PrivilegedAction

object Constants {

    private const val UNKNOWN: String = "Unknown"

    /** JVM vendor info.  */
    val JVM_VENDOR: String = getSysProp("java.vm.vendor", UNKNOWN)

    /** JVM vendor name.  */
    val JVM_NAME: String = getSysProp("java.vm.name", UNKNOWN)

    /** The value of `System.getProperty("os.name")`. *  */
    val OS_NAME: String = getSysProp("os.name", UNKNOWN)

    /** True iff running on Linux.  */
    val LINUX: Boolean = OS_NAME.startsWith("Linux")

    /** True iff running on Windows.  */
    val WINDOWS: Boolean = OS_NAME.startsWith("Windows")

    /** True iff running on SunOS.  */
    val SUN_OS: Boolean = OS_NAME.startsWith("SunOS")

    /** True iff running on Mac OS X  */
    val MAC_OS_X: Boolean = OS_NAME.startsWith("Mac OS X")

    /** True iff running on FreeBSD  */
    val FREE_BSD: Boolean = OS_NAME.startsWith("FreeBSD")

    /** The value of `System.getProperty("os.arch")`.  */
    val OS_ARCH: String = getSysProp("os.arch", UNKNOWN)

    /** The value of `System.getProperty("os.version")`.  */
    val OS_VERSION: String = getSysProp("os.version", UNKNOWN)

    /** The value of `System.getProperty("java.vendor")`.  */
    val JAVA_VENDOR: String = getSysProp("java.vendor", UNKNOWN)

    /** True iff the Java runtime is a client runtime and C2 compiler is not enabled.  */
    val IS_CLIENT_VM: Boolean =
        getSysProp("java.vm.info", "").contains("emulated-client")

    /** True iff the Java VM is based on Hotspot and has the Hotspot MX bean readable by Lucene.  */
    val IS_HOTSPOT_VM: Boolean = /*HotspotVMOptions.IS_HOTSPOT_VM*/
        false // TODO: impossible to implement in Kotlin common. JVM specific.

    /** True if jvmci is enabled (e.g. graalvm)  */
    val IS_JVMCI_VM: Boolean =
        /*HotspotVMOptions.get("UseJVMCICompiler").map(java.lang.Boolean::valueOf).orElse(false)*/ false

    /** True iff running on a 64bit JVM  */
    val JRE_IS_64BIT: Boolean = is64Bit()

    private fun is64Bit(): Boolean {
        val datamodel = getSysProp("sun.arch.data.model")
        return datamodel?.contains("64")
            ?: (OS_ARCH != UNKNOWN && OS_ARCH.contains(
                "64"
            ))
    }

    /** true if FMA likely means a cpu instruction and not BigDecimal logic.  */
    private val HAS_FMA: Boolean =
        /*(IS_CLIENT_VM == false) && HotspotVMOptions.get("UseFMA")
            .map(java.lang.Boolean::valueOf).orElse(false)*/ false

    /** maximum supported vectorsize.  */
    private val MAX_VECTOR_SIZE: Int =
        /*HotspotVMOptions.get("MaxVectorSize").map(java.lang.Integer::valueOf).orElse(0)*/ 0

    /** true for an AMD cpu with SSE4a instructions.  */
    private val HAS_SSE4A: Boolean = /*HotspotVMOptions.get("UseXmmI2F").map(java.lang.Boolean::valueOf).orElse(false)*/
        false

    /** true iff we know VFMA has faster throughput than separate vmul/vadd.  */
    val HAS_FAST_VECTOR_FMA: Boolean = hasFastVectorFMA()

    /** true iff we know FMA has faster throughput than separate mul/add.  */
    val HAS_FAST_SCALAR_FMA: Boolean = hasFastScalarFMA()

    private fun hasFastVectorFMA(): Boolean {
        if (HAS_FMA) {
            val value: String = getSysProp("lucene.useVectorFMA", "auto")
            if ("auto" == value) {
                // newer Neoverse cores have their act together
                // the problem is just apple silicon (this is a practical heuristic)
                if (OS_ARCH == "aarch64" && MAC_OS_X == false) {
                    return true
                }
                // zen cores or newer, its a wash, turn it on as it doesn't hurt
                // starts to yield gains for vectors only at zen4+
                if (HAS_SSE4A && MAX_VECTOR_SIZE >= 32) {
                    return true
                }
                // intel has their act together
                if (OS_ARCH == "amd64" && HAS_SSE4A == false) {
                    return true
                }
            } else {
                return value.toBoolean()
            }
        }
        // everyone else is slow, until proven otherwise by benchmarks
        return false
    }

    private fun hasFastScalarFMA(): Boolean {
        if (HAS_FMA) {
            val value: String = getSysProp("lucene.useScalarFMA", "auto")
            if ("auto" == value) {
                // newer Neoverse cores have their act together
                // the problem is just apple silicon (this is a practical heuristic)
                if (OS_ARCH == "aarch64" && MAC_OS_X == false) {
                    return true
                }
                // latency becomes 4 for the Zen3 (0x19h), but still a wash
                // until the Zen4 anyway, and big drop on previous zens:
                if (HAS_SSE4A && MAX_VECTOR_SIZE >= 64) {
                    return true
                }
                // intel has their act together
                if (OS_ARCH == "amd64" && HAS_SSE4A == false) {
                    return true
                }
            } else {
                return value.toBoolean()
            }
        }
        // everyone else is slow, until proven otherwise by benchmarks
        return false
    }

    /**
     * The default [ReadAdvice] used for opening index files. It will be [ ][ReadAdvice.RANDOM] by default, unless set by system property `org.apache.lucene.store.defaultReadAdvice`.
     */
    val DEFAULT_READADVICE: ReadAdvice =
        getSysProp("org.apache.lucene.store.defaultReadAdvice")
            ?.let { a: String -> ReadAdvice.valueOf(a.uppercase()) }
            ?: ReadAdvice.RANDOM

    private fun getSysProp(property: String): String? {
        // impossible to implement in Kotlin common. JVM specific.
        /*try {
            return doPrivileged<String>(PrivilegedAction<String> {
                getProperty(
                    property
                )
            })
        } catch (se: java.lang.SecurityException) {
            logSecurityWarning(property)
            return null
        }*/
        return EnvVar[property]
    }

    /**
     * @param property the property to get
     * @param def the default value to return if the property is not set
     */
    private fun getSysProp(property: String, def: String): String {
        // impossible to implement in Kotlin common. JVM specific.
        /*try {
            return doPrivileged<String>(PrivilegedAction<String> {
                java.lang.System.getProperty(
                    property,
                    def
                )
            })
        } catch (se: java.lang.SecurityException) {
            logSecurityWarning(property)
            return def
        }*/

        return EnvVar[property]
            ?: def
    }

    private fun logSecurityWarning(property: String) {
        val logger = KotlinLogging.logger {}
        logger.warn { "SecurityManager prevented access to system property: $property" }
    }


    // Extracted to a method to be able to apply the SuppressForbidden annotation
    /*@SuppressForbidden(reason = "security manager")*/
    private fun <T> doPrivileged(action: PrivilegedAction<T>): T {
        /*return AccessController.doPrivileged<T>(action)*/
        TODO()     // impossible to implement in Kotlin common. JVM specific.
    }

}
