plugins {
    id("com.android.library")
}

android {
    namespace = "com.astrbot.android.feature.provider.impl"
}

dependencies {
    implementation(project(":feature:provider:api"))
    implementation(project(":feature:provider:data"))
}
