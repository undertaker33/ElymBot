plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.core.runtime.secret"
}

dependencies {
    implementation(project(":core:logging"))

    implementation("javax.inject:javax.inject:1")

    testImplementation("junit:junit:4.13.2")
}
