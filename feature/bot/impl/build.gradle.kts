plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.feature.bot.impl"
}

dependencies {
    implementation(project(":feature:bot:api"))
    implementation(project(":feature:bot:data"))
}
