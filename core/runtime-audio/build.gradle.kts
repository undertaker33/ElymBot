plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.core.runtime.audio"
}

dependencies {
    implementation(project(":core:common"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
