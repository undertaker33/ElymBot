plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.feature.persona.impl"
}

dependencies {
    implementation(project(":feature:persona:api"))
    implementation(project(":feature:persona:data"))
}
