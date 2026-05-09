plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.feature.resource.impl"
}

dependencies {
    implementation(project(":feature:resource:api"))
    implementation(project(":feature:resource:data"))
}
