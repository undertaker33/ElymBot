plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.elymbot.android.feature.qq.presentation"

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")

    implementation(project(":core:ui"))
    implementation(project(":feature:qq:api"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.dagger:hilt-android:2.59.2")
    implementation("com.google.zxing:core:3.5.3")

    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    ksp("com.google.dagger:hilt-compiler:2.59.2")
}
