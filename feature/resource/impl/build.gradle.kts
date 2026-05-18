plugins {
    id("com.android.library")
}

android {
    namespace = "com.astrbot.android.feature.resource.impl"
}

dependencies {
    implementation(project(":feature:resource:api"))
    implementation(project(":feature:resource:data"))
}
