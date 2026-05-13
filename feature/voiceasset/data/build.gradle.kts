plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.astrbot.android.feature.voiceasset.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:db"))
    implementation(project(":core:logging"))
    implementation(project(":core:runtime-audio"))
    implementation(project(":core:runtime-container"))
    implementation(project(":download:api"))
    implementation(project(":feature:provider:api"))
    implementation(project(":feature:voiceasset:api"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.dagger:hilt-android:2.52")
    implementation("javax.inject:javax.inject:1")
    ksp("com.google.dagger:hilt-compiler:2.52")
}
