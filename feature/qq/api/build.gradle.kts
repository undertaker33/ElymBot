plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.feature.qq.api"
}

dependencies {
    implementation(project(":feature:bot:api"))
    implementation(project(":feature:chat:api"))
    implementation(project(":feature:conversation:api"))
    api(project(":feature:plugin:api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("javax.inject:javax.inject:1")
}
