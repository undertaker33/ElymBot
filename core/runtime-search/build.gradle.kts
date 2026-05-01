plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.core.runtime.search"
}

dependencies {
    implementation(project(":core:logging"))
    implementation(project(":core:network"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("javax.inject:javax.inject:1")

    testImplementation("junit:junit:4.13.2")
}
