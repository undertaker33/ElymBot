plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.elymbot.android.feature.provider.runtime"
}

dependencies {
    implementation(project(":core:runtime-audio"))
    implementation(project(":core:runtime-llm"))
    implementation(project(":feature:conversation:api"))
    implementation(project(":feature:provider:api"))
    implementation(project(":feature:voiceasset:api"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.google.dagger:hilt-android:2.59.2")
    implementation("javax.inject:javax.inject:1")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
}
