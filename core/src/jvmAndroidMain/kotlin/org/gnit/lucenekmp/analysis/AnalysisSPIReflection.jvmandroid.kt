package org.gnit.lucenekmp.analysis

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.jvm.java

internal actual object AnalysisSPIReflection {
    actual fun lookupSPIName(service: KClass<out AbstractAnalysisFactory>): String {
        val field = service.java.getField("NAME")
        val modifier = field.modifiers
        if (Modifier.isStatic(modifier)
            && Modifier.isFinal(modifier)
            && field.declaringClass == service.java
            && field.type == String::class.java
        ) {
            return field.get(null) as String
        }
        throw IllegalStateException("No SPI name defined.")
    }

    actual fun <T : AbstractAnalysisFactory> newFactoryClassInstance(
        clazz: KClass<T>,
        args: MutableMap<String, String>
    ): T {
        try {
            return clazz.java.getConstructor(MutableMap::class.java).newInstance(args)
        } catch (ite: InvocationTargetException) {
            val cause = ite.cause
            if (cause is RuntimeException) {
                throw cause
            }
            if (cause is Error) {
                throw cause
            }
            throw RuntimeException(
                "Unexpected checked exception while calling constructor of " + clazz.java.name,
                cause
            )
        } catch (e: ReflectiveOperationException) {
            throw UnsupportedOperationException(
                "Factory " + clazz.java.name +
                    " cannot be instantiated. This is likely due to missing Map<String,String> constructor.",
                e
            )
        }
    }
}
