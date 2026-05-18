plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.feature.provider.impl"
}

dependencies {
    implementation(project(":feature:provider:api"))
    implementation(project(":feature:provider:data"))
}
