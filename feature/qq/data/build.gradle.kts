plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.astrbot.android.feature.qq.data"
}

dependencies {
    implementation(project(":core:db"))
    implementation(project(":core:logging"))
    implementation(project(":core:network"))
    implementation(project(":core:runtime"))
    implementation(project(":core:runtime-container"))
    implementation(project(":feature:bot:api"))
    implementation(project(":feature:config:api"))
    implementation(project(":feature:conversation:api"))
    implementation(project(":feature:qq:api"))
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.dagger:hilt-android:2.59.2")
    implementation("javax.inject:javax.inject:1")
    implementation("org.json:json:20240303")
    ksp("com.google.dagger:hilt-compiler:2.59.2")

    testImplementation("junit:junit:4.13.2")
}
