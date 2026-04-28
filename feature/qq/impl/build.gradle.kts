plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.feature.qq.impl"
}

dependencies {
    implementation(project(":feature:qq:api"))
    implementation(project(":feature:chat:api"))
    implementation("javax.inject:javax.inject:1")
}
