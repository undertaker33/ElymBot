plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.core.runtime.session"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("javax.inject:javax.inject:1")

    testImplementation("junit:junit:4.13.2")
}
