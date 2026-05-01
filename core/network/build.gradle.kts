plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.core.network"
}

dependencies {
    implementation(project(":core:logging"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    api("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}
