plugins {
    id("com.android.library")
}

android {
    namespace = "com.astrbot.android.feature.cron.impl"

    sourceSets {
        getByName("main") {
            res.directories.add("../../../app/src/main/res")
        }
    }
}

dependencies {
    api(project(":feature:cron:api"))
    api(project(":feature:cron:data"))
    api(project(":feature:cron:presentation"))
    api(project(":feature:cron:runtime"))

    testImplementation("junit:junit:4.13.2")
}
