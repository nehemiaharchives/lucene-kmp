package org.gnit.lucenekmp.tests.util

import kotlin.reflect.KClass

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
///@java.lang.annotation.Inherited // not sure what to do
annotation class TestGroup(
    /**
     * The name of a test group. If not defined, the default (lowercased annotation
     * name) is used.
     */
    val name: String = "",
    /**
     * System property used to enable/ disable a group. If empty, a default is used:
     * <pre>
     * tests.*name*
    </pre> *
     */
    val sysProperty: String = "",
    /**
     * Is the group enabled or disabled by default (unless overridden by test group filtering
     * rules).
     */
    val enabled: Boolean = true
) {
    /**
     * Utilities to deal with annotations annotated with [TestGroup].
     */
    object Utilities {
        fun getGroupName(annotationClass: KClass<out Annotation>): String {
            // TODO not possible with kotlin common code, need to walk around
            /*val testGroup: TestGroup = annotationClass.findAnnotation<TestGroup>()
            requireNotNull(testGroup) {
                ("Annotation must have a @TestGroup annotation: "
                        + annotationClass)
            }

            val tmp = emptyToNull(testGroup.name)
            return tmp ?: annotationClass.getSimpleName().lowercase()*/
            return ""
        }

        fun getSysProperty(annotationClass: KClass<out Annotation>): String {
            // TODO not possible with kotlin common code, need to walk around
            /*val testGroup: TestGroup = annotationClass.findAnnotation<TestGroup>()
            requireNotNull(testGroup) {
                ("Annotation must have a @TestGroup annotation: "
                        + annotationClass)
            }

            val tmp = emptyToNull(testGroup.sysProperty)
            return (tmp ?: SysGlobals.prefixProperty(getGroupName(annotationClass)))*/
            return ""
        }

        private fun emptyToNull(value: String?): String? {
            if (value == null || value.trim { it <= ' ' }.isEmpty()) return null
            return value.trim { it <= ' ' }
        }
    }
}
