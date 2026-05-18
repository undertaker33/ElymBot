plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.feature.provider.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:db"))
    implementation(project(":core:logging"))
    implementation(project(":core:runtime-search"))
    implementation(project(":feature:provider:api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("javax.inject:javax.inject:1")
}
