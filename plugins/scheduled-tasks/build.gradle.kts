plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.elymbot.android.plugins.tasks"
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
    // The host owns these types at runtime; do not bundle a second Core copy in the plugin.
    compileOnly(project(":core"))
    // Core supplies the same JSON runtime in the host class loader.
    compileOnly(libs.kotlinx.serialization.json)

    testImplementation(project(":core"))
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
