plugins {
    id("com.android.library")
}

android {
    namespace = "com.astrbot.android.feature.bot.impl"
}

dependencies {
    implementation(project(":feature:bot:api"))
    implementation(project(":feature:bot:data"))
}
