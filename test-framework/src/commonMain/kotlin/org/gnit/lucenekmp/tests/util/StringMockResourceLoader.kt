package org.gnit.lucenekmp.tests.util

import okio.IOException
import org.gnit.lucenekmp.jdkport.ByteArrayInputStream
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.util.ResourceLoader
import kotlin.reflect.KClass


/** Fake resource loader for tests: works if you want to fake reading a single file  */
class StringMockResourceLoader(var text: String) : ResourceLoader {
    override fun <T> findClass(
        cname: String,
        expectedType: KClass<*>
    ): KClass<*> {
        try {
            return /*KClass.forName(cname).asSubclass<T>(expectedType)*/ TODO("implement something which works in kotlin common envinstead of forName")
        } catch (e: Exception) {
            throw RuntimeException("Cannot load class: $cname", e)
        }
    }

    @Throws(IOException::class)
    override fun openResource(resource: String): InputStream {
        return ByteArrayInputStream(text.encodeToByteArray())
    }
}
