plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.core.runtime.container"
}

dependencies {
    implementation(project(":core:logging"))
    implementation(project(":core:network"))
    implementation(project(":core:runtime-secret"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.tukaani:xz:1.10")
    implementation("javax.inject:javax.inject:1")
    implementation("com.google.dagger:hilt-android:2.52")
}
