plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.elymbot.android.download.impl"
}

dependencies {
    implementation(project(":download:api"))
    implementation(project(":core:db"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.dagger:hilt-android:2.59.2")
    implementation("androidx.room:room-runtime:2.8.4")
    ksp("com.google.dagger:hilt-compiler:2.59.2")

    testImplementation("junit:junit:4.13.2")
}
