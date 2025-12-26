package org.gnit.lucenekmp.util

import okio.IOException
import org.gnit.lucenekmp.jdkport.InputStream
import kotlin.reflect.KClass

/** Abstraction for loading resources (streams, files, and classes).  */
interface ResourceLoader {
    /** Opens a named resource  */
    @Throws(IOException::class)
    fun openResource(resource: String): InputStream

    /** Finds class of the name and expected type  */
    fun <T> findClass(cname: String, expectedType: KClass<*>): KClass<*>

    /** Creates an instance of the name and expected type  */ // TODO: fix exception handling
    fun <T> newInstance(cname: String, expectedType: KClass<*>): T {
        val clazz: KClass<*> = findClass<T>(cname, expectedType)
        // following was java lucene implementation
        /*try {
            return clazz.getConstructor().newInstance()
        } catch (e: java.lang.Exception) {
            throw java.lang.RuntimeException("Cannot create instance: " + cname, e)
        }*/
        TODO("implement like following")
        /*when (clazz){
            is Analyzer -> Analyzer()
            else -> error()
        }*/
    }
}
