plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.feature.config.api"
}

dependencies {
    implementation(project(":feature:resource:api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
