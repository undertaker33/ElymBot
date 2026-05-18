plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.feature.plugin.impl"

    sourceSets {
        getByName("main") {
            res.directories.add("../../../app/src/main/res")
        }
    }
}

dependencies {
    api(project(":feature:plugin:api"))
    api(project(":feature:plugin:data"))
    api(project(":feature:plugin:presentation"))
    api(project(":feature:plugin:runtime"))
}
