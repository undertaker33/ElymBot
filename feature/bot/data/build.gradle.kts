plugins {
    id("com.android.library")
}

android {
    namespace = "com.astrbot.android.feature.bot.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:db"))
    implementation(project(":core:logging"))
    implementation(project(":feature:bot:api"))
    implementation(project(":feature:config:api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("javax.inject:javax.inject:1")
}
