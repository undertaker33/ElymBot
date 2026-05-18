plugins {
    id("com.android.library")
}

android {
    namespace = "com.astrbot.android.feature.provider.api"
}

dependencies {
    api(project(":feature:voiceasset:api"))

    implementation(project(":feature:chat:api"))
    implementation(project(":feature:conversation:api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
