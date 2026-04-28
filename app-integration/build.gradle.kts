plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.astrbot.android.app.integration"
}

dependencies {
    val roomVersion = "2.6.1"

    implementation(project(":core:common"))
    implementation(project(":core:db"))
    implementation(project(":core:runtime"))
    implementation(project(":feature:bot:api"))
    implementation(project(":feature:bot:impl"))
    implementation(project(":feature:chat:api"))
    implementation(project(":feature:chat:impl"))
    implementation(project(":feature:config:api"))
    implementation(project(":feature:config:impl"))
    implementation(project(":feature:cron:api"))
    implementation(project(":feature:cron:impl"))
    implementation(project(":feature:persona:api"))
    implementation(project(":feature:persona:impl"))
    implementation(project(":feature:plugin:api"))
    implementation(project(":feature:plugin:impl"))
    implementation(project(":feature:provider:api"))
    implementation(project(":feature:provider:impl"))
    implementation(project(":feature:qq:api"))
    implementation(project(":feature:qq:impl"))
    implementation(project(":feature:resource:api"))
    implementation(project(":feature:resource:impl"))
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("javax.inject:javax.inject:1")
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
}
