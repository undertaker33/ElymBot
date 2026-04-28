plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.feature.plugin.api"
}

dependencies {
    api(project(":feature:chat:api"))
}
