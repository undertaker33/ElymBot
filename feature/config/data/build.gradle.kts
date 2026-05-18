plugins {
    id("com.android.library")
}

android {
    namespace = "com.astrbot.android.feature.config.data"
}

dependencies {
    val roomVersion = "2.8.4"

    implementation(project(":core:common"))
    implementation(project(":core:db"))
    implementation(project(":core:logging"))
    implementation(project(":feature:config:api"))
    implementation(project(":feature:resource:api"))
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("javax.inject:javax.inject:1")
}
