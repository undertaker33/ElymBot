plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.feature.plugin.api"
}

dependencies {
    api(project(":core:runtime-context"))
    api(project(":feature:chat:api"))
    api(project(":feature:conversation:api"))
    api(project(":feature:persona:api"))
    api("javax.inject:javax.inject:1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    api("org.json:json:20240303")
}
