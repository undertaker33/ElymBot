plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.feature.config.impl"
}

dependencies {
    implementation(project(":feature:config:api"))
    implementation(project(":feature:config:data"))
}
