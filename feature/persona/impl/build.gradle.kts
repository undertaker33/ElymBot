plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.feature.persona.impl"
}

dependencies {
    implementation(project(":feature:persona:api"))
    implementation(project(":feature:persona:data"))
}
