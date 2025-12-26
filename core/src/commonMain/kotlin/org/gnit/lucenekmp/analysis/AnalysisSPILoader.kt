package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.jdkport.ClassLoader
import org.gnit.lucenekmp.jdkport.ServiceLoader
import org.gnit.lucenekmp.jdkport.getClassLoader
import org.gnit.lucenekmp.util.ClassLoaderUtils
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass

/**
 * Helper class for loading named SPIs from classpath (e.g. Tokenizers, TokenStreams).
 *
 * @lucene.internal
 */
class AnalysisSPILoader<S : AbstractAnalysisFactory>(
    clazz: KClass<S>,
    classloader: ClassLoader? = null
) {
    @Volatile
    private var services: MutableMap<String, KClass<S>> =
        mutableMapOf<String, KClass<S>>()

    @Volatile
    private var originalNames = mutableSetOf<String>()
    private val clazz: KClass<S>

    init {
        var classloader: ClassLoader? = classloader
        this.clazz = clazz
        // if clazz' classloader is not a parent of the given one, we scan clazz's classloader, too:
        val clazzClassloader: ClassLoader? = clazz.getClassLoader()
        if (classloader == null) {
            classloader = clazzClassloader
        }
        if (clazzClassloader != null
            && !ClassLoaderUtils.isParentClassLoader(
                clazzClassloader,
                classloader
            )
        ) {
            reload(clazzClassloader)
        }
        if (classloader != null) {
            reload(classloader)
        }
    }

    /**
     * Reloads the internal SPI list from the given [ClassLoader]. Changes to the service list
     * are visible after the method ends, all iterators (e.g., from [.availableServices],...)
     * stay consistent.
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
        //java.util.Objects.requireNonNull<ClassLoader>(classloader, "classloader")
        requireNotNull(classloader) { "classloader must not be null" }
        val services: LinkedHashMap<String, KClass<S>> = LinkedHashMap(this.services)
        val originalNames: LinkedHashSet<String> = LinkedHashSet(this.originalNames)

        // following was java lucene implementation:
        /*java.util.ServiceLoader.load<S>(clazz, classloader).stream()
            .map { java.util.ServiceLoader.Provider.type() }
            .forEachOrdered { service: KClass<S> ->
                var name: String = null
                var originalName: String = null
                var cause: Throwable = null
                try {
                    originalName = lookupSPIName(service)
                    name = originalName!!.lowercase()
                    if (!isValidName(originalName)) {
                        throw java.util.ServiceConfigurationError(
                            ("The name "
                                    + originalName
                                    + " for "
                                    + service.getName()
                                    + " is invalid: Allowed characters are (English) alphabet, digits, and underscore. It should be started with an alphabet.")
                        )
                    }
                } catch (e: java.lang.NoSuchFieldException) {
                    cause = e
                } catch (e: java.lang.IllegalAccessException) {
                    cause = e
                } catch (e: java.lang.IllegalStateException) {
                    cause = e
                }
                if (name == null) {
                    throw java.util.ServiceConfigurationError(
                        ("The class name "
                                + service.getName()
                                + " has no service name field: [public static final String NAME]"),
                        cause
                    )
                }
                // only add the first one for each name, later services will be ignored
                // this allows to place services before others in classpath to make
                // them used instead of others
                //
                // TODO: Should we disallow duplicate names here
                // Allowing it may get confusing on collisions, as different packages
                // could contain same factory class, which is a naming bug!
                // When changing this be careful to allow reload()!
                if (!services.containsKey(name)) {
                    services.put(name, service)
                    // preserve (case-sensitive) original name for reference
                    originalNames.add(originalName)
                }
            }

        // make sure that the number of lookup keys is same to the number of original names.
        // in fact this constraint should be met in existence checks of the lookup map key,
        // so this is more like an assertion rather than a status check.
        if (services.keys.size != originalNames.size) {
            throw java.util.ServiceConfigurationError(
                "Service lookup key set is inconsistent with original name set!"
            )
        }*/

        for (service in ServiceLoader.load(clazz, classloader)) {
            val serviceClass = service::class as KClass<S>
            var name: String? = null
            var originalName: String? = null
            var cause: Exception? = null
            try {
                originalName = lookupSPIName(serviceClass as KClass<out AbstractAnalysisFactory>)
                name = originalName.lowercase()
                if (!isValidName(originalName)) {
                    throw IllegalStateException(
                        "The name $originalName for ${serviceClass.qualifiedName ?: serviceClass.simpleName} is invalid: " +
                            "Allowed characters are (English) alphabet, digits, and underscore. " +
                            "It should be started with an alphabet."
                    )
                }
            } catch (e: Exception) {
                cause = e
            }
            if (name == null) {
                val serviceName = serviceClass.qualifiedName ?: serviceClass.simpleName ?: "unknown"
                if (cause != null) {
                    throw IllegalStateException(
                        "The class name $serviceName has no service name field: [public static final String NAME]",
                        cause
                    )
                }
                throw IllegalStateException(
                    "The class name $serviceName has no service name field: [public static final String NAME]"
                )
            }
            if (!services.containsKey(name)) {
                services[name] = serviceClass
                originalNames.add(originalName!!)
            }
        }

        if (services.keys.size != originalNames.size) {
            throw IllegalStateException("Service lookup key set is inconsistent with original name set!")
        }

        this.services = services
        this.originalNames = originalNames
    }

    /*private fun isValidName(name: String): Boolean {
        return SERVICE_NAME_PATTERN.matcher(name).matches()
    }*/
    private fun isValidName(name: String): Boolean {
        return SERVICE_NAME_PATTERN.matches(name)
    }

    fun newInstance(name: String, args: MutableMap<String, String>): S {
        val service: KClass<out S> = lookupClass(name)
        return newFactoryClassInstance(service, args)
    }

    fun lookupClass(name: String): KClass<out S> {
        /*val service: KClass<out S> = services.get(name.lowercase())
        if (service != null) {
            return service
        } else {
            throw java.lang.IllegalArgumentException(
                ("A SPI class of type "
                        + clazz.getName()
                        + " with name '"
                        + name
                        + "' does not exist. "
                        + "You need to add the corresponding JAR file supporting this SPI to your classpath. "
                        + "The current classpath supports the following names: "
                        + availableServices())
            )
        }*/
        val service: KClass<out S>? = services[name.lowercase()]
        if (service != null) {
            return service
        }
        throw IllegalArgumentException(
            ("A SPI class of type "
                + (clazz.qualifiedName ?: clazz.simpleName)
                + " with name '"
                + name
                + "' does not exist. "
                + "You need to add the corresponding JAR file supporting this SPI to your classpath. "
                + "The current classpath supports the following names: "
                + availableServices())
        )
    }

    fun availableServices(): MutableSet<String> {
        return originalNames
    }

    companion object {
        /*private val SERVICE_NAME_PATTERN: java.util.regex.Pattern =
            java.util.regex.Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]+$")*/
        private val SERVICE_NAME_PATTERN = Regex("""^[a-zA-Z][a-zA-Z0-9_]+$""")

        /**
         * Looks up SPI name (static "NAME" field) with appropriate modifiers. Also it must be a String
         * class and declared in the concrete class.
         *
         * @return the SPI name
         * @throws NoSuchFieldException - if the "NAME" field is not defined.
         * @throws IllegalAccessException - if the "NAME" field is inaccessible.
         * @throws IllegalStateException - if the "NAME" field does not have appropriate modifiers or
         * isn't a String field.
         */
        fun lookupSPIName(service: KClass<out AbstractAnalysisFactory>): String {
            /*val field: java.lang.reflect.Field = service.getField("NAME")
            val modifier: Int = field.getModifiers()
            if (java.lang.reflect.Modifier.isStatic(modifier)
                && java.lang.reflect.Modifier.isFinal(modifier)
                && field.getDeclaringClass() == service
                && field.getType() == String::class.java
            ) {
                return (field.get(null) as String)
            }
            throw java.lang.IllegalStateException("No SPI name defined.")*/
            return AnalysisSPIReflection.lookupSPIName(service)
        }

        /**
         * Creates a new instance of the given [AbstractAnalysisFactory] by invoking the
         * constructor, passing the given argument map.
         */
        fun <T : AbstractAnalysisFactory> newFactoryClassInstance(
            clazz: KClass<T>, args: MutableMap<String, String>
        ): T {
            /*try {
                return clazz.getConstructor(MutableMap::class.java).newInstance(args)
            } catch (ite: java.lang.reflect.InvocationTargetException) {
                val cause: Throwable = ite.cause
                if (cause is java.lang.RuntimeException) {
                    throw cause as java.lang.RuntimeException
                }
                if (cause is java.lang.Error) {
                    throw cause as java.lang.Error
                }
                throw java.lang.RuntimeException(
                    "Unexpected checked exception while calling constructor of " + clazz.getName(),
                    cause
                )
            } catch (e: java.lang.ReflectiveOperationException) {
                throw java.lang.UnsupportedOperationException(
                    ("Factory "
                            + clazz.getName()
                            + " cannot be instantiated. This is likely due to missing Map<String,String> constructor."),
                    e
                )
            }*/
            return AnalysisSPIReflection.newFactoryClassInstance(clazz, args)
        }
    }
}
