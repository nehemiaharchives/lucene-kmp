package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.ClassLoader
import org.gnit.lucenekmp.jdkport.ServiceLoader
import org.gnit.lucenekmp.jdkport.getClassLoader
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass


/**
 * Helper class for loading named SPIs from classpath (e.g. Codec, PostingsFormat).
 *
 * @lucene.internal
 */
class NamedSPILoader<S : NamedSPILoader.NamedSPI>(
    clazz: KClass<S>,
    classloader: ClassLoader? = null
) : Iterable<S> {
    @Volatile
    private var services = mutableMapOf<String, S>()
    private val clazz: KClass<S>

    init {
        var classloader: ClassLoader? = classloader
        this.clazz = clazz
        // if clazz' classloader is not a parent of the given one, we scan clazz's classloader, too:
        val clazzClassloader: ClassLoader = clazz.getClassLoader()
        if (classloader == null) {
            classloader = clazzClassloader
        }
        if (clazzClassloader != null
            && !ClassLoaderUtils.isParentClassLoader(clazzClassloader, classloader)
        ) {
            reload(clazzClassloader)
        }
        reload(classloader)
    }

    /**
     * Reloads the internal SPI list from the given [ClassLoader]. Changes to the service list
     * are visible after the method ends, all iterators ([.iterator],...) stay consistent.
     *
     *
     * **NOTE:** Only new service providers are added, existing ones are never removed or
     * replaced.
     *
     *
     * *This method is expensive and should only be called for discovery of new service
     * providers on the given classpath/classloader!*
     */
    fun reload(classloader: ClassLoader) {
        requireNotNull<ClassLoader>(classloader) { "classloader must not be null" }
        val services: LinkedHashMap<String, S> = LinkedHashMap<String, S>(this.services)
        for (service in ServiceLoader.load<S>(clazz, classloader)) {
            val name: String = service.name
            // only add the first one for each name, later services will be ignored
            // this allows to place services before others in classpath to make
            // them used instead of others
            if (!services.containsKey(name)) {
                checkServiceName(name)
                services.put(name, service)
            }
        }
        this.services = services
    }

    fun lookup(name: String): S {
        val service = services.get(name)
        if (service != null) return service
        throw IllegalArgumentException(
            ("An SPI class of type "
                    + clazz.qualifiedName
                    + " with name '"
                    + name
                    + "' does not exist."
                    + "  You need to add the corresponding JAR file supporting this SPI to your classpath."
                    + "  The current classpath supports the following names: "
                    + availableServices())
        )
    }

    fun availableServices(): MutableSet<String> {
        return services.keys
    }

    override fun iterator(): MutableIterator<S> {
        return services.values.iterator()
    }

    /**
     * Interface to support [NamedSPILoader.lookup] by name.
     *
     *
     * Names must be all ascii alphanumeric, and less than 128 characters in length.
     */
    interface NamedSPI {
        val name: String
    }

    companion object {
        /** Validates that a service name meets the requirements of [NamedSPI]  */
        fun checkServiceName(name: String) {
            // based on harmony charset.java
            require(name.length < 128) { "Illegal service name: '$name' is too long (must be < 128 chars)." }
            var i = 0
            val len = name.length
            while (i < len) {
                val c = name[i]
                require(isLetterOrDigit(c)) { "Illegal service name: '$name' must be simple ascii alphanumeric." }
                i++
            }
        }

        /** Checks whether a character is a letter or digit (ascii) which are defined in the spec.  */
        private fun isLetterOrDigit(c: Char): Boolean {
            return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || ('0' <= c && c <= '9')
        }
    }
}
