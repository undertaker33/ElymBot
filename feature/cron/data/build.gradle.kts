plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.feature.cron.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:db"))
    implementation(project(":core:logging"))
    implementation(project(":feature:cron:api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("javax.inject:javax.inject:1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
