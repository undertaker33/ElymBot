plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.feature.config.impl"
}

dependencies {
    implementation(project(":feature:config:api"))
    implementation(project(":feature:config:data"))
}
