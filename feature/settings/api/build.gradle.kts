plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.feature.settings.api"
}

dependencies {
    api(project(":feature:bot:api"))
    api(project(":feature:config:api"))
    api(project(":feature:conversation:api"))
    api(project(":feature:persona:api"))
    api(project(":feature:provider:api"))
    api(project(":feature:qq:api"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("javax.inject:javax.inject:1")
}
