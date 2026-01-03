pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
/*plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}*/

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "lucene-kmp"
include(":analysis:common")
include(":analysis:extra")
include(":analysis:icu")
include(":analysis:kuromoji")
include(":analysis:morfologik")
//include(":analysis:morfologik.tests") // temporally not including due to module name convention error "." should not be in the name. need to figure out how to walk around this.
include(":analysis:nori")
include(":analysis:opennlp")
include(":analysis:phonetic")
include(":analysis:smartcn")
include(":analysis:stempel")
include(":core")
include(":queryparser")
include(":test-framework")
include("progress")
