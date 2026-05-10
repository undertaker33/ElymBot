plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.feature.plugin.api"
}

dependencies {
    api(project(":feature:chat:api"))
    api(project(":feature:conversation:api"))
    api("javax.inject:javax.inject:1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
