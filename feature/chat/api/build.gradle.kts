plugins {
    id("com.android.library")
}

android {
    namespace = "com.astrbot.android.feature.chat.api"
}

dependencies {
    api(project(":feature:conversation:api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("javax.inject:javax.inject:1")
}
