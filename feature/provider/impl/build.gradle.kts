plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.feature.provider.impl"
}

dependencies {
    implementation(project(":feature:provider:api"))
    implementation(project(":feature:provider:data"))
}
