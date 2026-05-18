plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.feature.resource.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:db"))
    implementation(project(":feature:config:api"))
    implementation(project(":feature:resource:api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("javax.inject:javax.inject:1")
}
