plugins {
    id("com.android.library")
}

android {
    namespace = "com.astrbot.android.feature.persona.impl"
}

dependencies {
    implementation(project(":feature:persona:api"))
    implementation(project(":feature:persona:data"))
}
