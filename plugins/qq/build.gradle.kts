plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.elymbot.android.plugins.qq"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.android.buildTools.get()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jdk.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jdk.get())
    }
}

dependencies {
    // The host owns Core and coroutine classes at runtime; a signed plugin must not bundle them.
    compileOnly(project(":core"))
    compileOnly(libs.kotlinx.coroutines.core)
    compileOnly(libs.kotlinx.serialization.json)

    testImplementation(project(":core"))
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
