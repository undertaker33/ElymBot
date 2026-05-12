plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.feature.plugin.impl"

    sourceSets {
        getByName("main") {
            res.srcDir("../../../app/src/main/res")
        }
    }
}

dependencies {
    api(project(":feature:plugin:api"))
    api(project(":feature:plugin:data"))
    api(project(":feature:plugin:presentation"))
    api(project(":feature:plugin:runtime"))
}
