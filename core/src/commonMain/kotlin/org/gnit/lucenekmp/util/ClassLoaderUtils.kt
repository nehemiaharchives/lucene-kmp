package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.ClassLoader

/**
 * No-op classloader for Kotlin Multiplatform is used.
 *
 * Helper class used by ServiceLoader to investigate parent/child relationships of [ ]s.
 *
 * @lucene.internal
 */
interface ClassLoaderUtils {
    companion object {
        /**
         * Utility method to check if some class loader is a (grand-)parent of or the same as another one.
         * This means the child will be able to load all classes from the parent, too.
         *
         *
         * If caller's codesource doesn't have enough permissions to do the check, `false` is
         * returned (this is fine, because if we get a `SecurityException` it is for sure no
         * parent).
         */
        fun isParentClassLoader(parent: ClassLoader?, child: ClassLoader?): Boolean {
            try {
                var cl: ClassLoader? = child
                while (cl != null) {
                    if (cl === parent) {
                        return true
                    }
                    cl = cl.getParent()
                }
                return false
            } catch (se: /*Security*/Exception) {
                return false
            }
        }
    }
}
