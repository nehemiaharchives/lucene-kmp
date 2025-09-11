plugins {
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply  false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
}

subprojects {
    group = providers.gradleProperty("group").get()
    version = providers.gradleProperty("version").get()
}
