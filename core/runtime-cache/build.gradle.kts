plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.core.runtime.cache"
}

dependencies {
    implementation(project(":core:logging"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("javax.inject:javax.inject:1")

    testImplementation("junit:junit:4.13.2")
}
