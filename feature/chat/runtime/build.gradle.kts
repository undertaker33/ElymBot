plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.feature.chat.runtime"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:runtime-context"))
    implementation(project(":core:runtime-llm"))
    implementation(project(":feature:bot:api"))
    implementation(project(":feature:chat:api"))
    implementation(project(":feature:config:api"))
    implementation(project(":feature:conversation:api"))
    implementation(project(":feature:cron:api"))
    implementation(project(":feature:cron:runtime"))
    implementation(project(":feature:persona:api"))
    implementation(project(":feature:plugin:api"))
    implementation(project(":feature:plugin:runtime"))
    implementation(project(":feature:plugin:impl"))
    implementation(project(":feature:provider:api"))
    implementation("com.google.dagger:hilt-android:2.59.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("javax.inject:javax.inject:1")

    testImplementation("junit:junit:4.13.2")
}
