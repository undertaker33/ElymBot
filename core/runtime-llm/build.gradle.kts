plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.core.runtime.llm"
}

dependencies {
    implementation(project(":core:common"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
