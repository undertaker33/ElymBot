plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.feature.bot.impl"
}

dependencies {
    implementation(project(":feature:bot:api"))
    implementation(project(":feature:bot:data"))
}
