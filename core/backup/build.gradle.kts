plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.core.backup"
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
