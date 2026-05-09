plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astrbot.android.feature.cron.impl"

    sourceSets {
        getByName("main") {
            res.srcDir("../../../app/src/main/res")
        }
    }
}

dependencies {
    api(project(":feature:cron:api"))
    api(project(":feature:cron:data"))
    api(project(":feature:cron:presentation"))
    api(project(":feature:cron:runtime"))
}
