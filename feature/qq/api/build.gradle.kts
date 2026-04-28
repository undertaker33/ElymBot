plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.feature.qq.api"
}

dependencies {
    implementation(project(":feature:bot:api"))
    implementation(project(":feature:chat:api"))
    implementation("javax.inject:javax.inject:1")
}
