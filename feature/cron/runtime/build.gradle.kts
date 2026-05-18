plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.astrbot.android.feature.cron.runtime"

    sourceSets {
        getByName("main") {
            res.directories.add("../../../app/src/main/res")
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:logging"))
    implementation(project(":core:runtime"))
    implementation(project(":core:runtime-context"))
    implementation(project(":core:runtime-llm"))
    implementation(project(":feature:bot:api"))
    implementation(project(":feature:conversation:api"))
    implementation(project(":feature:cron:api"))
    implementation(project(":feature:plugin:api"))
    implementation(project(":feature:plugin:impl"))
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    implementation("com.google.dagger:hilt-android:2.59.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("javax.inject:javax.inject:1")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    testImplementation(project(":feature:persona:api"))
    testImplementation("junit:junit:4.13.2")
}
