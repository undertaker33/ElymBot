plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.feature.qq.impl"
}

dependencies {
    implementation(project(":feature:qq:api"))
    implementation(project(":feature:qq:runtime"))
    implementation("javax.inject:javax.inject:1")

    testImplementation("junit:junit:4.13.2")
}
