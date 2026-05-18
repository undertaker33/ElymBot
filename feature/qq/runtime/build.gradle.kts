plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.elymbot.android.feature.qq.runtime"
}

dependencies {
    implementation(project(":core:logging"))
    implementation(project(":core:runtime"))
    implementation(project(":core:runtime-audio"))
    implementation(project(":core:runtime-container"))
    implementation(project(":core:runtime-context"))
    implementation(project(":core:runtime-llm"))
    implementation(project(":core:runtime-search"))
    implementation(project(":core:runtime-session"))
    implementation(project(":feature:bot:api"))
    implementation(project(":feature:chat:runtime"))
    implementation(project(":feature:config:api"))
    implementation(project(":feature:conversation:api"))
    implementation(project(":feature:cron:runtime"))
    implementation(project(":feature:persona:api"))
    implementation(project(":feature:plugin:api"))
    implementation(project(":feature:provider:api"))
    implementation(project(":feature:qq:api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.dagger:hilt-android:2.59.2")
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")
    implementation("org.json:json:20240303")
    implementation("javax.inject:javax.inject:1")
    ksp("com.google.dagger:hilt-compiler:2.59.2")

    testImplementation("junit:junit:4.13.2")
}
