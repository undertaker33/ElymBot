plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.feature.resource.api"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("javax.inject:javax.inject:1")
}
